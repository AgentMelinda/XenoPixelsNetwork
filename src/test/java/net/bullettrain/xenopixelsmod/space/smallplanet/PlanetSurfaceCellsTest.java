package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanetSurfaceCellsTest {
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
        assertEquals(moon, PlanetSurfaceCells.addressAtBlock(moon.centerBlockX(), moon.centerBlockZ())
                .orElseThrow().surfaceCell());
        assertEquals(3_756_032L, moon.centerBlockZ());
        assertTrue(moon.maxBlockZ() < 29_999_984L, "the reserved Moon cell must remain inside the vanilla border");
        assertThrows(IllegalArgumentException.class,
                () -> PlanetSurfaceCells.forPlanet(new UniverseField.Cell(0, 0), 0, SmallPlanetField.Profile.LUNAR));
        assertEquals(SmallPlanetField.Profile.ROCK,
                PlanetSurfaceCells.addressAt(new PlanetSurfaceCells.Cell(0, 0)).profile());
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
