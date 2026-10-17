package net.bullettrain.xenopixelsmod.space.dragonball;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

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

    public DragonBallSavedData() {
        for (int i = 0; i < balls.length; i++) balls[i] = new Ball(i + 1);
    }

    public static DragonBallSavedData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage()
                .computeIfAbsent(new Factory<>(DragonBallSavedData::new, DragonBallSavedData::load), FILE_NAME);
    }

    public static DragonBallSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DragonBallSavedData data = new DragonBallSavedData();
        boolean normalizeIdentityData = tag.getInt("DataVersion") != DATA_VERSION;
        data.cycle = tag.contains("Cycle") ? tag.getLong("Cycle") : -1;
        data.dormantUntilDay = tag.getLong("DormantUntilDay");
        ListTag list = tag.getList("Balls", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), data.balls.length); i++) {
            CompoundTag b = list.getCompound(i);
            Ball ball = data.balls[i];
            try { ball.status = Status.valueOf(b.getString("Status")); } catch (IllegalArgumentException bad) { ball.status = Status.SCATTERED; }
            ball.dimension = b.getString("Dimension").isEmpty() ? "minecraft:overworld" : b.getString("Dimension");
            ball.x = b.getInt("X"); ball.y = b.getInt("Y"); ball.z = b.getInt("Z");
            ball.hostChartId = b.getString("HostChartId");
            ball.planetBodyUUID = b.hasUUID("PlanetBodyUUID") ? b.getUUID("PlanetBodyUUID") : null;
            ball.hasPlanetOrbit = b.getBoolean("HasPlanetOrbit");
            if (ball.hasPlanetOrbit) {
                ball.planetOrbitRadiusBlocks = b.getDouble("PlanetOrbitRadiusBlocks");
                ball.planetOrbitPhaseRadians = b.getDouble("PlanetOrbitPhaseRadians");
                ball.planetOrbitPeriodSeconds = b.getDouble("PlanetOrbitPeriodSeconds");
                if (!(ball.planetOrbitRadiusBlocks > 0.0) || !Double.isFinite(ball.planetOrbitRadiusBlocks)
                        || !Double.isFinite(ball.planetOrbitPhaseRadians)
                        || ball.planetOrbitPeriodSeconds < 0.0 || !Double.isFinite(ball.planetOrbitPeriodSeconds)) {
                    ball.hasPlanetOrbit = false;
                    ball.planetOrbitRadiusBlocks = 0.0;
                    ball.planetOrbitPhaseRadians = 0.0;
                    ball.planetOrbitPeriodSeconds = 0.0;
                    normalizeIdentityData = true;
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
                    clearManualOrbit(ball);
                    normalizeIdentityData = true;
                }
            }
            if (b.getInt("IdentityVersion") != DragonBallIdentity.VERSION
                    || !b.hasUUID("PlanetId") || !ball.identity.planetId().equals(b.getUUID("PlanetId"))
                    || !ball.identity.planetKey().equals(b.getString("PlanetKey"))
                    || !ball.identity.surfaceCellKey().equals(b.getString("SurfaceCellKey")))
                normalizeIdentityData = true;
        }
        // Old and unknown identity schemas are normalized to the deterministic slot identities. Position,
        // status, cycle, and legacy item/block behavior remain unchanged.
        if (normalizeIdentityData) data.setDirty();
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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

    /** The Sable incarnation currently owned by this stable slot, or {@code null} before reconciliation. */
    public UUID planetBodyUuid(int number) { return ball(number).planetBodyUUID; }

    /** Manual orbit for a slot, or {@code null} when the data-pack assignment remains in force. */
    public ManualOrbit manualOrbit(int number) {
        Ball ball = ball(number);
        return ball.hasManualOrbit ? new ManualOrbit(ball.manualOrbitRadiusBlocks, ball.manualOrbitPhaseRadians,
                ball.manualOrbitPeriodSeconds) : null;
    }

    /** Store a validated persistent override without changing the data-pack's base assignment. */
    public boolean setManualOrbit(int number, double radiusBlocks, double phaseRadians, double periodSeconds) {
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
