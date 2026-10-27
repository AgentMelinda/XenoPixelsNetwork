package net.bullettrain.xenopixelsmod.space.smallplanet;

/** Pure vertical safety checks for a generated body's complete circular orbit and current Sable bounds. */
public final class SmallPlanetOrbitSafety {
    private static final double MIN_HOLD_STIFFNESS = 0.02;
    private static final double MIN_HOLD_DAMPING = 0.28;
    private static final double MIN_HOLD_ACCELERATION = 0.25;

    private SmallPlanetOrbitSafety() {}

    public static double holdStiffness(double configured) { return atLeast(configured, MIN_HOLD_STIFFNESS); }
    public static double holdDamping(double configured) { return atLeast(configured, MIN_HOLD_DAMPING); }
    public static double holdAcceleration(double configured) { return atLeast(configured, MIN_HOLD_ACCELERATION); }

    private static double atLeast(double configured, double minimum) {
        return Double.isFinite(configured) ? Math.max(minimum, configured) : minimum;
    }

    /** Conservative world-axis AABB half extent for a voxel body that may rotate after construction. */
    public static double rotatedVoxelHalfExtent(double radius) {
        if (!Double.isFinite(radius) || radius < 0.0) return Double.NaN;
        return Math.sqrt(3.0) * radius + 1.0;
    }

    /**
     * Whether the orbit's body AABB stays inside the live Sable removal interval. When unbounded-height mode is
     * disabled, also retain the player safety floor at {@code minBuildHeight - 64}. Callers pass Sable's effective
     * limits (including the Voidsea mixin's expanded limits when enabled).
     */
    public static boolean orbitFitsRemovalBand(SmallPlanetOrbit orbit, double bodyHalfHeight,
                                                double sableRemoveMin, double sableRemoveMax,
                                                int minBuildHeight, boolean unboundedHeight) {
        if (orbit == null || !Double.isFinite(bodyHalfHeight) || bodyHalfHeight < 0
                || invalidBand(sableRemoveMin, sableRemoveMax)) return false;
        double minimum = sableRemoveMin;
        if (!unboundedHeight) minimum = Math.max(minimum, minBuildHeight - 64.0);
        return orbit.staysWithinY(minimum + bodyHalfHeight, sableRemoveMax - bodyHalfHeight);
    }

    /** Sable removes on a strict outside comparison, so points exactly on either live AABB boundary are safe. */
    public static boolean currentBoundsFitRemovalBand(double bodyMinY, double bodyMaxY,
                                                       double sableRemoveMin, double sableRemoveMax,
                                                       int minBuildHeight, boolean unboundedHeight) {
        if (!Double.isFinite(bodyMinY) || !Double.isFinite(bodyMaxY) || bodyMinY > bodyMaxY
                || invalidBand(sableRemoveMin, sableRemoveMax)) return false;
        double minimum = sableRemoveMin;
        if (!unboundedHeight) minimum = Math.max(minimum, minBuildHeight - 64.0);
        return bodyMinY >= minimum && bodyMaxY <= sableRemoveMax;
    }

    /**
     * Bounded, slightly upstream safety target for actively braking a body whose analytic orbit is unsafe.
     * If the current centre is already outside, this returns the nearest safe centre; otherwise it places
     * the target opposite the current vertical velocity by its ideal stopping distance plus a small margin.
     */
    public static double safetyTargetY(double positionY, double velocityY, double safeMinY, double safeMaxY,
                                       double maximumBrakeAcceleration) {
        if (!Double.isFinite(positionY) || !Double.isFinite(velocityY)
                || invalidBand(safeMinY, safeMaxY)
                || safeMinY == Double.POSITIVE_INFINITY || safeMaxY == Double.NEGATIVE_INFINITY
                || !Double.isFinite(maximumBrakeAcceleration)
                || maximumBrakeAcceleration <= 0.0)
            throw new IllegalArgumentException("invalid small-planet safety target inputs");
        double target = Math.max(safeMinY, Math.min(safeMaxY, positionY));
        if (positionY >= safeMinY && positionY <= safeMaxY && velocityY != 0.0) {
            double stoppingDistance = velocityY * velocityY / (2.0 * maximumBrakeAcceleration) + 2.0;
            double requested = velocityY > 0.0 ? target - stoppingDistance : target + stoppingDistance;
            if (Double.isNaN(requested)) requested = target;
            else if (requested == Double.POSITIVE_INFINITY) requested = Double.MAX_VALUE;
            else if (requested == Double.NEGATIVE_INFINITY) requested = -Double.MAX_VALUE;
            target = Math.max(safeMinY, Math.min(safeMaxY, requested));
        }
        return target;
    }

    /** Sable allows unbounded IEEE endpoints; NaN and reversed ranges still cannot describe a usable band. */
    private static boolean invalidBand(double minimum, double maximum) {
        return Double.isNaN(minimum) || Double.isNaN(maximum) || minimum > maximum;
    }

}
