package net.bullettrain.xenopixelsmod.space.asteroid;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.space.chart.ChartDefinition;

/**
 * One authored asteroid belt from {@code data/<namespace>/xenospace/belt/<path>.json} (S11 B1). A belt is a ring in
 * the Voidsea's XZ plane around a chart body: its centre follows the parent's chart x/z, and its plane sits at
 * {@code plane_y} (default 128, the plane the physical planets are staged on). No Minecraft types, so it is testable
 * without the game. The asteroids themselves come from {@link AsteroidField}; nothing per asteroid is stored here.
 */
public record BeltDefinition(String id, String parentId, double innerRadius, double outerRadius,
                             double halfThickness, double planeY, boolean icy) {
    public static final double DEFAULT_PLANE_Y = 128;

    public BeltDefinition {
        ChartDefinition.id(id);
        ChartDefinition.id(parentId);
        // AsteroidField.Belt repeats the bound checks; failing here names the resource instead of a centre.
        new AsteroidField.Belt(id, 0, planeY, 0, innerRadius, outerRadius, halfThickness, icy);
        // Asteroids are Sable bodies, not world blocks, so the Voidsea block height (0..1024) does not bound them. The belt
        // must stay inside the unbounded-Voidsea limit; with voidseaUnboundedHeight off, Sable's own removal limits
        // (-10,000 / 100,000) apply, so keep authored belts inside those unless the switch is on.
        double limit = net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.VOIDSEA_HEIGHT_LIMIT;
        if (!Double.isFinite(planeY) || Math.abs(planeY) + halfThickness + AsteroidField.MAX_RADIUS > limit)
            throw new IllegalArgumentException("plane_y +- half_thickness must stay within +-" + (long) limit);
    }

    /** The belt in world coordinates for a parent currently at chart x/z. */
    public AsteroidField.Belt at(double parentX, double parentZ) {
        return new AsteroidField.Belt(id, parentX, planeY, parentZ, innerRadius, outerRadius, halfThickness, icy);
    }

    public static BeltDefinition parse(String id, JsonObject json) {
        int schema = (int) number(json, "schema_version", 1);
        if (schema != 1) throw new IllegalArgumentException("unsupported belt schema_version " + schema);
        String parent = ChartDefinition.string(json, "parent", null);
        if (parent == null) throw new IllegalArgumentException("belt requires parent");
        return new BeltDefinition(id, parent, number(json, "inner_radius", Double.NaN), number(json, "outer_radius", Double.NaN),
                number(json, "half_thickness", Double.NaN), number(json, "plane_y", DEFAULT_PLANE_Y),
                json.has("icy") && json.get("icy").getAsBoolean());
    }

    private static double number(JsonObject json, String key, double fallback) {
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull()) {
            if (Double.isNaN(fallback)) throw new IllegalArgumentException("belt requires " + key);
            return fallback;
        }
        return value.getAsDouble();
    }
}
