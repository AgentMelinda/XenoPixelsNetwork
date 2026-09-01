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
 * <p>Deliberately keyed by dimension id <b>string</b> rather than by any space mod's classes.
 * This replaces AeroStar's {@code OrbitGravitySystem}, which hard-referenced
 * {@code com.lightning.northstar.world.dimension.NorthstarDimensions} and therefore died with
 * {@code NoClassDefFoundError} the moment Northstar Redux 0.6 moved that class — AeroStar
 * 1.0.1 pins Redux 0.5.4 and was never updated for the 0.6 line. Reading ids from config means
 * a space mod can rename, move or repackage anything without breaking ship gravity, and server
 * owners can add dimensions we have never heard of.
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

    /**
     * Cancel AeroStar's own orbital gravity handler when that mod is installed.
     *
     * <p>Two systems both correcting gravity every physics tick would fight each other, so
     * exactly one has to own it. <b>Default is {@code false}: AeroStar owns ship gravity.</b>
     * The {@code NorthstarDimensions} shim makes AeroStar's handler resolve and run correctly
     * on Redux 0.6, so there is no longer a crash to avoid and its gravity is the behavior
     * servers are already tuned around.
     *
     * <p>This default is deliberate and was learned the hard way. The mixin that performs the
     * cancel silently failed to apply for a long time, so gravity was in practice always
     * AeroStar's regardless of what this flag said. When the mixin was finally fixed, a
     * {@code true} here flipped ownership to us for the first time and changed how every ship
     * flew. Defaulting to {@code false} keeps the behavior that servers actually run with;
     * turn it on to opt into our config-tunable per-dimension gravity instead.
     */
    public static boolean overrideAeroStar = false;

    /** Dimension id → gravity in m/s². */
    public static Map<String, Double> gravityByDimension = defaults();

    private OrbitalGravityConfig() {
    }

    /**
     * Gravity per body, taken from Northstar Redux's own
     * {@code data/northstar/dimension_physics/*.json} on the 1.21.1 branch, where each file
     * carries a {@code base_gravity} vector — moon {@code -1.62}, mars {@code -3.73}, venus
     * {@code -8.87}, mercury {@code -3.7}, and {@code 0.0} for every orbit dimension. Using
     * the mod's published numbers keeps ship gravity consistent with player gravity.
     *
     * <p>Redux 0.6.0 moved planets to a datapack-based system and added orbit dimensions for
     * Earth, Mars, Mercury and Venus; all four are listed here.
     */
    private static Map<String, Double> defaults() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("minecraft:overworld", 10.0);
        map.put("northstar:moon", 1.62);
        map.put("northstar:mars", 3.73);
        map.put("northstar:venus", 8.87);
        map.put("northstar:mercury", 3.70);
        map.put("northstar:earth_orbit", 0.0);
        map.put("northstar:mars_orbit", 0.0);
        map.put("northstar:mercury_orbit", 0.0);
        map.put("northstar:venus_orbit", 0.0);
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
            overrideAeroStar = data.overrideAeroStar;
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
                data.overrideAeroStar = overrideAeroStar;
                data.gravityByDimension = new LinkedHashMap<>(gravityByDimension);
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save orbital gravity config", e);
        }
    }

    public static final class Data {
        public boolean enabled = true;
        /** Must match {@link OrbitalGravityConfig#overrideAeroStar} — see its javadoc. */
        public boolean overrideAeroStar = false;
        public Map<String, Double> gravityByDimension;
    }
}
