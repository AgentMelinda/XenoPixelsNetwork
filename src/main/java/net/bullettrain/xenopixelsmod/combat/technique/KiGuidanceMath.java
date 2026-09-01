package net.bullettrain.xenopixelsmod.combat.technique;

/**
 * Lock-on homing curve for Ki Guidance. Level 0 is stock DMZ
 * (30-block range, 90-tick window, 0.2 turn). Minecraft-free for tests.
 *
 * <p>Not Sokidan: this only tightens existing homing, it never camera-steers.
 */
public final class KiGuidanceMath {

    public static final double BASE_RANGE = 30.0;
    public static final double BASE_CONTROL_RANGE = 192.0;
    public static final double CONTROL_RANGE_PER_LEVEL = 48.0;
    public static final int BASE_GRACE_TICKS = 30;
    public static final int BASE_EXTENDED_TICKS = 160;
    public static final double BASE_TURN = 0.32;
    public static final double RANGE_PER_LEVEL = 8.0;
    public static final int WINDOW_PER_LEVEL = 40;
    public static final double TURN_PER_LEVEL = 0.06;
    public static final double TURN_CAP = 0.5;
    public static final double CAMERA_VEL_RATE = 0.38;
    public static final double LEAD_TICK_CAP = 4.0;

    /** 0 = use {@link #BASE_CONTROL_RANGE} + level. Set by server config / command. */
    public static volatile double controlRangeOverride = 0.0;
    /** 0 = skill curve. Set by server config / command. */
    public static volatile double turnRateOverride = 0.0;
    /** 0 = {@link #CAMERA_VEL_RATE}. Set by server config / command. */
    public static volatile double cameraRateOverride = 0.0;
    /** 0 = 8 blocks. Set by server config / command. */
    public static volatile double lookRayMinOverride = 0.0;

    private KiGuidanceMath() {
    }

    public static double range(int level) {
        return BASE_RANGE + RANGE_PER_LEVEL * clamp(level);
    }

    /** How far your own ki may be and still accept Guidance / lock-home. */
    public static double controlRange(int level) {
        if (controlRangeOverride > 0.0) return controlRangeOverride;
        return BASE_CONTROL_RANGE + CONTROL_RANGE_PER_LEVEL * clamp(level);
    }

    public static int graceTicks() {
        return BASE_GRACE_TICKS;
    }

    public static int extendedTicks(int level) {
        return BASE_EXTENDED_TICKS + WINDOW_PER_LEVEL * clamp(level);
    }

    public static double turnRate(int level) {
        if (turnRateOverride > 0.0) {
            return Math.min(TURN_CAP, turnRateOverride);
        }
        return Math.min(TURN_CAP, BASE_TURN + TURN_PER_LEVEL * clamp(level));
    }

    public static double cameraRate() {
        return cameraRateOverride > 0.0 ? Math.min(1.0, cameraRateOverride) : CAMERA_VEL_RATE;
    }

    public static double lookRayMin() {
        return lookRayMinOverride > 0.0 ? lookRayMinOverride : 8.0;
    }

    /**
     * Nudge {@code current} toward {@code want} by this level's turn rate.
     * Both should be unit-ish; the result is normalised.
     */
    public static double[] steerDir(double cx, double cy, double cz,
                                   double wx, double wy, double wz, int level) {
        return slerpDir(cx, cy, cz, wx, wy, wz, turnRate(level));
    }

    /**
     * Constant-angle blend of two directions. Linear lerp of unit vectors
     * eases unevenly; this matches how the disk feels when it turns.
     */
    public static double[] slerpDir(double cx, double cy, double cz,
                                   double wx, double wy, double wz, double t) {
        double[] a = normalize(cx, cy, cz);
        double[] b = normalize(wx, wy, wz);
        double r = t < 0.0 ? 0.0 : Math.min(1.0, t);
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        if (dot > 0.9995) {
            return normalize(
                    a[0] + (b[0] - a[0]) * r,
                    a[1] + (b[1] - a[1]) * r,
                    a[2] + (b[2] - a[2]) * r);
        }
        double theta = Math.acos(Math.min(1.0, dot));
        double sin = Math.sin(theta);
        if (sin < 1.0e-8) return b;
        double w0 = Math.sin((1.0 - r) * theta) / sin;
        double w1 = Math.sin(r * theta) / sin;
        return new double[]{
                a[0] * w0 + b[0] * w1,
                a[1] * w0 + b[1] * w1,
                a[2] * w0 + b[2] * w1
        };
    }

    /** Linear blend of two vectors. Keeps magnitude (Sokidan parked chase). */
    public static double[] lerpVec(double cx, double cy, double cz,
                                  double wx, double wy, double wz, double rate) {
        double r = rate < 0.0 ? 0.0 : Math.min(1.0, rate);
        return new double[]{
                cx + (wx - cx) * r,
                cy + (wy - cy) * r,
                cz + (wz - cz) * r
        };
    }

    /**
     * Point along the look ray just ahead of the shot, so it slides onto
     * the reticle instead of flying parallel to the camera.
     */
    public static double[] lookRayPoint(double eyeX, double eyeY, double eyeZ,
                                       double aimX, double aimY, double aimZ,
                                       double kiX, double kiY, double kiZ,
                                       double speed) {
        double[] aim = normalize(aimX, aimY, aimZ);
        double along = (kiX - eyeX) * aim[0] + (kiY - eyeY) * aim[1] + (kiZ - eyeZ) * aim[2];
        along += Math.max(speed, 0.5);
        // High-Y look-down still has to travel down the reticle, not park 4
        // blocks in front of the camera — but do not slam to full control range.
        along = Math.max(along, lookRayMin());
        return new double[]{
                eyeX + aim[0] * along,
                eyeY + aim[1] * along,
                eyeZ + aim[2] * along
        };
    }

    /**
     * Aim point with a short velocity lead so tracking shots hit a moving body.
     */
    public static double[] leadPoint(double tx, double ty, double tz,
                                    double vx, double vy, double vz,
                                    double fromX, double fromY, double fromZ,
                                    double speed) {
        double dx = tx - fromX;
        double dy = ty - fromY;
        double dz = tz - fromZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double eta = speed > 1.0e-4 ? dist / speed : 0.0;
        if (eta > LEAD_TICK_CAP) eta = LEAD_TICK_CAP;
        return new double[]{tx + vx * eta, ty + vy * eta, tz + vz * eta};
    }

    private static double[] normalize(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (!(len > 1.0e-8)) return new double[]{x, y, z};
        return new double[]{x / len, y / len, z / len};
    }

    private static int clamp(int level) {
        return Math.max(0, Math.min(3, level));
    }
}
