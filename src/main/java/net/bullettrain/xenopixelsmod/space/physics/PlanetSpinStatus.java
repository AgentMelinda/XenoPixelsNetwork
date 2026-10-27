package net.bullettrain.xenopixelsmod.space.physics;

import net.bullettrain.xenopixelsmod.space.math.PlanetRotationMath;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Locale;

/** Read-only status formatting for a live Sable body's measured and authored angular velocity. */
public final class PlanetSpinStatus {
    private PlanetSpinStatus() {}

    /**
     * Formats a measured world-space angular velocity and, when present, its authored-period target.
     * The error vector is measured minus target; this reports state without mutating the body.
     */
    public static String format(Vector3dc actual, Double signedPeriodSeconds,
                                Quaterniondc orientation, boolean authoredSpinEnabled) {
        if (actual == null || !Double.isFinite(actual.x()) || !Double.isFinite(actual.y()) || !Double.isFinite(actual.z()))
            throw new IllegalArgumentException("actual angular velocity must be finite");
        StringBuilder result = new StringBuilder(String.format(Locale.ROOT,
                "angularVelocity=(%.6f %.6f %.6f)rad/s", cleanZero(actual.x()), cleanZero(actual.y()), cleanZero(actual.z())));
        if (signedPeriodSeconds == null) return result.toString();

        Vector3d target = PlanetRotationMath.angularVelocity(signedPeriodSeconds, orientation, new Vector3d());
        Vector3d error = new Vector3d(actual).sub(target);
        result.append(String.format(Locale.ROOT,
                " authoredRotationPeriod=%.6f s targetAngularVelocity=(%.6f %.6f %.6f)rad/s"
                        + " angularVelocityError=(%.6f %.6f %.6f)rad/s errorMagnitude=%.6f rad/s spinMode=%s",
                signedPeriodSeconds, cleanZero(target.x), cleanZero(target.y), cleanZero(target.z),
                cleanZero(error.x), cleanZero(error.y), cleanZero(error.z),
                error.length(), authoredSpinEnabled ? "authored" : "damped"));
        return result.toString();
    }

    private static double cleanZero(double value) {
        // Quaternion transforms can leave tiny signed residue that formats as "-0.000000".
        // Values below half a displayed micro-radian/second round to zero in this status line anyway.
        return java.lang.Math.abs(value) < 0.0000005 ? 0.0 : value;
    }
}
