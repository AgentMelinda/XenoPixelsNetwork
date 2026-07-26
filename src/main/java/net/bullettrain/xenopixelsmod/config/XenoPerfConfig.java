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
 * Server performance knobs for VS2 ship sleep + Create budgets.
 * Written to {@code config/xenopixelsmod-perf.json}.
 *
 * @see net.bullettrain.xenopixelsmod.perf.Vs2ShipSleepManager
 * @see docs/create-vs2-optimization.md
 * @see docs/vs2-optimization-and-missiles.md
 */
public final class XenoPerfConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-perf.json");

    /** Master switch for all XenoPixels perf systems. */
    public static boolean perfEnabled = true;

    // --- VS2 ship sleep (Hot / Cold via setStatic) ---
    public static boolean vs2SleepEnabled = true;
    /** Blocks: player within this → ship stays dynamic (Hot). */
    public static double vs2HotRange = 96.0;
    /**
     * Blocks: between hot and warm, still dynamic if moving;
     * beyond warm + slow → Cold (static).
     */
    public static double vs2WarmRange = 192.0;
    /** Linear speed below this (blocks/tick scale) counts as “parked”. */
    public static double vs2SleepLinearSpeed = 0.08;
    /** Seconds parked + far before setStatic(true). */
    public static int vs2IdleSeconds = 3;
    /** Soft cap on dynamic (non-static) loaded ships; excess cold-parked by age. */
    public static int vs2MaxActiveShipsGlobal = 24;
    /** How often to re-evaluate ships (ticks). */
    public static int vs2EvalIntervalTicks = 20;

    // --- MSPT watchdog ---
    public static boolean msptWatchdogEnabled = true;
    /** Average MSPT above this enters “stress” mode. */
    public static double msptThreshold = 45.0;
    /** Seconds of samples for MSPT average. */
    public static int msptWindowSeconds = 10;
    /** Multiply hot range by this under stress (0.5 = half range). */
    public static double msptStressHotScale = 0.55;
    /** Under stress, also force cold on non-near ships immediately. */
    public static boolean msptForceColdFarShips = true;

    // --- Create (optional; soft when Create is loaded) ---
    public static boolean createPerfEnabled = true;
    /** When VS ship goes Cold, mark Create ship networks as “asleep” (metrics + future hooks). */
    public static boolean createCoupledSleep = true;
    public static double createHotRange = 64.0;
    public static double createWarmRange = 128.0;
    public static int createMaxContraptionsGlobal = 48;
    public static int createMaxContraptionsPerPlayer = 4;
    public static int createIdleSleepSeconds = 30;

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
        d.perfEnabled = perfEnabled;
        d.vs2SleepEnabled = vs2SleepEnabled;
        d.vs2HotRange = vs2HotRange;
        d.vs2WarmRange = vs2WarmRange;
        d.vs2SleepLinearSpeed = vs2SleepLinearSpeed;
        d.vs2IdleSeconds = vs2IdleSeconds;
        d.vs2MaxActiveShipsGlobal = vs2MaxActiveShipsGlobal;
        d.vs2EvalIntervalTicks = vs2EvalIntervalTicks;
        d.msptWatchdogEnabled = msptWatchdogEnabled;
        d.msptThreshold = msptThreshold;
        d.msptWindowSeconds = msptWindowSeconds;
        d.msptStressHotScale = msptStressHotScale;
        d.msptForceColdFarShips = msptForceColdFarShips;
        d.createPerfEnabled = createPerfEnabled;
        d.createCoupledSleep = createCoupledSleep;
        d.createHotRange = createHotRange;
        d.createWarmRange = createWarmRange;
        d.createMaxContraptionsGlobal = createMaxContraptionsGlobal;
        d.createMaxContraptionsPerPlayer = createMaxContraptionsPerPlayer;
        d.createIdleSleepSeconds = createIdleSleepSeconds;
        return d;
    }

    public static void apply(Data d) {
        if (d == null) return;
        perfEnabled = d.perfEnabled;
        vs2SleepEnabled = d.vs2SleepEnabled;
        vs2HotRange = Math.max(16.0, d.vs2HotRange);
        vs2WarmRange = Math.max(vs2HotRange, d.vs2WarmRange);
        vs2SleepLinearSpeed = Math.max(0.01, d.vs2SleepLinearSpeed);
        vs2IdleSeconds = Math.max(1, Math.min(60, d.vs2IdleSeconds <= 0 ? 3 : d.vs2IdleSeconds));
        vs2MaxActiveShipsGlobal = Math.max(4, Math.min(256, d.vs2MaxActiveShipsGlobal <= 0 ? 24 : d.vs2MaxActiveShipsGlobal));
        vs2EvalIntervalTicks = Math.max(5, Math.min(100, d.vs2EvalIntervalTicks <= 0 ? 20 : d.vs2EvalIntervalTicks));
        msptWatchdogEnabled = d.msptWatchdogEnabled;
        msptThreshold = Math.max(20.0, d.msptThreshold);
        msptWindowSeconds = Math.max(3, Math.min(60, d.msptWindowSeconds <= 0 ? 10 : d.msptWindowSeconds));
        msptStressHotScale = Math.max(0.25, Math.min(1.0, d.msptStressHotScale <= 0 ? 0.55 : d.msptStressHotScale));
        msptForceColdFarShips = d.msptForceColdFarShips;
        createPerfEnabled = d.createPerfEnabled;
        createCoupledSleep = d.createCoupledSleep;
        createHotRange = Math.max(16.0, d.createHotRange);
        createWarmRange = Math.max(createHotRange, d.createWarmRange);
        createMaxContraptionsGlobal = Math.max(4, Math.min(256, d.createMaxContraptionsGlobal <= 0 ? 48 : d.createMaxContraptionsGlobal));
        createMaxContraptionsPerPlayer = Math.max(1, Math.min(32, d.createMaxContraptionsPerPlayer <= 0 ? 4 : d.createMaxContraptionsPerPlayer));
        createIdleSleepSeconds = Math.max(5, Math.min(300, d.createIdleSleepSeconds <= 0 ? 30 : d.createIdleSleepSeconds));
    }

    public static String statusLine() {
        return "perf=" + perfEnabled
                + " vs2Sleep=" + vs2SleepEnabled
                + " hot=" + vs2HotRange
                + " warm=" + vs2WarmRange
                + " maxShips=" + vs2MaxActiveShipsGlobal
                + " msptWatch=" + msptWatchdogEnabled
                + " create=" + createPerfEnabled
                + " createCoupled=" + createCoupledSleep;
    }

    public static class Data {
        public boolean perfEnabled = true;
        public boolean vs2SleepEnabled = true;
        public double vs2HotRange = 96.0;
        public double vs2WarmRange = 192.0;
        public double vs2SleepLinearSpeed = 0.08;
        public int vs2IdleSeconds = 3;
        public int vs2MaxActiveShipsGlobal = 24;
        public int vs2EvalIntervalTicks = 20;
        public boolean msptWatchdogEnabled = true;
        public double msptThreshold = 45.0;
        public int msptWindowSeconds = 10;
        public double msptStressHotScale = 0.55;
        public boolean msptForceColdFarShips = true;
        public boolean createPerfEnabled = true;
        public boolean createCoupledSleep = true;
        public double createHotRange = 64.0;
        public double createWarmRange = 128.0;
        public int createMaxContraptionsGlobal = 48;
        public int createMaxContraptionsPerPlayer = 4;
        public int createIdleSleepSeconds = 30;
    }
}
