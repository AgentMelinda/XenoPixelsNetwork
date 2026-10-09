package net.bullettrain.xenopixelsmod.npc.projectile;

/** Finite server-owned bounds for scripted item projectiles. */
public final class ItemProjectileRules {
    public static final double MAX_RANGE = 256;
    public static final int MAX_AGE = 200;
    private ItemProjectileRules() {}
    public static void accuracy(int accuracy) {
        if (accuracy < 0 || accuracy > 100) throw new IllegalArgumentException("Projectile accuracy must be 0-100");
    }
    public static boolean allowedDistance(double distanceSquared) {
        return Double.isFinite(distanceSquared) && distanceSquared > 1e-8 && distanceSquared <= MAX_RANGE * MAX_RANGE;
    }
    public static float speed(float speed) { return Float.isFinite(speed) && speed > 0 ? Math.clamp(speed, 0.1f, 3f) : 1.5f; }
    public static float damage(float damage) { return Float.isFinite(damage) && damage >= 0 ? Math.clamp(damage, 0f, 1000000f) : 1f; }
    public static boolean expired(int age) { return age < 0 || age >= MAX_AGE; }
}
