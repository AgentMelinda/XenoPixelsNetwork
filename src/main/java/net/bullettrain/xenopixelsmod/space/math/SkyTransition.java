package net.bullettrain.xenopixelsmod.space.math;

/** Pure altitude blend used by the per-dimension atmosphere transition. */
public final class SkyTransition {
    public record Rgb(float red, float green, float blue) {
        public Rgb {
            red = channel(red);
            green = channel(green);
            blue = channel(blue);
        }

        private static float channel(float value) {
            return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : 0.0f;
        }
    }

    private static final Rgb INDIGO = new Rgb(0.12f, 0.08f, 0.32f);
    private static final Rgb BLACK = new Rgb(0.0f, 0.0f, 0.0f);

    private SkyTransition() {}

    /** Progress from the authored atmosphere start to the body's handoff altitude. */
    public static float factor(double cameraY, double startAltitude, double endAltitude) {
        if (!Double.isFinite(cameraY) || !Double.isFinite(startAltitude) || !Double.isFinite(endAltitude)) return 0.0f;
        if (endAltitude <= startAltitude) return cameraY < startAltitude ? 0.0f : 1.0f;
        return (float) Math.max(0.0, Math.min(1.0, (cameraY - startAltitude) / (endAltitude - startAltitude)));
    }

    /** Keeps the default transition reachable when a body's exit is below the nominal Y450 start. */
    public static double defaultStartAltitude(double endAltitude, double minBuildHeight, double nominalStart) {
        if (!Double.isFinite(endAltitude) || !Double.isFinite(minBuildHeight) || !Double.isFinite(nominalStart))
            return nominalStart;
        if (endAltitude <= minBuildHeight) return endAltitude;
        if (nominalStart < endAltitude) return Math.max(minBuildHeight, nominalStart);
        double margin = Math.max(64.0, (endAltitude - minBuildHeight) * 0.35);
        return Math.max(minBuildHeight, Math.min(nominalStart, endAltitude - margin));
    }

    /** Blend base fog to indigo at mid-transition and black at the handoff altitude. */
    public static Rgb fogColor(Rgb base, float progress) {
        if (base == null || !Float.isFinite(progress)) return base;
        float t = Math.max(0.0f, Math.min(1.0f, progress));
        return t <= 0.5f ? mix(base, INDIGO, t * 2.0f) : mix(INDIGO, BLACK, (t - 0.5f) * 2.0f);
    }

    private static Rgb mix(Rgb from, Rgb to, float t) {
        return new Rgb(from.red + (to.red - from.red) * t,
                from.green + (to.green - from.green) * t,
                from.blue + (to.blue - from.blue) * t);
    }
}
