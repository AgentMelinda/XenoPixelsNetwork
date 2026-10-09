package net.bullettrain.xenopixelsmod.combat.v2;

/**
 * The geometry of "can this hit that". Minecraft-free.
 *
 * <p>Reach is measured from the attacker's eyes to the nearest point of the target's hitbox, not
 * between two pairs of feet. Feet to feet is wrong in both directions: it puts a wide or tall
 * target further away than it looks, and it counts the full height difference against a target
 * that is merely standing on a step. Facing is measured on the horizontal plane only, so a target
 * overhead or underfoot is not "behind" the attacker.
 */
public final class ReachRules {

    private ReachRules() {}

    /** Distance from a point to the nearest point of an axis-aligned box; 0 inside it. */
    public static double distanceToBox(double px, double py, double pz,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        double dx = Math.max(Math.max(minX - px, 0.0), px - maxX);
        double dy = Math.max(Math.max(minY - py, 0.0), py - maxY);
        double dz = Math.max(Math.max(minZ - pz, 0.0), pz - maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * How squarely the attacker faces the target on the horizontal plane: 1 dead ahead, 0 square
     * to the side, -1 directly behind. A target directly above or below, or an attacker looking
     * straight up or down, counts as faced.
     */
    public static double facingDot(double lookX, double lookZ, double toX, double toZ) {
        double lookLen = Math.hypot(lookX, lookZ);
        double toLen = Math.hypot(toX, toZ);
        if (lookLen < 1.0e-6 || toLen < 1.0e-6) return 1.0;
        return (lookX * toX + lookZ * toZ) / (lookLen * toLen);
    }

    /** Whether a target at {@code boxDistance}, faced by {@code facingDot}, can be struck. */
    public static boolean reachable(double boxDistance, double range, double facingDot,
                                    double minFacingDot) {
        return boxDistance <= Math.max(0.0, range) && facingDot >= minFacingDot;
    }
}
