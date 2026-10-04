package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Closed-loop flight for entity missiles after the silo rail: follow the planner's corridor
 * (climb to the apex, then glide down the ground track to the target).
 *
 * <p>Before this, a tube launch burned along the tube face for the whole boost and then coasted
 * ballistically. For a silo that face is straight up, so the round climbed, stalled and fell back
 * onto its own launcher. Kept free of {@code Level} so a whole shot can be simulated in a test.
 */
public final class MissileGuidance {
    /** Heading change per tick while burning / gliding, in radians. */
    static final double BOOST_TURN = 0.07;
    static final double GLIDE_TURN = 0.05;
    /** Lateral acceleration budget, blocks/tick²; about 0.25 rad/tick at MIN_SPEED. */
    static final double MAX_LATERAL_ACCEL = 0.2;
    /** Slowest a guided round flies; below this it cannot turn usefully. */
    static final double MIN_SPEED = 0.8;
    /** Minimum height kept above the silo mouth before the apex, so the climb clears the launcher. */
    static final double CLIMB_FLOOR = 32.0;

    private MissileGuidance() {}

    /** Ground track plus apex, as planned from the silo mouth. */
    public record Corridor(Vec3 launch, Vec3 target, double apexY, double peakF) {
        public static Corridor plan(Vec3 launch, Vec3 target, double boostAccel, int boostTicks,
                                    double desiredApexY, double gravitySi, double drag) {
            var plan = BallisticTrajectory.plan(launch, target, boostAccel, boostTicks, desiredApexY,
                    gravitySi, drag);
            double apex = Double.isFinite(plan.apexY()) ? plan.apexY() : launch.y + 64.0;
            double peak = Double.isFinite(plan.peakGroundFrac()) ? plan.peakGroundFrac() : 0.35;
            double horiz = Math.hypot(target.x - launch.x, target.z - launch.z);
            if (desiredApexY <= 0) {
                // The ship planner's automatic loft is tall for short shots; an entity round has
                // to turn over it, so keep the climb within reach of its turning circle.
                double ceiling = Math.max(launch.y, target.y) + Math.max(CLIMB_FLOOR, horiz * 0.6);
                apex = Math.min(apex, ceiling);
            }
            return new Corridor(launch, target, Math.max(apex, launch.y + CLIMB_FLOOR), Mth.clamp(peak, 0.05, 0.7));
        }

        /**
         * Engine cut-off speed: enough to fly the arc in reasonable time, not so fast that the
         * round cannot turn onto a short-range target. Long shots still reach high speed.
         */
        public double cruiseSpeed() {
            return Mth.clamp(1.0 + Math.sqrt(horizontal()) * 0.2, 1.6, 30.0);
        }

        double horizontal() {
            return Math.hypot(target.x - launch.x, target.z - launch.z);
        }

        double fraction(Vec3 pos) {
            return BallisticTrajectory.groundFrac(launch.x, launch.z, target.x, target.z, pos.x, pos.z);
        }
    }

    /** Engine acceleration per tick for a configured boost value (same scale as the planner). */
    public static double burnPerTick(double boostAccel) {
        return boostAccel * 55.0 / 400.0;
    }

    /** The point on the corridor a little ahead of the round; the target itself near the end. */
    static Vec3 aimPoint(Vec3 pos, double speed, Corridor c) {
        double horiz = c.horizontal();
        if (horiz < 1.0) return c.target;
        double f = c.fraction(pos);
        double look = Math.max(24.0, speed * 12.0) / horiz;
        if (f + look >= 1.0) return c.target;
        Vec3 aim = BallisticTrajectory.corridorPoint(c.launch.x, c.launch.y, c.launch.z,
                c.target.x, c.target.y, c.target.z, c.apexY, c.peakF, f + look);
        if (f + look <= c.peakF) {
            aim = new Vec3(aim.x, Math.max(aim.y, Math.min(c.apexY, c.launch.y + CLIMB_FLOOR)), aim.z);
        }
        return aim;
    }

    /** One powered tick: turn toward the corridor, burn along the new heading, then gravity and drag. */
    public static Vec3 boost(Vec3 pos, Vec3 vel, Corridor c, double burnPerTick, double tickGravity, double drag) {
        double speed = Math.max(MIN_SPEED, vel.length());
        Vec3 heading = steer(vel, aimPoint(pos, speed, c).subtract(pos), turnFor(speed, BOOST_TURN));
        double burn = speed < c.cruiseSpeed() ? burnPerTick : 0.0;
        Vec3 next = heading.scale(speed + burn).add(0, -tickGravity, 0);
        return applyDrag(next, drag, pos.y);
    }

    /**
     * One unpowered tick: gravity trades height for speed as in free flight, and the heading is
     * steered along the corridor so the round arrives instead of falling where it stalls.
     */
    public static Vec3 glide(Vec3 pos, Vec3 vel, Corridor c, double tickGravity, double drag) {
        Vec3 fallen = vel.add(0, -tickGravity, 0);
        double speed = Math.max(MIN_SPEED, fallen.length());
        Vec3 heading = steer(fallen, aimPoint(pos, speed, c).subtract(pos), turnFor(speed, GLIDE_TURN));
        return applyDrag(heading.scale(speed), drag, pos.y);
    }

    /**
     * Turn per tick from a lateral-acceleration limit: a slow round turns tightly, a fast one
     * sweeps wide, and {@code cap} bounds it either way.
     */
    static double turnFor(double speed, double cap) {
        return Math.min(cap, MAX_LATERAL_ACCEL / Math.max(MIN_SPEED, speed));
    }

    /** Unit heading turned from {@code current} toward {@code desired} by at most {@code maxTurn}. */
    static Vec3 steer(Vec3 current, Vec3 desired, double maxTurn) {
        if (desired.lengthSqr() < 1.0e-9) return current.lengthSqr() < 1.0e-9 ? new Vec3(0, 1, 0) : current.normalize();
        Vec3 d = desired.normalize();
        if (current.lengthSqr() < 1.0e-9) return d;
        Vec3 cur = current.normalize();
        double angle = Math.acos(Mth.clamp(cur.dot(d), -1.0, 1.0));
        if (angle <= maxTurn) return d;
        double t = maxTurn / angle;
        Vec3 mixed = cur.scale(1.0 - t).add(d.scale(t));
        return mixed.lengthSqr() < 1.0e-9 ? d : mixed.normalize();
    }

    static Vec3 applyDrag(Vec3 velocity, double drag, double altitude) {
        double speed = velocity.length();
        if (speed < 1.0e-9 || drag <= 0.0) return velocity;
        double dragAccel = Math.min(speed * 0.95, drag * BallisticCalculator.airDensityFactor(altitude) * speed * speed);
        return velocity.scale(Math.max(0.0, 1.0 - dragAccel / speed));
    }
}
