package net.bullettrain.xenopixelsmod.client.space;

/** Lightweight vertex-color equivalent of an emissive violet shader band at the active impact cut. */
final class PlanetHakaiSurfaceTint {
    private static final float INNER_EDGE = 0.004f;
    private static final float OUTER_EDGE = 0.11f;
    private static final float GREEN_DROP = 190.0f;

    private PlanetHakaiSurfaceTint() {}

    /** Returns opaque white away from the cut and a smooth pulsing magenta tint at its moving boundary. */
    static int color(float surfaceDot, float cut, float pulse) {
        if (!Float.isFinite(surfaceDot) || !Float.isFinite(cut) || !Float.isFinite(pulse)) return 0xFFFFFFFF;
        float distance = Math.abs(surfaceDot - cut);
        float t = clamp((distance - INNER_EDGE) / (OUTER_EDGE - INNER_EDGE));
        float edge = 1.0f - t * t * (3.0f - 2.0f * t);
        float strength = edge * (0.72f + 0.28f * clamp(pulse));
        int green = Math.round(255.0f - GREEN_DROP * strength);
        return 0xFFFF00FF | (green << 8);
    }

    private static float clamp(float value) { return Math.max(0.0f, Math.min(1.0f, value)); }
}
