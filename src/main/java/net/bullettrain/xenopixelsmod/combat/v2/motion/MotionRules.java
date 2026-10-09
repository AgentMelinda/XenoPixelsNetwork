package net.bullettrain.xenopixelsmod.combat.v2.motion;

/**
 * The arithmetic behind every v2 travel move. Minecraft-free.
 *
 * <p>Chase, dragon homing, Z-Burst, Dragon Dash and the rush approach all cross a gap
 * to a point. They differ only in how fast, how close they stop and how long they may take, so
 * they share this instead of each owning a movement engine.
 */
public final class MotionRules {

    /** Ceiling on any travel, whatever the distance: a stuck flight must end on its own. */
    public static final int ABSOLUTE_MAX_TICKS = 600;

    private MotionRules() {}

    /**
     * This tick's velocity toward a goal as {@code [x, y, z]}: full speed until the last stretch,
     * then exactly the remaining distance so the fighter lands on the point instead of orbiting it.
     */
    public static double[] stepToward(double dx, double dy, double dz, double speed) {
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1.0e-6 || !(speed > 0.0)) return new double[]{0.0, 0.0, 0.0};
        double step = Math.min(speed, dist);
        double k = step / dist;
        return new double[]{dx * k, dy * k, dz * k};
    }

    /** True when the fighter is close enough to the goal to stop. */
    public static boolean arrived(double distance, double arriveDistance) {
        return distance <= Math.max(0.05, arriveDistance);
    }

    /**
     * How long a travel may run: long enough to cover the distance with slack, never shorter than
     * the profile's floor and never longer than {@link #ABSOLUTE_MAX_TICKS}.
     */
    public static int timeoutTicks(double distance, double speed, int minTicks) {
        int travel = speed > 0.0 ? (int) Math.ceil(distance / speed) + 20 : minTicks;
        return Math.min(ABSOLUTE_MAX_TICKS, Math.max(Math.max(1, minTicks), travel));
    }

    /**
     * A directional blink's displacement in world space as {@code [x, z]}.
     *
     * @param yawDeg   the fighter's yaw in Minecraft degrees
     * @param forward  +1 forward, -1 back, 0 none
     * @param strafe   +1 right, -1 left, 0 none
     * @param distance how far the blink travels
     */
    public static double[] stepOffset(float yawDeg, int forward, int strafe, double distance) {
        if (forward == 0 && strafe == 0) return new double[]{0.0, 0.0};
        double yaw = Math.toRadians(yawDeg);
        // Minecraft: yaw 0 looks along +Z, yaw 90 along -X.
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx = -fz;
        double rz = fx;
        double x = fx * forward + rx * strafe;
        double z = fz * forward + rz * strafe;
        double len = Math.hypot(x, z);
        return new double[]{x / len * distance, z / len * distance};
    }
}
