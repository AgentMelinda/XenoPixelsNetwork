package net.bullettrain.xenopixelsmod.aero;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aero flight-controller tuning, written to {@code config/xenopixelsmod-aero.json}.
 *
 * <p>Kept separate from {@code XenoServerConfig} deliberately: that class requires every field
 * to be repeated across its {@code Data} holder, {@code snapshot()} and {@code apply()}, and
 * Aero has enough knobs that the boilerplate would dominate. This follows the lighter
 * {@code OrbitalGravityConfig} shape instead.
 *
 * <p>Defaults reproduce the power budget from the design notes: a 1,200 FE/t baseline sized
 * around ten Mekanism Advanced Solar Generators, with engaged flight, the terrain map and
 * advanced cooling adding on top.
 */
public final class AeroConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-aero.json");

    /**
     * Master switch for the power requirement.
     *
     * <p>When false the controller runs for free and always reports
     * {@link AeroBus.PowerTier#NOMINAL}. Servers that do not want an FE dependency for flight
     * can turn this off without losing any other Aero behavior.
     */
    public static boolean requirePower = true;

    /** Buffer size in FE. Large enough that the combined load does not empty it instantly. */
    public static int energyCapacity = 5_000_000;

    /** Maximum FE/t accepted from adjacent power, sized so real infrastructure can saturate it. */
    public static int maxReceiveFePerTick = 12_000;

    /** Draw while the controller is in flight mode, before any subsystem is enabled. */
    public static int baselineDrawFePerTick = 1_200;

    /**
     * Per-subsystem draw overrides, keyed by {@link AeroSubsystem#name()}.
     *
     * <p>Absent or unparsable entries fall back to the enum's own
     * {@link AeroSubsystem#drawFePerTick()}, so a typo degrades to the default rather than
     * zeroing a subsystem's cost.
     */
    public static Map<String, Integer> subsystemDraw = defaultSubsystemDraw();

    private AeroConfig() {
    }

    private static Map<String, Integer> defaultSubsystemDraw() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (AeroSubsystem subsystem : AeroSubsystem.values()) {
            map.put(subsystem.name(), subsystem.drawFePerTick());
        }
        return map;
    }

    /** Configured draw for a subsystem, falling back to its declared default. */
    public static int drawFor(AeroSubsystem subsystem) {
        if (subsystem == null) return 0;
        Integer configured = subsystemDraw.get(subsystem.name());
        if (configured == null || configured < 0) return subsystem.drawFePerTick();
        return configured;
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            requirePower = data.requirePower;
            energyCapacity = Math.max(1_000, data.energyCapacity);
            maxReceiveFePerTick = Math.max(1, data.maxReceiveFePerTick);
            baselineDrawFePerTick = Math.max(0, data.baselineDrawFePerTick);
            if (data.subsystemDraw != null && !data.subsystemDraw.isEmpty()) {
                subsystemDraw = new LinkedHashMap<>(data.subsystemDraw);
            }
        } catch (Exception e) {
            // A malformed file must never stop the server booting.
            XenoPixelsMod.LOGGER.warn("Failed to load Aero config; using defaults", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                Data data = new Data();
                data.requirePower = requirePower;
                data.energyCapacity = energyCapacity;
                data.maxReceiveFePerTick = maxReceiveFePerTick;
                data.baselineDrawFePerTick = baselineDrawFePerTick;
                data.subsystemDraw = new LinkedHashMap<>(subsystemDraw);
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save Aero config", e);
        }
    }

    public static final class Data {
        public boolean requirePower = true;
        public int energyCapacity = 5_000_000;
        public int maxReceiveFePerTick = 12_000;
        public int baselineDrawFePerTick = 1_200;
        public Map<String, Integer> subsystemDraw;
    }
}
