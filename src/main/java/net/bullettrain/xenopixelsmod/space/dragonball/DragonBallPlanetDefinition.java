package net.bullettrain.xenopixelsmod.space.dragonball;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

/** Data-pack assignment for one planet-form Super Dragon Ball. */
public record DragonBallPlanetDefinition(int slot, String hostChartId, double orbitRadiusBlocks,
                                         double orbitPhaseRadians, double orbitPeriodSeconds) {
    /** Compatibility constructor for assignments that derive their orbit from host gravity. */
    public DragonBallPlanetDefinition(int slot, String hostChartId, double orbitRadiusBlocks,
                                      double orbitPhaseRadians) {
        this(slot, hostChartId, orbitRadiusBlocks, orbitPhaseRadians, 0.0);
    }

    public DragonBallPlanetDefinition {
        if (slot < 1 || slot > CosmicDragonBalls.COUNT) throw new IllegalArgumentException("slot must be in [1, " + CosmicDragonBalls.COUNT + "]");
        if (hostChartId == null || hostChartId.indexOf(':') < 1 || ResourceLocation.tryParse(hostChartId) == null)
            throw new IllegalArgumentException("host_chart_id must be a namespaced resource id");
        if (!(orbitRadiusBlocks > 0) || !Double.isFinite(orbitRadiusBlocks) || orbitRadiusBlocks > 1_000_000)
            throw new IllegalArgumentException("orbit_radius_blocks must be finite and in (0, 1000000]");
        if (!Double.isFinite(orbitPhaseRadians)) throw new IllegalArgumentException("orbit_phase_radians must be finite");
        if (orbitPeriodSeconds < 0.0 || !Double.isFinite(orbitPeriodSeconds))
            throw new IllegalArgumentException("orbit_period_seconds must be finite and >= 0 when supplied");
    }

    public static DragonBallPlanetDefinition parse(String resourceId, JsonObject json) {
        int slot = integer(json, "slot");
        ResourceLocation resource = ResourceLocation.tryParse(resourceId);
        if (resource == null || !resource.getPath().equals(Integer.toString(slot)))
            throw new IllegalArgumentException("assignment file must be exactly <namespace>:xenospace/superdragonball/" + slot + ".json");
        String host = string(json, "host_chart_id");
        double radius = number(json, "orbit_radius_blocks");
        double phase = json.has("orbit_phase_radians") ? number(json, "orbit_phase_radians") : 0.0;
        double period = json.has("orbit_period_seconds") ? number(json, "orbit_period_seconds") : 0.0;
        if (json.has("orbit_period_seconds") && !(period > 0.0))
            throw new IllegalArgumentException("orbit_period_seconds must be > 0 when supplied");
        return new DragonBallPlanetDefinition(slot, host, radius, phase, period);
    }

    private static int integer(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException(key + " must be an integer");
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException(key + " must be an integer");
        return (int) value;
    }

    private static String string(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException(key + " must be a string");
        return json.get(key).getAsString();
    }

    private static double number(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException(key + " must be a number");
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException(key + " must be finite");
        return value;
    }
}
