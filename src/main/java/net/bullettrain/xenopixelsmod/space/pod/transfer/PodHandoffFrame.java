package net.bullettrain.xenopixelsmod.space.pod.transfer;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Frame maths for putting a pod body where a handoff wants its rider, and for converting the pod's own motion
 * when the outer frame changes.
 *
 * <p>Two velocity domains meet here and must not be mixed. A Sable body's linear velocity is world-space
 * blocks per second, while vanilla entity motion is blocks per tick, so the per-tick division belongs to the
 * player handoff alone and never to a body. Chart orbital velocities are blocks per second.
 *
 * <p>Pure maths: no level, no sub-level runtime, no server-thread assumption beyond the caller's own.
 */
public final class PodHandoffFrame {
    /** Game ticks per second, used only where a per-second rate becomes vanilla per-tick motion. */
    public static final double TICKS_PER_SECOND = 20.0;

    private PodHandoffFrame() {}

    /**
     * The world point a rider occupies for a body pose: its plot-local offset pushed through the pose's own
     * mapping, so rotation-point and scale handling stay inside Sable's implementation.
     *
     * @return {@code result}, or null when any input is missing or non-finite
     */
    public static Vector3d landingPointOf(Pose3dc destinationPose, Vector3dc riderPlotLocal, Vector3d result) {
        if (destinationPose == null || riderPlotLocal == null || result == null) return null;
        return destinationPose.transformPosition(riderPlotLocal, result);
    }

    /**
     * The plot-local offset a rider must carry to land on a chosen world point. This is the inverse of
     * {@link #landingPointOf} and is the only form that keeps the offset frame-local.
     *
     * @return {@code result}, or null when any input is missing or non-finite
     */
    public static Vector3d riderOffsetFor(Pose3dc destinationPose, Vector3dc destinationWorld, Vector3d result) {
        if (destinationPose == null || destinationWorld == null || result == null) return null;
        return destinationPose.transformPositionInverse(destinationWorld, result);
    }

    /**
     * Where the body has to sit so the rider lands on {@code destinationWorld}.
     *
     * <p>Solved as a shift of the pose's own position by the rider's landing error, which holds for any pose
     * whose position enters its transform additively. It is deliberately not {@code D - R*o}: that form assumes
     * the transform is a plain rotation plus translation and would silently mis-place a pose with a non-zero
     * rotation point or a scale.
     *
     * @return {@code result}, or null when any input is missing or non-finite
     */
    public static Vector3d bodyPositionFor(Pose3dc destinationPose, Vector3dc riderPlotLocal,
                                           Vector3dc destinationWorld, Vector3d result) {
        if (destinationPose == null || riderPlotLocal == null || destinationWorld == null || result == null) {
            return null;
        }
        Vector3d landing = destinationPose.transformPosition(riderPlotLocal, new Vector3d());
        if (!finite(landing) || !finite(destinationWorld) || !finite(destinationPose.position())) return null;
        return result.set(destinationPose.position().x() + (destinationWorld.x() - landing.x),
                destinationPose.position().y() + (destinationWorld.y() - landing.y),
                destinationPose.position().z() + (destinationWorld.z() - landing.z));
    }

    /**
     * The pod's body velocity after the outer frame changes, in blocks per second on both sides.
     *
     * <p>Adding the departure frame's orbital velocity and subtracting the arrival frame's is inertial
     * preservation: the pod keeps the motion it was carrying, expressed against the body it arrives at. This
     * matches the only shipped conversion in this subsystem, {@code SpaceHandoffVelocity.convert}, in direction
     * as well as shape. No tick conversion happens here: feeding a per-second body velocity through the player
     * handoff's divisor would quietly land it at a twentieth of the intended correction.
     *
     * @return {@code result}, or null when any input is missing or non-finite
     */
    public static Vector3d bodyVelocityAfterFrameChange(Vector3dc sourceBodyPerSecond,
                                                        Vector3dc sourceOrbitPerSecond,
                                                        Vector3dc targetOrbitPerSecond,
                                                        Vector3d result) {
        if (!finite(sourceBodyPerSecond) || !finite(sourceOrbitPerSecond) || !finite(targetOrbitPerSecond)
                || result == null) {
            return null;
        }
        double x = sourceBodyPerSecond.x() + sourceOrbitPerSecond.x() - targetOrbitPerSecond.x();
        double y = sourceBodyPerSecond.y() + sourceOrbitPerSecond.y() - targetOrbitPerSecond.y();
        double z = sourceBodyPerSecond.z() + sourceOrbitPerSecond.z() - targetOrbitPerSecond.z();
        return result.set(x, y, z);
    }

    /**
     * The one place a rate crosses into vanilla per-tick motion: a world-space per-second velocity expressed as
     * the blocks-per-tick delta Minecraft stores on an entity.
     *
     * @return {@code result}, or null when any input is missing or non-finite
     */
    public static Vector3d motionPerSecondToVanillaDelta(Vector3dc perSecond, Vector3d result) {
        if (!finite(perSecond) || result == null) return null;
        double scale = 1.0 / TICKS_PER_SECOND;
        return result.set(perSecond.x() * scale, perSecond.y() * scale, perSecond.z() * scale);
    }

    private static boolean finite(Vector3dc value) {
        return value != null && Double.isFinite(value.x()) && Double.isFinite(value.y()) && Double.isFinite(value.z());
    }
}
