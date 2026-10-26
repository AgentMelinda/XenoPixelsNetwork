package net.bullettrain.xenopixelsmod.space.dragonball;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;

import java.util.UUID;

/**
 * Where the seven Super Dragon Balls are (design-doc 16, v1 items + radar). Each ball is SCATTERED (a stored target spot,
 * not yet in the world), PLACED (a block exists at the stored spot) or HELD (picked up as an item). Placement is lazy: a
 * scattered ball becomes a block only when a player comes near its spot, so scattering never loads remote chunks. The
 * cycle counter seeds each scatter; after a wish the balls are dormant until {@code dormantUntilDay}.
 */
public final class DragonBallSavedData extends SavedData {
    private static final String FILE_NAME = "xenopixels_cosmic_dragon_balls";
    private static final int DATA_VERSION = 5;
    public enum Status { SCATTERED, PLACED, HELD }
    public record ManualOrbit(double radiusBlocks, double phaseRadians, double periodSeconds) {
        public ManualOrbit {
            if (!(radiusBlocks > 0.0) || !Double.isFinite(radiusBlocks) || radiusBlocks > 1_000_000.0
                    || !Double.isFinite(phaseRadians) || !(periodSeconds > 0.0) || !Double.isFinite(periodSeconds))
                throw new IllegalArgumentException("manual SDB orbit must have finite radius, phase and positive period");
        }
    }

    public static final class Ball {
        public final DragonBallIdentity identity;
        public Status status = Status.SCATTERED;
        public String dimension = "minecraft:overworld";
        public int x, y, z;
        /**
         * Last valid configured host for the planet-form ball; empty until a pack assigns a host.
         * This is not proof that the host is active: consumers must verify it against the current chart,
         * since erased or temporarily unavailable chart bodies retain this last-known assignment.
         */
        public String hostChartId = "";
        /** Loaded Sable incarnation, if a persistent planet body has been reconciled. */
        private UUID planetBodyUUID;
        /** Last validated data-pack orbit assignment; actual physical position still comes from Sable once reconciled. */
        public boolean hasPlanetOrbit;
        public double planetOrbitRadiusBlocks, planetOrbitPhaseRadians, planetOrbitPeriodSeconds;
        /** Player/tool-authored orbit override. Kept separate so chart reloads never overwrite the pack assignment. */
        public boolean hasManualOrbit;
        public double manualOrbitRadiusBlocks, manualOrbitPhaseRadians, manualOrbitPeriodSeconds;

        private Ball(int slot) { identity = DragonBallIdentity.forSlot(slot); }
    }

    final Ball[] balls = new Ball[CosmicDragonBalls.COUNT];
    long cycle = -1;
    long dormantUntilDay;
    private CompoundTag preservedInvalidTag;
    private String invalidReason;

    public DragonBallSavedData() {
        for (int i = 0; i < balls.length; i++) balls[i] = new Ball(i + 1);
    }

    public static DragonBallSavedData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage()
                .computeIfAbsent(new Factory<>(DragonBallSavedData::new, DragonBallSavedData::load), FILE_NAME);
    }

    public static DragonBallSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DragonBallSavedData data = new DragonBallSavedData();
        int version = tag.getInt("DataVersion");
        if (tag.contains("DataVersion") && !tag.contains("DataVersion", Tag.TAG_INT))
            return invalid(data, tag, "DataVersion is not an integer");
        if (version < 0 || version > DATA_VERSION)
            return invalid(data, tag, "unsupported data version " + version);
        if (tag.contains("Cycle") && !tag.contains("Cycle", Tag.TAG_LONG))
            return invalid(data, tag, "Cycle is not a long");
        if (tag.contains("DormantUntilDay") && !tag.contains("DormantUntilDay", Tag.TAG_LONG))
            return invalid(data, tag, "DormantUntilDay is not a long");
        if (!tag.contains("Balls", Tag.TAG_LIST)) return invalid(data, tag, "missing Balls list");
        ListTag list = tag.getList("Balls", Tag.TAG_COMPOUND);
        if (list.size() != data.balls.length)
            return invalid(data, tag, "Balls count " + list.size() + " does not match expected " + data.balls.length);
        boolean normalizeIdentityData = tag.getInt("DataVersion") != DATA_VERSION;
        // The pre-identity loader treated these two fields as optional. Keep its documented defaults
        // for older saves while still rejecting a present field with the wrong NBT type.
        if (tag.contains("Cycle", Tag.TAG_LONG)) data.cycle = tag.getLong("Cycle");
        if (tag.contains("DormantUntilDay", Tag.TAG_LONG)) data.dormantUntilDay = tag.getLong("DormantUntilDay");
        for (int i = 0; i < data.balls.length; i++) {
            CompoundTag b = list.getCompound(i);
            Ball ball = data.balls[i];
            if (!b.contains("Status", Tag.TAG_STRING) || !b.contains("Dimension", Tag.TAG_STRING)
                    || b.getString("Dimension").isBlank()
                    || !b.contains("X", Tag.TAG_INT) || !b.contains("Y", Tag.TAG_INT) || !b.contains("Z", Tag.TAG_INT))
                return invalid(data, tag, "Balls[" + i + "] is missing required status, dimension, or coordinates");
            try { ball.status = Status.valueOf(b.getString("Status")); }
            catch (IllegalArgumentException bad) { return invalid(data, tag, "Balls[" + i + "] has unknown status"); }
            ball.dimension = b.getString("Dimension");
            ball.x = b.getInt("X"); ball.y = b.getInt("Y"); ball.z = b.getInt("Z");
            ball.hostChartId = b.getString("HostChartId");
            if (b.contains("PlanetBodyUUID") && !b.hasUUID("PlanetBodyUUID"))
                return invalid(data, tag, "Balls[" + i + "] has malformed PlanetBodyUUID");
            ball.planetBodyUUID = b.hasUUID("PlanetBodyUUID") ? b.getUUID("PlanetBodyUUID") : null;
            ball.hasPlanetOrbit = b.getBoolean("HasPlanetOrbit");
            if (ball.hasPlanetOrbit) {
                ball.planetOrbitRadiusBlocks = b.getDouble("PlanetOrbitRadiusBlocks");
                ball.planetOrbitPhaseRadians = b.getDouble("PlanetOrbitPhaseRadians");
                ball.planetOrbitPeriodSeconds = b.getDouble("PlanetOrbitPeriodSeconds");
                if (!(ball.planetOrbitRadiusBlocks > 0.0) || !Double.isFinite(ball.planetOrbitRadiusBlocks)
                        || !Double.isFinite(ball.planetOrbitPhaseRadians)
                        || ball.planetOrbitPeriodSeconds < 0.0 || !Double.isFinite(ball.planetOrbitPeriodSeconds)) {
                    return invalid(data, tag, "Balls[" + i + "] has malformed planet orbit values");
                }
            }
            ball.hasManualOrbit = b.getBoolean("HasManualOrbit");
            if (ball.hasManualOrbit) {
                ball.manualOrbitRadiusBlocks = b.getDouble("ManualOrbitRadiusBlocks");
                ball.manualOrbitPhaseRadians = b.getDouble("ManualOrbitPhaseRadians");
                ball.manualOrbitPeriodSeconds = b.getDouble("ManualOrbitPeriodSeconds");
                try {
                    new ManualOrbit(ball.manualOrbitRadiusBlocks, ball.manualOrbitPhaseRadians,
                            ball.manualOrbitPeriodSeconds);
                } catch (IllegalArgumentException malformed) {
                    return invalid(data, tag, "Balls[" + i + "] has malformed manual orbit values");
                }
            }
            if (b.contains("IdentityVersion") && !b.contains("IdentityVersion", Tag.TAG_INT))
                return invalid(data, tag, "Balls[" + i + "] has malformed IdentityVersion");
            int identityVersion = b.getInt("IdentityVersion");
            if (identityVersion > DragonBallIdentity.VERSION)
                return invalid(data, tag, "Balls[" + i + "] uses unsupported identity version " + identityVersion);
            if (b.contains("PlanetId") && !b.hasUUID("PlanetId"))
                return invalid(data, tag, "Balls[" + i + "] has malformed PlanetId");
            boolean identityMatches = b.hasUUID("PlanetId") && ball.identity.planetId().equals(b.getUUID("PlanetId"))
                    && ball.identity.planetKey().equals(b.getString("PlanetKey"))
                    && ball.identity.surfaceCellKey().equals(b.getString("SurfaceCellKey"));
            if (identityVersion == DragonBallIdentity.VERSION && !identityMatches)
                return invalid(data, tag, "Balls[" + i + "] has inconsistent current slot identity metadata");
            boolean hasAnyIdentity = b.contains("PlanetId") || b.contains("PlanetKey") || b.contains("SurfaceCellKey");
            if (identityVersion != DragonBallIdentity.VERSION && hasAnyIdentity)
                return invalid(data, tag, "Balls[" + i + "] has partial or unsupported slot identity metadata");
            if (identityVersion != DragonBallIdentity.VERSION)
                normalizeIdentityData = true;
        }
        // Old and unknown identity schemas are normalized to the deterministic slot identities. Position,
        // status, cycle, and legacy item/block behavior remain unchanged.
        if (normalizeIdentityData) data.setDirty();
        return data;
    }

    private static DragonBallSavedData invalid(DragonBallSavedData data, CompoundTag source, String reason) {
        data.preservedInvalidTag = source.copy();
        data.invalidReason = reason;
        XenoPixelsMod.LOGGER.error("Super Dragon Ball saved data is malformed: {}. Preserving the raw tag and disabling SDB gameplay until repaired.", reason);
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (preservedInvalidTag != null) return preservedInvalidTag.copy();
        tag.putInt("DataVersion", DATA_VERSION);
        tag.putLong("Cycle", cycle);
        tag.putLong("DormantUntilDay", dormantUntilDay);
        ListTag list = new ListTag();
        for (Ball ball : balls) {
            CompoundTag b = new CompoundTag();
            b.putString("Status", ball.status.name());
            b.putString("Dimension", ball.dimension);
            b.putInt("X", ball.x); b.putInt("Y", ball.y); b.putInt("Z", ball.z);
            b.putString("HostChartId", ball.hostChartId);
            if (ball.planetBodyUUID != null) b.putUUID("PlanetBodyUUID", ball.planetBodyUUID);
            b.putBoolean("HasPlanetOrbit", ball.hasPlanetOrbit);
            if (ball.hasPlanetOrbit) {
                b.putDouble("PlanetOrbitRadiusBlocks", ball.planetOrbitRadiusBlocks);
                b.putDouble("PlanetOrbitPhaseRadians", ball.planetOrbitPhaseRadians);
                if (ball.planetOrbitPeriodSeconds > 0.0)
                    b.putDouble("PlanetOrbitPeriodSeconds", ball.planetOrbitPeriodSeconds);
            }
            b.putBoolean("HasManualOrbit", ball.hasManualOrbit);
            if (ball.hasManualOrbit) {
                b.putDouble("ManualOrbitRadiusBlocks", ball.manualOrbitRadiusBlocks);
                b.putDouble("ManualOrbitPhaseRadians", ball.manualOrbitPhaseRadians);
                b.putDouble("ManualOrbitPeriodSeconds", ball.manualOrbitPeriodSeconds);
            }
            b.putInt("IdentityVersion", DragonBallIdentity.VERSION);
            b.putUUID("PlanetId", ball.identity.planetId());
            b.putString("PlanetKey", ball.identity.planetKey());
            b.putString("SurfaceCellKey", ball.identity.surfaceCellKey());
            list.add(b);
        }
        tag.put("Balls", list);
        return tag;
    }

    public Ball ball(int number) { return balls[number - 1]; }

    /** Whether all seven saved slots were read without inventing or dropping gameplay data. */
    public boolean isGameplayStateValid() { return invalidReason == null; }

    /** Operator-facing diagnostic; empty when the saved data is valid. */
    public String invalidReason() { return invalidReason == null ? "" : invalidReason; }

    public void requireGameplayStateValid() {
        if (!isGameplayStateValid()) throw new IllegalStateException("Super Dragon Ball saved data is unavailable: " + invalidReason);
    }

    /** The Sable incarnation currently owned by this stable slot, or {@code null} before reconciliation. */
    public UUID planetBodyUuid(int number) { return ball(number).planetBodyUUID; }

    /** True when the valid saved ledger still owns any Sable incarnation; used to refuse unsafe mode switches. */
    public boolean hasAnyPlanetBodyBinding() {
        if (!isGameplayStateValid()) return false;
        for (Ball ball : balls) if (ball.planetBodyUUID != null) return true;
        return false;
    }

    /** Manual orbit for a slot, or {@code null} when the data-pack assignment remains in force. */
    public ManualOrbit manualOrbit(int number) {
        Ball ball = ball(number);
        return ball.hasManualOrbit ? new ManualOrbit(ball.manualOrbitRadiusBlocks, ball.manualOrbitPhaseRadians,
                ball.manualOrbitPeriodSeconds) : null;
    }

    /** Store a validated persistent override without changing the data-pack's base assignment. */
    public boolean setManualOrbit(int number, double radiusBlocks, double phaseRadians, double periodSeconds) {
        requireGameplayStateValid();
        ManualOrbit value = new ManualOrbit(radiusBlocks, phaseRadians, periodSeconds);
        Ball ball = ball(number);
        if (ball.hasManualOrbit && Double.compare(ball.manualOrbitRadiusBlocks, value.radiusBlocks()) == 0
                && Double.compare(ball.manualOrbitPhaseRadians, value.phaseRadians()) == 0
                && Double.compare(ball.manualOrbitPeriodSeconds, value.periodSeconds()) == 0) return false;
        ball.hasManualOrbit = true;
        ball.manualOrbitRadiusBlocks = value.radiusBlocks();
        ball.manualOrbitPhaseRadians = value.phaseRadians();
        ball.manualOrbitPeriodSeconds = value.periodSeconds();
        setDirty();
        return true;
    }

    /** Remove a manual override so the latest validated data-pack assignment is used again. */
    public boolean clearManualOrbit(int number) {
        requireGameplayStateValid();
        Ball ball = ball(number);
        if (!ball.hasManualOrbit) return false;
        clearManualOrbit(ball);
        setDirty();
        return true;
    }

    private static void clearManualOrbit(Ball ball) {
        ball.hasManualOrbit = false;
        ball.manualOrbitRadiusBlocks = 0.0;
        ball.manualOrbitPhaseRadians = 0.0;
        ball.manualOrbitPeriodSeconds = 0.0;
    }

    /**
     * Bind one slot to a Sable body UUID. Repeating the same bind is a no-op; assigning one incarnation to two
     * different SDB slots is rejected so a caller cannot make startup reconciliation claim duplicate planets.
     */
    public boolean bindPlanetBody(int number, UUID bodyId) {
        requireGameplayStateValid();
        if (bodyId == null) throw new IllegalArgumentException("SDB Sable body UUID must not be null");
        Ball target = ball(number);
        for (Ball other : balls) {
            if (other != target && bodyId.equals(other.planetBodyUUID))
                throw new IllegalStateException("Sable body " + bodyId + " is already assigned to SDB slot " + other.identity.slot());
        }
        if (bodyId.equals(target.planetBodyUUID)) return false;
        target.planetBodyUUID = bodyId;
        setDirty();
        return true;
    }

    /** Clear a slot only if it still owns the expected incarnation; delayed cleanup cannot erase a replacement bind. */
    public boolean clearPlanetBody(int number, UUID expectedBodyId) {
        requireGameplayStateValid();
        if (expectedBodyId == null) return false;
        Ball target = ball(number);
        if (!expectedBodyId.equals(target.planetBodyUUID)) return false;
        target.planetBodyUUID = null;
        setDirty();
        return true;
    }

    /** Malformed/old saves with duplicate body references must block reconciliation rather than create replacements. */
    public boolean hasUniquePlanetBodyUuids() {
        var seen = new java.util.HashSet<UUID>();
        for (Ball ball : balls) if (ball.planetBodyUUID != null && !seen.add(ball.planetBodyUUID)) return false;
        return true;
    }

    public long cycle() { return cycle; }

    public long dormantUntilDay() { return dormantUntilDay; }
}
