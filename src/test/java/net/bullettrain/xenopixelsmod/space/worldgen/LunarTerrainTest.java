package net.bullettrain.xenopixelsmod.space.worldgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LunarTerrainTest {
    @Test void requiredMetreScalesAndProfiles() {
        int[] radii = {50, 250, 500, 1000, 2500};
        for (int i = 0; i < radii.length; i++) {
            assertEquals(radii[i], LunarTerrain.radius(i));
            assertTrue(LunarTerrain.craterProfile(0, radii[i], 0) < 0);
            assertTrue(LunarTerrain.craterProfile(1, radii[i], 0) > 0);
            assertEquals(0, LunarTerrain.craterProfile(1.6, radii[i], 0));
            assertTrue(LunarTerrain.craterProfile(0, radii[i], 1) > LunarTerrain.craterProfile(0, radii[i], 0));
            for (double edge : new double[]{1, 1.6}) {
                assertEquals(LunarTerrain.craterProfile(edge - 1e-8, radii[i], .3),
                        LunarTerrain.craterProfile(edge + 1e-8, radii[i], .3), 1e-4);
            }
        }
    }

    @Test void deterministicSeededBoundedAcrossNegativeCoordinates() {
        int different = 0;
        for (int x = -20000; x <= 20000; x += 101) {
            double h = LunarTerrain.height(1234, x, x * .71, .1, -.2);
            assertEquals(h, LunarTerrain.height(1234, x, x * .71, .1, -.2));
            assertTrue(Double.isFinite(h));
            assertTrue(h >= LunarTerrain.MIN_HEIGHT && h <= LunarTerrain.MAX_HEIGHT);
            if (h != LunarTerrain.height(999, x, x * .71, .1, -.2)) different++;
        }
        assertTrue(different > 100);
    }

    @Test void continuousAtSpatialCellAndChunkBoundaries() {
        for (int boundary : new int[]{-10000, -4000, -2000, -1000, -200, -16, 0, 16, 200, 1000, 2000, 4000, 10000}) {
            assertEquals(LunarTerrain.height(17, boundary - 1e-7, -327.0, 0, 0),
                    LunarTerrain.height(17, boundary + 1e-7, -327.0, 0, 0), 1e-4);
        }
    }

    @Test void extremeReliefAndOverlapsStillRespectArrivalClearance() {
        for (int i = -2000; i < 2000; i++) {
            double h = LunarTerrain.height(3, i * 317, i * -191, (i % 3) - 1, i % 2 == 0 ? 100 : -100);
            assertTrue(h >= LunarTerrain.MIN_HEIGHT && h <= LunarTerrain.MAX_HEIGHT);
            assertTrue(600 > h);
            assertTrue(675 > h);
        }
    }

    @Test void mareTransitionDoesNotSwitchCraterContributionAbruptly() {
        for (int cell = -20; cell <= 20; cell++) {
            long hash = LunarTerrain.cellHash(123, cell, -7, 4);
            double probability = LunarTerrain.unit(hash);
            if (probability <= .15 * .4 || probability >= .15) continue;
            double weight = (1 - probability / .15) / .6;
            double low = 0, high = 1;
            for (int i = 0; i < 60; i++) {
                double mid = (low + high) / 2;
                if (mid * mid * (3 - 2 * mid) < weight) low = mid; else high = mid;
            }
            double maria = (low + high) / 2 * .5 - .15;
            double x = (cell + LunarTerrain.unit(LunarTerrain.mix(hash + 1))) * 10000;
            double z = (-7 + LunarTerrain.unit(LunarTerrain.mix(hash + 2))) * 10000;
            assertEquals(LunarTerrain.height(123, x, z, maria - 1e-8, 0),
                    LunarTerrain.height(123, x, z, maria + 1e-8, 0), .001);
        }
    }
}
