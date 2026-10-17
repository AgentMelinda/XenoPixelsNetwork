package net.bullettrain.xenopixelsmod.space.dragonball;

import net.bullettrain.xenopixelsmod.space.math.OrbitalElements;
import net.bullettrain.xenopixelsmod.space.physics.PlanetOrbitHold;

/** Reusable host-relative circular track for one planet-form Super Dragon Ball. */
public final class DragonBallPlanetOrbit {
    private final double mu;
    private final OrbitalElements elements;
    private final double[] relativePosition = new double[3];
    private final double[] relativeVelocity = new double[3];
    private final double[] relativeAcceleration = new double[3];

    private DragonBallPlanetOrbit(double radius, double phase, double mu) {
        this.mu = mu;
        this.elements = OrbitalElements.fromGravitationalParameter(radius, 0.0, 0.0, 0.0,
                0.0, phase, mu, 0.0);
    }

    private DragonBallPlanetOrbit(OrbitalElements elements, double mu) {
        this.mu = mu;
        this.elements = elements;
    }

    /** Builds the circular orbit from the assigned radius/phase and the host chart body's gravitational parameter. */
    public static DragonBallPlanetOrbit create(double radius, double phase, double mu) {
        if (!(radius > 0.0) || !Double.isFinite(radius))
            throw new IllegalArgumentException("orbit radius must be finite and > 0");
        if (!Double.isFinite(phase)) throw new IllegalArgumentException("orbit phase must be finite");
        if (!(mu > 0.0) || !Double.isFinite(mu))
            throw new IllegalArgumentException("host gravitational parameter must be finite and > 0");
        return new DragonBallPlanetOrbit(radius, phase, mu);
    }

    /**
     * Builds a circular orbit from an explicitly authored period for hosts with no gravitational parameter.
     * The equivalent circular-orbit parameter is derived from radius and period; this does not claim the host
     * physically exerts that gravity and must only be used when a pack intentionally supplies the period.
     */
    public static DragonBallPlanetOrbit createWithPeriod(double radius, double phase, double periodSeconds) {
        if (!(radius > 0.0) || !Double.isFinite(radius))
            throw new IllegalArgumentException("orbit radius must be finite and > 0");
        if (!Double.isFinite(phase)) throw new IllegalArgumentException("orbit phase must be finite");
        if (!(periodSeconds > 0.0) || !Double.isFinite(periodSeconds))
            throw new IllegalArgumentException("orbit period must be finite and > 0");
        double speed = OrbitalElements.TWO_PI * (radius / periodSeconds);
        double mu = speed * speed * radius;
        if (!(mu > 0.0) || !Double.isFinite(mu))
            throw new IllegalArgumentException("orbit radius and period do not produce a representable gravitational parameter");
        var elements = new OrbitalElements(radius, 0.0, 0.0, 0.0, 0.0, phase, periodSeconds, 0.0);
        return new DragonBallPlanetOrbit(elements, mu);
    }

    public OrbitalElements elements() { return elements; }

    /**
     * Writes the absolute target in Minecraft world axes: chart X/Y orbital coordinates become world X/Z,
     * matching the chart-to-world mapping used by {@link PlanetOrbitHold}. All arrays are caller-owned length-3
     * scratch buffers; this method allocates nothing and must be called from one thread per orbit instance.
     */
    public void evaluate(double[] hostPosition, double[] hostVelocity, double[] hostAcceleration, double timeSeconds,
                         double[] position, double[] velocity, double[] acceleration) {
        requireVector(hostPosition, "host position");
        requireVector(hostVelocity, "host velocity");
        requireVector(hostAcceleration, "host acceleration");
        requireOutput(position, "target position");
        requireOutput(velocity, "target velocity");
        requireOutput(acceleration, "target acceleration");
        if (!Double.isFinite(timeSeconds)) throw new IllegalArgumentException("orbit time must be finite");
        if (position == velocity || position == acceleration || velocity == acceleration
                || position == hostPosition || position == hostVelocity || position == hostAcceleration
                || velocity == hostPosition || velocity == hostVelocity || velocity == hostAcceleration
                || acceleration == hostPosition || acceleration == hostVelocity || acceleration == hostAcceleration)
            throw new IllegalArgumentException("orbit input and output vectors must not alias");

        PlanetOrbitHold.orbitTrack(elements, mu, timeSeconds,
                relativePosition, relativeVelocity, relativeAcceleration);
        for (int axis = 0; axis < 3; axis++) {
            double targetPosition = hostPosition[axis] + relativePosition[axis];
            double targetVelocity = hostVelocity[axis] + relativeVelocity[axis];
            double targetAcceleration = hostAcceleration[axis] + relativeAcceleration[axis];
            if (!Double.isFinite(targetPosition) || !Double.isFinite(targetVelocity)
                    || !Double.isFinite(targetAcceleration))
                throw new IllegalStateException("orbit produced a non-finite target");
            position[axis] = targetPosition;
            velocity[axis] = targetVelocity;
            acceleration[axis] = targetAcceleration;
        }
    }

    private static void requireVector(double[] vector, String name) {
        if (vector == null || vector.length != 3)
            throw new IllegalArgumentException(name + " must be a non-null length-3 vector");
        for (double value : vector)
            if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must contain only finite values");
    }

    private static void requireOutput(double[] vector, String name) {
        if (vector == null || vector.length != 3)
            throw new IllegalArgumentException(name + " must be a non-null length-3 vector");
    }
}
