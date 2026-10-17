package net.bullettrain.xenopixelsmod.space.dragonball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DragonBallPlanetOrbitTest {
    @Test void hostMotionAndCircularOrbitAreCombinedInMinecraftWorldAxes() {
        double mu = 9_900_000.0;
        double radius = 260.0;
        double[] hostPosition = {100.0, 80.0, -40.0};
        double[] hostVelocity = {1.0, 2.0, 3.0};
        double[] hostAcceleration = {0.25, -0.5, 0.75};
        double[] position = new double[3], velocity = new double[3], acceleration = new double[3];
        var orbit = DragonBallPlanetOrbit.create(radius, 0.0, mu);

        double period = orbit.elements().periodSeconds();
        orbit.evaluate(hostPosition, hostVelocity, hostAcceleration, period / 4.0,
                position, velocity, acceleration);

        assertEquals(hostPosition[0], position[0], 1e-8);
        assertEquals(hostPosition[1], position[1], 1e-8);
        assertEquals(hostPosition[2] + radius, position[2], 1e-7);
        assertEquals(hostVelocity[0] - Math.sqrt(mu / radius), velocity[0], 1e-7);
        assertEquals(hostVelocity[1], velocity[1], 1e-8);
        assertEquals(hostVelocity[2], velocity[2], 1e-7);
        assertEquals(hostAcceleration[0], acceleration[0], 1e-8);
        assertEquals(hostAcceleration[1], acceleration[1], 1e-8);
        assertEquals(hostAcceleration[2] - mu / (radius * radius), acceleration[2], 1e-7);
        assertEquals(radius, Math.hypot(position[0] - hostPosition[0], position[2] - hostPosition[2]), 1e-7);
    }

    @Test void phaseOffsetsTheOrbitWithoutChangingItsRadius() {
        double mu = 4_000_000.0, radius = 400.0, phase = Math.PI / 2.0;
        double[] position = new double[3], velocity = new double[3], acceleration = new double[3];
        var orbit = DragonBallPlanetOrbit.create(radius, phase, mu);

        orbit.evaluate(new double[3], new double[3], new double[3], 0.0,
                position, velocity, acceleration);

        assertEquals(0.0, position[0], 1e-7);
        assertEquals(radius, position[2], 1e-7);
        assertEquals(radius, Math.hypot(position[0], position[2]), 1e-7);
    }

    @Test void explicitPeriodSupportsStaticHostsWithoutInventingTheirGravity() {
        double radius = 900.0, period = 7200.0;
        double[] position = new double[3], velocity = new double[3], acceleration = new double[3];
        var orbit = DragonBallPlanetOrbit.createWithPeriod(radius, 0.0, period);

        orbit.evaluate(new double[3], new double[3], new double[3], period / 4.0,
                position, velocity, acceleration);

        assertEquals(period, orbit.elements().periodSeconds(), 1.0e-8);
        assertEquals(0.0, position[0], 1.0e-7);
        assertEquals(radius, position[2], 1.0e-7);
        assertEquals(radius, Math.hypot(position[0], position[2]), 1.0e-7);
        assertEquals(0.0, velocity[1], 1.0e-9);
        assertEquals(-Math.pow(2.0 * Math.PI / period, 2.0) * radius, acceleration[2], 1.0e-9);
    }

    @Test void explicitPeriodRejectsNonFiniteOrNonPositiveValuesAndUnrepresentableTracks() {
        assertThrows(IllegalArgumentException.class, () -> DragonBallPlanetOrbit.createWithPeriod(900, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> DragonBallPlanetOrbit.createWithPeriod(900, 0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> DragonBallPlanetOrbit.createWithPeriod(900, 0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> DragonBallPlanetOrbit.createWithPeriod(1.0e6, 0, Double.MIN_VALUE));
    }

    @Test void rejectsInvalidOrbitInputsAndAliasedOutputBuffers() {
        assertThrows(IllegalArgumentException.class, () -> DragonBallPlanetOrbit.create(0.0, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> DragonBallPlanetOrbit.create(1.0, Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class, () -> DragonBallPlanetOrbit.create(1.0, 0.0, 0.0));
        var orbit = DragonBallPlanetOrbit.create(1.0, 0.0, 1.0);
        double[] shared = new double[3];
        assertThrows(IllegalArgumentException.class, () -> orbit.evaluate(
                new double[3], new double[3], new double[3], 0.0, shared, shared, new double[3]));
        assertThrows(IllegalArgumentException.class, () -> orbit.evaluate(
                new double[3], new double[3], new double[3], Double.NaN,
                new double[3], new double[3], new double[3]));
    }
}
