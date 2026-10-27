package net.bullettrain.xenopixelsmod.space.pod.transfer;

import net.bullettrain.xenopixelsmod.compat.sable.SableLoadingTickets;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.SubLevelDimensionDataMapper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.UUID;

/**
 * Moves a pod body, its measured motion and its loading tickets between levels, and can rebind an optional rider
 * that is already in the destination level. This class does not change a player's dimension.
 *
 * <p>This synchronous server-thread transaction does not yield a server tick. With separate containers, the
 * destination is loaded and verified while the source is still registered, so two live incarnations briefly
 * coexist across those containers before commit removes the source. Within one container, the source must be
 * released before a copy can reuse its UUID and plot slot, so there is a synchronous registration gap; a failure
 * reloads the source from the serialized tag held in memory. The result reports which body survived when the call
 * returns; this is not an atomicity guarantee for callbacks invoked during serialization or removal.
 *
 * <p>Sable 2.0.3 keeps force-load tickets outside the serialized body: removing a body with
 * {@link SubLevelRemovalReason#REMOVED} deletes every ticket recorded for its UUID (SubLevelTicketLoadingSystem).
 * The source's tickets are therefore captured before teardown, added to the destination copy, and put back on a
 * rollback survivor. Across containers the copy is ticketed before the source is removed; that order is safe only
 * because each ServerSubLevelContainer owns its own ticket maps, so the source's REMOVED wipe cannot reach the
 * destination's entries. Do not reorder these phases on the assumption that the maps are shared.
 *
 * <p>Server-thread only. The production handoff calls this for the pod-only leg from {@link
 * net.bullettrain.xenopixelsmod.space.SpaceHandoffPass} podLeg; the player is moved separately by that
 * compensating protocol. The optional cross-container rider parameter is not used by that production call and has
 * no live cross-dimension acceptance yet.
 */
public final class PodTransferTransaction {
    public enum Status {
        /** Destination verified, motion and tickets restored, source removed. */
        TRANSFERRED,
        /** Refused before anything was taken apart; the source is exactly as it was. */
        REFUSED,
        /** Something was taken apart and put back; the source is valid again and the destination is clean. */
        ROLLED_BACK,
        /** The source body is back, but its motion, tickets or rider binding could not be fully restored. */
        RECOVERY_INCOMPLETE,
        /** The destination failed and the source could not be restored. Requires operator attention. */
        SOURCE_LOST
    }

    /**
     * @param phase        the last phase entered, which is where a failure stopped
     * @param destination  the surviving body: the destination when transferred, otherwise the source
     * @param storageShift how far the copy's storage (plot) frame moved, in blocks, when it was placed in another
     *                     plot cell ({@link PlotCellRemap}); zero otherwise. Storage-frame values a caller captured
     *                     from the source (a rider's plot-local seat) are off by this much on the copy.
     */
    public record Result(Status status, String phase, String reason, ServerSubLevel destination,
                         Vector3dc storageShift) {
        public Result(Status status, String phase, String reason, ServerSubLevel destination) {
            this(status, phase, reason, destination, new Vector3d());
        }

        public Result {
            storageShift = storageShift == null ? new Vector3d() : new Vector3d(storageShift);
        }

        @Override
        public Vector3dc storageShift() {
            return new Vector3d(storageShift);
        }

        public boolean success() { return status == Status.TRANSFERRED; }
    }

    /**
     * Test-only control flow: a GameTest installs a hook that throws on entering a phase, so a rollback branch can
     * be reached deterministically. It proves this class's recovery path, not how Sable itself fails. Null in
     * production; never set outside tests.
     */
    @FunctionalInterface
    interface PhaseHook {
        void entered(String phase);
    }

    static volatile PhaseHook phaseHook;

    /** Test-only: fires at each recovery step (release-copy-ticket, remove-copy, reload-source). Null in production. */
    static volatile PhaseHook recoveryHook;

    /** Same key ClassicEggPodHull stamps and PodTransferSnapshot reads. */
    private static final String DATA_POD_ID = "xenopixelsmod:space_pod_id";
    private static final double VELOCITY_TOLERANCE = 1.0E-4;

    private PodTransferTransaction() {}

    /**
     * Transfers {@code sourceBody} into {@code destination}.
     *
     * @param rider the player bound to the pod, or null when nobody is riding it
     */
    public static Result transfer(ServerLevel source, ServerLevel destination, ServerSubLevel sourceBody,
                                  Player rider) {
        return transfer(source, destination, sourceBody, rider, null);
    }

    /**
     * Transfers a pod and optionally places the destination body's rotation point at a requested world position.
     * The source pose is verified before placement; the loaded copy is teleported, its bounds/interpolation state
     * refreshed, and then its captured motion and rider binding are restored.
     */
    public static Result transfer(ServerLevel source, ServerLevel destination, ServerSubLevel sourceBody,
                                  Player rider, Vector3dc destinationPosition) {
        return transfer(source, destination, sourceBody, rider, destinationPosition, null);
    }

    /**
     * As {@link #transfer(ServerLevel, ServerLevel, ServerSubLevel, Player, Vector3dc)}, and when
     * {@code destinationLinearVelocity} is non-null the copy's linear velocity is set to it (Sable body units,
     * blocks per second) after the captured motion is restored, so a frame change lands inside the transaction
     * rather than being patched on by the caller afterwards. Angular velocity is restored unchanged.
     */
    public static Result transfer(ServerLevel source, ServerLevel destination, ServerSubLevel sourceBody,
                                  Player rider, Vector3dc destinationPosition, Vector3dc destinationLinearVelocity) {
        if (source == null || destination == null || sourceBody == null)
            return new Result(Status.REFUSED, "guard", "source level, destination level and source body are required",
                    sourceBody);
        Running transaction;
        try {
            transaction = new Running(source, destination, sourceBody, rider, destinationPosition,
                    destinationLinearVelocity);
        } catch (RuntimeException setup) {
            return new Result(Status.REFUSED, "guard", "transaction setup failed: " + setup, sourceBody);
        }
        try {
            return transaction.run();
        } catch (RuntimeException failure) {
            // An Error (out of memory, stack overflow) is not caught: running a multi-step rollback after one is
            // more likely to corrupt state than to restore it.
            XenoPixelsMod.LOGGER.error("Pod transfer threw in phase {}: pod {}", transaction.phase,
                    sourceBody.getUniqueId(), failure);
            return transaction.fail("failed in " + transaction.phase + ": " + failure);
        }
    }

    /**
     * Gives up the force-load ticket a successful transfer retains. The transferred pod is held only as long as
     * some caller owns that ticket, so the handoff has to end somewhere; a failure path releases it on its own.
     */
    public static void releaseTicket(ServerSubLevel destination, UUID podId) {
        PodTransferDestination.release(destination, podId.toString());
    }

    /** The stable pod identity a Classic Egg pod body carries, or null when {@code body} is not a pod. */
    public static UUID podId(ServerSubLevel body) {
        if (body == null || body.isRemoved()) return null;
        var data = body.getUserDataTag();
        return data != null && data.hasUUID(DATA_POD_ID) ? data.getUUID(DATA_POD_ID) : null;
    }

    /** How a failure settles once nothing could be reloaded: torn-down work is a rollback, untouched work a refusal. */
    static Status unwound(boolean survived, boolean tornDown) {
        if (!survived) return Status.SOURCE_LOST;
        return tornDown ? Status.ROLLED_BACK : Status.REFUSED;
    }

    /** Adds one captured ticket to {@code body}; the wildcard is captured here, so no unchecked cast is needed. */
    private static <T> void addTicket(ServerSubLevelContainer container, ServerSubLevel body,
                                      SubLevelLoadingTicket<T> ticket) {
        if (SableLoadingTickets.type(ticket) == null || SableLoadingTickets.key(ticket) == null)
            throw new IllegalStateException("captured ticket has no type or key: " + ticket);
        // false only means an equal ticket is already recorded for this UUID (the body instance is added either
        // way), so the result is checked by recorded() rather than trusted.
        container.addForceLoadTicket(body, SableLoadingTickets.type(ticket), SableLoadingTickets.key(ticket));
    }

    /** True when the container persists an equal ticket (type and key) for {@code bodyId}. */
    private static <T> boolean recorded(ServerSubLevelContainer container, UUID bodyId, SubLevelLoadingTicket<T> ticket) {
        var info = container.getAllTickets().get(bodyId);
        return info != null && info.tickets().contains(new SubLevelLoadingTicket<>(SableLoadingTickets.type(ticket), bodyId, SableLoadingTickets.key(ticket)));
    }

    /** Re-adds every captured ticket and names the first one that is not recorded afterwards, or null. */
    private static String restoreTickets(ServerSubLevelContainer container, ServerSubLevel body,
                                         List<SubLevelLoadingTicket<?>> tickets) {
        for (SubLevelLoadingTicket<?> ticket : tickets) {
            addTicket(container, body, ticket);
            if (!recorded(container, body.getUniqueId(), ticket)) return "ticket " + ticket + " was not recorded";
        }
        return null;
    }

    private static final class Running {
        private final ServerLevel source;
        private final ServerLevel destination;
        private final ServerSubLevel sourceBody;
        private final Player rider;
        private final Vector3d destinationPosition;
        private final Vector3d destinationLinearVelocity;
        private final ServerSubLevelContainer sourceContainer;
        private final boolean sameContainer;
        private String phase = "guard";
        private String ticketKey;
        private PodTransferSnapshot snapshot;
        private PodRiderBinding.Captured capturedRider;
        private List<SubLevelLoadingTicket<?>> sourceTickets = List.of();
        private SubLevelData serialized;
        private ServerSubLevel copy;
        private boolean copyLoaded;
        private boolean copyHeld;
        private boolean sourceRemoved;
        private boolean sourceStillRegistered;
        private boolean riderBindingAttempted;
        private boolean recovering;
        private String failedPhase;
        private String reloadFailure;
        private String cellFailureReason;
        private final Vector3d storageShift = new Vector3d();

        Running(ServerLevel source, ServerLevel destination, ServerSubLevel sourceBody, Player rider,
                Vector3dc destinationPosition, Vector3dc destinationLinearVelocity) {
            this.source = source;
            this.destination = destination;
            this.sourceBody = sourceBody;
            this.rider = rider;
            this.destinationPosition = destinationPosition == null ? null : new Vector3d(destinationPosition);
            this.destinationLinearVelocity = destinationLinearVelocity == null ? null
                    : new Vector3d(destinationLinearVelocity);
            this.sourceContainer = SubLevelContainer.getContainer(source);
            this.sameContainer = source == destination;
        }

        private void enter(String next) {
            phase = next;
            PhaseHook hook = phaseHook;
            if (hook != null) hook.entered(next);
        }

        Result run() {
            enter("precheck");
            if (!source.getServer().isSameThread())
                return fail("pod transfer must run on the server thread");
            if (sourceContainer == null) return fail("the source level has no Sable container");
            if (sourceBody.isRemoved()) return fail("the source pod body no longer exists");
            if (sourceContainer.getSubLevel(sourceBody.getUniqueId()) != sourceBody)
                return fail("the source pod body is not registered in its own container");

            enter("capture");
            capturedRider = rider != null && PodRiderBinding.trackedBody(rider) == sourceBody
                    ? PodRiderBinding.capture(rider, sourceBody) : null;
            RigidBodyHandle handle = RigidBodyHandle.of(sourceBody);
            if (handle == null || !handle.isValid()) return fail("the source pod physics body is not ready");
            snapshot = PodTransferSnapshot.of(sourceBody, handle.getLinearVelocity(new Vector3d()),
                    handle.getAngularVelocity(new Vector3d()), capturedRider);
            ticketKey = snapshot.podId().toString();
            // PodRiderBinding.restore only sets tracking and position inside the player's current level; it never
            // changes dimension. A rider still outside the destination level would be bound to a body in another
            // level and moved to destination coordinates in the wrong dimension, so refuse before any teardown.
            if (capturedRider != null && rider.level() != destination)
                return fail("the rider is in " + rider.level().dimension().location() + " but the pod is going to "
                        + destination.dimension().location() + "; a rider is only rebound inside the level it is in");
            var ticketInfo = sourceContainer.getAllTickets().get(sourceBody.getUniqueId());
            sourceTickets = ticketInfo == null ? List.of() : List.copyOf(ticketInfo.tickets());

            enter("serialize");
            serialized = SubLevelSerializer.toData(sourceBody, List.of());

            enter("map");
            SubLevelData destinationData = SubLevelDimensionDataMapper
                    .mapToDestination(serialized, source, destination);

            enter("validate-destination");
            if (!sameContainer) {
                // fullyLoad allocates at the serialized cell; move the copy when that cell is taken in the
                // destination, or when the containers' storage frames differ (different origins).
                SubLevelData placed = cellFor(destinationData);
                if (placed == null) return fail(cellFailureReason == null
                        ? "destination plot cell cannot be safely remapped" : cellFailureReason);
                destinationData = placed;
            }
            String refusal = PodTransferDestination.refusal(source, destination, sourceBody, destinationData,
                    destinationPosition);
            if (refusal != null) return fail(refusal);

            if (sameContainer) {
                // The copy reuses the serialized plot slot, so in one container the source has to give it up
                // first. Loading the same body UUID twice would overwrite the registration and orphan the source.
                enter("release-source-slot");
                String stuck = removeSource();
                if (stuck != null) return fail(stuck);
            }

            enter("load");
            copy = SubLevelSerializer.fullyLoad(destination, destinationData);
            if (copy == null) return fail("Sable refused the destination load (see its log lines)");
            copyLoaded = true;

            enter("ticket");
            PodTransferDestination.hold(copy, ticketKey);
            copyHeld = PodTransferDestination.holds(destination, copy.getUniqueId(), ticketKey);
            if (!copyHeld) return fail("the destination force-load ticket was not recorded");

            enter("verify");
            String invalid = PodTransferDestination.verification(copy, snapshot, destination, ticketKey,
                    destinationPosition != null);
            if (invalid != null) return fail(invalid);

            enter("carry-tickets");
            String ticketFailure = restoreTickets(SubLevelContainer.getContainer(destination), copy, sourceTickets);
            if (ticketFailure != null) return fail("source tickets did not carry over: " + ticketFailure);

            if (destinationPosition != null) {
                enter("place");
                String placementFailure = placeCopy();
                if (placementFailure != null) return fail(placementFailure);
            }

            enter("motion");
            String motionFailure = PodTransferDestination.restoreMotion(copy, snapshot);
            if (motionFailure != null) return fail(motionFailure);
            if (destinationLinearVelocity != null) {
                enter("frame-velocity");
                String frameFailure = applyLinearVelocity();
                if (frameFailure != null) return fail(frameFailure);
            }

            if (capturedRider != null) {
                enter("rider");
                // Mark the attempt first: restore() mutates tracking/position before verifying the binding,
                // and a failed postcondition still needs the rollback path to put the rider back.
                riderBindingAttempted = true;
                if (!PodRiderBinding.restore(rider, copy, capturedRider, storageShift))
                    return fail("the rider did not end up bound to the destination body");
            }

            if (!sourceRemoved) {
                enter("remove-source");
                String stuck = removeSource();
                if (stuck != null) return fail(stuck);
            }

            enter("commit");
            ServerSubLevelContainer destinationContainer = SubLevelContainer.getContainer(destination);
            if (destinationContainer.getSubLevel(copy.getUniqueId()) != copy)
                return fail("the destination body is not registered under the transferred identity");
            XenoPixelsMod.LOGGER.info("Pod transfer committed: pod {} body {} from {} to {} at {}",
                    snapshot.podId(), snapshot.bodyId(), source.dimension().location(),
                    destination.dimension().location(), copy.logicalPose().position());
            return new Result(Status.TRANSFERRED, phase, null, copy, storageShift);
        }

        /**
         * The serialized data placed in a free destination cell, the data unchanged when its own cell is free and
         * the storage frames match, or null with {@code cellFailureReason} set when the slot cannot be mapped safely.
         */
        private SubLevelData cellFor(SubLevelData data) {
            if (!data.fullTag().contains("plot", Tag.TAG_COMPOUND)) {
                cellFailureReason = "serialized pod has no plot tag";
                return null;
            }
            CompoundTag plot = data.fullTag().getCompound("plot");
            if (!plot.contains("plot_x", Tag.TAG_INT) || !plot.contains("plot_z", Tag.TAG_INT)) {
                cellFailureReason = "serialized pod has no valid local plot cell";
                return null;
            }
            ServerSubLevelContainer target = SubLevelContainer.getContainer(destination);
            if (target == null) {
                cellFailureReason = "destination has no Sable plot container";
                return null;
            }
            if (target.getLogPlotSize() != sourceContainer.getLogPlotSize()) {
                cellFailureReason = "source and destination Sable plot sizes differ; pod remap is unsafe";
                return null;
            }
            int x = plot.getInt("plot_x"), z = plot.getInt("plot_z");
            int cellX = x, cellZ = z;
            if (!cellFree(target, x, z)) {
                var free = PlotCellRemap.freeCell(target.getLogSideLength(), target.getOccupancy(),
                        (cx, cz) -> target.getSubLevel(cx, cz) == null);
                if (free.isEmpty()) {
                    cellFailureReason = "destination Sable plot grid has no safely available cell";
                    return null;
                }
                cellX = free.get().x;
                cellZ = free.get().y;
            }
            var sourceOrigin = sourceContainer.getOrigin();
            var targetOrigin = target.getOrigin();
            int log = target.getLogPlotSize();
            final PlotCellRemap.Cell cell;
            try {
                cell = new PlotCellRemap.Cell(cellX, cellZ,
                        PlotCellRemap.shiftBlocks(x, sourceOrigin.x, cellX, targetOrigin.x, log),
                        PlotCellRemap.shiftBlocks(z, sourceOrigin.y, cellZ, targetOrigin.y, log));
            } catch (IllegalArgumentException invalidShift) {
                cellFailureReason = "serialized pod storage-frame shift is outside the supported coordinate range";
                return null;
            }
            if (cell.x() == x && cell.z() == z && cell.shiftX() == 0 && cell.shiftZ() == 0) return data;
            var remapped = PlotCellRemap.remap(data, cell);
            if (remapped.isEmpty()) {
                cellFailureReason = "serialized pod has an unsupported or overflowing storage-frame tag; refusing unsafe remap";
                return null;
            }
            storageShift.set(cell.shiftX(), 0, cell.shiftZ());
            XenoPixelsMod.LOGGER.info("Pod transfer: pod {} moves from plot cell {},{} to free cell {},{} in {} "
                            + "(storage shift {}, {})", snapshot.podId(), x, z, cell.x(), cell.z(),
                    destination.dimension().location(), cell.shiftX(), cell.shiftZ());
            return remapped.get();
        }

        /** Free for a new body: occupancy bit clear AND no plot still holding it (Sable 2.0.x slot reuse). */
        private static boolean cellFree(ServerSubLevelContainer container, int x, int z) {
            int logSideLength = container.getLogSideLength();
            if (logSideLength < 0 || logSideLength > 15) return false;
            int side = 1 << logSideLength;
            if (x < 0 || z < 0 || x >= side || z >= side) return false;
            return !container.getOccupancy().get(container.getIndex(x, z)) && container.getSubLevel(x, z) == null;
        }

        /**
         * Removes the source and confirms it left the registry. A source that is still registered is never treated
         * as removed: reloading the serialized tag over it would put a second incarnation under the same UUID.
         *
         * @return why the removal did not take, or null
         */
        private String removeSource() {
            sourceContainer.removeSubLevel(sourceBody, SubLevelRemovalReason.REMOVED);
            if (sourceContainer.getSubLevel(snapshot.bodyId()) != null) {
                sourceStillRegistered = true;
                return "the source body is still registered after removal";
            }
            sourceRemoved = true;
            return null;
        }

        /** Sets the copy's linear velocity to the destination-frame value; Sable only exposes an additive setter. */
        private String applyLinearVelocity() {
            Vector3d target = destinationLinearVelocity;
            if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z))
                return "destination pod velocity is not finite";
            RigidBodyHandle handle = RigidBodyHandle.of(copy);
            if (handle == null || !handle.isValid()) return "destination physics body is not ready for its velocity";
            Vector3d current = handle.getLinearVelocity(new Vector3d());
            handle.addLinearAndAngularVelocity(new Vector3d(target).sub(current), new Vector3d());
            Vector3d settled = handle.getLinearVelocity(new Vector3d());
            if (settled.distanceSquared(target) > VELOCITY_TOLERANCE * VELOCITY_TOLERANCE)
                return "destination pod velocity settled at " + settled + " instead of " + target;
            return null;
        }

        /** Places the verified copy in the destination frame before restoring velocity and the rider offset. */
        private String placeCopy() {
            if (!Double.isFinite(destinationPosition.x) || !Double.isFinite(destinationPosition.y)
                    || !Double.isFinite(destinationPosition.z)) return "destination pod position is not finite";
            var currentPosition = copy.logicalPose().position();
            var bounds = copy.boundingBox();
            double deltaY = destinationPosition.y - currentPosition.y();
            double targetMinY = bounds.minY() + deltaY;
            double targetMaxY = bounds.maxY() + deltaY;
            if (targetMinY < destination.getMinBuildHeight() || targetMaxY > destination.getMaxBuildHeight()) {
                return "destination pod spans Y " + targetMinY + ".." + targetMaxY
                        + ", outside the destination build height [" + destination.getMinBuildHeight() + ", "
                        + destination.getMaxBuildHeight() + ")";
            }
            RigidBodyHandle handle = RigidBodyHandle.of(copy);
            if (handle == null || !handle.isValid()) return "destination physics body is not ready for placement";
            var orientation = new org.joml.Quaterniond(copy.logicalPose().orientation());
            handle.teleport(destinationPosition, orientation);
            copy.updateBoundingBox();
            copy.forceUpdateGlobalBounds();
            copy.updateLastPose();
            var settled = copy.logicalPose();
            if (settled.position().distanceSquared(destinationPosition) > 1.0E-12)
                return "destination pod position did not settle at " + destinationPosition
                        + " (got " + settled.position() + ")";
            var placedBounds = copy.boundingBox();
            if (placedBounds.minY() < destination.getMinBuildHeight()
                    || placedBounds.maxY() > destination.getMaxBuildHeight()) {
                return "placed pod spans Y " + placedBounds.minY() + ".." + placedBounds.maxY()
                        + ", outside the destination build height";
            }
            return null;
        }

        /**
         * Puts the world back and never throws a RuntimeException: each recovery step is guarded, a nested call is
         * reported instead of repeating teardown, and if recovery itself fails the result names the body that is
         * actually registered. Order matters: in one container the destination copy has to vacate the shared plot
         * slot before the source can be reloaded into it, and the rider only follows a body that is already valid.
         */
        Result fail(String reason) {
            if (recovering) {
                XenoPixelsMod.LOGGER.error("Pod transfer recovery re-entered in phase {}: {}", phase, reason);
                return settleFromRegistry(reason, "recovery was re-entered");
            }
            recovering = true;
            failedPhase = phase;
            try {
                return unwind(reason);
            } catch (RuntimeException recoveryFailure) {
                XenoPixelsMod.LOGGER.error("Pod transfer recovery threw after {} failed: pod {}", failedPhase,
                        sourceBody.getUniqueId(), recoveryFailure);
                return settleFromRegistry(reason, "recovery threw: " + recoveryFailure);
            }
        }

        private Result unwind(String reason) {
            String notes = null;
            if (ticketKey != null && copyHeld && copy != null) {
                try {
                    recoveryStep("release-copy-ticket");
                    PodTransferDestination.release(copy, ticketKey);
                } catch (RuntimeException releaseFailure) {
                    // Harmless on its own: removing the copy with REMOVED drops its tickets as well.
                    notes = appendFailure(notes, "copy ticket release threw: " + releaseFailure);
                }
            }
            copyHeld = false;
            if (copy != null && !copy.isRemoved()) {
                // Deliberately unguarded: a copy that cannot be removed still holds the shared slot, so reloading
                // the source would collide. fail() then reports what is registered instead.
                recoveryStep("remove-copy");
                SubLevelContainer.getContainer(destination).removeSubLevel(copy, SubLevelRemovalReason.REMOVED);
            }
            if (sourceStillRegistered) return keepRegisteredSource(withNotes(reason, notes));
            ServerSubLevel survivor = sourceBody;
            if (sourceRemoved) {
                phase = "rollback-source";
                ServerSubLevel restored = reloadSource();
                String noted = withNotes(withNotes(reason, notes), reloadFailure);
                if (restored != null && snapshot.identityMatches(restored))
                    return recoverRestored(restored, noted);
                String orphan = restored == null ? "" : removeOrphan(restored);
                XenoPixelsMod.LOGGER.error("Pod transfer LOST the source: pod {} could not be restored to {} ({}{})",
                        snapshot.podId(), source.dimension().location(), noted, orphan);
                safeClearRider();
                return new Result(Status.SOURCE_LOST, failedPhase, noted + orphan, null);
            }
            reason = withNotes(reason, notes);
            if (riderBindingAttempted && capturedRider != null && survivor != null && !survivor.isRemoved()) {
                try {
                    if (!PodRiderBinding.restore(rider, survivor, capturedRider)) {
                        safeClearRider();
                        return new Result(Status.RECOVERY_INCOMPLETE, failedPhase,
                                reason + "; rider tracking did not return to the source body", survivor);
                    }
                } catch (RuntimeException recoveryException) {
                    safeClearRider();
                    return new Result(Status.RECOVERY_INCOMPLETE, failedPhase,
                            reason + "; rider recovery threw: " + recoveryException, survivor);
                }
            }
            return new Result(unwound(survivor != null, copyLoaded || riderBindingAttempted), failedPhase, reason,
                    survivor);
        }

        /**
         * Reloads the serialized source. A load that throws after registering the body is not loaded again; the
         * registered body is returned so the identity check decides whether it is the pod or an orphan.
         */
        private ServerSubLevel reloadSource() {
            if (serialized == null) return null;
            try {
                recoveryStep("reload-source");
                return SubLevelSerializer.fullyLoad(source, serialized);
            } catch (RuntimeException loadFailure) {
                reloadFailure = "source reload threw: " + loadFailure;
                return sourceContainer.getSubLevel(snapshot.bodyId()) instanceof ServerSubLevel partial
                        && !partial.isRemoved() ? partial : null;
            }
        }

        /**
         * Last resort when recovery itself failed or was re-entered: report the body that is actually registered
         * rather than guessing. A registered source (or same-UUID copy) means the pod exists but is not known to be
         * consistent; nothing registered means it is lost.
         */
        private Result settleFromRegistry(String reason, String problem) {
            ServerSubLevel registered = null;
            try {
                UUID id = snapshot != null ? snapshot.bodyId() : sourceBody.getUniqueId();
                if (sourceContainer.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved())
                    registered = body;
            } catch (RuntimeException lookupFailure) {
                problem = problem + "; registry lookup threw: " + lookupFailure;
            }
            if (registered == null && copy != null && !copy.isRemoved()) {
                registered = copy;
                problem = problem + "; only the destination copy is still registered";
            }
            Status status = registered != null ? Status.RECOVERY_INCOMPLETE : Status.SOURCE_LOST;
            return new Result(status, failedPhase != null ? failedPhase : phase, reason + "; " + problem, registered);
        }

        private void recoveryStep(String step) {
            phase = step;
            PhaseHook hook = recoveryHook;
            if (hook != null) hook.entered(step);
        }

        private void safeClearRider() {
            if (rider == null) return;
            try {
                PodRiderBinding.clear(rider);
            } catch (RuntimeException clearFailure) {
                XenoPixelsMod.LOGGER.error("Pod transfer could not clear rider tracking", clearFailure);
            }
        }

        private static String withNotes(String reason, String notes) {
            return notes == null ? reason : reason + "; " + notes;
        }

        /** The reloaded source is the pod: put back its motion, its tickets (REMOVED cleared them) and its rider. */
        private Result recoverRestored(ServerSubLevel restored, String reason) {
            String recoveryFailure = null;
            try {
                recoveryFailure = PodTransferDestination.restoreMotion(restored, snapshot);
            } catch (RuntimeException recoveryException) {
                recoveryFailure = "source motion recovery threw: " + recoveryException;
            }
            try {
                String ticketFailure = restoreTickets(sourceContainer, restored, sourceTickets);
                if (ticketFailure != null) recoveryFailure = appendFailure(recoveryFailure, ticketFailure);
            } catch (RuntimeException recoveryException) {
                recoveryFailure = appendFailure(recoveryFailure, "ticket recovery threw: " + recoveryException);
            }
            // The source was already removed in this branch, including before the destination-side
            // rider restore could have happened. Rebind every captured rider to the restored instance.
            if (capturedRider != null) recoveryFailure = rebindRider(restored, recoveryFailure);
            if (recoveryFailure != null) {
                XenoPixelsMod.LOGGER.error("Pod transfer recovery incomplete: pod {} restored to {} but {} ({})",
                        snapshot.podId(), source.dimension().location(), recoveryFailure, reason);
                return new Result(Status.RECOVERY_INCOMPLETE, failedPhase,
                        reason + "; recovery incomplete: " + recoveryFailure, restored);
            }
            XenoPixelsMod.LOGGER.warn("Pod transfer rolled back: pod {} restored to {} ({})",
                    snapshot.podId(), source.dimension().location(), reason);
            return new Result(Status.ROLLED_BACK, failedPhase, reason, restored);
        }

        /**
         * Removal returned but the source is still registered. Nothing is reloaded (that would duplicate its UUID);
         * the registered body is kept, its tickets and rider are put back as far as possible, and the outcome is
         * reported as incomplete because Sable did not behave as the transaction expected.
         */
        private Result keepRegisteredSource(String reason) {
            ServerSubLevel registered = sourceContainer.getSubLevel(snapshot.bodyId()) instanceof ServerSubLevel body
                    ? body : null;
            String recoveryFailure = "source removal did not unregister it; no duplicate was loaded";
            if (registered == null || registered.isRemoved()) {
                if (capturedRider != null) safeClearRider();
                return new Result(Status.RECOVERY_INCOMPLETE, failedPhase, reason + "; " + recoveryFailure
                        + "; the registered source is not usable", registered);
            }
            try {
                String ticketFailure = restoreTickets(sourceContainer, registered, sourceTickets);
                if (ticketFailure != null) recoveryFailure = appendFailure(recoveryFailure, ticketFailure);
            } catch (RuntimeException recoveryException) {
                recoveryFailure = appendFailure(recoveryFailure, "ticket recovery threw: " + recoveryException);
            }
            if (capturedRider != null) recoveryFailure = rebindRider(registered, recoveryFailure);
            XenoPixelsMod.LOGGER.error("Pod transfer kept a source that removal left registered: pod {} ({}; {})",
                    snapshot.podId(), reason, recoveryFailure);
            return new Result(Status.RECOVERY_INCOMPLETE, failedPhase, reason + "; " + recoveryFailure, registered);
        }

        private String rebindRider(ServerSubLevel body, String recoveryFailure) {
            try {
                if (!PodRiderBinding.restore(rider, body, capturedRider)) {
                    safeClearRider();
                    return appendFailure(recoveryFailure, "rider tracking did not rebind to the restored source");
                }
                return recoveryFailure;
            } catch (RuntimeException recoveryException) {
                safeClearRider();
                return appendFailure(recoveryFailure, "rider recovery threw: " + recoveryException);
            }
        }

        /** A reload that is not this pod must not stay live and untracked; remove it and say what happened. */
        private String removeOrphan(ServerSubLevel orphan) {
            try {
                if (!orphan.isRemoved()) sourceContainer.removeSubLevel(orphan, SubLevelRemovalReason.REMOVED);
                return "; an identity-mismatched reload was removed";
            } catch (RuntimeException removalFailure) {
                return "; an identity-mismatched reload could not be removed: " + removalFailure;
            }
        }

        private static String appendFailure(String prior, String next) {
            return prior == null ? next : prior + "; " + next;
        }
    }
}
