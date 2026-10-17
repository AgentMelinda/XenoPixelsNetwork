package net.bullettrain.xenopixelsmod.space.dragonball;

/** Pure geometry for selecting and assigning an SDB's horizontal circular orbit with the orbital wand. */
public final class DragonBallOrbitWandMath {
    private static final double TWO_PI = Math.PI * 2.0;

    public record Placement(double radiusBlocks, double phaseRadians, double periodSeconds) {
        public Placement {
            if (!(radiusBlocks > 0.0) || !Double.isFinite(radiusBlocks) || radiusBlocks > 1_000_000.0
                    || !Double.isFinite(phaseRadians) || !(periodSeconds > 0.0) || !Double.isFinite(periodSeconds))
                throw new IllegalArgumentException("invalid SDB orbit placement");
        }
    }

    private DragonBallOrbitWandMath() {}

    /**
     * Makes the circular track pass through the selected world X/Z offset at {@code timeSeconds}. A supplied
     * positive period is retained; otherwise the period is derived from the host's positive gravitational parameter.
     */
    public static Placement fromOffset(double offsetX, double offsetZ, double timeSeconds,
                                       double gravitationalParameter, double assignedPeriodSeconds) {
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetZ) || !Double.isFinite(timeSeconds))
            throw new IllegalArgumentException("orbit offset and time must be finite");
        double radius = Math.hypot(offsetX, offsetZ);
        if (!(radius > 0.0) || radius > 1_000_000.0)
            throw new IllegalArgumentException("orbit radius must be in (0, 1000000]");
        double period;
        if (assignedPeriodSeconds > 0.0 && Double.isFinite(assignedPeriodSeconds)) {
            period = assignedPeriodSeconds;
        } else {
            if (!(gravitationalParameter > 0.0) || !Double.isFinite(gravitationalParameter))
                throw new IllegalArgumentException("host needs positive gravity or an authored period");
            period = TWO_PI * Math.sqrt(radius * radius * radius / gravitationalParameter);
        }
        if (!(period > 0.0) || !Double.isFinite(period))
            throw new IllegalArgumentException("derived orbit period is not finite and positive");
        double bearing = Math.atan2(offsetZ, offsetX);
        double phase = Math.atan2(Math.sin(bearing - TWO_PI * timeSeconds / period),
                Math.cos(bearing - TWO_PI * timeSeconds / period));
        return new Placement(radius, phase, period);
    }

    /** Returns the first forward ray/sphere intersection distance, or -1 when the normalized ray misses. */
    public static double raySphereDistance(double ox, double oy, double oz,
                                           double dx, double dy, double dz,
                                           double cx, double cy, double cz,
                                           double radius, double maxDistance) {
        if (!allFinite(ox, oy, oz, dx, dy, dz, cx, cy, cz, radius, maxDistance)
                || !(radius > 0.0) || !(maxDistance >= 0.0)) return -1.0;
        double a = dx * dx + dy * dy + dz * dz;
        if (!(a > 0.0)) return -1.0;
        double mx = ox - cx, my = oy - cy, mz = oz - cz;
        double b = mx * dx + my * dy + mz * dz;
        double c = mx * mx + my * my + mz * mz - radius * radius;
        double discriminant = b * b - a * c;
        if (discriminant < 0.0) return -1.0;
        double root = Math.sqrt(discriminant);
        double distance = (-b - root) / a;
        if (distance < 0.0) distance = (-b + root) / a;
        return distance >= 0.0 && distance <= maxDistance ? distance : -1.0;
    }

    private static boolean allFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
