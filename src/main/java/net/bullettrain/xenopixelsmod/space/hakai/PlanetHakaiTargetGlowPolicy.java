package net.bullettrain.xenopixelsmod.space.hakai;

/** Pure timing for the short planet-only pulse at Hakai's authoritative impact point. */
public final class PlanetHakaiTargetGlowPolicy {
    private static final double DURATION = 0.32;
    private PlanetHakaiTargetGlowPolicy() {}

    public record Sample(boolean visible, float angleRadians, float halfWidthRadians, float alpha) {}

    public static Sample sample(double progress, boolean enabled) {
        if (!enabled || !Double.isFinite(progress) || progress < 0 || progress >= DURATION)
            return new Sample(false, 0, 0, 0);
        double t = Math.max(0, Math.min(1, progress / DURATION));
        double eased = t * t * (3 - 2 * t);
        return new Sample(true,
                (float) (0.015 + 0.34 * eased),
                (float) (0.010 + 0.045 * eased),
                (float) (1 - eased));
    }
}
