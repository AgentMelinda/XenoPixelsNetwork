package net.bullettrain.xenopixelsmod.combat;

/**
 * The view assist that keeps a rush or a chase pointed at its victim.
 *
 * <p>Minecraft-free so the arithmetic can be tested, because the arithmetic is where this went
 * wrong. Aiming at a target is an angular problem, and the angular rate needed to track one grows
 * without bound as the distance shrinks: at six blocks a half-block sidestep is about five degrees,
 * at half a block it is closer to forty-five. A rush ends *at contact*, against a target that has
 * just been *launched*, which is the worst case for that on both counts.
 *
 * <p>An earlier pass tried to fix this with a deadzone, gentle easing and a per-tick cap. Those
 * bound how fast a correction may be applied, but not the error demanding it — at contact range the
 * error was enormous every tick, so the assist simply sat on its cap and the camera never settled.
 * Damping a diverging signal does not converge it. The assist has to stop asking for the
 * impossible, which is what {@link #assistStrength} does.
 */
public final class RushCamera {

    /** Inside this the assist is silent: the fighter is already facing the target. */
    public static final double CONTACT_RANGE = 3.0;
    /** By this distance the assist has full authority. */
    public static final double FULL_ASSIST_RANGE = 7.0;
    /** Floor on the pitch denominator, so a near-zero horizontal cannot drive pitch to ±90°. */
    public static final double MIN_PITCH_BASIS = 2.0;

    public static final float YAW_EASE = 0.18f;
    /** Pitch eases slower than yaw: a launched target climbs fast and pitch swings worst. */
    public static final float PITCH_EASE = 0.10f;
    /** Aim error tolerated before steering at all, so ordinary mouse aim is left alone. */
    public static final float DEADZONE_DEG = 6.0f;
    /** Hard cap on one tick of correction, so no single frame can snap. */
    public static final float MAX_STEP_DEG = 8.0f;

    private RushCamera() {
    }

    /**
     * How much authority the assist has at this distance: 0 at contact, 1 at range.
     *
     * <p>Fading out rather than clamping harder is the point. Close in there is nothing left to
     * correct and the maths is least trustworthy, so the assist should simply stop.
     */
    public static float assistStrength(double distance) {
        if (distance <= CONTACT_RANGE) return 0.0f;
        if (distance >= FULL_ASSIST_RANGE) return 1.0f;
        double t = (distance - CONTACT_RANGE) / (FULL_ASSIST_RANGE - CONTACT_RANGE);
        // Smoothstep, so the assist arrives and leaves without a visible edge.
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    /** Yaw that points from the fighter toward an offset, in Minecraft's convention. */
    public static float wantYaw(double dx, double dz) {
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    /**
     * Pitch toward an offset, with the horizontal distance floored.
     *
     * <p>Without the floor this is {@code -atan2(dy, ~0)}, which runs to ±90° and swings tens of
     * degrees from a few centimetres of jostling — the divergence that made the camera thrash.
     */
    public static float wantPitch(double dy, double horizontal) {
        return (float) (-Math.toDegrees(Math.atan2(dy, Math.max(MIN_PITCH_BASIS, horizontal))));
    }

    /** One tick of correction: eased, scaled by authority, and capped. */
    public static float step(float error, float ease, float strength) {
        float raw = error * ease * strength;
        return raw < -MAX_STEP_DEG ? -MAX_STEP_DEG : (raw > MAX_STEP_DEG ? MAX_STEP_DEG : raw);
    }

    /** Below the deadzone the aim is close enough that steering would fight the player. */
    public static boolean shouldSteer(float error, float strength) {
        return strength > 0.0f && Math.abs(error) > DEADZONE_DEG;
    }
}
