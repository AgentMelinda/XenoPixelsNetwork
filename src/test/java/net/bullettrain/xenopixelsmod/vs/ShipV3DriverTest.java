package net.bullettrain.xenopixelsmod.vs;

import net.bullettrain.xenopixelsmod.missile.BallisticCalculator;
import net.bullettrain.xenopixelsmod.missile.MissileFlightSim;
import net.bullettrain.xenopixelsmod.missile.MissilePhase;
import net.bullettrain.xenopixelsmod.missile.MissileSpeed;
import net.bullettrain.xenopixelsmod.missile.v3.GuidanceV3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guidance V3 on a ship: commands every game tick (m/s, m/s²), physics in several substeps holding
 * the last command, gravity and drag as the controller's physics tick applies them.
 */
class ShipV3DriverTest {
    private static final double G_SI = BallisticCalculator.EARTH_GRAVITY;
    private static final Vec3 LAUNCH = new Vec3(0, 64, 0);
    private static final int SUBSTEPS = 3;

    record Flight(boolean hit, boolean impact, double closest, int ticks, MissilePhase lastPhase) {}

    static Flight fly(Vec3 target, int speedLevel, GuidanceV3.Mode mode, int stallFrom, int stallTo) {
        return fly(target, speedLevel, mode, stallFrom, stallTo, G_SI);
    }

    /** {@code stallFrom..stallTo}: game ticks with no guidance update (the last command is held). */
    static Flight fly(Vec3 target, int speedLevel, GuidanceV3.Mode mode, int stallFrom, int stallTo, double gSi) {
        ShipV3Driver driver = ShipV3Driver.plan(LAUNCH, target, MissileSpeed.accelFor(speedLevel), 0,
                MissileSpeed.ticksFor(speedLevel), gSi, BallisticCalculator.DEFAULT_DRAG, mode,
                GuidanceV3.Arc.AUTO, 0, 0, true, new Vec3(0, 1, 0));
        Vec3 pos = LAUNCH;
        Vec3 vel = Vec3.ZERO;
        ShipV3Driver.Output out = null;
        double closest = Double.MAX_VALUE;
        double dt = 0.05 / SUBSTEPS;
        for (int tick = 0; tick < 20 * 60 * 10; tick++) {
            boolean stalled = tick >= stallFrom && tick < stallTo;
            if (!stalled || out == null) {
                out = driver.tick(pos, vel, target, Vec3.ZERO, pos.y < 40);
                if (out.impact()) return new Flight(pos.distanceTo(target) < 4, true, closest, tick, out.phase());
            }
            if (stalled && !driver.holdThroughStall()) break; // V1 would brake here
            Vec3 prev = pos;
            for (int i = 0; i < SUBSTEPS; i++) {
                Vec3 accel = out.accelSi().add(0, -gSi, 0);
                double speed = vel.length();
                if (speed > 1.0e-6) {
                    double drag = BallisticCalculator.DEFAULT_DRAG * BallisticCalculator.airDensityFactor(pos.y) * speed * speed;
                    accel = accel.subtract(vel.scale(drag / speed));
                }
                vel = vel.add(accel.scale(dt));
                pos = pos.add(vel.scale(dt));
            }
            closest = Math.min(closest, pos.distanceTo(target));
            if (MissileFlightSim.reachedTarget(prev, pos, target.x, target.y, target.z, 3.0)) {
                return new Flight(true, false, 0, tick, out.phase());
            }
        }
        return new Flight(false, false, closest, -1, out == null ? null : out.phase());
    }

    @Test
    void shipShotsHitInBothModes() {
        for (double range : new double[] {300, 2_000, 6_000}) {
            Vec3 target = new Vec3(range * 0.6, 70, -range * 0.8);
            for (GuidanceV3.Mode mode : GuidanceV3.Mode.values()) {
                Flight f = fly(target, 12, mode, -1, -1);
                assertTrue(f.hit(), "range " + range + " " + mode + ": " + f);
            }
        }
    }

    @Test
    void aStallWhileCoastingDoesNotCancelTheArc() {
        Vec3 target = new Vec3(1_500, 70, 400);
        Flight clean = fly(target, 12, GuidanceV3.Mode.BALLISTIC, -1, -1);
        assertTrue(clean.hit(), "clean: " + clean);
        // 8 game ticks = 400 ms without guidance, mid-flight.
        Flight stalled = fly(target, 12, GuidanceV3.Mode.BALLISTIC, clean.ticks() / 2, clean.ticks() / 2 + 8);
        assertTrue(stalled.hit(), "stalled: " + stalled);
    }

    /** 2026-09-28 owner: no fuel limit, so even speed 1 gets there - it just takes longer. */
    @Test
    void theSlowestSpeedStillArrives() {
        assertTrue(fly(new Vec3(3_000, 64, 0), 1, GuidanceV3.Mode.BALLISTIC, -1, -1).hit());
    }

    @Test
    void terrainInTheWayReportsImpact() {
        // The harness reports ground below Y 40; a target at Y 20 lies behind that terrain.
        Flight f = fly(new Vec3(1_000, 20, 0), 10, GuidanceV3.Mode.BALLISTIC, -1, -1, 15.0);
        assertTrue(f.impact(), "expected a ground impact: " + f);
        assertFalse(f.hit());
    }

    @Test
    void phasesMapToTheControllersPhases() {
        assertEquals(MissilePhase.EJECT, ShipV3Driver.phaseOf(GuidanceV3.Phase.RAIL));
        assertEquals(MissilePhase.BOOST, ShipV3Driver.phaseOf(GuidanceV3.Phase.BOOST));
        assertEquals(MissilePhase.COAST, ShipV3Driver.phaseOf(GuidanceV3.Phase.COAST));
        assertEquals(MissilePhase.TERMINAL, ShipV3Driver.phaseOf(GuidanceV3.Phase.TERMINAL));
    }
}
