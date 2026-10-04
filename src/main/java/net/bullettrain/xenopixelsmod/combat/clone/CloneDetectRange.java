package net.bullettrain.xenopixelsmod.combat.clone;

/**
 * How far copies notice a fight. Multi-form uses it as the leash/hostile scan;
 * Zanzoken uses it as the radius that nearby AI can be fooled onto afterimages.
 *
 * <p>Minecraft-free so the clamp and in-range tests stay unit-tested.
 */
public final class CloneDetectRange {
    public static final double DEFAULT = CloneCombatPolicy.LEASH;
    public static final double MIN = 1.0;
    public static final double MAX = 128.0;

    private CloneDetectRange() {
    }

    public static double clamp(double range) {
        if (!Double.isFinite(range) || range <= 0.0) {
            return DEFAULT;
        }
        return Math.max(MIN, Math.min(MAX, range));
    }

    public static boolean within(double distance, double range) {
        double limit = clamp(range);
        return Double.isFinite(distance) && distance <= limit;
    }

    public static boolean withinSqr(double distSqr, double range) {
        double limit = clamp(range);
        return Double.isFinite(distSqr) && distSqr <= limit * limit;
    }
}
