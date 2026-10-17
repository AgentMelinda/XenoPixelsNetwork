package net.bullettrain.xenopixelsmod.space.flight;

import static org.junit.jupiter.api.Assertions.*;

import net.bullettrain.xenopixelsmod.space.flight.SpaceFlightCore.Body;
import net.bullettrain.xenopixelsmod.space.flight.SpaceFlightCore.Environment;
import net.bullettrain.xenopixelsmod.space.flight.SpaceFlightCore.Intent;
import net.bullettrain.xenopixelsmod.space.flight.SpaceFlightCore.Tuning;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * The flight-core seam. Pure maths, no Sable and no Minecraft, so the whole model is checkable here.
 *
 * <p>The two cases that matter most are the ones a playtest would not catch quickly: that drag scales
 * continuously to zero as atmosphere thins (a discontinuity at the exit altitude would be a visible
 * jolt on every handoff), and that a bad or malicious control packet cannot produce an unbounded force.
 */
class SpaceFlightCoreTest {

    private static final Tuning TUNING = Tuning.defaults();
    /** Standard hull basis: forward is -Z, matching Minecraft's convention. */
    private static final Vector3d FORWARD = new Vector3d(0, 0, -1);
    private static final Vector3d UP = new Vector3d(0, 1, 0);
    private static final Vector3d RIGHT = new Vector3d(1, 0, 0);

    private static Environment vacuum() {
        return new Environment(new Vector3d(0, 0, 0), 0.0, FORWARD, UP, RIGHT);
    }

    private static Environment withGravityAndAir(double gravityY, double density) {
        return new Environment(new Vector3d(0, gravityY, 0), density, FORWARD, UP, RIGHT);
    }

    private static Body body(Vector3d velocity) {
        return new Body(velocity, new Vector3d(0, 0, 0), 1000.0);
    }

    @Test void fullThrottleAcceleratesAlongTheHullForwardAxis() {
        Vector3d linear = new Vector3d();
        Vector3d angular = new Vector3d();
        SpaceFlightCore.solve(new Intent(1, 0, 0, 0, 0, 0), body(new Vector3d()),
                vacuum(), TUNING, linear, angular);

        assertEquals(0.0, linear.x, 1.0e-9);
        assertEquals(0.0, linear.y, 1.0e-9);
        assertEquals(-TUNING.maxThrustAccel(), linear.z, 1.0e-9,
                "thrust must be along -Z, the hull forward, not a world axis");
    }

    @Test void reverseThrottlePushesBackward() {
        Vector3d linear = new Vector3d();
        SpaceFlightCore.solve(new Intent(-1, 0, 0, 0, 0, 0), body(new Vector3d()),
                vacuum(), TUNING, linear, new Vector3d());
        assertEquals(TUNING.maxThrustAccel(), linear.z, 1.0e-9);
    }

    @Test void strafeAndLiftUseTheHullRightAndUpAxes() {
        Vector3d linear = new Vector3d();
        SpaceFlightCore.solve(new Intent(0, 0, 0, 0, 1, 1), body(new Vector3d()),
                vacuum(), TUNING, linear, new Vector3d());
        assertEquals(TUNING.maxStrafeAccel(), linear.x, 1.0e-9);
        assertEquals(TUNING.maxLiftAccel(), linear.y, 1.0e-9);
    }

    @Test void chartGravityIsAddedStraightThrough() {
        Vector3d linear = new Vector3d();
        SpaceFlightCore.solve(Intent.IDLE, body(new Vector3d()),
                withGravityAndAir(-9.81, 0.0), TUNING, linear, new Vector3d());
        assertEquals(-9.81, linear.y, 1.0e-9, "with no input the only force is gravity");
        assertEquals(0.0, linear.x, 1.0e-9);
    }

    @Test void dragOpposesMotionAndScalesWithTheSquareOfSpeed() {
        Tuning tuning = new Tuning(0, 0, 0, 0, 0, 1.0, 0);
        Vector3d slow = new Vector3d();
        Vector3d fast = new Vector3d();
        SpaceFlightCore.solve(Intent.IDLE, body(new Vector3d(10, 0, 0)),
                withGravityAndAir(0, 1.0), tuning, slow, new Vector3d());
        SpaceFlightCore.solve(Intent.IDLE, body(new Vector3d(20, 0, 0)),
                withGravityAndAir(0, 1.0), tuning, fast, new Vector3d());

        assertTrue(slow.x < 0.0, "drag must oppose the direction of travel");
        // Quadratic: doubling speed quadruples the drag.
        assertEquals(slow.x * 4.0, fast.x, 1.0e-9, "drag must be quadratic in speed");
    }

    /**
     * The §3 handoff requirement. A ship crossing the exit altitude must not feel a step change, so
     * drag has to fall smoothly to zero as density does — not switch off at a threshold.
     */
    @Test void dragFallsContinuouslyToZeroAsTheAtmosphereThins() {
        Tuning tuning = new Tuning(0, 0, 0, 0, 0, 1.0, 0);
        Vector3d out = new Vector3d();
        double previous = 0.0;
        boolean first = true;
        for (int step = 100; step >= 0; step--) {
            double density = step / 100.0;
            SpaceFlightCore.solve(Intent.IDLE, body(new Vector3d(10, 0, 0)),
                    withGravityAndAir(0, density), tuning, out, new Vector3d());
            double dragMagnitude = Math.abs(out.x);
            if (!first) {
                // Walking density downward, drag magnitude must never increase, and must never jump.
                assertTrue(dragMagnitude <= previous + 1.0e-12,
                        "drag grew as density fell at density=" + density);
                assertTrue(Math.abs(dragMagnitude - previous) < 1.0,
                        "drag jumped discontinuously at density=" + density);
            }
            previous = dragMagnitude;
            first = false;
        }
        assertEquals(0.0, previous, 1.0e-12, "in vacuum there must be no drag at all");
    }

    @Test void inVacuumAShipCoastsUnderGravityAlone() {
        Vector3d linear = new Vector3d();
        SpaceFlightCore.solve(Intent.IDLE, body(new Vector3d(500, 0, 0)),
                withGravityAndAir(-3.7, 0.0), TUNING, linear, new Vector3d());
        assertEquals(-3.7, linear.y, 1.0e-9);
        assertEquals(0.0, linear.x, 1.0e-9,
                "no drag in vacuum, so 500 blocks/s of sideways motion must not decay on its own");
    }

    @Test void optionalLinearDampingDecaysVelocityWhenAWorldAsksForIt() {
        Tuning arcade = new Tuning(0, 0, 0, 0, 0, 0, 2.0);
        Vector3d linear = new Vector3d();
        SpaceFlightCore.solve(Intent.IDLE, body(new Vector3d(10, 0, 0)), vacuum(), arcade,
                linear, new Vector3d());
        assertEquals(-20.0, linear.x, 1.0e-9, "damping opposes velocity proportional to it");

        Tuning newtonian = new Tuning(0, 0, 0, 0, 0, 0, 0.0);
        SpaceFlightCore.solve(Intent.IDLE, body(new Vector3d(10, 0, 0)), vacuum(), newtonian,
                linear, new Vector3d());
        assertEquals(0.0, linear.x, 1.0e-9, "the default must be Newtonian: zero damping");
    }

    @Test void stickInputProducesRotationAndDampingBringsItBackToZero() {
        Vector3d angular = new Vector3d();
        SpaceFlightCore.solve(new Intent(0, 1, 0, 0, 0, 0), body(new Vector3d()),
                vacuum(), TUNING, new Vector3d(), angular);
        assertEquals(TUNING.maxAngularAccel(), angular.x, 1.0e-9, "pitch input drives the pitch axis");

        // With no input but existing spin, damping must decelerate rather than accelerate.
        Body spinning = new Body(new Vector3d(), new Vector3d(2.0, 0, 0), 1000.0);
        SpaceFlightCore.solve(Intent.IDLE, spinning, vacuum(), TUNING, new Vector3d(), angular);
        assertEquals(-2.0 * TUNING.angularDamping(), angular.x, 1.0e-9,
                "RCS-style damping must oppose the existing rate, or a tapped ship spins forever");
    }

    @Test void anOutOfRangeOrNonFinitePacketCannotProduceAnUnboundedForce() {
        Vector3d linear = new Vector3d();
        Vector3d angular = new Vector3d();
        // throttle huge, pitch NaN, yaw infinite, roll hugely negative, strafe NaN, lift out of range.
        Intent hostile = new Intent(1.0e300, Double.NaN, Double.POSITIVE_INFINITY, -1.0e300,
                Double.NaN, 42.0);
        SpaceFlightCore.solve(hostile, body(new Vector3d()), vacuum(), TUNING, linear, angular);

        assertTrue(linear.isFinite(), "the linear result must stay finite, got " + linear);
        assertTrue(angular.isFinite(), "the angular result must stay finite, got " + angular);
        assertEquals(TUNING.maxThrustAccel(), Math.abs(linear.z), 1.0e-9,
                "throttle must clamp to full, not to an astronomically larger force");
        assertEquals(TUNING.maxLiftAccel(), linear.y, 1.0e-9, "lift of 42 clamps to full authority");
        assertEquals(0.0, linear.x, 1.0e-9,
                "a NaN strafe must degrade to zero thrust, not to a huge force and not to NaN");
        assertEquals(0.0, angular.x, 1.0e-9, "a NaN pitch must degrade to no rotation");
        assertEquals(TUNING.maxAngularAccel(), angular.y, 1.0e-9, "an infinite yaw clamps to full");
        assertEquals(-TUNING.maxAngularAccel(), angular.z, 1.0e-9,
                "a hugely negative roll clamps to full reverse, not to negative infinity");

        // The other sanitising direction: a finite but absurd strafe clamps rather than scaling.
        SpaceFlightCore.solve(new Intent(0, 0, 0, 0, 1.0e300, 0), body(new Vector3d()),
                vacuum(), TUNING, linear, angular);
        assertEquals(TUNING.maxStrafeAccel(), linear.x, 1.0e-9,
                "a wildly out-of-range strafe must clamp to full authority");
    }

    @Test void aNonFiniteVelocityOrGravityIsIgnoredRatherThanPropagated() {
        Vector3d linear = new Vector3d();
        Body badVelocity = new Body(new Vector3d(Double.NaN, 0, 0), new Vector3d(), 1000.0);
        SpaceFlightCore.solve(new Intent(1, 0, 0, 0, 0, 0), badVelocity,
                withGravityAndAir(-9.81, 1.0), TUNING, linear, new Vector3d());
        assertTrue(linear.isFinite(), "a NaN velocity must not poison the result");

        Environment badGravity = new Environment(new Vector3d(Double.NaN, 0, 0), 0.0, FORWARD, UP, RIGHT);
        SpaceFlightCore.solve(new Intent(1, 0, 0, 0, 0, 0), body(new Vector3d()),
                badGravity, TUNING, linear, new Vector3d());
        assertTrue(linear.isFinite(), "a NaN gravity must not poison the result");
        assertEquals(-TUNING.maxThrustAccel(), linear.z, 1.0e-9, "thrust still applies");
    }

    @Test void aZeroOrNegativeMassIsTreatedAsOneRatherThanDividingByZero() {
        Body massless = new Body(new Vector3d(), new Vector3d(), 0.0);
        assertEquals(1.0, massless.safeMass(), 1.0e-12);
        Body negative = new Body(new Vector3d(), new Vector3d(), -500.0);
        assertEquals(1.0, negative.safeMass(), 1.0e-12);
        Body nan = new Body(new Vector3d(), new Vector3d(), Double.NaN);
        assertEquals(1.0, nan.safeMass(), 1.0e-12);
        Body real = new Body(new Vector3d(), new Vector3d(), 1234.0);
        assertEquals(1234.0, real.safeMass(), 1.0e-12);
    }

    @Test void nullInputsAreToleratedOnARenderPathThatRacesAReload() {
        Vector3d linear = new Vector3d(1, 2, 3);
        Vector3d angular = new Vector3d(4, 5, 6);
        SpaceFlightCore.solve(null, null, null, null, linear, angular);
        assertEquals(0.0, linear.x, 0.0);
        assertEquals(0.0, linear.y, 0.0);
        assertEquals(0.0, linear.z, 0.0);
        assertEquals(0.0, angular.x, 0.0, "outputs are zeroed even when the inputs are unusable");

        // A null intent is the neutral stick, not an error.
        SpaceFlightCore.solve(null, body(new Vector3d()), withGravityAndAir(-9.81, 0.0),
                TUNING, linear, angular);
        assertEquals(-9.81, linear.y, 1.0e-9);
    }

    @Test void solveLinearLeavesTheAngularScratchUnobserved() {
        Vector3d linear = new Vector3d();
        SpaceFlightCore.solveLinear(new Intent(1, 1, 1, 1, 0, 0), body(new Vector3d()),
                vacuum(), TUNING, linear);
        assertEquals(-TUNING.maxThrustAccel(), linear.z, 1.0e-9,
                "the linear-only path must still produce the same translation");
    }

    @Test void anIdleShipInVacuumWithNoGravityHasNoAccelerationAtAll() {
        Vector3d linear = new Vector3d();
        Vector3d angular = new Vector3d();
        SpaceFlightCore.solve(Intent.IDLE, body(new Vector3d()), vacuum(), TUNING, linear, angular);
        assertEquals(0.0, linear.lengthSquared(), 0.0);
        assertEquals(0.0, angular.lengthSquared(), 0.0);
        assertTrue(Intent.IDLE.sanitized().equals(Intent.IDLE));
    }

    /**
     * §13's Q21: drag and downforce on potentially hundreds of pods, every tick, inside a 5 % budget.
     * This is the model those pods would run, measured at 200 of them.
     */
    @Test void twoHundredPodsSolveWellInsideATickBudget() {
        int pods = 200;
        Intent[] intents = new Intent[pods];
        Body[] bodies = new Body[pods];
        Environment[] environments = new Environment[pods];
        for (int i = 0; i < pods; i++) {
            intents[i] = new Intent(0.5, 0, 0, 0, 0, 0);
            bodies[i] = new Body(new Vector3d(i, i * 0.5, -i), new Vector3d(), 50.0 + i);
            environments[i] = new Environment(new Vector3d(0, -9.81, 0), i % 2 == 0 ? 0.0 : 0.012,
                    FORWARD, UP, RIGHT);
        }
        Vector3d linear = new Vector3d();
        Vector3d angular = new Vector3d();

        for (int warm = 0; warm < 200; warm++) {
            for (int i = 0; i < pods; i++) {
                SpaceFlightCore.solve(intents[i], bodies[i], environments[i], TUNING, linear, angular);
            }
        }

        int ticks = 1_000;
        long start = System.nanoTime();
        for (int tick = 0; tick < ticks; tick++) {
            for (int i = 0; i < pods; i++) {
                SpaceFlightCore.solve(intents[i], bodies[i], environments[i], TUNING, linear, angular);
            }
        }
        long elapsed = System.nanoTime() - start;

        double perTickMillis = elapsed / 1_000_000.0 / ticks;
        assertTrue(perTickMillis < 5.0,
                "solving 200 pods took " + perTickMillis + " ms/tick; a 20 TPS server has 50 ms total");
        assertTrue(linear.isFinite());
    }
}
