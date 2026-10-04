package net.bullettrain.xenopixelsmod.missile.v3;

import net.bullettrain.xenopixelsmod.missile.BallisticCalculator;
import net.bullettrain.xenopixelsmod.missile.MissileEject;
import net.bullettrain.xenopixelsmod.missile.MissileFlightSim;
import net.bullettrain.xenopixelsmod.missile.MissileSpeed;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tube missiles under V3: the entity's own rail out of the silo (as V1), then {@link TubeV3} every
 * tick - the same silo scenarios as {@code MissileGuidanceTest}.
 */
class TubeV3Test {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    record Outcome(boolean hit, boolean fellOnLauncher, double closest) {}

    static Outcome fly(Vec3 launch, Vec3 target, int speedLevel, int clearance, int cruiseY) {
        TubeV3 v3 = TubeV3.plan(launch, target, MissileSpeed.accelFor(speedLevel), MissileSpeed.ticksFor(speedLevel),
                BallisticCalculator.EARTH_GRAVITY, BallisticCalculator.DEFAULT_DRAG, 0, cruiseY, true, clearance, UP);
        Vec3 pos = launch;
        Vec3 vel = UP;
        int age = 0;
        while (!MissileEject.finished(age, launch, pos, UP, clearance, 0)) {
            age++;
            vel = UP.scale(1.2 + Math.min(age, 40) * 0.15);
            pos = pos.add(vel);
        }
        double closest = Double.MAX_VALUE;
        boolean fell = false;
        for (int t = 0; t < 30_000; t++) {
            vel = v3.step(pos, vel, target);
            Vec3 prev = pos;
            pos = pos.add(vel);
            closest = Math.min(closest, pos.distanceTo(target));
            if (MissileFlightSim.reachedTarget(prev, pos, target.x, target.y, target.z, 1.5)) {
                return new Outcome(true, fell, 0);
            }
            if (pos.y < launch.y + 4 && Math.hypot(pos.x - launch.x, pos.z - launch.z) < 24) fell = true;
            if (pos.y < Math.min(launch.y, target.y) - 32) break;
        }
        return new Outcome(closest < 3, fell, closest);
    }

    @Test
    void siloShotsHitWithoutComingBackDown() {
        Vec3 launch = new Vec3(0, 64, 0);
        for (double range : new double[] {120, 400, 1_500, 8_000}) {
            for (int speed : new int[] {5, 10, 20}) {
                Vec3 target = new Vec3(range * 0.8, 70, range * 0.6);
                if (!TubeV3.plan(launch, target, MissileSpeed.accelFor(speed), MissileSpeed.ticksFor(speed),
                        BallisticCalculator.EARTH_GRAVITY, BallisticCalculator.DEFAULT_DRAG, 0, 0, true, 24, UP)
                        .reachable()) {
                    continue;
                }
                Outcome o = fly(launch, target, speed, MissileEject.DEFAULT_CLEARANCE, 0);
                assertTrue(o.hit(), "range " + range + " speed " + speed + ": " + o);
                assertFalse(o.fellOnLauncher(), "range " + range + " speed " + speed + ": " + o);
            }
        }
    }

    @Test
    void deepSilosAndCruiseAltitudeShotsArrive() {
        assertTrue(fly(new Vec3(0, -20, 0), new Vec3(600, 90, -300), 12, 96, 0).hit());
        assertTrue(fly(new Vec3(0, 64, 0), new Vec3(2_000, 70, 500), 12, 24, 200).hit(), "cruise at Y 200");
    }

    @Test
    void phasesMapOntoTheEntityPhases() {
        TubeV3 v3 = TubeV3.plan(new Vec3(0, 64, 0), new Vec3(500, 64, 0), MissileSpeed.accelFor(10),
                MissileSpeed.ticksFor(10), BallisticCalculator.EARTH_GRAVITY, BallisticCalculator.DEFAULT_DRAG,
                0, 0, true, 24, UP);
        assertEquals(net.bullettrain.xenopixelsmod.missile.MissilePhase.BOOST, v3.phase());
        v3.step(new Vec3(0, 90, 0), new Vec3(0, 3, 0), new Vec3(500, 64, 0));
        assertNotNull(v3.phase());
        assertTrue(v3.fuelTicks() <= MissileSpeed.ticksFor(10));
    }
}
