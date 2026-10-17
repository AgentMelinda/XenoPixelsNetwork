package net.bullettrain.xenopixelsmod.space.asteroid;

import java.util.SplittableRandom;

/** Pure thresholds and geometry shared by the server fracture scheduler and its unit tests. */
public final class AsteroidFractureMath {
    private AsteroidFractureMath() {}

    /**
     * Returns whether one sampled velocity delta strictly exceeds the configured fracture threshold.
     * Non-finite samples and a negative/non-finite threshold are rejected rather than treated as an impact.
     */
    public static boolean impactExceedsThreshold(double previousX, double previousY, double previousZ,
                                                 double currentX, double currentY, double currentZ,
                                                 double threshold) {
        if (!Double.isFinite(previousX) || !Double.isFinite(previousY) || !Double.isFinite(previousZ)
                || !Double.isFinite(currentX) || !Double.isFinite(currentY) || !Double.isFinite(currentZ)
                || !Double.isFinite(threshold) || threshold < 0.0) {
            return false;
        }
        double dx = currentX - previousX;
        double dy = currentY - previousY;
        double dz = currentZ - previousZ;
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) return false;
        double deltaV = Math.hypot(Math.hypot(dx, dy), dz);
        return Double.isFinite(deltaV) && deltaV > threshold;
    }

    /**
     * Conservative half-width that prevents any 18-connected lattice step from jumping from one side of a
     * plane directly to the other without intersecting the removed slab. Sable 2.0.3 uses the 18 face+edge
     * offsets; half the L1 norm bounds every such projected step. The returned width is in voxel units.
     */
    public static double minimumHalfWidthFor18NeighborSeparation(double normalX, double normalY, double normalZ) {
        if (!Double.isFinite(normalX) || !Double.isFinite(normalY) || !Double.isFinite(normalZ)) return Double.NaN;
        double length = Math.hypot(Math.hypot(normalX, normalY), normalZ);
        if (!(length > 1e-12) || !Double.isFinite(length)) return Double.NaN;
        return (Math.abs(normalX) + Math.abs(normalY) + Math.abs(normalZ)) / (2.0 * length);
    }

    /**
     * Builds a deterministic fracture plane through the body's centre and the surface point opposite the
     * measured velocity-change vector. The seeded normal rotates around that impact axis, producing a
     * repeatable cross-section without choosing a tangent cap that might leave the body connected.
     *
     * @return the plane, or {@code null} for invalid geometry or a zero impact vector
     */
    public static Plane seededPlane(long seed, double centerX, double centerY, double centerZ, double radius,
                                    double deltaVx, double deltaVy, double deltaVz) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY) || !Double.isFinite(centerZ)
                || !Double.isFinite(radius) || radius <= 0.0
                || !Double.isFinite(deltaVx) || !Double.isFinite(deltaVy) || !Double.isFinite(deltaVz)) return null;
        double speed = Math.hypot(Math.hypot(deltaVx, deltaVy), deltaVz);
        if (!Double.isFinite(speed) || speed <= 1e-12) return null;

        // The impulse direction points away from the contact face; place the impact point on its opposite side.
        double hitX = -deltaVx / speed, hitY = -deltaVy / speed, hitZ = -deltaVz / speed;
        double axisX, axisY, axisZ;
        if (Math.abs(hitX) <= Math.abs(hitY) && Math.abs(hitX) <= Math.abs(hitZ)) {
            axisX = 1.0; axisY = 0.0; axisZ = 0.0;
        } else if (Math.abs(hitY) <= Math.abs(hitZ)) {
            axisX = 0.0; axisY = 1.0; axisZ = 0.0;
        } else {
            axisX = 0.0; axisY = 0.0; axisZ = 1.0;
        }
        double basisUX = hitY * axisZ - hitZ * axisY;
        double basisUY = hitZ * axisX - hitX * axisZ;
        double basisUZ = hitX * axisY - hitY * axisX;
        double basisLength = Math.hypot(Math.hypot(basisUX, basisUY), basisUZ);
        if (!(basisLength > 0.0) || !Double.isFinite(basisLength)) return null;
        basisUX /= basisLength; basisUY /= basisLength; basisUZ /= basisLength;
        double basisVX = hitY * basisUZ - hitZ * basisUY;
        double basisVY = hitZ * basisUX - hitX * basisUZ;
        double basisVZ = hitX * basisUY - hitY * basisUX;

        double angle = new SplittableRandom(seed).nextDouble(Math.PI * 2.0);
        double cosine = Math.cos(angle), sine = Math.sin(angle);
        double normalX = basisUX * cosine + basisVX * sine;
        double normalY = basisUY * cosine + basisVY * sine;
        double normalZ = basisUZ * cosine + basisVZ * sine;
        double impactX = centerX + hitX * radius;
        double impactY = centerY + hitY * radius;
        double impactZ = centerZ + hitZ * radius;
        if (!Double.isFinite(impactX) || !Double.isFinite(impactY) || !Double.isFinite(impactZ)) return null;
        return new Plane(impactX, impactY, impactZ, normalX, normalY, normalZ);
    }

    /** Plane point is the impact point; its normal is unit length. */
    public record Plane(double impactX, double impactY, double impactZ,
                        double normalX, double normalY, double normalZ) {
        /** Tests whether the point falls in the fracture slab, where halfWidth is measured in blocks. */
        public boolean withinSlab(double x, double y, double z, double halfWidth) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Double.isFinite(halfWidth) || halfWidth < 0.0) return false;
            double signed = (x - impactX) * normalX + (y - impactY) * normalY + (z - impactZ) * normalZ;
            return Double.isFinite(signed) && Math.abs(signed) <= halfWidth;
        }
    }
}
