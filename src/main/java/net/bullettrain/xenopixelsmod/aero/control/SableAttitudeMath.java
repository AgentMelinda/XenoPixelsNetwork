package net.bullettrain.xenopixelsmod.aero.control;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Frame-safe attitude helpers. Sable logical-pose orientation maps body axes into world axes. */
public final class SableAttitudeMath {
    private static final Vector3d WORLD_UP = new Vector3d(0, 1, 0);

    private SableAttitudeMath() {}

    /** yaw 0=north (-Z), +90=east (+X); pitch is nose-up; roll is about the nose. */
    public static Quaterniond desiredOrientation(double yawDeg, double pitchDeg, double rollDeg,
                                                  Vector3dc bodyNose, Vector3dc bodyUp) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch);
        Vector3d forward = new Vector3d(Math.sin(yaw) * cp, Math.sin(pitch), -Math.cos(yaw) * cp)
                .normalize();

        Vector3d levelUp = new Vector3d(WORLD_UP).sub(new Vector3d(forward).mul(WORLD_UP.dot(forward)));
        if (levelUp.lengthSquared() < 1.0e-8) {
            // Straight up or straight down: world up projects to nothing, so take the limit the
            // non-degenerate branch approaches instead of an arbitrary axis. Normalising the
            // general case gives (-sin(pitch)*sin(yaw), cos(pitch), sin(pitch)*cos(yaw)); at
            // vertical that is (-s*sin(yaw), 0, s*cos(yaw)) for s = +/-1. The previous fallback,
            // (cos(yaw), 0, sin(yaw)), is perpendicular to it, so roll jumped 90 degrees the
            // moment the nose passed through vertical.
            double s = Math.signum(Math.sin(pitch));
            if (s == 0.0) s = 1.0;
            levelUp.set(-s * Math.sin(yaw), 0, s * Math.cos(yaw));
        }
        levelUp.normalize().rotateAxis(Math.toRadians(rollDeg), forward.x, forward.y, forward.z);

        Vector3d sourceForward = safeNormal(bodyNose, new Vector3d(0, 1, 0));
        Vector3d sourceUp = orthogonalUp(sourceForward, bodyUp);

        Quaterniond noseRotation = new Quaterniond().rotationTo(sourceForward, forward);
        Vector3d rotatedUp = noseRotation.transform(new Vector3d(sourceUp));
        rotatedUp.sub(new Vector3d(forward).mul(rotatedUp.dot(forward))).normalize();

        double sin = forward.dot(new Vector3d(rotatedUp).cross(levelUp));
        double cos = clamp(rotatedUp.dot(levelUp), -1.0, 1.0);
        Quaterniond rollRotation = new Quaterniond().fromAxisAngleRad(
                forward.x, forward.y, forward.z, Math.atan2(sin, cos));
        return rollRotation.mul(noseRotation).normalize();
    }

    /** Shortest world-space axis-angle error taking current body->world pose to desired pose. */
    public static Vector3d errorVectorWorld(Quaterniondc current, Quaterniondc desired,
                                            Vector3d destination) {
        Quaterniond error = new Quaterniond(desired).mul(new Quaterniond(current).conjugate()).normalize();
        if (error.w < 0.0) error.mul(-1.0);
        double vectorLength = Math.sqrt(error.x * error.x + error.y * error.y + error.z * error.z);
        if (vectorLength < 1.0e-9) return destination.zero();
        double angle = 2.0 * Math.atan2(vectorLength, clamp(error.w, -1.0, 1.0));
        return destination.set(error.x, error.y, error.z).mul(angle / vectorLength);
    }

    public static Vector3d calibratedNose(double bx, double by, double bz,
                                           double nx, double ny, double nz) {
        return safeNormal(new Vector3d(nx - bx, ny - by, nz - bz), new Vector3d(0, 1, 0));
    }

    public static Vector3d calibratedUp(Vector3dc nose, double bx, double by, double bz,
                                         double cx, double cy, double cz) {
        return orthogonalUp(nose, new Vector3d(cx - bx, cy - by, cz - bz));
    }

    private static Vector3d orthogonalUp(Vector3dc forward, Vector3dc candidate) {
        Vector3d up = safeNormal(candidate, new Vector3d(0, 1, 0));
        up.sub(new Vector3d(forward).mul(up.dot(forward)));
        if (up.lengthSquared() < 1.0e-8) {
            Vector3d fallback = Math.abs(forward.y()) < 0.9 ? new Vector3d(0, 1, 0) : new Vector3d(1, 0, 0);
            up.set(fallback).sub(new Vector3d(forward).mul(fallback.dot(forward)));
        }
        return up.normalize();
    }

    private static Vector3d safeNormal(Vector3dc value, Vector3d fallback) {
        Vector3d result = new Vector3d(value);
        return result.lengthSquared() < 1.0e-10 ? fallback.normalize() : result.normalize();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
