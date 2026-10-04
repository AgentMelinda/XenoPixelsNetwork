package net.bullettrain.xenopixelsmod.client.compat.npc;

/**
 * FULL-DMZ fly pose math. DMZ remote fly clips compare this-tick XZ delta to body yaw and
 * pick {@code FLY_LEFT}/{@code FLY_RIGHT} when the sideways component wins. Combat Brain
 * yaws at the target and flies at the target the way {@code DBSagasEntity} does, so this
 * travel lock should stay on {@code FLY_FRONT} (or idle hover).
 */
public final class NpcFlyPose {
    /** Same horizontal epsilon DMZ uses in {@code dragonminez$resolveFlyAnimation}. */
    public static final double MOVE_EPSILON = 0.01;

    public record Snapshot(float yBodyRot, float yRot, float xRot, float yRotO,
                           boolean travelLocked) {}

    private NpcFlyPose() {}

    public static Snapshot forProxy(boolean flySkillOn, double dx, double dy, double dz,
                                    float bodyYaw, float lookPitch, float yRotO) {
        if (!flySkillOn) {
            return new Snapshot(bodyYaw, bodyYaw, lookPitch, yRotO, false);
        }
        double horiz = Math.hypot(dx, dz);
        if (horiz < MOVE_EPSILON) {
            return new Snapshot(bodyYaw, bodyYaw, lookPitch, yRotO, false);
        }
        float travelYaw = yaw(dx, dz);
        // A mostly vertical correction at low horizontal speed tipped the whole body nose-down or
        // nose-up; cap the lean so a hover adjustment reads as a hover.
        float travelPitch = Math.max(-45.0f, Math.min(45.0f, pitch(dx, dy, dz)));
        return new Snapshot(travelYaw, travelYaw, travelPitch, travelYaw, true);
    }

    /** Prefer live velocity when interpolated position delta is below DMZ's fly epsilon. */
    public static Snapshot forProxy(boolean flySkillOn, double dx, double dy, double dz,
                                    double vx, double vy, double vz,
                                    float bodyYaw, float lookPitch, float yRotO) {
        double horiz = Math.hypot(dx, dz);
        if (horiz < MOVE_EPSILON) {
            dx = vx;
            dy = vy;
            dz = vz;
        }
        return forProxy(flySkillOn, dx, dy, dz, bodyYaw, lookPitch, yRotO);
    }

    /**
     * Turns {@code current} toward {@code target} by at most {@code maxStep} degrees along the
     * short way round. The result stays in [-180, 180).
     */
    public static float slew(float current, float target, float maxStep) {
        return net.bullettrain.xenopixelsmod.compat.npc.NpcBrainKiRotation.slew(current, target, maxStep);
    }

    public static float yaw(double dx, double dz) {
        return net.bullettrain.xenopixelsmod.compat.npc.NpcBrainKiRotation.travelYaw(dx, dz);
    }

    /**
     * DMZ chooses a remote player's fly clip from {@code position - xOld/zOld} relative to body
     * yaw. While the proxy body eases through a turn, the real tick delta is briefly sideways to
     * that yaw. Give only the render proxy a forward clip delta; its real world position and
     * movement stay unchanged. A stationary proxy still gets the idle clip.
     */
    public static double[] frontClipDelta(float renderedYaw, double tickDx, double tickDz,
                                          double velocityX, double velocityZ) {
        double distance = Math.hypot(tickDx, tickDz);
        if (distance < MOVE_EPSILON) distance = Math.hypot(velocityX, velocityZ);
        if (distance < MOVE_EPSILON) return new double[]{0.0, 0.0};
        double yawRadians = Math.toRadians(renderedYaw);
        return new double[]{-Math.sin(yawRadians) * distance,
                Math.cos(yawRadians) * distance};
    }

    public static float pitch(double dx, double dy, double dz) {
        return net.bullettrain.xenopixelsmod.compat.npc.NpcBrainKiRotation.travelPitch(dx, dy, dz);
    }
}
