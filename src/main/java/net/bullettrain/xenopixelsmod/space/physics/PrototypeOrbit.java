package net.bullettrain.xenopixelsmod.space.physics;

/** Bounded central acceleration for the disposable, 32-block-radius orbit experiment. */
public final class PrototypeOrbit {
    public static final double MU = 128.0;
    private PrototypeOrbit() {}

    /** Multiply the displacement to the anchor by this factor; soften within eight blocks. */
    public static double accelerationFactor(double dx, double dy, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) return 0;
        double radius = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(radius)) return 0;
        double softened = Math.max(8, radius);
        return MU / (softened * softened * softened);
    }

    public static double step(double seconds) {
        return Double.isFinite(seconds) ? Math.max(0, Math.min(0.1, seconds)) : 0;
    }

    /** The experiment's original size: a 123-voxel sphere, kept as the three-argument behaviour. */
    public static boolean containsVoxel(int x, int y, int z) {
        return containsVoxel(x, y, z, 3);
    }

    /**
     * Whether the voxel at this body-local offset is inside a planet of the given radius.
     *
     * <p>The bounding-box test comes first and the sphere test second, which is what keeps a hostile
     * coordinate out of the multiplication: {@code x * x} on {@link Integer#MIN_VALUE} wraps to zero and
     * would read as "inside the surface".
     *
     * @param radius the body's radius in voxels; a radius below one is a body with no interior, so it has
     *               no voxels at all rather than one
     */
    public static boolean containsVoxel(int x, int y, int z, int radius) {
        if (radius < 1) return false;
        long reach = (long) radius * radius;
        return x >= -radius && x <= radius && y >= -radius && y <= radius && z >= -radius && z <= radius
                && (long) x * x + (long) y * y + (long) z * z <= reach;
    }
}
