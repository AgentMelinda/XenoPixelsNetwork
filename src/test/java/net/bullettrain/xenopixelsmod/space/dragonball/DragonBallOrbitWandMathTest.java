package net.bullettrain.xenopixelsmod.space.dragonball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DragonBallOrbitWandMathTest {
    @Test
    void derivesACircularOrbitThatPassesThroughTheSelectedPositionAtCurrentTime() {
        double now = 123.5, dx = 640, dz = -480, mu = 2.4e8;
        var placement = DragonBallOrbitWandMath.fromOffset(dx, dz, now, mu, 0.0);
        var orbit = DragonBallPlanetOrbit.createWithPeriod(placement.radiusBlocks(), placement.phaseRadians(),
                placement.periodSeconds());
        double[] zero = new double[3], position = new double[3], velocity = new double[3], acceleration = new double[3];
        orbit.evaluate(zero, zero, zero, now, position, velocity, acceleration);
        assertEquals(dx, position[0], 1.0e-7);
        assertEquals(dz, position[2], 1.0e-7);
    }

    @Test
    void preservesAnExplicitPeriodWhenTheOrbitIsMoved() {
        var placement = DragonBallOrbitWandMath.fromOffset(800, 0, 10, 0, 900.0);
        assertEquals(900.0, placement.periodSeconds());
    }

    @Test
    void raySphereHitReturnsNearestForwardIntersection() {
        assertEquals(8.0, DragonBallOrbitWandMath.raySphereDistance(
                0, 0, 0, 1, 0, 0, 10, 0, 0, 2, 100), 1.0e-9);
    }

    @Test
    void raySphereRejectsMissesBehindTheOriginAndOutOfRangeHits() {
        assertEquals(-1, DragonBallOrbitWandMath.raySphereDistance(0, 0, 0, 1, 0, 0, 10, 4, 0, 2, 100));
        assertEquals(-1, DragonBallOrbitWandMath.raySphereDistance(0, 0, 0, 1, 0, 0, -10, 0, 0, 2, 100));
        assertEquals(-1, DragonBallOrbitWandMath.raySphereDistance(0, 0, 0, 1, 0, 0, 10, 0, 0, 2, 7));
    }
}
