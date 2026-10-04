package net.bullettrain.xenopixelsmod.missile.v3;

import net.bullettrain.xenopixelsmod.missile.BallisticCalculator;
import net.bullettrain.xenopixelsmod.missile.MissileFlightSim;
import net.bullettrain.xenopixelsmod.missile.MissileGuidance;
import net.bullettrain.xenopixelsmod.missile.MissileSpeed;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-28 owner report: "V1 guidance is bad when choosing ballistic, no correction". V3 keeps
 * correcting after the motor cuts off. These fly whole shots in a point-mass simulator (blocks,
 * ticks; the same gravity and drag the game uses) and check where they come down.
 */
class GuidanceV3Test {
    private static final double G = BallisticCalculator.toTickGravity(BallisticCalculator.EARTH_GRAVITY);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 LAUNCH = new Vec3(0, 64, 0);

    record Shot(double closest, boolean fellOnLauncher, int ticks, int fuelUsed, boolean hit) {}

    /** Flies a shot; {@code kickAtTick} > 0 multiplies the velocity by {@code kick} once in flight. */
    static Shot fly(Vec3 launch, Vec3 target, int speedLevel, GuidanceV3.Mode mode, GuidanceV3.Arc arc,
                    double gravity, int kickAtTick, double kick) {
        double engine = GuidanceV3.engineAccel(MissileGuidance.burnPerTick(MissileSpeed.accelFor(speedLevel)), 0, gravity);
        int fuel = MissileSpeed.ticksFor(speedLevel);
        double drag = BallisticCalculator.DEFAULT_DRAG;
        GuidanceV3.Params params = GuidanceV3.Params.plan(launch, target, engine, fuel, gravity, drag,
                mode, arc, 0, 0, true, 24, UP);
        Vec3 pos = launch;
        Vec3 vel = Vec3.ZERO;
        GuidanceV3.Phase phase = GuidanceV3.Phase.RAIL;
        int phaseTicks = 0;
        int fuelLeft = fuel;
        double closest = Double.MAX_VALUE;
        boolean fellOnLauncher = false;
        int t = 0;
        for (; t < 40_000; t++) {
            GuidanceV3.Command c = GuidanceV3.step(
                    new GuidanceV3.State(pos, vel, target, Vec3.ZERO, phase, phaseTicks, fuelLeft), params);
            phaseTicks = c.phase() == phase ? phaseTicks + 1 : 0;
            phase = c.phase();
            if (c.engineOn()) fuelLeft--;
            vel = ArcPredictor.dragged(vel.add(c.accel()).add(0, -gravity, 0), drag, pos.y, 1.0);
            if (t == kickAtTick) vel = vel.scale(kick);
            Vec3 prev = pos;
            pos = pos.add(vel);
            closest = Math.min(closest, pos.distanceTo(target));
            if (MissileFlightSim.reachedTarget(prev, pos, target.x, target.y, target.z, 1.5)) {
                return new Shot(0, fellOnLauncher, t, fuel - fuelLeft, true);
            }
            if (t > 40 && pos.y < launch.y + 4 && Math.hypot(pos.x - launch.x, pos.z - launch.z) < 24) {
                fellOnLauncher = true;
            }
            if (pos.y < Math.min(launch.y, target.y) - 32) break;
        }
        return new Shot(closest, fellOnLauncher, t, fuel - fuelLeft, closest < 3);
    }

    static Shot fly(Vec3 target, int speed, GuidanceV3.Mode mode, GuidanceV3.Arc arc) {
        return fly(LAUNCH, target, speed, mode, arc, G, -1, 1);
    }

    @Test
    void reachableShotsHitInBothModesAndArcs() {
        int hits = 0;
        for (double range : new double[] {200, 1_500, 8_000, 30_000}) {
            Vec3 target = new Vec3(range * 0.8, 70, range * 0.6);
            for (int speed : new int[] {1, 10, 20}) {
                double engine = GuidanceV3.engineAccel(MissileGuidance.burnPerTick(MissileSpeed.accelFor(speed)), 0, G);
                for (GuidanceV3.Mode mode : GuidanceV3.Mode.values()) {
                    for (GuidanceV3.Arc arc : GuidanceV3.Arc.values()) {
                        boolean reachable = GuidanceV3.Params.plan(LAUNCH, target, engine,
                                MissileSpeed.ticksFor(speed), G, BallisticCalculator.DEFAULT_DRAG, mode, arc,
                                0, 0, true, 24, UP).reachable();
                        if (!reachable) continue;
                        Shot shot = fly(target, speed, mode, arc);
                        String what = "range " + range + " speed " + speed + " " + mode + " " + arc + ": " + shot;
                        assertTrue(shot.hit(), "missed, " + what);
                        assertFalse(shot.fellOnLauncher(), "fell on the launcher, " + what);
                        hits++;
                    }
                }
            }
        }
        // No fuel limit (owner, 2026-09-28): every speed level reaches every range.
        assertEquals(4 * 3 * 2 * 3, hits, "every shot should be reachable");
    }

    /** 2026-09-28 owner: there is no fuel limit - the speed level only sets how hard it pushes. */
    @Test
    void everySpeedLevelReachesEveryRange() {
        for (double range : new double[] {200, 1_500, 8_000, 30_000}) {
            Vec3 target = new Vec3(range, 64, 0);
            for (int speed : new int[] {1, 10, 20}) {
                double engine = GuidanceV3.engineAccel(MissileGuidance.burnPerTick(MissileSpeed.accelFor(speed)), 0, G);
                assertTrue(GuidanceV3.Params.plan(LAUNCH, target, engine, MissileSpeed.ticksFor(speed), G,
                        BallisticCalculator.DEFAULT_DRAG, GuidanceV3.Mode.BALLISTIC, GuidanceV3.Arc.AUTO,
                        0, 0, true, 24, UP).reachable(), "range " + range + " speed " + speed);
            }
        }
    }

    /** The point of V3: a ballistic round knocked off its arc mid-coast still comes down on target. */
    @Test
    void ballisticModeCorrectsAVelocityErrorMidCoast() {
        Vec3 target = new Vec3(1_200, 70, 900);
        Shot clean = fly(target, 12, GuidanceV3.Mode.BALLISTIC, GuidanceV3.Arc.AUTO);
        assertTrue(clean.hit(), "clean shot: " + clean);
        for (double kick : new double[] {0.9, 1.1}) {
            Shot knocked = fly(LAUNCH, target, 12, GuidanceV3.Mode.BALLISTIC, GuidanceV3.Arc.AUTO, G,
                    clean.ticks() / 2, kick);
            assertTrue(knocked.hit(), "kick " + kick + ": " + knocked);
        }
    }

    /** The V1-style law for comparison: same kick, motor off, no correction - it misses. */
    @Test
    void anUncorrectedArcMissesAfterTheSameKick() {
        Vec3 target = new Vec3(1_200, 70, 900);
        double engine = GuidanceV3.engineAccel(MissileGuidance.burnPerTick(MissileSpeed.accelFor(12)), 0, G);
        GuidanceV3.Params params = GuidanceV3.Params.plan(LAUNCH, target, engine, MissileSpeed.ticksFor(12), G,
                BallisticCalculator.DEFAULT_DRAG, GuidanceV3.Mode.BALLISTIC, GuidanceV3.Arc.AUTO, 0, 0, true, 24, UP);
        Vec3 v = params.launchVelocity().scale(1.1);
        ArcPredictor.Landing landing = ArcPredictor.predict(LAUNCH.add(0, 1, 0), v, G,
                BallisticCalculator.DEFAULT_DRAG, target.y, 40_000);
        assertNotNull(landing);
        assertTrue(Math.hypot(landing.point().x - target.x, landing.point().z - target.z) > 50,
                "an open-loop arc 10% fast should land well past the target: " + landing);
    }

    /** 2026-09-28 owner: "make the engine stronger" - V3's engine never drops below 3 g. */
    @Test
    void theV3EngineIsStrongAtEverySpeedAndGravity() {
        for (double gSi : new double[] {1.6, 9.80665, 15.0, 30.0}) {
            double g = BallisticCalculator.toTickGravity(gSi);
            for (int speed : new int[] {1, 10, 20}) {
                double engine = GuidanceV3.engineAccel(MissileGuidance.burnPerTick(MissileSpeed.accelFor(speed)), 0, g);
                assertTrue(engine >= 3 * g - 1e-12, "speed " + speed + " at " + gSi + " m/s²");
                assertTrue(GuidanceV3.Params.plan(LAUNCH, new Vec3(5_000, 64, 0), engine, 1, g,
                        BallisticCalculator.DEFAULT_DRAG, GuidanceV3.Mode.BALLISTIC, GuidanceV3.Arc.AUTO, 0, 0,
                        true, 24, UP).reachable(), "burn time is not a limit");
            }
        }
        double base = MissileGuidance.burnPerTick(MissileSpeed.accelFor(20));
        assertEquals(base * 2.0, GuidanceV3.engineAccel(base, 0, G), 1e-12, "speed levels push twice V1's base");
        assertEquals(base * 2.0 * 3.0, GuidanceV3.engineAccel(base, 8, G), 1e-12, "+25% per paired thruster, no cap");
        assertEquals(base * 2.0 * 5.0, GuidanceV3.engineAccel(base, 16, G), 1e-12);
    }

    @Test
    void lowGravityAndHighTargetsStillHit() {
        assertTrue(fly(LAUNCH, new Vec3(900, 64, -400), 10, GuidanceV3.Mode.BALLISTIC, GuidanceV3.Arc.AUTO,
                BallisticCalculator.toTickGravity(5.0), -1, 1).hit());
        assertTrue(fly(new Vec3(-600, 230, 300), 12, GuidanceV3.Mode.BALLISTIC, GuidanceV3.Arc.HIGH).hit());
        assertTrue(fly(new Vec3(700, 20, 100), 12, GuidanceV3.Mode.GUIDED, GuidanceV3.Arc.LOW).hit());
    }
}
