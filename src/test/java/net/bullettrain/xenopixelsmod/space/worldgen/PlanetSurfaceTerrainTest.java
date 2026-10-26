package net.bullettrain.xenopixelsmod.space.worldgen;

import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetField;
import net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanetSurfaceTerrainTest {
    @Test void everyReservedChartBodyCellHasAStableDistinctSeedIncludingSlotsBeyondProceduralLimit() {
        String[] bodyIds = {"xenopixelsmod:moon", "xenopixelsmod:phobos", "xenopixelsmod:deimos",
                "xenopixelsmod:io", "xenopixelsmod:europa", "xenopixelsmod:ganymede",
                "xenopixelsmod:callisto", "xenopixelsmod:triton", "xenopixelsmod:namek_moon_1",
                "xenopixelsmod:namek_moon_2", "xenopixelsmod:namek_moon_3"};
        long[] seeds = new long[bodyIds.length];
        for (int i = 0; i < bodyIds.length; i++) {
            var address = PlanetSurfaceCells.addressAt(PlanetSurfaceCells.forChartBody(bodyIds[i]));
            assertEquals(bodyIds[i], address.chartBodyId());
            seeds[i] = PlanetSurfaceTerrain.addressSeed(address, 0x1234_5678L);
            assertEquals(seeds[i], PlanetSurfaceTerrain.addressSeed(address, 0x1234_5678L));
        }
        for (int i = 0; i < seeds.length; i++) for (int j = i + 1; j < seeds.length; j++)
            assertNotEquals(seeds[i], seeds[j], bodyIds[i] + " and " + bodyIds[j]);
    }

    @Test void existingMoonCellKeepsItsLegacyTerrainSeedAcrossSaveUpgrades() {
        var moon = PlanetSurfaceCells.addressAt(PlanetSurfaceCells.moonSurfaceCell());
        for (long sample : new long[]{0L, 1L, 0x1234_5678L, -1L})
            assertEquals(PlanetSurfaceTerrain.cellSeed(0, 0, 0, sample),
                    PlanetSurfaceTerrain.addressSeed(moon, sample), "Moon terrain seed sample=" + sample);
    }

    @Test void sdbSurfaceSeedsDoNotCollideWithOriginSystemProceduralPlanetSlots() {
        long worldSeedSample = 0x1020_3040L;
        for (int slot = 0; slot < 7; slot++) {
            var address = PlanetSurfaceCells.addressAt(PlanetSurfaceCells.forDragonBall(slot));
            long seed = PlanetSurfaceTerrain.addressSeed(address, worldSeedSample);
            assertNotEquals(PlanetSurfaceTerrain.cellSeed(0, 0, slot, worldSeedSample), seed,
                    "SDB surface slot " + slot + " must not share the origin system's procedural identity");
            for (int other = slot + 1; other < 7; other++) {
                var otherAddress = PlanetSurfaceCells.addressAt(PlanetSurfaceCells.forDragonBall(other));
                assertNotEquals(seed, PlanetSurfaceTerrain.addressSeed(otherAddress, worldSeedSample),
                        "SDB surfaces must have distinct deterministic seeds");
            }
        }
    }

    @Test void profilesHaveSeededRepeatableAndDistinctLandforms() {
        int x = 12_345, z = -6_789;
        long seed = 0xBADC0FFEE0DDF00DL;
        double rock = PlanetSurfaceTerrain.height(SmallPlanetField.Profile.ROCK, seed, x, z, 0.2, -0.1, 0.35);
        assertEquals(rock, PlanetSurfaceTerrain.height(SmallPlanetField.Profile.ROCK, seed, x, z,
                0.2, -0.1, 0.35), 0.0);
        assertNotEquals(rock, PlanetSurfaceTerrain.height(SmallPlanetField.Profile.ICE, seed, x, z,
                0.2, -0.1, 0.35));
        assertNotEquals(rock, PlanetSurfaceTerrain.height(SmallPlanetField.Profile.ROCK, seed + 1, x, z,
                0.2, -0.1, 0.35));
    }

    @Test void generatedHeightsRemainInsideDimensionAndUseClampedNoiseInputs() {
        for (SmallPlanetField.Profile profile : SmallPlanetField.Profile.values()) {
            for (int x = -20_000; x <= 20_000; x += 1_337) {
                double height = PlanetSurfaceTerrain.height(profile, x * 31L, x, -x * 3,
                        100.0, -100.0, Double.NaN);
                assertTrue(PlanetSurfaceTerrain.MIN_SURFACE_Y <= height
                                && height <= PlanetSurfaceTerrain.MAX_SURFACE_Y,
                        profile + " height=" + height);
            }
        }
    }

    @Test void cavesCarveOnlyBelowTheSurfaceAndWithinTheBoundedCaveBand() {
        double surface = 96.0;
        assertTrue(PlanetSurfaceTerrain.density(surface, 95, 1.0) > 0.0);
        assertTrue(PlanetSurfaceTerrain.density(surface, 97, 1.0) < 0.0);
        assertTrue(PlanetSurfaceTerrain.density(surface, 80, 0.2) > 0.0);
        assertTrue(PlanetSurfaceTerrain.density(surface, 80, 0.9) < 0.0);
        assertTrue(PlanetSurfaceTerrain.density(surface, -64, 0.9) > 0.0);
    }

    @Test void reservedLunarProfileUsesKilometreScaleCraterReliefInsideDimensionHeight() {
        int[] radii = {50, 250, 500, 1_000, 2_500};
        for (int scale = 0; scale < radii.length; scale++)
            assertEquals(radii[scale], LunarTerrain.radius(scale));
        for (int i = -20_000; i <= 20_000; i += 97) {
            double height = PlanetSurfaceTerrain.height(SmallPlanetField.Profile.LUNAR, 987_654L,
                    i, i * 13, 0.2, -0.6, 0.35);
            assertTrue(height >= PlanetSurfaceTerrain.MIN_SURFACE_Y && height <= PlanetSurfaceTerrain.MAX_SURFACE_Y,
                    "lunar terrain leaves dimension height at " + i + ": " + height);
            assertEquals(LunarTerrain.height(987_654L, i, i * 13, 0.2, 0.35), height, 1.0e-9);
        }
    }

    @Test void craterReliefMatchesThePreCacheSeededTerrainAtCellBoundaries() {
        long seed = 123_456_789L;
        int[][] points = {{0, 0}, {127, 127}, {128, 128}, {-129, -129},
                {12_345, -6_789}, {8_191, 8_191}, {8_192, 8_192}, {-8_193, -8_193}};
        double[] expectedRock = {102.69201085943850, 98.58796403380063, 98.71703481355674,
                96.14252699730346, 104.60933137463984, 97.74984323822119, 97.54931016885400,
                97.82813105759341};
        double[] expectedIce = {96.99201085943852, 92.88796403380066, 93.01703481355676,
                90.44252699730349, 98.90933137463986, 92.04984323822121, 91.84931016885403,
                92.12813105759344};
        double[] expectedDesert = {107.83201085943851, 103.72796403380065, 103.85703481355675,
                101.28252699730348, 109.74933137463985, 102.88984323822120, 102.68931016885402,
                102.96813105759342};

        for (int i = 0; i < points.length; i++) {
            int x = points[i][0], z = points[i][1];
            assertEquals(expectedRock[i], PlanetSurfaceTerrain.height(SmallPlanetField.Profile.ROCK,
                    seed, x, z, 0.23, -0.41, 0.67), 1.0e-12, "rock at " + x + "," + z);
            assertEquals(expectedIce[i], PlanetSurfaceTerrain.height(SmallPlanetField.Profile.ICE,
                    seed, x, z, 0.23, -0.41, 0.67), 1.0e-12, "ice at " + x + "," + z);
            assertEquals(expectedDesert[i], PlanetSurfaceTerrain.height(SmallPlanetField.Profile.DESERT,
                    seed, x, z, 0.23, -0.41, 0.67), 1.0e-12, "desert at " + x + "," + z);
        }

        double otherSeed = PlanetSurfaceTerrain.height(SmallPlanetField.Profile.ROCK, seed + 1,
                0, 0, 0.23, -0.41, 0.67);
        PlanetSurfaceTerrain.height(SmallPlanetField.Profile.ROCK, seed, 12_345, -6_789,
                0.23, -0.41, 0.67);
        assertEquals(otherSeed, PlanetSurfaceTerrain.height(SmallPlanetField.Profile.ROCK, seed + 1,
                0, 0, 0.23, -0.41, 0.67), 0.0, "cache must distinguish world seeds");
    }
}
