package net.bullettrain.xenopixelsmod.space.pod.transfer;

import net.bullettrain.xenopixelsmod.compat.sable.SableLoadingTickets;
import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * The destination half of a pod transfer: reserve the plot slot, check that what came back is the pod that went
 * in, and put the motion back. Server thread only, and every method reports a refusal reason instead of
 * half-applying a change, so the caller's rollback always knows how far the transaction got.
 */
public final class PodTransferDestination {
    /** Separate from the hull-build handoff ticket so a pod can hold both at once without either being shadowed. */
    static final SubLevelLoadingTicketType<String> TRANSFER_TICKET = SubLevelLoadingTicketType.create(
            ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "space_pod_transfer"), Codec.STRING);

    private static final double POSE_TOLERANCE_BLOCKS = 1.0E-3;
    /** Compared in the same units as Sable's velocity getter; the runtime unit has not been measured here. */
    private static final double VELOCITY_TOLERANCE = 1.0E-4;

    private PodTransferDestination() {}

    /** Forces transfer-ticket type registration during mod construction, before Sable decodes saved tickets. */
    public static void bootstrap() {}

    /**
     * Why this pair of levels cannot carry a pod yet, or null when it can.
     *
     * <p>Different build-height layouts are allowed when the already-mapped plot sections and body bounds fit the
     * destination. {@link net.bullettrain.xenopixelsmod.space.physics.SubLevelDimensionDataMapper} maps section
     * indices through absolute section Y; Sable's loader preserves the serialized world-space pose. Sections that
     * do not exist in the destination are rejected by the mapper before this reservation check.
     */
    public static String refusal(ServerLevel source, ServerLevel destination, ServerSubLevel sourceBody,
                                 SubLevelData data) {
        return refusal(source, destination, sourceBody, data, null);
    }

    /**
     * As above, but when the transaction will teleport the copy to {@code destinationPosition} (the world
     * position of its rotation point) the build-height gate applies to the bounds at that final position. The
     * copy is loaded at the source pose and moved in the same server-thread call before any physics step, and
     * the pinned Sable 2.0.3 fullyLoad performs no build-height check of its own, so only the final placement
     * has to fit. Without a target the source bounds must fit, because the copy stays where it loads.
     */
    public static String refusal(ServerLevel source, ServerLevel destination, ServerSubLevel sourceBody,
                                 SubLevelData data, org.joml.Vector3dc destinationPosition) {
        if (source == null || destination == null) return "source and destination levels are required";
        if (sourceBody == null || sourceBody.isRemoved()) return "the source pod body no longer exists";
        if (data == null) return "the source body was never serialized";
        var bounds = sourceBody.boundingBox();
        double deltaY = 0.0;
        String which = "source";
        if (destinationPosition != null) {
            if (!Double.isFinite(destinationPosition.x()) || !Double.isFinite(destinationPosition.y())
                    || !Double.isFinite(destinationPosition.z())) return "destination pod position is not finite";
            deltaY = destinationPosition.y() - sourceBody.logicalPose().position().y();
            which = "placed";
        }
        double minY = bounds.minY() + deltaY, maxY = bounds.maxY() + deltaY;
        if (!Double.isFinite(minY) || !Double.isFinite(maxY)
                || minY < destination.getMinBuildHeight() || maxY > destination.getMaxBuildHeight()) {
            return which + " pod spans Y " + minY + ".." + maxY
                    + ", outside the destination build height [" + destination.getMinBuildHeight() + ", "
                    + destination.getMaxBuildHeight() + ")";
        }
        return reservation(destination, data, sourceBody);
    }

    /** Sable allocates by the serialized plot slot, so that slot has to be genuinely free before anything loads. */
    private static String reservation(ServerLevel destination, SubLevelData data, ServerSubLevel sourceBody) {
        CompoundTag fullTag = data.fullTag();
        if (!fullTag.contains("plot", CompoundTag.TAG_COMPOUND)) return "serialized body carries no plot tag";
        CompoundTag plot = fullTag.getCompound("plot");
        int slotX = plot.getInt("plot_x");
        int slotZ = plot.getInt("plot_z");
        ServerSubLevelContainer container = SubLevelContainer.getContainer(destination);
        int side = 1 << container.getLogSideLength();
        if (slotX < 0 || slotX >= side || slotZ < 0 || slotZ >= side) {
            return "serialized plot slot " + slotX + "," + slotZ + " is outside the destination container grid (0.."
                    + (side - 1) + ")";
        }
        var occupant = container.getSubLevel(slotX, slotZ);
        if (occupant != null && occupant != sourceBody)
            return "destination plot slot " + slotX + "," + slotZ + " is held by another body";
        // UNLOADED bodies retain their occupancy reservation after leaving the live sub-level registry.
        // Require both the registry and occupancy map to show the slot free before loading into it.
        if (occupant == null && container.getOccupancy().get(container.getIndex(slotX, slotZ)))
            return "destination plot slot " + slotX + "," + slotZ
                    + " is reserved (occupancy is set but no live body is registered)";
        return null;
    }

    /**
     * Claims the loaded body for this tick.
     *
     * @return false when Sable refused the ticket, which the caller must treat as a leak signal and roll back
     */
    public static boolean hold(ServerSubLevel copy, String ticketKey) {
        ServerLevel level = copy.getLevel();
        boolean newlyRecorded = SubLevelContainer.getContainer(level)
                .addForceLoadTicket(copy, TRANSFER_TICKET, ticketKey);
        return newlyRecorded || holds(level, copy.getUniqueId(), ticketKey);
    }

    public static void release(ServerSubLevel copy, String ticketKey) {
        if (copy.isRemoved()) return;
        SubLevelContainer.getContainer(copy.getLevel()).removeForceLoadTicket(copy, TRANSFER_TICKET, ticketKey);
    }

    /** True only when the container still lists this exact ticket for this body. */
    public static boolean holds(ServerLevel level, UUID bodyId, String ticketKey) {
        var info = SubLevelContainer.getContainer(level).getAllTickets().get(bodyId);
        if (info == null) return false;
        for (var ticket : info.tickets()) {
            if (TRANSFER_TICKET.equals(SableLoadingTickets.type(ticket)) && ticketKey.equals(SableLoadingTickets.key(ticket))) return true;
        }
        return false;
    }

    /** Reason the loaded body is not a faithful copy of what went in, or null when every check passed. */
    public static String verification(ServerSubLevel copy, PodTransferSnapshot snapshot, ServerLevel destination,
                                      String ticketKey) {
        return verification(copy, snapshot, destination, ticketKey, false);
    }

    /**
     * @param placementFollows true when the copy is teleported right after this check; its bounds at the source
     *                         pose are then not final, and the placement step checks the final bounds instead
     */
    public static String verification(ServerSubLevel copy, PodTransferSnapshot snapshot, ServerLevel destination,
                                      String ticketKey, boolean placementFollows) {
        if (copy == null) return "Sable refused the destination load";
        if (!snapshot.identityMatches(copy)) {
            return "destination body does not carry the transferred pod identity (body "
                    + copy.getUniqueId() + " vs " + snapshot.bodyId() + ")";
        }
        if (copy.getPlot().getBoundingBox().volume() <= 0) return "destination plot is empty after load";
        var pose = copy.logicalPose();
        if (Math.sqrt(pose.position().distanceSquared(snapshot.pose().position())) > POSE_TOLERANCE_BLOCKS) {
            return "destination pose position drifted by more than " + POSE_TOLERANCE_BLOCKS
                    + " blocks from " + snapshot.pose().position() + " to " + pose.position();
        }
        // q and -q are the same rotation, so only the magnitude of the dot product is meaningful.
        double alignment = Math.abs(new Quaterniond(pose.orientation()).dot(snapshot.pose().orientation()));
        if (alignment < 1.0 - POSE_TOLERANCE_BLOCKS) {
            return "destination pose orientation differs from the captured rotation (alignment " + alignment + ")";
        }
        var bounds = copy.boundingBox();
        if (!placementFollows
                && (bounds.minY() < destination.getMinBuildHeight() || bounds.maxY() > destination.getMaxBuildHeight())) {
            return "destination body spans Y " + bounds.minY() + ".." + bounds.maxY()
                    + ", outside the destination build height";
        }
        if (!holds(destination, copy.getUniqueId(), ticketKey))
            return "destination force-load ticket was not recorded";
        return null;
    }

    /**
     * Brings the destination body's motion back to the captured values. Sable scales the deserialized velocity by
     * its retention config and only offers an additive call, so the correction is the measured difference between
     * what the capture held and what the loaded body actually reports.
     *
     * @return a failure reason when the correction did not land, or null
     */
    public static String restoreMotion(ServerSubLevel copy, PodTransferSnapshot snapshot) {
        RigidBodyHandle handle = RigidBodyHandle.of(copy);
        if (handle == null || !handle.isValid()) return "destination physics body is not ready";
        Vector3d linear = handle.getLinearVelocity(new Vector3d());
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        Vector3d addLinear = PodTransferSnapshot.velocityCorrection(snapshot.linearVelocity(), linear, new Vector3d());
        Vector3d addAngular = PodTransferSnapshot.velocityCorrection(snapshot.angularVelocity(), angular,
                new Vector3d());
        double toleranceSquared = VELOCITY_TOLERANCE * VELOCITY_TOLERANCE;
        if (addLinear.lengthSquared() <= toleranceSquared && addAngular.lengthSquared() <= toleranceSquared) return null;
        handle.addLinearAndAngularVelocity(addLinear, addAngular);
        Vector3d settled = handle.getLinearVelocity(new Vector3d());
        if (!PodTransferSnapshot.velocitiesMatch(snapshot.linearVelocity(), settled,
                VELOCITY_TOLERANCE)) {
            return "linear velocity settled at " + settled + " instead of the captured "
                    + snapshot.linearVelocity();
        }
        Vector3d settledSpin = handle.getAngularVelocity(new Vector3d());
        if (!PodTransferSnapshot.velocitiesMatch(snapshot.angularVelocity(), settledSpin,
                VELOCITY_TOLERANCE)) {
            return "angular velocity settled at " + settledSpin + " instead of the captured "
                    + snapshot.angularVelocity();
        }
        return null;
    }
}
