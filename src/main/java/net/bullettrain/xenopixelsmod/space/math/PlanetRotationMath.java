package net.bullettrain.xenopixelsmod.space.math;

import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** Pure conversion from an authored signed sidereal period to the body's angular-velocity vector. */
public final class PlanetRotationMath {
    private static final double MIN_PERIOD_SECONDS = 0.05;
    private static final double MAX_PERIOD_SECONDS = 1.0e12;

    private PlanetRotationMath() {}

    /**
     * Produces world-space radians/second about the body's local +Y axis. Positive periods mean prograde
     * rotation in the chart's world frame; negative periods describe retrograde rotation. ChartRegistry maps
     * Kepler's XY plane to Minecraft XZ by swapping axes, a reflection that reverses angular-momentum Y, so
     * this conversion negates the usual right-hand period sign. The caller supplies a reusable output vector
     * so the server physics pass allocates nothing.
     */
    public static Vector3d angularVelocity(double signedPeriodSeconds, Quaterniondc bodyOrientation, Vector3d out) {
        if (!Double.isFinite(signedPeriodSeconds)
                || Math.abs(signedPeriodSeconds) < MIN_PERIOD_SECONDS
                || Math.abs(signedPeriodSeconds) > MAX_PERIOD_SECONDS) {
            throw new IllegalArgumentException("sidereal rotation period magnitude must be in [0.05, 1e12] seconds");
        }
        if (bodyOrientation == null || out == null) throw new IllegalArgumentException("orientation and output are required");
        bodyOrientation.transform(0.0, 1.0, 0.0, out);
        double axisLengthSquared = out.lengthSquared();
        if (!Double.isFinite(axisLengthSquared) || axisLengthSquared < 1.0e-12)
            throw new IllegalArgumentException("body orientation does not provide a finite rotation axis");
        return out.normalize().mul(-2.0 * Math.PI / signedPeriodSeconds);
    }
}
