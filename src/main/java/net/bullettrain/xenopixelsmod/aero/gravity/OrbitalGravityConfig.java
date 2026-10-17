package net.bullettrain.xenopixelsmod.aero.gravity;

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
 * Per-dimension surface gravity for Sable ships, written to
 * {@code config/xenopixelsmod-orbital-gravity.json}.
 *
 * <p>Deliberately keyed by dimension id <b>string</b> rather than by any space mod's classes. AeroStar's
 * {@code OrbitGravitySystem} hard-referenced {@code com.lightning.northstar.world.dimension.NorthstarDimensions}
 * and therefore died with {@code NoClassDefFoundError} the moment Northstar Redux 0.6 moved that class.
 * Reading ids from config means a space mod can rename, move or repackage anything without breaking ship
 * gravity, and server owners can add dimensions we have never heard of. That reasoning outlives the
 * AeroStar compat this config used to negotiate with — which is gone, so {@link OrbitalGravitySystem}
 * now owns ship gravity outright.
 *
 * <p>Values are in m/s², matching {@link net.bullettrain.xenopixelsmod.vs.ShipGravityControl}
 * whose {@code NORMAL_GRAVITY} baseline is 10.0. Unlisted dimensions get the baseline.
 */
public final class OrbitalGravityConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-orbital-gravity.json");

    /** Master switch; when false ship gravity is left entirely alone. */
    public static boolean enabled = true;

    /** Dimension id → gravity in m/s². */
    public static Map<String, Double> gravityByDimension = defaults();

    private OrbitalGravityConfig() {
    }

    /**
     * Only the vanilla overworld is listed by default now.
     *
     * <p>The Northstar Redux entries this used to ship — moon 1.62, mars 3.73, venus 8.87, mercury
     * 3.70, and 0.0 for each of its four orbit dimensions, taken from Redux's own
     * {@code data/northstar/dimension_physics/*.json} — were dropped along with the rest of the
     * Northstar coupling. A server that still runs Redux keeps working, it just gets the 10.0
     * baseline on those dimensions until the owner adds them back, and the numbers above are the ones
     * to add if they want Redux's own values.
     *
     * <p><b>Upgrade note:</b> {@link #load()} only replaces this map when the file has a non-empty
     * one, so an existing {@code xenopixelsmod-orbital-gravity.json} keeps whatever dimensions it
     * already listed, Northstar ones included. Removing the defaults changes fresh installs, not
     * configured servers.
     */
    private static Map<String, Double> defaults() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("minecraft:overworld", 10.0);
        return map;
    }

    /**
     * @return gravity for {@code dimensionId}, or {@code fallback} when unconfigured
     */
    public static double gravityFor(String dimensionId, double fallback) {
        if (!enabled || dimensionId == null) return fallback;
        Double value = gravityByDimension.get(dimensionId);
        if (value == null || !Double.isFinite(value)) return fallback;
        // Clamp to the range ShipGravityControl accepts so a typo cannot launch every ship.
        return Math.max(-100.0, Math.min(100.0, value));
    }

    public static boolean hasDimension(String dimensionId) {
        return enabled && dimensionId != null && gravityByDimension.containsKey(dimensionId);
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            enabled = data.enabled;
            if (data.gravityByDimension != null && !data.gravityByDimension.isEmpty()) {
                gravityByDimension = new LinkedHashMap<>(data.gravityByDimension);
            }
        } catch (Exception e) {
            // A malformed file must not stop the server booting; keep the defaults.
            XenoPixelsMod.LOGGER.warn("Failed to load orbital gravity config; using defaults", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                Data data = new Data();
                data.enabled = enabled;
                data.gravityByDimension = new LinkedHashMap<>(gravityByDimension);
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save orbital gravity config", e);
        }
    }

    /**
     * The on-disk shape. The old {@code overrideAeroStar} key is simply absent now; Gson ignores
     * unknown keys on read, so a config file written by an earlier build loads without complaint and
     * the key is dropped the next time the file is saved. No migration step is needed, and a server
     * that had it set to either value gets the same result: this mod owns gravity.
     */
    public static final class Data {
        public boolean enabled = true;
        public Map<String, Double> gravityByDimension;
    }
}
