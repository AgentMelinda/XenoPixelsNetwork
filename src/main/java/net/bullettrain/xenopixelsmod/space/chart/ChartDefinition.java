package net.bullettrain.xenopixelsmod.space.chart;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.space.math.GravityField;
import net.bullettrain.xenopixelsmod.space.math.OrbitalElements;
import java.util.Locale;
import java.util.Set;

/** Version-one chart data. Pure parsing, with no level loading or Minecraft class dependency. */
public final class ChartDefinition {
    private ChartDefinition() {}
    public static String id(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("invalid namespaced id: " + value);
        return value;
    }
    public static CelestialBody parse(String resourceId, JsonObject data) {
        id(resourceId);
        keys(data, "schema_version", "kind", "display_name", "parent", "position", "orbit",
                "radius_blocks", "gravity", "dimension", "handoff_anchor_y", "exit_altitude",
                "warm_up_altitude", "spatial_anchor",
                "orbital_motion", "erasable", "danger_rating", "void_damage", "sky_style",
                // §6 oxygen flag (read by OxygenSystem from the raw definition) and §15 black-hole block
                // (read by BlackHoleRegistry); validated here so a malformed value is a diagnostic, not a silent default.
                "breathable", "black_hole");
        if (data.has("breathable") && !(data.get("breathable").isJsonPrimitive() && data.get("breathable").getAsJsonPrimitive().isBoolean()))
            throw new IllegalArgumentException("breathable must be true or false");
        if (data.has("black_hole")) {
            JsonObject hole = object(data, "black_hole");
            keys(hole, "horizon_radius", "tidal_radius", "lens_radius", "pull", "exit");
            double horizon = number(hole, "horizon_radius", -1), tidal = number(hole, "tidal_radius", -1),
                    lens = number(hole, "lens_radius", -1);
            if (!(horizon > 0) || !(tidal > horizon) || !(lens >= tidal) || lens > 1_000_000)
                throw new IllegalArgumentException("black_hole needs 0 < horizon_radius < tidal_radius <= lens_radius <= 1000000");
            if (hole.has("exit")) {
                JsonObject exit = object(hole, "exit");
                keys(exit, "dimension", "x", "y", "z");
                String exitDimension = string(exit, "dimension", null);
                if (exitDimension != null) id(exitDimension);
            }
        }
        if (number(data, "schema_version", 1) != 1) throw new IllegalArgumentException("unsupported schema_version");
        double radius = number(data, "radius_blocks", 96);
        if (radius <= 0 || radius > 1_000_000) throw new IllegalArgumentException("radius_blocks outside (0, 1000000]");
        var kind = CelestialBody.BodyKind.valueOf(string(data, "kind", "planet").toUpperCase(Locale.ROOT));
        JsonObject position = object(data, "position");
        keys(position, "x", "y", "z");
        double x = number(position, "x", 0), y = number(position, "y", 0), z = number(position, "z", 0);
        JsonObject gravity = object(data, "gravity");
        keys(gravity, "surface_ms2", "falloff");
        double surface = number(gravity, "surface_ms2", 0);
        if (surface < 0 || surface > 1_000_000) throw new IllegalArgumentException("surface_ms2 outside [0, 1000000]");
        var falloff = GravityField.Falloff.valueOf(string(gravity, "falloff", "inverse_square").toUpperCase(Locale.ROOT));
        double mu = falloff == GravityField.Falloff.INVERSE_SQUARE ? surface * radius * radius : surface;
        String parent = string(data, "parent", null);
        OrbitalElements orbit = null;
        if (data.has("orbit")) {
            JsonObject o = object(data, "orbit");
            keys(o, "parent", "semi_major", "eccentricity", "inclination", "ascending_node",
                    "argument_of_periapsis", "mean_anomaly", "period_seconds", "epoch_seconds");
            String orbitParent = string(o, "parent", parent);
            if (parent != null && orbitParent != null && !parent.equals(orbitParent))
                throw new IllegalArgumentException("parent conflicts with orbit.parent");
            parent = orbitParent;
            orbit = new OrbitalElements(number(o, "semi_major", -1), number(o, "eccentricity", 0),
                    number(o, "inclination", 0), number(o, "ascending_node", 0),
                    number(o, "argument_of_periapsis", 0), number(o, "mean_anomaly", 0),
                    number(o, "period_seconds", -1), number(o, "epoch_seconds", 0));
        }
        if (parent != null) id(parent);
        String dimension = string(data, "dimension", null);
        if (dimension != null) id(dimension);
        double exitAltitude = number(data, "exit_altitude", 700);
        double warmUpAltitude = number(data, "warm_up_altitude",
                CelestialBody.defaultWarmUpAltitude(exitAltitude));
        JsonObject spatialAnchor = object(data, "spatial_anchor");
        keys(spatialAnchor, "x", "z");
        double danger = number(data, "danger_rating", 0);
        if (danger < 0 || danger > 10 || danger != Math.rint(danger))
            throw new IllegalArgumentException("danger_rating must be an integer in [0, 10]");
        double style = number(data, "sky_style", 0);
        if (style < 0 || style >= CelestialBody.MAX_VISUAL_STYLES || style != Math.rint(style))
            throw new IllegalArgumentException("sky_style must be an integer cell in [0, "
                    + CelestialBody.MAX_VISUAL_STYLES + ")");
        return new CelestialBody(resourceId, kind, string(data, "display_name", resourceId), orbit,
                x, y, z, parent, radius, mu, falloff, dimension,
                number(data, "handoff_anchor_y", 64), exitAltitude, warmUpAltitude,
                bool(data, "orbital_motion", true), bool(data, "erasable", false), (int) danger,
                nullableBool(data, "void_damage"), (int) style,
                number(spatialAnchor, "x", 0.0), number(spatialAnchor, "z", 0.0));
    }
    public static JsonObject merge(JsonObject base, JsonObject override) {
        JsonObject result = base.deepCopy();
        override.entrySet().forEach(entry -> {
            JsonElement old = result.get(entry.getKey()), value = entry.getValue();
            result.add(entry.getKey(), old != null && old.isJsonObject() && value.isJsonObject()
                    ? merge(old.getAsJsonObject(), value.getAsJsonObject()) : value.deepCopy());
        });
        return result;
    }
    public static void keys(JsonObject o, String... allowed) {
        Set<String> names = Set.of(allowed);
        for (String key : o.keySet()) if (!names.contains(key))
            throw new IllegalArgumentException("unsupported field: " + key);
    }
    public static JsonObject object(JsonObject o, String key) {
        if (!o.has(key)) return new JsonObject();
        if (!o.get(key).isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        return o.getAsJsonObject(key);
    }
    public static String string(JsonObject o, String key, String fallback) {
        if (!o.has(key) || o.get(key).isJsonNull()) return fallback;
        if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException(key + " must be a string");
        String value = o.get(key).getAsString();
        if (value.isBlank() || value.length() > 256) throw new IllegalArgumentException(key + " must contain 1..256 characters");
        return value;
    }
    private static double number(JsonObject o, String key, double fallback) {
        if (!o.has(key)) return fallback;
        if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException(key + " must be numeric");
        double value = o.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException(key + " must be finite");
        return value;
    }
    private static boolean bool(JsonObject o, String key, boolean fallback) {
        if (!o.has(key)) return fallback;
        if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isBoolean())
            throw new IllegalArgumentException(key + " must be boolean");
        return o.get(key).getAsBoolean();
    }
    /** A tri-state key: absent or JSON null means "inherit", so it stays distinguishable from false. */
    private static Boolean nullableBool(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return null;
        if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isBoolean())
            throw new IllegalArgumentException(key + " must be boolean");
        return o.get(key).getAsBoolean();
    }
}
