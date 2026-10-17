package net.bullettrain.xenopixelsmod.space.math;

/**
 * Kepler's equation, solved deterministically and without allocating.
 *
 * <p>Everything in the Voidsea's sky is evaluated here: given a body's {@link OrbitalElements} and a
 * time, this returns where it is and how fast it is moving. The server and every client call the same
 * pure function with the same inputs, so they cannot disagree and nothing needs streaming (§20
 * Tier 1). That is also what lets an unloaded planet still orbit for free (§21) — its position was
 * never a stored value, it is arithmetic.
 *
 * <p><b>No allocation on the hot path.</b> Results are written into caller-supplied {@code double[3]}
 * arrays. The chart is re-evaluated for every body in view on every render frame and every physics
 * pass, so returning a {@code Vec3} or a small array from each call would put hundreds of short-lived
 * objects per tick into the young generation for no reason — the house rule on hot-path allocation
 * applies to client frame code exactly as much as to server ticks.
 *
 * <p><b>Convergence.</b> Newton–Raphson from a standard starter, capped, with a bisection fallback.
 * {@code f(E) = E − e·sin E − M} is strictly increasing for {@code e < 1} because
 * {@code f'(E) = 1 − e·cos E ≥ 1 − e > 0}, so it has exactly one root in {@code [−π, π]} and bisection
 * on that bracket is guaranteed to find it. Newton is used first because it converges in three to five
 * iterations for ordinary eccentricities; the fallback exists because Newton can overshoot badly as
 * {@code e} approaches 1, and comets plus black-hole slingshot routes are authored right at that end
 * of the range. A solver that returns NaN for an eccentric orbit would take the whole chart with it.
 */
public final class KeplerSolver {

    /** Newton iterations before giving up and bisecting. Five is ample for e below about 0.9. */
    public static final int DEFAULT_MAX_ITERATIONS = 8;

    /** Convergence target on the residual {@code |E − e·sin E − M|}, in radians. */
    public static final double TOLERANCE = 1.0e-12;

    /** Bisection passes; 60 halvings of a 2π bracket is far below double resolution. */
    private static final int BISECTION_PASSES = 60;

    private KeplerSolver() {
    }

    /**
     * Solves {@code M = E − e·sin E} for the eccentric anomaly {@code E}.
     *
     * @param meanAnomaly  {@code M} in radians; any range is accepted and folded internally
     * @param eccentricity {@code e} in {@code [0, 1)}
     * @return {@code E} in {@code [−π, π]}, or {@code M} unchanged when the input is not finite so a
     *         bad authored value degrades to a wrong-but-harmless position instead of NaN
     */
    public static double eccentricAnomaly(double meanAnomaly, double eccentricity) {
        return eccentricAnomaly(meanAnomaly, eccentricity, DEFAULT_MAX_ITERATIONS);
    }

    public static double eccentricAnomaly(double meanAnomaly, double eccentricity, int maxIterations) {
        double m = OrbitalElements.normalizeAngle(meanAnomaly);
        double e = eccentricity;
        if (!Double.isFinite(e) || e < 0.0) return m;
        if (e >= 1.0) e = OrbitalElements.MAX_ECCENTRICITY;
        // A circular orbit is already solved: E == M exactly. Skipping the iteration keeps the common
        // case (every charted planet) at the cost of one comparison.
        if (e == 0.0) return m;

        // Starter: M is fine for modest eccentricity; near-parabolic orbits need the apsis, or the
        // first Newton step can jump clean out of the bracket.
        double ecc = e < 0.8 ? m : Math.copySign(Math.PI, m);
        int limit = Math.max(1, maxIterations);
        for (int i = 0; i < limit; i++) {
            double sinE = Math.sin(ecc);
            double f = ecc - e * sinE - m;
            if (Math.abs(f) < TOLERANCE) return OrbitalElements.normalizeAngle(ecc);
            double derivative = 1.0 - e * Math.cos(ecc);
            if (!(derivative > 1.0e-9)) break;
            double next = ecc - f / derivative;
            if (!Double.isFinite(next)) break;
            ecc = next;
        }
        return bisect(m, e);
    }

    /** Guaranteed-convergent fallback over the bracket that is known to contain the single root. */
    private static double bisect(double m, double e) {
        double low = -Math.PI;
        double high = Math.PI;
        for (int i = 0; i < BISECTION_PASSES; i++) {
            double mid = 0.5 * (low + high);
            double f = mid - e * Math.sin(mid) - m;
            if (Math.abs(f) < TOLERANCE) return mid;
            // f is strictly increasing, so the sign tells us which half the root is in.
            if (f > 0.0) high = mid;
            else low = mid;
        }
        return 0.5 * (low + high);
    }

    /**
     * Writes the body's position at {@code timeSeconds} into {@code out} as {@code [x, y, z]}.
     *
     * @param out a {@code double[3]} owned by the caller, reused across calls; must not be null
     * @return {@code out}, so callers can chain
     */
    public static double[] position(OrbitalElements elements, double timeSeconds, double[] out) {
        return positionAndVelocity(elements, timeSeconds, out, null);
    }

    /**
     * Writes the body's velocity at {@code timeSeconds} into {@code out} as {@code [vx, vy, vz]}, in
     * system units per second.
     */
    public static double[] velocity(OrbitalElements elements, double timeSeconds, double[] out) {
        return positionAndVelocity(elements, timeSeconds, null, out);
    }

    /**
     * Writes position and/or velocity, solving Kepler's equation <b>once</b> for both.
     *
     * <p>Either output may be null. Asking for both together is the normal case — a handoff anchor
     * needs the body's position and its orbital velocity for the carry-over (§20) — and sharing one
     * anomaly solve halves the transcendentals, which are the only real cost in here.
     *
     * <p>The transform is the standard perifocal-to-reference rotation {@code Rz(Ω)·Rx(i)·Rz(ω)}
     * expanded by hand. Building three rotation matrices per call would allocate nine doubles and do
     * twice the multiplies; the expanded form is six trig values and nine multiply-adds, and it is the
     * same arithmetic either way.
     */
    public static double[] positionAndVelocity(OrbitalElements elements, double timeSeconds,
                                               double[] positionOut, double[] velocityOut) {
        if (elements == null) {
            if (positionOut != null) zero(positionOut);
            if (velocityOut != null) zero(velocityOut);
            return positionOut != null ? positionOut : velocityOut;
        }

        double m = elements.meanAnomalyAt(timeSeconds);
        double e = elements.eccentricity();
        double anomaly = eccentricAnomaly(m, e);
        double a = elements.semiMajorAxis();
        double n = elements.meanMotion();

        double sinE = Math.sin(anomaly);
        double cosE = Math.cos(anomaly);
        // sqrt(1-e²) is the ellipse's minor-to-major ratio; hoisted because both outputs need it.
        double rootOneMinusE2 = Math.sqrt(Math.max(0.0, 1.0 - e * e));

        // Perifocal position: a(cos E - e), a√(1-e²) sin E
        double px = a * (cosE - e);
        double py = a * rootOneMinusE2 * sinE;

        // Perifocal velocity: d/dt of the above, with Ė = n/(1 - e·cos E).
        // The denominator is the radius over a, and is bounded away from zero for e < 1, but a
        // degenerate authored value must not be able to produce an infinite velocity.
        double edotDivN = 1.0 / Math.max(1.0e-9, 1.0 - e * cosE);
        double vx = -a * n * sinE * edotDivN;
        double vy = a * n * rootOneMinusE2 * cosE * edotDivN;

        double cosW = Math.cos(elements.argumentOfPeriapsis());
        double sinW = Math.sin(elements.argumentOfPeriapsis());
        double cosI = Math.cos(elements.inclination());
        double sinI = Math.sin(elements.inclination());
        double cosO = Math.cos(elements.ascendingNode());
        double sinO = Math.sin(elements.ascendingNode());

        if (positionOut != null) rotate(px, py, cosW, sinW, cosI, sinI, cosO, sinO, positionOut);
        if (velocityOut != null) rotate(vx, vy, cosW, sinW, cosI, sinI, cosO, sinO, velocityOut);
        return positionOut != null ? positionOut : velocityOut;
    }

    /** Applies {@code Rz(Ω)·Rx(i)·Rz(ω)} to a perifocal {@code (x, y, 0)} vector, in place. */
    private static void rotate(double x, double y, double cosW, double sinW,
                               double cosI, double sinI, double cosO, double sinO, double[] out) {
        double u = x * cosW - y * sinW;
        double v = x * sinW + y * cosW;
        out[0] = u * cosO - v * cosI * sinO;
        out[1] = u * sinO + v * cosI * cosO;
        out[2] = v * sinI;
    }

    /**
     * Distance from the focus at {@code timeSeconds}, without the rotation. Cheaper than a full
     * position when only the radius matters — which is all the LOD ladder (§21) and the gravity
     * falloff need, and both are evaluated for every body on the chart.
     */
    public static double radius(OrbitalElements elements, double timeSeconds) {
        if (elements == null) return 0.0;
        double e = elements.eccentricity();
        double anomaly = eccentricAnomaly(elements.meanAnomalyAt(timeSeconds), e);
        return elements.semiMajorAxis() * (1.0 - e * Math.cos(anomaly));
    }

    /**
     * The body's angular diameter in radians as seen from {@code distance}, used for the LOD swap.
     * §21 switches detail by <i>angular size</i> rather than raw distance, so a small distant moon
     * stays a billboard forever while a near one is worth a geodesic proxy.
     */
    public static double angularSize(double radiusBlocks, double distance) {
        if (!(radiusBlocks > 0.0) || !(distance > 0.0) || !Double.isFinite(distance)) return 0.0;
        return 2.0 * Math.atan(radiusBlocks / distance);
    }

    private static void zero(double[] out) {
        for (int i = 0; i < out.length; i++) out[i] = 0.0;
    }
}
