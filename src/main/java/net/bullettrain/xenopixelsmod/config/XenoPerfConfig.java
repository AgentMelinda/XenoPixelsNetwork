package net.bullettrain.xenopixelsmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Module-only knobs for thrusters / missile chunk tickets / stats.
 * Written to {@code config/xenopixelsmod-perf.json}.
 * <p>
 * Standalone thruster forces default on; expensive route chunk loading remains opt-in.
 */
public final class XenoPerfConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-perf.json");

    public static boolean perfEnabled = true;

    // --- Missile / VLS chunk force-load ---
    /** Default OFF — no setChunkForced until enabled. */
    public static boolean forceChunksEnabled = false;
    public static boolean forceChunksTargetOnly = true;
    public static int forceChunksRadius = 1;
    public static int forceChunksDurationTicks = 20 * 20;
    public static double forceChunksPlayerRange = 0.0;

    // --- Ballistic guidance range ---
    /**
     * Max horizontal range (blocks) for guidance target / ship launch.
     * Default {@code 1_000_000} = 1000 km. Set {@code 0} for unlimited (world bounds only).
     */
    public static int ballisticMaxRangeBlocks = 1_000_000;

    // --- Thruster phys force ---
    /**
     * Master switch for thruster {@code applyModelForce}. Default <b>true</b> so a
     * redstone-powered thruster behaves like a thruster without extra commands.
     */
    public static boolean thrusterPhysForceEnabled = true;
    /**
     * When phys force is enabled: if true, always while powered; if false, only when a
     * player is within {@link #thrusterForcePlayerRange}.
     */
    public static boolean thrusterForceAlways = true;
    public static double thrusterForcePlayerRange = 128.0;

    // --- Stats HUD ---
    public static int statsSyncIntervalTicks = 10;
    public static int statsSyncHeartbeatTicks = 40;
    public static boolean statsSyncOnlyWhenDirty = true;

    private XenoPerfConfig() {}

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null) apply(data);
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load perf config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(snapshot(), writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save perf config", e);
        }
    }

    public static Data snapshot() {
        Data d = new Data();
        d.version = 2;
        d.perfEnabled = perfEnabled;
        d.forceChunksEnabled = forceChunksEnabled;
        d.forceChunksTargetOnly = forceChunksTargetOnly;
        d.forceChunksRadius = forceChunksRadius;
        d.forceChunksDurationTicks = forceChunksDurationTicks;
        d.forceChunksPlayerRange = forceChunksPlayerRange;
        d.ballisticMaxRangeBlocks = ballisticMaxRangeBlocks;
        d.thrusterPhysForceEnabled = thrusterPhysForceEnabled;
        d.thrusterForceAlways = thrusterForceAlways;
        d.thrusterForcePlayerRange = thrusterForcePlayerRange;
        d.statsSyncIntervalTicks = statsSyncIntervalTicks;
        d.statsSyncHeartbeatTicks = statsSyncHeartbeatTicks;
        d.statsSyncOnlyWhenDirty = statsSyncOnlyWhenDirty;
        return d;
    }

    public static void apply(Data d) {
        if (d == null) return;
        perfEnabled = d.perfEnabled;
        forceChunksEnabled = d.forceChunksEnabled;
        forceChunksTargetOnly = d.forceChunksTargetOnly;
        forceChunksRadius = Math.max(0, Math.min(2, d.forceChunksRadius));
        forceChunksDurationTicks = Math.max(40, Math.min(20 * 120,
                d.forceChunksDurationTicks <= 0 ? 400 : d.forceChunksDurationTicks));
        forceChunksPlayerRange = Math.max(0.0, d.forceChunksPlayerRange);
        // 0 = unlimited; else clamp 1 km … 30_000 km (world half-extent)
        if (d.ballisticMaxRangeBlocks <= 0) {
            ballisticMaxRangeBlocks = 0;
        } else {
            ballisticMaxRangeBlocks = Math.max(1_000, Math.min(30_000_000, d.ballisticMaxRangeBlocks));
        }
        // Version 1/missing-version configs inherited the old plume-only default.
        // Migrate them once; version 2 still permits operators to explicitly disable force.
        thrusterPhysForceEnabled = d.version == null || d.version < 2 || d.thrusterPhysForceEnabled;
        thrusterForceAlways = d.thrusterForceAlways;
        thrusterForcePlayerRange = Math.max(16.0,
                d.thrusterForcePlayerRange <= 0 ? 128.0 : d.thrusterForcePlayerRange);
        statsSyncIntervalTicks = Math.max(5, Math.min(40,
                d.statsSyncIntervalTicks <= 0 ? 10 : d.statsSyncIntervalTicks));
        statsSyncHeartbeatTicks = Math.max(statsSyncIntervalTicks, Math.min(200,
                d.statsSyncHeartbeatTicks <= 0 ? 40 : d.statsSyncHeartbeatTicks));
        statsSyncOnlyWhenDirty = d.statsSyncOnlyWhenDirty;
    }

    public static String statusLine() {
        return "perf=" + perfEnabled
                + " maxRange=" + (ballisticMaxRangeBlocks <= 0 ? "∞" : ballisticMaxRangeBlocks)
                + " thrForce=" + thrusterPhysForceEnabled
                + " thrAlways=" + thrusterForceAlways
                + " thrRange=" + thrusterForcePlayerRange
                + " forceChunks=" + forceChunksEnabled
                + " targetOnly=" + forceChunksTargetOnly
                + " radius=" + forceChunksRadius
                + " statsSync=" + statsSyncIntervalTicks + "t";
    }

    /** Horizontal range cap in blocks, or {@link Double#MAX_VALUE} if unlimited. */
    public static double maxBallisticRange() {
        if (!perfEnabled || ballisticMaxRangeBlocks <= 0) return Double.MAX_VALUE;
        return ballisticMaxRangeBlocks;
    }

    public static class Data {
        public Integer version;
        public boolean perfEnabled = true;
        public boolean forceChunksEnabled = false;
        public boolean forceChunksTargetOnly = true;
        public int forceChunksRadius = 1;
        public int forceChunksDurationTicks = 400;
        public double forceChunksPlayerRange = 0.0;
        public int ballisticMaxRangeBlocks = 1_000_000;
        public boolean thrusterPhysForceEnabled = true;
        public boolean thrusterForceAlways = true;
        public double thrusterForcePlayerRange = 128.0;
        public int statsSyncIntervalTicks = 10;
        public int statsSyncHeartbeatTicks = 40;
        public boolean statsSyncOnlyWhenDirty = true;
    }
}
