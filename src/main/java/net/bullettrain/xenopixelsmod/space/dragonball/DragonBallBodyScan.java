package net.bullettrain.xenopixelsmod.space.dragonball;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuildRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Reads which loaded Sable bodies already claim each SDB slot, in the exact shape
 * {@link DragonBallBodyPlan#plan(java.util.List, Map)} consumes.
 *
 * <p>The three outcomes of a scan are deliberately different, because the planner treats them differently:
 * <ul>
 *     <li>an entry mapped to an empty list means every body in this level was opened and none claims the slot,
 *     so a slot with no saved UUID may be created;</li>
 *     <li>an entry mapped to {@code null} means a body carrying an SDB tag could not be attributed, so the slot
 *     conflicts rather than building a second planet beside a real one;</li>
 *     <li>no map at all means the level, a body, or a body's user data was unavailable, which blocks the whole
 *     cycle. A body that could not be opened is never read as "claims nothing": that is how a duplicate planet
 *     would be created beside the real one.</li>
 * </ul>
 *
 * <p>Bodies tagged by the temporary {@code /xenopixels} bench use a different key on purpose, so an admin test
 * planet can never be adopted into a saved slot.
 */
public final class DragonBallBodyScan {
    /** Nested tag key this adapter writes at bind time and the only one it trusts at startup. */
    public static final String TAG = "xenopixelsmod_dragon_ball_body_v1";
    static final String SLOT_KEY = "Slot";
    static final String IDENTITY_VERSION_KEY = "IdentityVersion";
    private static final Map<MinecraftServer, DragonBallDiagnosticLatch> SCAN_DIAGNOSTICS = new WeakHashMap<>();
    private static final Map<MinecraftServer, DragonBallBodyScanMetrics> SCAN_METRICS = new WeakHashMap<>();

    /** What one loaded body contributes to the scan. */
    public enum Read {
        /** A valid claim on the slot its tag names. */
        CLAIM,
        /** Tagged as SDB but not usable, so the slots it might have claimed conflict instead of creating. */
        UNATTRIBUTABLE
    }

    /** Whether a listing entry is one of our bodies, or one we have no right to draw a conclusion about. */
    public enum BodyRead {
        /** Carries this adapter's binding tag, so its slot values decide a claim. */
        OURS,
        /** Opened successfully and proven not ours by the absence of the binding tag key. */
        FOREIGN,
        /** A missing body or missing user data: unreadable, so the scan cannot report any slot as free. */
        UNREADABLE
    }

    private DragonBallBodyScan() {}

    /**
     * Pure rule for whether one listing entry may be read at all, kept free of Sable and NBT types so the
     * abort-versus-absent distinction is testable without a running server. Absence of our tag is a fact about
     * the body; absence of the body or of its user data is a failure of the scan.
     */
    public static BodyRead attribute(boolean bodyPresent, boolean userDataPresent, boolean bindingTagPresent) {
        if (!bodyPresent || !userDataPresent) return BodyRead.UNREADABLE;
        return bindingTagPresent ? BodyRead.OURS : BodyRead.FOREIGN;
    }

    /**
     * Pure classification of one body's tag values, kept free of Sable and NBT types so the rule is testable
     * without a running server. A body only reaches here once its SDB tag key is present, so a tag that names
     * no slot is damage rather than absence.
     *
     * @param slot            the tagged slot, or {@code null} when the tag carries no slot
     * @param identityVersion the tagged {@link DragonBallIdentity#VERSION}, or zero when absent
     * @param slots           the ledger's slot count, which the caller takes from its own data
     */
    /**
     * SDB planet key this body's own persisted tag claims, or null when untagged, out of range, or of another
     * identity version: the same acceptance rule the scan applies through {@link #classify}.
     */
    public static String taggedPlanetKey(ServerSubLevel body, int slots) {
        CompoundTag user = body == null ? null : body.getUserDataTag();
        if (user == null || !user.contains(TAG)) return null;
        CompoundTag tag = user.getCompound(TAG);
        Integer slot = tag.contains(SLOT_KEY) ? tag.getInt(SLOT_KEY) : null;
        if (classify(slot, tag.getInt(IDENTITY_VERSION_KEY), slots) != Read.CLAIM) return null;
        return DragonBallIdentity.forSlot(slot).planetKey();
    }

    public static Read classify(Integer slot, int identityVersion, int slots) {
        if (slot == null || slot < 1 || slot > slots || identityVersion != DragonBallIdentity.VERSION)
            return Read.UNATTRIBUTABLE;
        return Read.CLAIM;
    }

    /**
     * Scans every loaded body in {@code level} once. A malformed or out-of-range SDB tag marks the slots it could
     * have claimed unattributable, and other slots keep their confirmed results. A body that cannot be opened
     * aborts the whole scan: the scan may only report a slot free when it can vouch for every body it saw.
     *
     * @return slot to claimed body UUIDs, or {@code null} when the level, container, listing, or any body in it
     *         was unavailable
     */
    public static Map<Integer, List<UUID>> tagged(ServerLevel level, int slots) {
        if (level == null || slots < 1) return null;
        MinecraftServer server = level.getServer();
        if (server == null) return null;
        long started = System.nanoTime();
        ScanProgress progress = new ScanProgress();
        boolean complete = false;
        try {
            Map<Integer, List<UUID>> result = scan(level, slots, progress);
            complete = result != null;
            return result;
        } finally {
            long elapsed = Math.max(0L, System.nanoTime() - started);
            synchronized (SCAN_METRICS) {
                SCAN_METRICS.computeIfAbsent(server, ignored -> new DragonBallBodyScanMetrics())
                        .record(progress.listedBodies, progress.visitedBodies, progress.skippedInFlight, elapsed, complete);
            }
        }
    }

    /** Per-server report for the operator perf command. */
    public static String metricsReport(MinecraftServer server) {
        if (server == null) return "SDB reconcile scan=unavailable";
        synchronized (SCAN_METRICS) {
            DragonBallBodyScanMetrics metrics = SCAN_METRICS.get(server);
            return metrics == null ? "SDB reconcile scan=not-run" : metrics.report();
        }
    }

    private static Map<Integer, List<UUID>> scan(ServerLevel level, int slots, ScanProgress progress) {
        ServerSubLevelContainer container;
        try {
            container = SubLevelContainer.getContainer(level);
        } catch (RuntimeException unavailable) {
            reportScanFailure(level, "container-unavailable", "SDB body scan skipped: sub-level container unavailable", unavailable);
            return null;
        }
        if (container == null) return null;
        List<ServerSubLevel> bodies;
        try {
            bodies = container.getAllSubLevels();
        } catch (RuntimeException unavailable) {
            reportScanFailure(level, "listing-failed", "SDB body scan skipped: sub-level listing failed", unavailable);
            return null;
        }
        if (bodies == null) return null;
        progress.listedBodies = bodies.size();
        PlotBodyBuildRegistry.pruneMissingOrRemoved(level, container);

        var claims = freshSlotMap(slots);
        for (int index = 0; index < bodies.size(); index++) {
            progress.visitedBodies++;
            ServerSubLevel body = bodies.get(index);
            UUID bodyId = readableId(body);
            if (body != null && bodyId == null) {
                reportScanFailure(level, "body-identity:" + index,
                        "SDB body scan aborted: the listing entry at index " + index
                                + " has no readable identity, so no slot is reported free this cycle", null);
                return null;
            }
            if (bodyId != null && PlotBodyBuildRegistry.isInFlight(bodyId, level.getServer())) {
                progress.skippedInFlight++;
                continue;
            }
            CompoundTag user = null;
            boolean removed;
            try {
                removed = body != null && body.isRemoved();
                if (!removed) user = body == null ? null : body.getUserDataTag();
            } catch (RuntimeException unreadable) {
                reportScanFailure(level, "body-open:" + index,
                        "SDB body scan aborted: the listing entry at index " + index
                                + " could not be opened, so no slot is reported free this cycle", unreadable);
                return null;
            }
            if (removed) continue;
            BodyRead read = attribute(body != null, user != null, user != null && user.contains(TAG));
            if (read == BodyRead.UNREADABLE) {
                String reason = body == null ? "absent" : "body-with-no-user-data";
                String signature = "body-unreadable:" + index + ":" + reason + ":" + bodyId;
                reportScanFailure(level, signature,
                        unreadableBodyMessage(index, progress.listedBodies, reason, bodyId), null);
                return null;
            }
            if (read == BodyRead.FOREIGN) continue;
            CompoundTag tag = user.getCompound(TAG);
            Integer slot = tag.contains(SLOT_KEY) ? tag.getInt(SLOT_KEY) : null;
            fold(claims, slot, tag.getInt(IDENTITY_VERSION_KEY), slots, bodyId);
        }
        // Sorting makes a duplicate-claim conflict report the same body on every pass and every restart.
        var resolved = new TreeMap<Integer, List<UUID>>();
        claims.forEach((slot, ids) -> {
            if (ids != null) ids.sort(UUID::compareTo);
            resolved.put(slot, ids == null ? null : Collections.unmodifiableList(ids));
        });
        clearScanFailure(level.getServer());
        return Collections.unmodifiableMap(resolved);
    }

    static String unreadableBodyMessage(int index, int listedBodies, String reason, UUID bodyId) {
        String identity = bodyId == null ? "unavailable" : bodyId.toString();
        return "SDB body scan aborted: listing entry " + index + "/" + listedBodies + " uuid=" + identity
                + " is " + reason + " rather than a body that can be asked what it claims,"
                + " so no slot is reported free this cycle";
    }

    private static UUID readableId(ServerSubLevel body) {
        try {
            return body == null ? null : body.getUniqueId();
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** Called when a scan becomes readable again; the manager emits the single recovery summary. */
    static void forget(MinecraftServer server) {
        synchronized (SCAN_DIAGNOSTICS) { SCAN_DIAGNOSTICS.remove(server); }
        synchronized (SCAN_METRICS) { SCAN_METRICS.remove(server); }
    }

    private static final class ScanProgress {
        int listedBodies = -1;
        int visitedBodies;
        int skippedInFlight;
    }

    private static void reportScanFailure(ServerLevel level, String signature, String message, Throwable cause) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            if (cause == null) XenoPixelsMod.LOGGER.warn("{}", message);
            else XenoPixelsMod.LOGGER.warn(message, cause);
            return;
        }
        boolean shouldReport;
        synchronized (SCAN_DIAGNOSTICS) {
            shouldReport = SCAN_DIAGNOSTICS.computeIfAbsent(server, ignored -> new DragonBallDiagnosticLatch())
                    .shouldReport("scan", signature);
        }
        if (!shouldReport) return;
        if (cause == null) XenoPixelsMod.LOGGER.warn("{}", message);
        else XenoPixelsMod.LOGGER.warn(message, cause);
    }

    private static void clearScanFailure(MinecraftServer server) {
        synchronized (SCAN_DIAGNOSTICS) {
            DragonBallDiagnosticLatch latch = SCAN_DIAGNOSTICS.get(server);
            if (latch != null && latch.clear("scan")) SCAN_DIAGNOSTICS.remove(server);
        }
    }

    /** Every slot starts confirmed unclaimed; a body read later can only narrow that. Package-private for tests. */
    static Map<Integer, List<UUID>> freshSlotMap(int slots) {
        var claims = new TreeMap<Integer, List<UUID>>();
        for (int slot = 1; slot <= slots; slot++) claims.put(slot, new ArrayList<>());
        return claims;
    }

    /**
     * The whole attribution rule for one of our bodies, with no Sable or NBT types so the fail-closed behaviour is
     * testable without a running server. Package-private because this is the path production actually takes.
     */
    static void fold(Map<Integer, List<UUID>> claims, Integer slot, int identityVersion, int slots, UUID id) {
        if (classify(slot, identityVersion, slots) == Read.CLAIM) markClaim(claims, slot, id);
        else markUnattributable(claims, candidates(slot, slots));
    }

    /**
     * Records a claim without ever overwriting a slot a damaged binding already made ambiguous. A torn tag and a
     * real claimant naming the same slot is exactly the conflict the {@code null} entry reports, so turning it
     * into a plain claim would build a second planet beside one that already exists.
     */
    private static void markClaim(Map<Integer, List<UUID>> claims, int slot, UUID id) {
        List<UUID> ids = claims.get(slot);
        if (ids == null) return; // The manager reports the resulting stable slot conflict once per state change.
        ids.add(id);
    }

    /** The slots a damaged binding could have claimed; an unusable slot number makes every slot suspect. */
    private static List<Integer> candidates(Integer slot, int slots) {
        if (slot != null && slot >= 1 && slot <= slots) return List.of(slot);
        var all = new ArrayList<Integer>(slots);
        for (int index = 1; index <= slots; index++) all.add(index);
        return all;
    }

    private static void markUnattributable(Map<Integer, List<UUID>> claims, List<Integer> slots) {
        for (int slot : slots) if (claims.containsKey(slot)) claims.put(slot, null);
    }
}
