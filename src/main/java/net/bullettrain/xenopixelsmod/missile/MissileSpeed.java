package net.bullettrain.xenopixelsmod.missile;

/**
 * Shared 1–20 speed curve used by the guidance computer and per-tube overrides.
 */
public final class MissileSpeed {
    private MissileSpeed() {
    }

    public static int clampLevel(int level) {
        return Math.max(1, Math.min(20, level));
    }

    public static double accelFor(int level) {
        int speed = clampLevel(level);
        double t = speed / 20.0;
        return Math.min(5.0, 0.12 + speed * 0.10 + t * t * 0.80);
    }

    public static int ticksFor(int level) {
        int speed = clampLevel(level);
        return Math.min(300, 24 + speed * 11);
    }
}
