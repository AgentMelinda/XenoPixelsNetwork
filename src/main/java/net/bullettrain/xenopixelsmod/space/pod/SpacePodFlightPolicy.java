package net.bullettrain.xenopixelsmod.space.pod;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Pure bounded velocity-servo policy for the P15 two-tier pod flight controls. */
public final class SpacePodFlightPolicy {
    private static final double MIN_VECTOR_LENGTH_SQUARED = 1.0e-12;

    private SpacePodFlightPolicy() {}

    /**
     * Computes world-space acceleration toward a configured speed along the pilot's look direction.
     * The response is proportional to velocity error and bounded by the configured acceleration.
     */
    public static Vector3d acceleration(Vector3dc velocity, Vector3dc heading,
                                        double targetSpeed, double maxAcceleration,
                                        double responsePerSecond, Vector3d out) {
        out.zero();
        if (velocity == null || heading == null || !velocity.isFinite() || !heading.isFinite()
                || !Double.isFinite(targetSpeed) || !Double.isFinite(maxAcceleration)
                || !Double.isFinite(responsePerSecond) || targetSpeed < 0.0
                || maxAcceleration <= 0.0 || responsePerSecond <= 0.0) return out;

        double headingLengthSquared = heading.lengthSquared();
        if (!Double.isFinite(headingLengthSquared) || headingLengthSquared < MIN_VECTOR_LENGTH_SQUARED)
            return out;
        double inverseHeadingLength = 1.0 / Math.sqrt(headingLengthSquared);
        double errorX = heading.x() * inverseHeadingLength * targetSpeed - velocity.x();
        double errorY = heading.y() * inverseHeadingLength * targetSpeed - velocity.y();
        double errorZ = heading.z() * inverseHeadingLength * targetSpeed - velocity.z();
        double errorLengthSquared = errorX * errorX + errorY * errorY + errorZ * errorZ;
        if (!Double.isFinite(errorLengthSquared) || errorLengthSquared < MIN_VECTOR_LENGTH_SQUARED) return out;

        double errorLength = Math.sqrt(errorLengthSquared);
        double magnitude = Math.min(maxAcceleration, errorLength * responsePerSecond);
        return out.set(errorX, errorY, errorZ).mul(magnitude / errorLength);
    }
}
