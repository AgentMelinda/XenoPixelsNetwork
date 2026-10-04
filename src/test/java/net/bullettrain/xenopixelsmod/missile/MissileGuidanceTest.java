package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-09-28 owner report: a silo missile "loses velocity and hits the launcher". Tube launches
 * used the tube face (straight up) as the whole boost direction and then coasted ballistically, so
 * nothing ever turned the round toward the target: it climbed, stalled and fell back on the silo.
 * Silo missiles now follow the planner's corridor (climb to apex, glide to target) after the rail.
 */
class MissileGuidanceTest {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private record Outcome(double closestToTarget, boolean fellOnLauncher, int ticks) {}

    /** The entity's own flight: rail push, guided boost, guided glide - without the Level. */
    private static Outcome fly(Vec3 launch, Vec3 target, int speedLevel, int clearance) {
        double accel = MissileSpeed.accelFor(speedLevel);
        int boostTicks = MissileSpeed.ticksFor(speedLevel);
        double g = BallisticCalculator.toTickGravity(BallisticCalculator.EARTH_GRAVITY);
        double drag = BallisticCalculator.DEFAULT_DRAG;
        MissileGuidance.Corridor corridor = MissileGuidance.Corridor.plan(launch, target, accel, boostTicks,
                0, BallisticCalculator.EARTH_GRAVITY, drag);

        Vec3 pos = launch;
        Vec3 vel = UP;
        int age = 0;
        while (!MissileEject.finished(age, launch, pos, UP, clearance, 0)) {
            age++;
            vel = UP.scale(1.2 + Math.min(age, 40) * 0.15);
            pos = pos.add(vel);
        }
        double closest = Double.MAX_VALUE;
        boolean fellOnLauncher = false;
        int ticks = 0;
        for (; ticks < 30_000; ticks++) {
            vel = ticks < boostTicks
                    ? MissileGuidance.boost(pos, vel, corridor, MissileGuidance.burnPerTick(accel), g, drag)
                    : MissileGuidance.glide(pos, vel, corridor, g, drag);
            Vec3 prev = pos;
            pos = pos.add(vel);
            closest = Math.min(closest, pos.distanceTo(target));
            // The entity tests the whole segment it flew this tick (radius 1.5), not just the end.
            if (MissileFlightSim.reachedTarget(prev, pos, target.x, target.y, target.z, 1.5)) closest = 0;
            double fromLauncher = Math.hypot(pos.x - launch.x, pos.z - launch.z);
            if (pos.y < launch.y + 4 && fromLauncher < 24) fellOnLauncher = true;
            if (closest < 3 || pos.y < Math.min(launch.y, target.y) - 32) break;
        }
        return new Outcome(closest, fellOnLauncher, ticks);
    }

    @Test
    void siloShotsReachTheTargetInsteadOfFallingBack() {
        Vec3 launch = new Vec3(0, 64, 0);
        for (double range : new double[] {120, 400, 1_500, 8_000}) {
            for (int speed : new int[] {1, 5, 10, 20}) {
                Vec3 target = new Vec3(range * 0.8, 70, range * 0.6);
                Outcome o = fly(launch, target, speed, MissileEject.DEFAULT_CLEARANCE);
                String what = "range " + range + " speed " + speed + ": " + o;
                assertTrue(o.closestToTarget() < 3, "missed the target, " + what);
                assertTrue(!o.fellOnLauncher(), "came back down on the launcher, " + what);
            }
        }
    }

    /** The flight law this replaced: burn along the tube face, then coast. Kept as evidence. */
    @Test
    void theOldStraightUpBoostFellBackOnTheSilo() {
        Vec3 launch = new Vec3(0, 64, 0);
        double g = BallisticCalculator.toTickGravity(BallisticCalculator.EARTH_GRAVITY);
        Vec3 pos = launch;
        Vec3 vel = UP;
        int age = 0;
        while (!MissileEject.finished(age, launch, pos, UP, MissileEject.DEFAULT_CLEARANCE, 0)) {
            age++;
            vel = UP.scale(1.2 + Math.min(age, 40) * 0.15);
            pos = pos.add(vel);
        }
        int boost = MissileSpeed.ticksFor(5);
        for (int t = 0; t < 20_000 && pos.y >= launch.y; t++) {
            vel = vel.add(0, -g, 0);
            if (t < boost) vel = vel.add(UP.scale(MissileGuidance.burnPerTick(MissileSpeed.accelFor(5))));
            pos = pos.add(vel);
        }
        assertTrue(Math.hypot(pos.x, pos.z) < 1.0, "old law came down beside the silo at " + pos);
    }

    @Test
    void deepSilosAndHighTargetsStillArrive() {
        Vec3 launch = new Vec3(0, -20, 0);
        Outcome deep = fly(launch, new Vec3(600, 90, -300), 8, 96);
        assertTrue(deep.closestToTarget() < 3, "deep silo: " + deep);
        Outcome high = fly(new Vec3(0, 64, 0), new Vec3(-500, 220, 200), 6, 24);
        assertTrue(high.closestToTarget() < 3, "high target: " + high);
    }
}
