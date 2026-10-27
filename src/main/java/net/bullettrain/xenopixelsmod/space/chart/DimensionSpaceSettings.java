package net.bullettrain.xenopixelsmod.space.chart;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.TreeMap;

/** Validated per-dimension settings from an administrator YAML sidecar. Null values inherit. */
public record DimensionSpaceSettings(
        Boolean enabled, Double exitAltitude, Double bandThickness, Double warmUpAltitude,
        Double skyTransitionAltitude, Double reentryAltitude, Double safeEntrySpeed,
        Double gravityMultiplier, Boolean orbitalMotion, Boolean oxygenRequiredAboveExit,
        String oxygenDrainRate, String destructible, Integer handoffOverlayMs,
        Map<String, Object> flags) {

    public double bandThicknessOr(double globalFallback) {
        return bandThickness == null ? globalFallback : bandThickness;
    }

    public boolean enabledOrDefault(boolean defaultValue) {
        return enabled == null ? defaultValue : enabled;
    }

    public double reentryAltitudeOr(double exitAltitude) {
        return reentryAltitude == null ? exitAltitude : reentryAltitude;
    }

    public int handoffOverlayMillisOr(int globalFallback) {
        return handoffOverlayMs == null ? globalFallback : handoffOverlayMs;
    }

    /** True only for the explicitly selected supported inbound effect; absent and off both disable it. */
    public boolean reentryPlasmaShellEnabled() {
        return "plasma_shell".equals(flags.get("reentry_fx"));
    }

    /** Optional sidecar policy; older sidecars keep the existing pod-entry behavior. */
    public boolean allowShipsInAtmosphereOrDefault() {
        Object value = flags.get("allow_ships_in_atmosphere");
        return value == null || Boolean.TRUE.equals(value);
    }

    /** Optional per-dimension spacewalk permission; absent sidecars preserve the prior no-spacewalk behavior. */
    public boolean allowKiSpacewalkOrDefault() {
        Object value = flags.get("allow_ki_spacewalk");
        return Boolean.TRUE.equals(value);
    }

    public static DimensionSpaceSettings parse(JsonObject space, JsonObject flags) {
        ChartDefinition.keys(space, "enabled", "exit_altitude", "band_thickness", "warm_up_altitude",
                "sky_transition_altitude", "reentry_altitude", "safe_entry_speed", "gravity_multiplier",
                "orbital_motion", "oxygen", "destructible", "handoff_overlay_ms");
        ChartDefinition.keys(flags, "allow_ships_in_atmosphere", "allow_ki_spacewalk", "reentry_fx");
        JsonObject oxygen = ChartDefinition.object(space, "oxygen");
        ChartDefinition.keys(oxygen, "required_above_exit", "drain_rate");
        Double exit = number(space, "exit_altitude", -4096, 2032);
        Double band = number(space, "band_thickness", 0, 1_000_000);
        Double warm = number(space, "warm_up_altitude", -4096, 2032);
        Double sky = number(space, "sky_transition_altitude", -4096, 2032);
        Double reentry = number(space, "reentry_altitude", -4096, 2032);
        Double speed = number(space, "safe_entry_speed", 0, 1_000_000);
        Double gravity = number(space, "gravity_multiplier", 0, 1_000_000);
        Integer overlay = integer(space, "handoff_overlay_ms", 0, 60_000);
        String drain = string(oxygen, "drain_rate", null);
        if (drain != null && !drain.matches("slow|normal|fast"))
            throw new IllegalArgumentException("oxygen.drain_rate must be slow, normal, or fast");
        String destructible = string(space, "destructible", null);
        if (destructible != null && !destructible.matches("none|gated|free|admin_only"))
            throw new IllegalArgumentException("destructible must be none, gated, free, or admin_only");
        Map<String, Object> parsedFlags = new TreeMap<>();
        for (var entry : flags.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) throw new IllegalArgumentException("flags." + entry.getKey() + " must be a scalar");
            var value = entry.getValue().getAsJsonPrimitive();
            if (entry.getKey().equals("allow_ships_in_atmosphere") || entry.getKey().equals("allow_ki_spacewalk")) {
                if (!value.isBoolean()) throw new IllegalArgumentException("flags." + entry.getKey() + " must be boolean");
                parsedFlags.put(entry.getKey(), value.getAsBoolean());
            } else if (entry.getKey().equals("reentry_fx")) {
                if (!value.isString() || !value.getAsString().matches("off|plasma_shell"))
                    throw new IllegalArgumentException("flags.reentry_fx must be off or plasma_shell");
                parsedFlags.put(entry.getKey(), value.getAsString());
            }
            else throw new IllegalArgumentException("flags." + entry.getKey() + " must be boolean or string");
        }
        return new DimensionSpaceSettings(bool(space, "enabled"), exit, band, warm, sky, reentry, speed,
                gravity, bool(space, "orbital_motion"), bool(oxygen, "required_above_exit"), drain,
                destructible, overlay, Map.copyOf(parsedFlags));
    }

    private static Boolean bool(JsonObject object, String key) {
        if (!object.has(key)) return null;
        if (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isBoolean())
            throw new IllegalArgumentException(key + " must be boolean");
        return object.get(key).getAsBoolean();
    }
    private static String string(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        if (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException(key + " must be string");
        return object.get(key).getAsString();
    }
    private static Double number(JsonObject object, String key, double min, double max) {
        if (!object.has(key)) return null;
        if (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException(key + " must be numeric");
        double value = object.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(key + " must be in [" + min + ", " + max + "]");
        return value;
    }
    private static Integer integer(JsonObject object, String key, int min, int max) {
        Double value = number(object, key, min, max);
        if (value == null) return null;
        if (value != Math.rint(value)) throw new IllegalArgumentException(key + " must be an integer");
        return value.intValue();
    }
}
