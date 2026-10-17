package net.bullettrain.xenopixelsmod.space.asteroid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidFractureMathTest {
    @Test void slabWidthSeparatesAll18ConnectedNeighborStepsForUnitPlaneNormals() {
        assertEquals(0.5, AsteroidFractureMath.minimumHalfWidthFor18NeighborSeparation(1, 0, 0), 1e-12);
        assertEquals(Math.sqrt(3.0) / 2.0,
                AsteroidFractureMath.minimumHalfWidthFor18NeighborSeparation(1, 1, 1), 1e-12);
        var random = new java.util.SplittableRandom(41L);
        for (int sample = 0; sample < 2_000; sample++) {
            double nx = random.nextDouble(-1, 1), ny = random.nextDouble(-1, 1), nz = random.nextDouble(-1, 1);
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < 1e-9) continue;
            nx /= length; ny /= length; nz /= length;
            double halfWidth = AsteroidFractureMath.minimumHalfWidthFor18NeighborSeparation(nx, ny, nz);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                if (axes == 0 || axes > 2) continue;
                double projectedStep = Math.abs(dx * nx + dy * ny + dz * nz);
                assertTrue(projectedStep <= 2.0 * halfWidth + 1e-12,
                        "18-neighbor step " + dx + "," + dy + "," + dz + " crosses the slab");
            }
        }
    }

    @Test void impactTriggersOnlyWhenOneTickVelocityChangeStrictlyExceedsThreshold() {
        assertFalse(AsteroidFractureMath.impactExceedsThreshold(1.0, 2.0, 3.0, 4.0, 6.0, 3.0, 5.0),
                "a delta exactly equal to the configured threshold must not fracture");
        assertTrue(AsteroidFractureMath.impactExceedsThreshold(1.0, 2.0, 3.0, 4.0, 6.0, 3.0, 4.99));
    }

    @Test void nonFiniteVelocityOrInvalidThresholdNeverTriggersFracture() {
        assertFalse(AsteroidFractureMath.impactExceedsThreshold(Double.NaN, 0.0, 0.0, 2.0, 0.0, 0.0, 1.0));
        assertFalse(AsteroidFractureMath.impactExceedsThreshold(0.0, 0.0, 0.0, 2.0, 0.0, 0.0, -1.0));
        assertFalse(AsteroidFractureMath.impactExceedsThreshold(0.0, 0.0, 0.0, 2.0, 0.0, 0.0, Double.NaN));
    }

    @Test void seededPlaneIsRepeatableAndCutsThroughBothImpactPointAndBodyCenter() {
        var first = AsteroidFractureMath.seededPlane(17L, 8.0, -2.0, 4.0, 15.0, 1.0, 0.0, 0.0);
        var repeat = AsteroidFractureMath.seededPlane(17L, 8.0, -2.0, 4.0, 15.0, 1.0, 0.0, 0.0);
        assertEquals(first.normalX(), repeat.normalX(), 1e-12);
        assertEquals(first.normalY(), repeat.normalY(), 1e-12);
        assertEquals(first.normalZ(), repeat.normalZ(), 1e-12);
        double nx = first.normalX(), ny = first.normalY(), nz = first.normalZ();
        assertEquals(1.0, Math.sqrt(nx * nx + ny * ny + nz * nz), 1e-12);
        double px = first.impactX(), py = first.impactY(), pz = first.impactZ();
        assertEquals(0.0, (px - 8.0) * nx + (py + 2.0) * ny + (pz - 4.0) * nz, 1e-10,
                "the plane must contain both the impact point and the sphere center");
        assertTrue(first.withinSlab(8.0, -2.0, 4.0, 0.5));
        assertTrue(first.withinSlab(px, py, pz, 0.5));
        assertFalse(first.withinSlab(8.0 + nx * 0.51, -2.0 + ny * 0.51, 4.0 + nz * 0.51, 0.5));
    }

    @Test void seededPlaneRejectsDegenerateImpactVectorsAndRadius() {
        assertNull(AsteroidFractureMath.seededPlane(17L, 0.0, 0.0, 0.0, 15.0, 0.0, 0.0, 0.0));
        assertNull(AsteroidFractureMath.seededPlane(17L, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0));
        assertNull(AsteroidFractureMath.seededPlane(17L, 0.0, 0.0, 0.0, 15.0, Double.NaN, 0.0, 0.0));
    }
}
