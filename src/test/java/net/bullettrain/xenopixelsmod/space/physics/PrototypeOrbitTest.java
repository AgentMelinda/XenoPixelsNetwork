package net.bullettrain.xenopixelsmod.space.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrototypeOrbitTest {
    @Test void circularSeedMatchesCentralAttraction() {
        assertEquals(2 * 2 / 32.0, 32 * PrototypeOrbit.accelerationFactor(32, 0, 0), 1e-12);
    }
    @Test void softenedCenterAndInvalidInputsStayFinite() {
        assertTrue(Double.isFinite(PrototypeOrbit.accelerationFactor(0, 0, 0)));
        assertEquals(0, PrototypeOrbit.accelerationFactor(Double.NaN, 0, 0));
        assertEquals(0, PrototypeOrbit.accelerationFactor(Double.MAX_VALUE, 0, 0));
        assertEquals(0, PrototypeOrbit.step(Double.NaN));
        assertEquals(0, PrototypeOrbit.step(-1));
        assertEquals(0.1, PrototypeOrbit.step(10));
    }
    @Test void collisionPrototypeIsBoundedSphericalVoxelSet() {
        int count = 0;
        for (int x=-4;x<=4;x++) for(int y=-4;y<=4;y++) for(int z=-4;z<=4;z++)
            if(PrototypeOrbit.containsVoxel(x,y,z)) count++;
        assertEquals(123,count);
        assertFalse(PrototypeOrbit.containsVoxel(3,3,3));
        assertTrue(PrototypeOrbit.containsVoxel(3,0,0));
        assertFalse(PrototypeOrbit.containsVoxel(Integer.MIN_VALUE,0,0));
    }

    /**
     * The three-argument form is the experiment's original 123-voxel body, so the size knob cannot quietly
     * rename it: every radius the spike may assemble has to agree with the r-specific form.
     */
    @Test void theDefaultRadiusIsTheThreeVoxelBody() {
        for (int x = -4; x <= 4; x++) for (int y = -4; y <= 4; y++) for (int z = -4; z <= 4; z++)
            assertEquals(PrototypeOrbit.containsVoxel(x, y, z), PrototypeOrbit.containsVoxel(x, y, z, 3));
    }

    /**
     * Growing the radius grows the hull as r³, and the box test has to stay exactly as wide as the sphere
     * is allowed to reach — otherwise the assembly loop would skip surface voxels it should have placed.
     */
    @Test void aBiggerRadiusIsABiggerHullWithTheSameShapeRule() {
        // Lattice points inside the sphere: the count the assembly places, and the reason the config
        // ceiling exists — 123 at 3 becomes 2109 at 8, on the server thread.
        assertEquals(123, voxels(3));
        assertEquals(257, voxels(4));
        assertEquals(2109, voxels(8));
        for (int radius = 1; radius <= 8; radius++) {
            for (int axis = 0; axis < 3; axis++) {
                assertTrue(PrototypeOrbit.containsVoxel(axis == 0 ? radius : 0, axis == 1 ? radius : 0,
                        axis == 2 ? radius : 0, radius), "the pole voxel belongs to the hull at r=" + radius);
                assertFalse(PrototypeOrbit.containsVoxel(axis == 0 ? radius + 1 : 0,
                        axis == 1 ? radius + 1 : 0, axis == 2 ? radius + 1 : 0, radius));
            }
            // The corner is outside the sphere but inside the box the scan walks: that is the stair-step.
            assertFalse(PrototypeOrbit.containsVoxel(radius, radius, radius, radius));
        }
    }

    /** A radius below one is no body at all, and a hostile coordinate must not wrap into the sphere. */
    @Test void degenerateRadiiAndOverflowingCoordinatesAreEmpty() {
        assertEquals(0, voxels(0));
        assertEquals(0, voxels(-5));
        assertFalse(PrototypeOrbit.containsVoxel(Integer.MIN_VALUE, 0, 0, 8));
        assertFalse(PrototypeOrbit.containsVoxel(Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 16));
        assertTrue(PrototypeOrbit.containsVoxel(0, 0, 0, 1));
    }

    private static int voxels(int radius) {
        int count = 0;
        for (int x = -radius - 1; x <= radius + 1; x++)
            for (int y = -radius - 1; y <= radius + 1; y++)
                for (int z = -radius - 1; z <= radius + 1; z++)
                    if (PrototypeOrbit.containsVoxel(x, y, z, radius)) count++;
        return count;
    }
}
