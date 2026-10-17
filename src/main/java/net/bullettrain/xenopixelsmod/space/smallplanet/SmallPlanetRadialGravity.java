package net.bullettrain.xenopixelsmod.space.smallplanet;

import org.joml.Vector4d;

/** Pure, allocation-free force calculation for the opt-in gravity on small walkable bodies. */
public final class SmallPlanetRadialGravity {
    private SmallPlanetRadialGravity() {}

    /** Remaining world-down component complementary to the radial blend. */
    public static double vanillaComplement(double radialBlend) {
        if (!Double.isFinite(radialBlend)) return 1.0;
        return 1.0 - Math.max(0.0, Math.min(1.0, radialBlend));
    }

    /**
     * Computes clamped inverse-square radial acceleration with a continuous linear cutoff at 2r.
     * XYZ contains force in blocks/tick²; W is the radial blend weight used for complementary vanilla gravity.
     */
    public static boolean accelerationAt(double x, double y, double z,
                                         double centerX, double centerY, double centerZ,
                                         double radius, double gravity, Vector4d output) {
        if (output == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(centerX) || !Double.isFinite(centerY) || !Double.isFinite(centerZ)
                || !Double.isFinite(radius) || radius <= 0 || radius > SmallPlanetField.WALKABLE_RADIUS_LIMIT
                || !Double.isFinite(gravity) || gravity <= 0) return false;

        double dx = centerX - x, dy = centerY - y, dz = centerZ - z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (!Double.isFinite(distanceSquared) || distanceSquared <= 1.0e-12 || distanceSquared >= 4.0 * radius * radius)
            return false;

        double distance = Math.sqrt(distanceSquared);
        double strength = distance <= radius ? 1.0
                : (radius / distance) * (radius / distance) * ((2.0 * radius - distance) / radius);
        double scale = gravity * strength / distance;
        output.set(dx * scale, dy * scale, dz * scale, strength);
        return true;
    }
}
