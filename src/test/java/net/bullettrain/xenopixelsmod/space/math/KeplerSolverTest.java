package net.bullettrain.xenopixelsmod.space.math;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Kepler's equation solver: correctness first, then the cost that §21's budget is written against.
 *
 * <p>The correctness cases matter more than the timing ones. A solver that is subtly wrong does not
 * crash — it puts every body in the sky a little off, and because the server and clients all run the
 * same math they agree with each other, so nothing detects it. A handoff then lands a ship inside a
 * planet that is not where the chart said it was.
 *
 * <p>Timing assertions here are deliberately loose. The real budget check is the gametest harness from
 * §13's Q5 (eight players orbit-cruising, measured against a baseline TPS), because wall-clock numbers
 * on a shared CI machine are noise. What these catch is an algorithmic regression — a solver that
 * starts iterating to its cap on every call, or an accidental O(n²) — not a 10 % drift.
 */
class KeplerSolverTest {

    private static final double TAU = OrbitalElements.TWO_PI;

    @Test void aCircularOrbitStaysAtConstantRadius() {
        OrbitalElements elements = OrbitalElements.circular(1000.0, 600.0);
        double[] pos = new double[3];
        for (int step = 0; step < 360; step++) {
            double t = step * (600.0 / 360.0);
            KeplerSolver.position(elements, t, pos);
            double radius = Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]);
            assertEquals(1000.0, radius, 1.0e-6, "radius must not vary on a circular orbit at t=" + t);
        }
    }

    @Test void aCircularOrbitClosesExactlyAfterOnePeriod() {
        OrbitalElements elements = OrbitalElements.circular(1000.0, 600.0);
        double[] start = new double[3];
        double[] after = new double[3];
        KeplerSolver.position(elements, 0.0, start);
        KeplerSolver.position(elements, 600.0, after);
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(start[axis], after[axis], 1.0e-6,
                    "position after exactly one period must equal the start on axis " + axis);
        }
    }

    /** The defining property: whatever the solver returns must actually satisfy Kepler's equation. */
    @Test void solvedAnomalySatisfiesKeplersEquation() {
        double[] eccentricities = {0.0, 0.1, 0.5, 0.8, 0.9, 0.95, 0.99, 0.999};
        for (double e : eccentricities) {
            for (int step = 0; step < 64; step++) {
                double m = -Math.PI + step * (TAU / 64.0);
                double anomaly = KeplerSolver.eccentricAnomaly(m, e);
                double residual = anomaly - e * Math.sin(anomaly) - OrbitalElements.normalizeAngle(m);
                assertTrue(Math.abs(residual) < 1.0e-9,
                        "Kepler residual too large at e=" + e + " M=" + m + ": " + residual);
                assertTrue(Double.isFinite(anomaly), "anomaly must be finite at e=" + e + " M=" + m);
            }
        }
    }

    /**
     * Near-parabolic orbits are where Newton–Raphson overshoots and a naive solver returns NaN. Comets
     * (§23.2) and black-hole slingshot routes (§15) are authored at exactly this end of the range, so
     * the bisection fallback has to hold.
     */
    @Test void highEccentricityOrbitsConvergeWithoutNaN() {
        for (double e : new double[]{0.99, 0.995, 0.999, 0.9999}) {
            for (double m : new double[]{0.0, 1.0e-6, 0.01, 0.1, Math.PI / 2, Math.PI, -1.0e-6}) {
                double anomaly = KeplerSolver.eccentricAnomaly(m, Math.min(e, OrbitalElements.MAX_ECCENTRICITY));
                assertTrue(Double.isFinite(anomaly), "non-finite anomaly at e=" + e + " M=" + m);
                assertTrue(Math.abs(anomaly) <= Math.PI + 1.0e-9,
                        "anomaly escaped its bracket at e=" + e + " M=" + m + ": " + anomaly);
            }
        }
    }

    @Test void periapsisAndApoapsisAreReachedAtTheExpectedAnomalies() {
        OrbitalElements elements = new OrbitalElements(
                1000.0, 0.5, 0.0, 0.0, 0.0, 0.0, 600.0, 0.0);
        assertEquals(500.0, elements.periapsisDistance(), 1.0e-9);
        assertEquals(1500.0, elements.apoapsisDistance(), 1.0e-9);

        double[] pos = new double[3];
        // M0 = 0 puts the body at periapsis at t = epoch.
        KeplerSolver.position(elements, 0.0, pos);
        assertEquals(500.0, length(pos), 1.0e-6, "t=epoch with M0=0 must be periapsis");
        // Half a period later it is at apoapsis.
        KeplerSolver.position(elements, 300.0, pos);
        assertEquals(1500.0, length(pos), 1.0e-6, "half a period later must be apoapsis");
    }

    @Test void anEccentricOrbitConservesAngularMomentum() {
        // r × v is constant along a Keplerian orbit. Checking it validates position and velocity
        // together — a solver with a correct position and a wrong velocity derivative passes every
        // other test here and still breaks the §20 handoff velocity carry-over.
        OrbitalElements elements = new OrbitalElements(
                2000.0, 0.6, 0.3, 0.7, 1.1, 0.4, 900.0, 0.0);
        double[] pos = new double[3];
        double[] vel = new double[3];
        double[] reference = null;
        for (int step = 0; step < 40; step++) {
            double t = step * (900.0 / 40.0);
            KeplerSolver.positionAndVelocity(elements, t, pos, vel);
            double hx = pos[1] * vel[2] - pos[2] * vel[1];
            double hy = pos[2] * vel[0] - pos[0] * vel[2];
            double hz = pos[0] * vel[1] - pos[1] * vel[0];
            if (reference == null) {
                reference = new double[]{hx, hy, hz};
            } else {
                assertEquals(reference[0], hx, 1.0e-6 * scale(reference), "h.x drifted at t=" + t);
                assertEquals(reference[1], hy, 1.0e-6 * scale(reference), "h.y drifted at t=" + t);
                assertEquals(reference[2], hz, 1.0e-6 * scale(reference), "h.z drifted at t=" + t);
            }
        }
    }

    @Test void visVivaSpeedMatchesTheSolvedVelocityMagnitude() {
        double mu = 4.0e6;
        OrbitalElements elements = OrbitalElements.fromGravitationalParameter(
                1000.0, 0.3, 0.0, 0.0, 0.0, 0.0, mu, 0.0);
        double[] pos = new double[3];
        double[] vel = new double[3];
        for (int step = 0; step < 20; step++) {
            double t = step * (elements.periodSeconds() / 20.0);
            KeplerSolver.positionAndVelocity(elements, t, pos, vel);
            double r = length(pos);
            double expected = elements.speedAtRadius(mu, r);
            assertEquals(expected, length(vel), 1.0e-6 * Math.max(1.0, expected),
                    "vis-viva and the solved velocity must agree at t=" + t);
        }
    }

    @Test void inclinationTiltsTheOrbitOutOfTheReferencePlane() {
        OrbitalElements flat = OrbitalElements.circular(1000.0, 600.0);
        OrbitalElements tilted = new OrbitalElements(1000.0, 0.0, Math.PI / 4, 0.0, 0.0, 0.0, 600.0, 0.0);
        double[] a = new double[3];
        double[] b = new double[3];
        KeplerSolver.position(flat, 150.0, a);
        KeplerSolver.position(tilted, 150.0, b);
        assertEquals(0.0, a[2], 1.0e-9, "a zero-inclination orbit must stay in the reference plane");
        assertTrue(Math.abs(b[2]) > 1.0, "a 45-degree inclination must leave the plane, got z=" + b[2]);
        assertEquals(length(a), length(b), 1.0e-6, "tilting must not change the radius");
    }

    @Test void angleNormalizationFoldsIntoOneTurnWithoutLosingPrecision() {
        assertEquals(0.0, OrbitalElements.normalizeAngle(0.0), 1.0e-12);
        assertEquals(0.0, OrbitalElements.normalizeAngle(TAU), 1.0e-12);
        assertEquals(0.0, OrbitalElements.normalizeAngle(-TAU), 1.0e-12);
        assertEquals(Math.PI, OrbitalElements.normalizeAngle(-Math.PI), 1.0e-12,
                "the -pi endpoint folds to +pi so the range is a clean half-open interval");
        // A long-running server accumulates a huge mean anomaly; it must still fold correctly.
        double huge = TAU * 1.0e9 + 1.0;
        double folded = OrbitalElements.normalizeAngle(huge);
        assertTrue(Math.abs(folded) <= Math.PI, "folded angle out of range: " + folded);
        assertEquals(0.0, OrbitalElements.normalizeAngle(Double.NaN), 0.0, "NaN degrades to zero, not NaN");
    }

    @Test void degenerateInputsDegradeInsteadOfPropagatingNaN() {
        double[] out = new double[3];
        // Null elements must not throw on a render path that races a chart reload.
        KeplerSolver.position(null, 0.0, out);
        assertEquals(0.0, out[0], 0.0);
        assertEquals(0.0, out[1], 0.0);
        assertEquals(0.0, out[2], 0.0);

        // Eccentricity outside [0,1) is clamped rather than producing a hyperbolic nonsense answer.
        double clamped = KeplerSolver.eccentricAnomaly(1.0, 1.5);
        assertTrue(Double.isFinite(clamped), "an out-of-range eccentricity must still return a finite anomaly");
        assertEquals(1.0, KeplerSolver.eccentricAnomaly(1.0, 0.0), 1.0e-12,
                "a circular orbit is already solved: E == M exactly");
    }

    @Test void angularSizeShrinksWithDistance() {
        double near = KeplerSolver.angularSize(100.0, 200.0);
        double far = KeplerSolver.angularSize(100.0, 200_000.0);
        assertTrue(near > far, "a nearer body must subtend a larger angle");
        assertEquals(0.0, KeplerSolver.angularSize(100.0, 0.0), 0.0, "zero distance must not divide by zero");
        assertEquals(0.0, KeplerSolver.angularSize(-5.0, 100.0), 0.0, "a negative radius is not a size");
    }

    /**
     * §21's headline claim: a Kepler solve is ~0.3 ms, and fifty idle bodies must cost under 1 ms per
     * tick in total. This solves far more than fifty, far more often than once per tick, and asserts a
     * bound loose enough to survive a busy CI machine but tight enough to catch the solver silently
     * falling through to bisection on every call.
     */
    @Test void twoHundredBodiesSolveWellInsideThePerTickBudget() {
        int bodies = 200;
        OrbitalElements[] chart = new OrbitalElements[bodies];
        for (int i = 0; i < bodies; i++) {
            chart[i] = new OrbitalElements(
                    500.0 + i * 137.0,
                    (i % 20) / 100.0,
                    (i % 7) * 0.11,
                    (i % 11) * 0.23,
                    (i % 5) * 0.4,
                    (i % 13) * 0.3,
                    300.0 + i * 61.0,
                    0.0);
        }
        double[] pos = new double[3];
        double[] vel = new double[3];

        // Warm up so the measured run is compiled code, not the interpreter.
        for (int warm = 0; warm < 50; warm++) {
            for (int i = 0; i < bodies; i++) KeplerSolver.positionAndVelocity(chart[i], warm, pos, vel);
        }

        int passes = 200;
        long start = System.nanoTime();
        for (int pass = 0; pass < passes; pass++) {
            for (int i = 0; i < bodies; i++) KeplerSolver.positionAndVelocity(chart[i], pass * 0.05, pos, vel);
        }
        long elapsed = System.nanoTime() - start;

        double perPassMillis = elapsed / 1_000_000.0 / passes;
        // Twenty times the stated budget: this is a regression fence, not a precision measurement.
        assertTrue(perPassMillis < 20.0,
                "200 position+velocity solves took " + perPassMillis + " ms, far over the §21 budget");
        assertTrue(Double.isFinite(pos[0]) && Double.isFinite(vel[0]), "solver produced a non-finite result");
    }

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static double scale(double[] v) {
        return Math.max(1.0, length(v));
    }
}
