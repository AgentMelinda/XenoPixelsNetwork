package net.bullettrain.xenopixelsmod.space.event;

import java.util.Objects;

/** Pure, bounded visual ramp for a chart-star lifecycle stage. It does not change physics or chart gravity. */
public final class StellarStageVisual {
    private static final Appearance STABLE = new Appearance(1.0, 1.0, 1.0, 1.0);
    private static final Appearance SWELLING = new Appearance(1.0, 0.78, 0.58, 1.30);
    private static final Appearance RED_GIANT = new Appearance(1.0, 0.34, 0.18, 1.85);
    private static final Appearance DYING = new Appearance(1.0, 0.08, 0.03, 2.0);
    private static final Appearance REMNANT = new Appearance(0.72, 0.82, 1.0, 1.05);

    private StellarStageVisual() {}

    /** RGB shader multiplier in [0,1] plus a far-projection-only radius scale. */
    public record Appearance(double red, double green, double blue, double radiusScale) {
        public Appearance {
            if (!channel(red) || !channel(green) || !channel(blue)
                    || !Double.isFinite(radiusScale) || radiusScale < 1.0 || radiusScale > 2.0)
                throw new IllegalArgumentException("invalid stellar appearance");
        }

        private static boolean channel(double value) {
            return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
        }
    }

    /**
     * Evaluates a normalized stage transition. Progress is clamped; non-finite values resolve to the stage start.
     * Endpoints are shared between adjacent stages, preventing visible color or size jumps when a stage advances.
     */
    public static Appearance at(StellarStage stage, double progress) {
        Objects.requireNonNull(stage, "stage");
        if (!Double.isFinite(progress)) progress = 0.0;
        double t = Math.max(0.0, Math.min(1.0, progress));
        // Smoothstep avoids a sudden derivative change when an operator starts or advances a lifecycle stage.
        t = t * t * (3.0 - 2.0 * t);
        return switch (stage) {
            case STABLE -> STABLE;
            case SWELLING -> mix(STABLE, SWELLING, t);
            case RED_GIANT -> mix(SWELLING, RED_GIANT, t);
            case DYING -> mix(RED_GIANT, DYING, t);
            // Fade from the dying star into the remnant over the full nova stage. The next
            // stage starts at this same appearance, so advancing to REMNANT cannot pop.
            case SUPERNOVA -> mix(DYING, REMNANT, t);
            case REMNANT -> REMNANT;
        };
    }

    private static Appearance mix(Appearance a, Appearance b, double t) {
        return new Appearance(lerp(a.red(), b.red(), t), lerp(a.green(), b.green(), t),
                lerp(a.blue(), b.blue(), t), lerp(a.radiusScale(), b.radiusScale(), t));
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }
}
