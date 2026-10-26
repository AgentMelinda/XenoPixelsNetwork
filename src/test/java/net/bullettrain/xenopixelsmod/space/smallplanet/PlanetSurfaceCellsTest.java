package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallIdentity;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanetSurfaceCellsTest {
    @Test void firstFourSlotsKeepTheirExistingCellCoordinates() {
        var system = new UniverseField.Cell(17, -23);
        int cellsPerSystemX = 10;
        int cellsPerSystemZ = 2;
        for (int slot = 0; slot < 4; slot++) {
            int expectedX = system.x() * cellsPerSystemX + (slot & 1);
            int expectedZ = system.z() * cellsPerSystemZ + (slot >>> 1);
            assertEquals(new PlanetSurfaceCells.Cell(expectedX, expectedZ),
                    PlanetSurfaceCells.forPlanet(system, slot, SmallPlanetField.Profile.ROCK));
        }
    }

    @Test void newSlotAddressHasAGoldenCoordinate() {
        assertEquals(new PlanetSurfaceCells.Cell(2_470, 113), PlanetSurfaceCells.forPlanet(
                new UniverseField.Cell(17, -23), 4, SmallPlanetField.Profile.ROCK));
    }

    @Test void extraStripeRejectsUnusedTailAndRowsOutsideItsRange() {
        assertTrue(PlanetSurfaceCells.addressAtBlock(2_902L * PlanetSurfaceCells.CELL_SIZE,
                1_522L * PlanetSurfaceCells.CELL_SIZE).isEmpty());
        assertTrue(PlanetSurfaceCells.addressAtBlock(2_290L * PlanetSurfaceCells.CELL_SIZE,
                1_523L * PlanetSurfaceCells.CELL_SIZE).isEmpty());
        assertTrue(PlanetSurfaceCells.addressAtBlock(2_290L * PlanetSurfaceCells.CELL_SIZE,
                -1_523L * PlanetSurfaceCells.CELL_SIZE).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new PlanetSurfaceCells.Cell(2_902, 1_522));
        assertThrows(IllegalArgumentException.class, () -> new PlanetSurfaceCells.Cell(2_290, 1_523));
        assertThrows(IllegalArgumentException.class, () -> new PlanetSurfaceCells.Cell(2_290, -1_523));
    }

    @Test void allEightSlotsAndProfilesUseDistinctCellsWithinSampledSystems() {
        var profiles = proceduralProfiles();
        var systems = new UniverseField.Cell[]{new UniverseField.Cell(-228, 228), new UniverseField.Cell(0, 0),
                new UniverseField.Cell(228, -228)};
        for (var system : systems) {
            var cells = new java.util.HashSet<PlanetSurfaceCells.Cell>();
            for (int slot = 0; slot < SmallPlanetField.MAX_PLANETS_PER_SYSTEM; slot++)
                for (var profile : profiles)
                    assertTrue(cells.add(PlanetSurfaceCells.forPlanet(system, slot, profile)),
                            "surface address collision at " + system + " slot=" + slot + " profile=" + profile);
            assertEquals(8 * profiles.length, cells.size());
        }
    }

    @Test void everyUniverseSystemSlotAndProfileRoundTrips() {
        var profiles = proceduralProfiles();
        for (int systemX = -UniverseField.MAX_CELL; systemX <= UniverseField.MAX_CELL; systemX++) {
            for (int systemZ = -UniverseField.MAX_CELL; systemZ <= UniverseField.MAX_CELL; systemZ++) {
                var system = new UniverseField.Cell(systemX, systemZ);
                for (int slot = 0; slot < SmallPlanetField.MAX_PLANETS_PER_SYSTEM; slot++) {
                    for (var profile : profiles) {
                        var surface = PlanetSurfaceCells.forPlanet(system, slot, profile);
                        var address = PlanetSurfaceCells.addressAt(surface);
                        assertEquals(system, address.systemCell());
                        assertEquals(slot, address.planetSlot());
                        assertEquals(profile, address.profile());
                    }
                }
            }
        }
    }

    @Test void cellsStayInsideMinecraftWorldBorder() {
        long border = 29_999_984L;
        for (int systemCoordinate : new int[]{-UniverseField.MAX_CELL, 0, UniverseField.MAX_CELL}) {
            for (int slot = 0; slot < SmallPlanetField.MAX_PLANETS_PER_SYSTEM; slot++) {
                for (var profile : proceduralProfiles()) {
                    var cell = PlanetSurfaceCells.forPlanet(
                            new UniverseField.Cell(systemCoordinate, systemCoordinate), slot, profile);
                    assertTrue(cell.minBlockX() >= -border);
                    assertTrue(cell.maxBlockX() <= border);
                    assertTrue(cell.minBlockZ() >= -border);
                    assertTrue(cell.maxBlockZ() <= border);
                }
            }
        }
    }

    @Test void everyProfileLaneAndSlotIsRecoverableFromItsCellCenter() {
        var systems = new UniverseField.Cell[]{
                new UniverseField.Cell(-UniverseField.MAX_CELL, UniverseField.MAX_CELL),
                new UniverseField.Cell(-1, -1), new UniverseField.Cell(0, 0),
                new UniverseField.Cell(UniverseField.MAX_CELL, -UniverseField.MAX_CELL)
        };
        for (var system : systems) for (int slot = 0; slot < SmallPlanetField.MAX_PLANETS_PER_SYSTEM; slot++)
            for (var profile : proceduralProfiles()) {
                var cell = PlanetSurfaceCells.forPlanet(system, slot, profile);
                var resolved = PlanetSurfaceCells.addressAtBlock(cell.centerBlockX(), cell.centerBlockZ()).orElseThrow();
                assertEquals(system, resolved.systemCell());
                assertEquals(slot, resolved.planetSlot());
                assertEquals(profile, resolved.profile());
            }
    }

    @Test void blockLookupUsesFloorDivisionAcrossNegativeEdges() {
        int[][] positions = {
                {-8_193, -1}, {-8_192, 0}, {-1, -8_193}, {0, 0},
                {8_191, 8_191}, {8_192, 8_192}, {-18_022_400, -3_604_480},
                {18_104_319, 3_620_351}
        };
        for (int[] position : positions) {
            var actual = PlanetSurfaceCells.addressAtBlock(position[0], position[1]).orElseThrow();
            var expectedCell = new PlanetSurfaceCells.Cell(
                    (int) Math.floorDiv((long) position[0], PlanetSurfaceCells.CELL_SIZE),
                    (int) Math.floorDiv((long) position[1], PlanetSurfaceCells.CELL_SIZE));
            assertEquals(expectedCell, actual.surfaceCell());
            assertTrue(position[0] >= actual.surfaceCell().minBlockX());
            assertTrue(position[0] <= actual.surfaceCell().maxBlockX());
            assertTrue(position[1] >= actual.surfaceCell().minBlockZ());
            assertTrue(position[1] <= actual.surfaceCell().maxBlockZ());
        }
    }

    @Test void chartedMoonUsesAReservedLunarCellWithoutMovingExistingCells() {
        var moon = PlanetSurfaceCells.moonSurfaceCell();
        var address = PlanetSurfaceCells.addressAt(moon);
        assertEquals(SmallPlanetField.Profile.LUNAR, address.profile());
        assertEquals("xenopixelsmod:moon", address.chartBodyId());
        assertEquals(moon, PlanetSurfaceCells.addressAtBlock(moon.centerBlockX(), moon.centerBlockZ())
                .orElseThrow().surfaceCell());
        assertEquals(3_756_032L, moon.centerBlockZ());
        assertTrue(moon.maxBlockZ() < 29_999_984L, "the reserved Moon cell must remain inside the vanilla border");
        assertThrows(IllegalArgumentException.class,
                () -> PlanetSurfaceCells.forPlanet(new UniverseField.Cell(0, 0), 0, SmallPlanetField.Profile.LUNAR));
        assertEquals(SmallPlanetField.Profile.ROCK,
                PlanetSurfaceCells.addressAt(new PlanetSurfaceCells.Cell(0, 0)).profile());
    }

    @Test void chartedMoonCellsHaveStableUniqueProfilesAndDoNotAliasOtherSurfaceOwners() {
        var bodies = java.util.Map.of(
                "xenopixelsmod:phobos", SmallPlanetField.Profile.LUNAR,
                "xenopixelsmod:deimos", SmallPlanetField.Profile.LUNAR,
                "xenopixelsmod:io", SmallPlanetField.Profile.LAVA,
                "xenopixelsmod:europa", SmallPlanetField.Profile.ICE,
                "xenopixelsmod:ganymede", SmallPlanetField.Profile.ICE,
                "xenopixelsmod:callisto", SmallPlanetField.Profile.ICE,
                "xenopixelsmod:triton", SmallPlanetField.Profile.ICE,
                "xenopixelsmod:namek_moon_1", SmallPlanetField.Profile.ROCK,
                "xenopixelsmod:namek_moon_2", SmallPlanetField.Profile.ROCK,
                "xenopixelsmod:namek_moon_3", SmallPlanetField.Profile.ROCK);
        var cells = new java.util.HashSet<PlanetSurfaceCells.Cell>();
        cells.add(PlanetSurfaceCells.moonSurfaceCell());
        for (var entry : bodies.entrySet()) {
            var cell = PlanetSurfaceCells.forChartBody(entry.getKey());
            assertTrue(cells.add(cell), "duplicate chart surface cell " + entry.getKey());
            assertEquals(entry.getKey(), PlanetSurfaceCells.chartBodyIdAt(cell).orElseThrow());
            var address = PlanetSurfaceCells.addressAtBlock(cell.centerBlockX(), cell.centerBlockZ()).orElseThrow();
            assertEquals(entry.getKey(), address.chartBodyId());
            assertEquals(entry.getValue(), address.profile());
            assertTrue(cell.minBlockX() >= -29_999_984L && cell.maxBlockX() <= 29_999_984L);
            assertTrue(cell.minBlockZ() >= -29_999_984L && cell.maxBlockZ() <= 29_999_984L);
            assertFalse(cells.contains(PlanetSurfaceCells.forDragonBall(0)));
            assertFalse(cells.contains(PlanetSurfaceCells.forPlanet(new UniverseField.Cell(0, 0), 0,
                    SmallPlanetField.Profile.ROCK)));
        }
        assertTrue(PlanetSurfaceCells.chartBodyIdAt(PlanetSurfaceCells.forDragonBall(0)).isEmpty());
        assertTrue(PlanetSurfaceCells.chartBodyIdAt(PlanetSurfaceCells.forPlanet(new UniverseField.Cell(0, 0), 0,
                SmallPlanetField.Profile.ROCK)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> PlanetSurfaceCells.forChartBody("xenopixelsmod:pluto"));
        assertEquals(11, cells.size());
    }

    @Test void sevenSuperDragonBallSurfacesHaveReservedDeterministicCells() {
        var cells = new java.util.HashSet<PlanetSurfaceCells.Cell>();
        for (int slot = 0; slot < 7; slot++) {
            var cell = PlanetSurfaceCells.forDragonBall(slot);
            assertTrue(cells.add(cell), "duplicate Super Dragon Ball surface cell for slot " + slot);
            assertEquals(slot, PlanetSurfaceCells.dragonBallSlotAt(cell).orElseThrow());
            assertEquals(cell, PlanetSurfaceCells.forDragonBallKey(
                    DragonBallIdentity.forSlot(slot + 1).surfaceCellKey()));
            var address = PlanetSurfaceCells.addressAtBlock(cell.centerBlockX(), cell.centerBlockZ()).orElseThrow();
            assertEquals(cell, address.surfaceCell());
            assertEquals(slot, address.planetSlot());
            assertEquals(SmallPlanetField.Profile.ROCK, address.profile());
            assertTrue(cell.minBlockX() >= -29_999_984L && cell.maxBlockX() <= 29_999_984L);
            assertTrue(cell.minBlockZ() >= -29_999_984L && cell.maxBlockZ() <= 29_999_984L);
        }
        assertEquals(7, cells.size());
        assertFalse(cells.contains(PlanetSurfaceCells.moonSurfaceCell()));
        for (var profile : proceduralProfiles())
            for (int slot = 0; slot < SmallPlanetField.MAX_PLANETS_PER_SYSTEM; slot++)
                assertFalse(cells.contains(PlanetSurfaceCells.forPlanet(new UniverseField.Cell(0, 0), slot, profile)));
        assertThrows(IllegalArgumentException.class,
                () -> PlanetSurfaceCells.forDragonBallKey("xenopixelsmod:planet_surfaces/sdb/slot-0"));
        assertThrows(IllegalArgumentException.class,
                () -> PlanetSurfaceCells.forDragonBallKey("xenopixelsmod:planet_surfaces/sdb/slot-8"));
        assertThrows(IllegalArgumentException.class, () -> PlanetSurfaceCells.forDragonBall(-1));
        assertThrows(IllegalArgumentException.class, () -> PlanetSurfaceCells.forDragonBall(7));
    }

    @Test void rejectsInvalidSlotsAndCellsOutsideUniverse() {
        assertThrows(IllegalArgumentException.class,
                () -> PlanetSurfaceCells.forPlanet(new UniverseField.Cell(0, 0), -1, SmallPlanetField.Profile.ROCK));
        assertThrows(IllegalArgumentException.class,
                () -> PlanetSurfaceCells.forPlanet(new UniverseField.Cell(0, 0), SmallPlanetField.MAX_PLANETS_PER_SYSTEM,
                        SmallPlanetField.Profile.ROCK));
        assertThrows(NullPointerException.class,
                () -> PlanetSurfaceCells.forPlanet(new UniverseField.Cell(0, 0), 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PlanetSurfaceCells.Cell(Integer.MAX_VALUE, 0));
        assertTrue(PlanetSurfaceCells.addressAtBlock(Long.MAX_VALUE, 0).isEmpty());
    }

    private static SmallPlanetField.Profile[] proceduralProfiles() {
        return java.util.Arrays.stream(SmallPlanetField.Profile.values())
                .filter(profile -> profile.weight > 0).toArray(SmallPlanetField.Profile[]::new);
    }
}
