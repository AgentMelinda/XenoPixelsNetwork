package net.bullettrain.xenopixelsmod.space.math;

/**
 * The six classical orbital elements plus the period, as one immutable value.
 *
 * <p>This is the whole of a body's motion, per §20 Tier 1 of the space design: position at time
 * {@code t} is pure math over these numbers, so the server and every client compute the
 * <i>identical</i> answer from a shared epoch and nothing has to be streamed per tick. That
 * determinism is what makes 200 idle charted bodies cost nothing but a registry row (§21), and it is
 * why there is no N-body integration and no drift anywhere in this package.
 *
 * <p>A record rather than a mutable class: elements are authored once (built-in enum, datapack JSON,
 * or a {@code .yml} sidecar) and then only ever read, on the physics pass and on every client that
 * renders the chart. Immutability is what lets a single instance be shared across threads without
 * copying, and what makes registry-sync packets safe to hand to render code.
 *
 * <p><b>Units.</b> Distance is whatever the chart's system unit is — for the Voidsea that is blocks,
 * scaled so a system fits inside float-precise coordinates (see the design's Q11 on large-coordinate
 * jitter). Time is seconds. Nothing here knows which, so the same solver serves a solar system and a
 * moon system.
 *
 * @param semiMajorAxis       {@code a}, the ellipse's semi-major axis in system units; must be &gt; 0
 * @param eccentricity        {@code e}, 0 for a circle up to (excluding) 1 for an ellipse. Comets and
 *                            black-hole slingshot routes sit near the top of that range, so the
 *                            solver has to stay stable there rather than only near zero.
 * @param inclination         {@code i}, radians, tilt of the orbital plane from the reference plane
 * @param ascendingNode       {@code Ω}, radians, where the orbit crosses the reference plane going north
 * @param argumentOfPeriapsis {@code ω}, radians, angle from the ascending node to periapsis
 * @param meanAnomalyAtEpoch  {@code M₀}, radians, the body's phase at {@code epochSeconds}
 * @param periodSeconds       {@code T}, one full orbit in seconds; must be &gt; 0
 * @param epochSeconds        the instant {@code meanAnomalyAtEpoch} is stated for. Shared by server
 *                            and client — a body's motion is undefined without agreeing on this.
 */
public record OrbitalElements(
        double semiMajorAxis,
        double eccentricity,
        double inclination,
        double ascendingNode,
        double argumentOfPeriapsis,
        double meanAnomalyAtEpoch,
        double periodSeconds,
        double epochSeconds) {

    /** Two π, cached because every call below normalises an angle. */
    public static final double TWO_PI = 2.0 * Math.PI;

    /**
     * Highest eccentricity this solver treats as a closed ellipse. At or above it the period is
     * infinite and Kepler's equation has no bounded solution, so such a body must be authored as a
     * different kind of object rather than quietly producing NaN positions.
     */
    public static final double MAX_ECCENTRICITY = 0.999;

    public OrbitalElements {
        if (!(semiMajorAxis > 0.0) || !Double.isFinite(semiMajorAxis)) {
            throw new IllegalArgumentException("semiMajorAxis must be finite and > 0, got " + semiMajorAxis);
        }
        if (!(eccentricity >= 0.0) || eccentricity >= MAX_ECCENTRICITY || !Double.isFinite(eccentricity)) {
            throw new IllegalArgumentException(
                    "eccentricity must be in [0, " + MAX_ECCENTRICITY + "), got " + eccentricity);
        }
        if (!(periodSeconds > 0.0) || !Double.isFinite(periodSeconds)) {
            throw new IllegalArgumentException("periodSeconds must be finite and > 0, got " + periodSeconds);
        }
        for (double angle : new double[]{inclination, ascendingNode, argumentOfPeriapsis, meanAnomalyAtEpoch}) {
            if (!Double.isFinite(angle)) {
                throw new IllegalArgumentException("orbital angles must be finite");
            }
        }
        if (!Double.isFinite(epochSeconds)) {
            throw new IllegalArgumentException("epochSeconds must be finite, got " + epochSeconds);
        }
    }

    /**
     * A circular orbit in the reference plane — the common case for charted planets, and the shape
     * every fallback and test uses when it does not care about tilt or phase.
     */
    public static OrbitalElements circular(double semiMajorAxis, double periodSeconds) {
        return new OrbitalElements(semiMajorAxis, 0.0, 0.0, 0.0, 0.0, 0.0, periodSeconds, 0.0);
    }

    /**
     * Elements from a central body's gravitational parameter instead of a hand-authored period, via
     * Kepler's third law {@code T = 2π √(a³/μ)}. Useful when a pack specifies masses rather than
     * periods, and it keeps a body's orbit consistent with the gravity it also exerts.
     *
     * @param mu the standard gravitational parameter {@code GM} of the parent body, in
     *           units³/s²; must be &gt; 0
     */
    public static OrbitalElements fromGravitationalParameter(double semiMajorAxis, double eccentricity,
                                                             double inclination, double ascendingNode,
                                                             double argumentOfPeriapsis,
                                                             double meanAnomalyAtEpoch,
                                                             double mu, double epochSeconds) {
        if (!(mu > 0.0) || !Double.isFinite(mu)) {
            throw new IllegalArgumentException("mu must be finite and > 0, got " + mu);
        }
        double period = TWO_PI * Math.sqrt(semiMajorAxis * semiMajorAxis * semiMajorAxis / mu);
        return new OrbitalElements(semiMajorAxis, eccentricity, inclination, ascendingNode,
                argumentOfPeriapsis, meanAnomalyAtEpoch, period, epochSeconds);
    }

    /** Mean motion {@code n = 2π/T}, radians per second. */
    public double meanMotion() {
        return TWO_PI / periodSeconds;
    }

    /**
     * Mean anomaly at {@code timeSeconds}, normalised into {@code (-π, π]}.
     *
     * <p>Normalising matters for precision, not just tidiness: {@code M₀ + n·Δt} grows without bound
     * over a long-running server, and a large argument to {@code Math.sin} loses significant digits.
     * Folding each step keeps the solver's input small no matter how many days the world has run.
     */
    public double meanAnomalyAt(double timeSeconds) {
        return normalizeAngle(meanAnomalyAtEpoch + meanMotion() * (timeSeconds - epochSeconds));
    }

    /** Periapsis distance {@code a(1-e)} — the closest approach, and the tidal-band inner edge. */
    public double periapsisDistance() {
        return semiMajorAxis * (1.0 - eccentricity);
    }

    /** Apoapsis distance {@code a(1+e)}. Equal to the radius for a circular orbit. */
    public double apoapsisDistance() {
        return semiMajorAxis * (1.0 + eccentricity);
    }

    /**
     * Orbital speed at radius {@code r} from the focus, from the vis-viva equation
     * {@code v² = μ(2/r − 1/a)}. This is what §20's velocity carry-over adds to a ship leaving a
     * body, and what a slingshot (§15) trades against.
     *
     * @param mu the parent body's gravitational parameter, the same one used to derive the period
     */
    public double speedAtRadius(double mu, double r) {
        if (!(r > 0.0) || !Double.isFinite(r)) return 0.0;
        double v2 = mu * (2.0 / r - 1.0 / semiMajorAxis);
        return v2 > 0.0 ? Math.sqrt(v2) : 0.0;
    }

    /** Folds any angle into {@code (-π, π]} without branching on quadrant. */
    public static double normalizeAngle(double radians) {
        if (!Double.isFinite(radians)) return 0.0;
        double wrapped = Math.IEEEremainder(radians, TWO_PI);
        // IEEEremainder returns [-π, π]; nudge the -π endpoint so the range is a clean half-open one.
        return wrapped == -Math.PI ? Math.PI : wrapped;
    }
}
