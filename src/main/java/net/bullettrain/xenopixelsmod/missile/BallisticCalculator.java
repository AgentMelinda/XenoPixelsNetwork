package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.world.phys.Vec3;

/** Analytic firing solution plus a drag-aware point-mass verification pass. */
public final class BallisticCalculator {
    public static final double EARTH_GRAVITY = 9.80665;
    public static final double DEFAULT_DRAG = 0.00002;

    private BallisticCalculator() {
    }

    public record Result(
            double azimuthDeg,
            double lowAngleDeg,
            double highAngleDeg,
            double selectedAngleDeg,
            double requiredSpeed,
            double availableSpeed,
            double flightTimeTicks,
            double apexY,
            double impactSpeed,
            double predictedMiss,
            boolean reachable,
            boolean dragVerified
    ) {
    }

    /**
     * Calculates a high-arc solution. Speeds are blocks/tick; gravity is m/s²
     * and is converted using one block = one metre and 20 ticks/second.
     */
    public static Result calculate(Vec3 launch, Vec3 target, double availableSpeed,
                                   double desiredApexY, double gravitySi, double dragPerBlock) {
        return calculate(launch, target, availableSpeed, desiredApexY, gravitySi, dragPerBlock, true);
    }

    /** Solves the speed required to hit the target at one player-selected pitch. */
    public static Result calculateForAngle(Vec3 launch, Vec3 target, double availableSpeed,
                                           double desiredAngleDeg, double gravitySi,
                                           double dragPerBlock) {
        double dx = target.x - launch.x;
        double dz = target.z - launch.z;
        double horizontal = Math.hypot(dx, dz);
        double dy = target.y - launch.y;
        double azimuth = normalizeDegrees(Math.toDegrees(Math.atan2(dx, dz)));
        double angleDeg = Math.max(1.0, Math.min(89.0, desiredAngleDeg));
        double angle = Math.toRadians(angleDeg);
        double gravity = toTickGravity(gravitySi);
        double drag = sanitizeDrag(dragPerBlock);
        double speed = Math.max(0.01, availableSpeed);

        if (horizontal < 1.0e-6) {
            return calculate(launch, target, speed, 0.0, gravitySi, dragPerBlock, true);
        }
        double cos = Math.cos(angle);
        double denominator = 2.0 * cos * cos * (horizontal * Math.tan(angle) - dy);
        if (!(denominator > 1.0e-9)) {
            Verification verification = verify(launch, target, horizontal, speed, angle, gravity, drag);
            return new Result(azimuth, angleDeg, angleDeg, angleDeg,
                    Double.POSITIVE_INFINITY, speed, verification.timeTicks,
                    verification.apexY, verification.impactSpeed, verification.miss,
                    false, verification.completed);
        }

        double vacuumRequired = Math.sqrt(gravity * horizontal * horizontal / denominator);
        double low = Math.max(0.01, vacuumRequired * 0.75);
        double high = Math.max(speed, vacuumRequired * 1.35 + 0.01);
        Verification highVerification = verify(launch, target, horizontal, high, angle, gravity, drag);
        for (int i = 0; i < 24 && highVerification.landedHorizontal < horizontal; i++) {
            high *= 1.35;
            highVerification = verify(launch, target, horizontal, high, angle, gravity, drag);
        }
        for (int i = 0; i < 42; i++) {
            double mid = (low + high) * 0.5;
            Verification midVerification = verify(launch, target, horizontal, mid, angle, gravity, drag);
            if (midVerification.landedHorizontal >= horizontal) high = mid;
            else low = mid;
        }
        double required = high;
        // Available speed is engine capability. A fixed-angle shot must throttle to
        // the solved required speed instead of firing at maximum and overshooting.
        double commandedSpeed = speed >= required ? required : speed;
        Verification verification = verify(launch, target, horizontal, commandedSpeed, angle, gravity, drag);
        boolean reachable = speed >= required * 0.98
                && verification.miss <= Math.max(16.0, horizontal * 0.001);
        return new Result(azimuth, angleDeg, angleDeg, angleDeg, required, speed,
                verification.timeTicks, verification.apexY, verification.impactSpeed,
                verification.miss, reachable, verification.completed);
    }

    public static Result calculate(Vec3 launch, Vec3 target, double availableSpeed,
                                   double desiredApexY, double gravitySi, double dragPerBlock,
                                   boolean highArc) {
        double dx = target.x - launch.x;
        double dz = target.z - launch.z;
        double horizontal = Math.hypot(dx, dz);
        double dy = target.y - launch.y;
        double azimuth = normalizeDegrees(Math.toDegrees(Math.atan2(dx, dz)));
        double gravity = toTickGravity(gravitySi);
        double drag = sanitizeDrag(dragPerBlock);
        double speed = Math.max(0.01, availableSpeed);

        double low;
        double high;
        double selected;
        double required = speed;
        boolean reachable;

        if (horizontal < 1.0e-6) {
            low = high = selected = dy >= 0 ? 90.0 : -90.0;
            required = Math.sqrt(Math.max(0.0, 2.0 * gravity * Math.abs(dy)));
            reachable = speed >= required;
        } else if (desiredApexY > Math.max(launch.y, target.y)) {
            double rise = desiredApexY - launch.y;
            double fall = desiredApexY - target.y;
            double vy = Math.sqrt(2.0 * gravity * rise);
            double time = vy / gravity + Math.sqrt(2.0 * fall / gravity);
            double vx = horizontal / Math.max(1.0e-6, time);
            required = Math.hypot(vx, vy);
            selected = Math.toDegrees(Math.atan2(vy, vx));
            low = high = selected;
            reachable = speed >= required * 0.98;
        } else {
            double v2 = speed * speed;
            double diagonal = Math.hypot(horizontal, dy);
            double minimumSpeed = Math.sqrt(Math.max(0.0, gravity * (dy + diagonal)));
            double discriminant = v2 * v2 - gravity * (gravity * horizontal * horizontal + 2.0 * dy * v2);
            if (discriminant >= 0.0) {
                double root = Math.sqrt(discriminant);
                low = Math.toDegrees(Math.atan((v2 - root) / (gravity * horizontal)));
                high = Math.toDegrees(Math.atan((v2 + root) / (gravity * horizontal)));
                selected = highArc ? high : low;
                required = minimumSpeed;
                reachable = true;
            } else {
                required = minimumSpeed;
                selected = Math.toDegrees(Math.atan2(dy + diagonal, horizontal));
                low = high = selected;
                reachable = false;
            }
        }

        Verification verification = verify(launch, target, horizontal, speed,
                Math.toRadians(selected), gravity, drag);
        return new Result(azimuth, low, high, selected, required, speed,
                verification.timeTicks, verification.apexY, verification.impactSpeed,
                verification.miss, reachable && verification.miss <= Math.max(16.0, horizontal * 0.001),
                verification.completed);
    }

    public static double toTickGravity(double gravitySi) {
        return Math.max(0.000025, Math.min(0.25, gravitySi / 400.0));
    }

    public static double sanitizeGravity(double gravitySi) {
        return Math.max(0.01, Math.min(100.0, gravitySi));
    }

    public static double sanitizeDrag(double drag) {
        return Math.max(0.0, Math.min(0.01, drag));
    }

    /** Exponential Earth atmosphere approximation (8.5 km scale height, sea level Y=64). */
    public static double airDensityFactor(double worldY) {
        return Math.max(0.0, Math.min(1.5, Math.exp(-(worldY - 64.0) / 8_500.0)));
    }

    private static Verification verify(Vec3 launch, Vec3 target, double horizontal,
                                       double speed, double pitch, double gravity, double drag) {
        double vx = Math.cos(pitch) * speed;
        double vy = Math.sin(pitch) * speed;
        double x = 0.0;
        double y = launch.y;
        double apex = y;
        double time = 0.0;
        double previousY = y;
        double previousX = x;
        double dt = Math.max(0.25, Math.min(10.0, horizontal / 50_000.0));
        int maxSteps = 50_000;
        boolean completed = false;

        for (int i = 0; i < maxSteps; i++) {
            double velocity = Math.hypot(vx, vy);
            double dragAccel = drag * airDensityFactor(y) * velocity * velocity;
            double ax = velocity > 1.0e-9 ? -dragAccel * vx / velocity : 0.0;
            double ay = -gravity + (velocity > 1.0e-9 ? -dragAccel * vy / velocity : 0.0);
            vx += ax * dt;
            vy += ay * dt;
            previousX = x;
            previousY = y;
            x += vx * dt;
            y += vy * dt;
            time += dt;
            apex = Math.max(apex, y);

            if (vy <= 0.0 && y <= target.y) {
                double denom = previousY - y;
                double fraction = Math.abs(denom) > 1.0e-9
                        ? Math.max(0.0, Math.min(1.0, (previousY - target.y) / denom)) : 1.0;
                x = previousX + (x - previousX) * fraction;
                time -= dt * (1.0 - fraction);
                completed = true;
                break;
            }
            if (x > horizontal * 2.0 + 1_000.0 || time >= Integer.MAX_VALUE / 4.0) break;
        }
        return new Verification(time, apex, Math.hypot(vx, vy), Math.abs(horizontal - x),
                completed, x);
    }

    private static double normalizeDegrees(double degrees) {
        double normalized = degrees % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    private record Verification(double timeTicks, double apexY, double impactSpeed,
                                double miss, boolean completed, double landedHorizontal) {
    }
}
