package net.bullettrain.xenopixelsmod.space.universe;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InfiniteUniverseFieldTest {
    @Test void generationIsDeterministicAtCoordinatesBeyondThePhysicalBorder() {
        long[][] coordinates = {
                {229, -229}, {-1_000_000, 9_000_000},
                {1L << 50, -(1L << 51)},
                {Long.MIN_VALUE, Long.MAX_VALUE},
                {Long.MAX_VALUE, Long.MIN_VALUE}
        };
        Set<String> ids = new HashSet<>();
        int occupied = 0;
        for (long[] pair : coordinates) {
            var cell = new InfiniteUniverseField.Cell(pair[0], pair[1]);
            var first = InfiniteUniverseField.systemAt(123456789L, cell);
            var second = InfiniteUniverseField.systemAt(123456789L, cell);
            assertEquals(first, second, "same seed and full-width address must reproduce the same system");
            assertTrue(ids.add(cell.id()), "full coordinate pairs form stable unique IDs");
            if (first != null) {
                occupied++;
                assertEquals(cell, first.cell());
                assertTrue(first.localX() >= -UniverseField.JITTER * UniverseField.CELL_SIZE);
                assertTrue(first.localX() <= UniverseField.JITTER * UniverseField.CELL_SIZE);
                assertTrue(first.localZ() >= -UniverseField.JITTER * UniverseField.CELL_SIZE);
                assertTrue(first.localZ() <= UniverseField.JITTER * UniverseField.CELL_SIZE);
            }
        }
        assertTrue(occupied > 0, "sample includes generated systems outside the vanilla border");
    }

    @Test void existingBorderSafeCellsKeepTheirCurrentGeneratedResults() {
        long seed = -987654321L;
        for (int x = -UniverseField.MAX_CELL; x <= UniverseField.MAX_CELL; x += 37) {
            for (int z = -UniverseField.MAX_CELL; z <= UniverseField.MAX_CELL; z += 41) {
                var oldCell = new UniverseField.Cell(x, z);
                var oldSystem = UniverseField.systemAt(seed, oldCell);
                var newSystem = InfiniteUniverseField.systemAt(seed,
                        new InfiniteUniverseField.Cell((long) x, (long) z));
                if (oldSystem == null) {
                    assertNull(newSystem);
                } else {
                    assertNotNull(newSystem);
                    assertEquals(oldCell.id(), newSystem.cell().id());
                    assertEquals(oldSystem.seed(), newSystem.seed());
                    assertEquals(oldSystem.name(), newSystem.name());
                    assertEquals(oldSystem.starClass(), newSystem.starClass());
                    assertEquals(oldSystem.x() - (double) x * UniverseField.CELL_SIZE, newSystem.localX(), 1.0e-8);
                    assertEquals(oldSystem.z() - (double) z * UniverseField.CELL_SIZE, newSystem.localZ(), 1.0e-8);
                    assertEquals(oldSystem.planetSlots(), newSystem.planetSlots());
                }
            }
        }
    }

    @Test void homeAddressRemainsSolAndChangingWorldSeedChangesGeneratedSeed() {
        var home = InfiniteUniverseField.systemAt(41L, new InfiniteUniverseField.Cell(0, 0));
        assertNotNull(home);
        assertEquals("Sol", home.name());
        assertEquals(0, home.cell().x());
        assertEquals(0, home.cell().z());

        InfiniteUniverseField.StarSystem first = null;
        InfiniteUniverseField.StarSystem second = null;
        for (long coordinate = 229; coordinate < 10_000 && (first == null || second == null); coordinate++) {
            var address = new InfiniteUniverseField.Cell(coordinate, -coordinate);
            first = InfiniteUniverseField.systemAt(41L, address);
            second = InfiniteUniverseField.systemAt(42L, address);
        }
        assertNotNull(first, "find a deterministic occupied address for seed 41");
        assertNotNull(second, "find a deterministic occupied address for seed 42");
        assertNotEquals(first.seed(), second.seed());
    }

    @Test void logicalAddressNeverClaimsAPhysicalVoidseaCoordinate() {
        var cell = new InfiniteUniverseField.Cell(Long.MAX_VALUE, Long.MIN_VALUE);
        var system = InfiniteUniverseField.systemAt(7L, cell);
        assertTrue(cell.id().startsWith(Long.MAX_VALUE + ":" + Long.MIN_VALUE));
        if (system != null) {
            assertTrue(Math.abs(system.localX()) < UniverseField.CELL_SIZE);
            assertTrue(Math.abs(system.localZ()) < UniverseField.CELL_SIZE);
        }
    }

    @Test void logicalPageHasAWorkCapAndRejectsCoordinateWraparound() {
        var page = InfiniteUniverseField.page(99L, 10_000L, -20_000L, 64, 64);
        assertEquals(InfiniteUniverseField.MAX_PAGE_CELLS, page.queriedCells());
        assertEquals(page, InfiniteUniverseField.page(99L, 10_000L, -20_000L, 64, 64));
        assertTrue(page.systems().size() <= InfiniteUniverseField.MAX_PAGE_CELLS);
        for (var system : page.systems()) {
            assertTrue(system.cell().x() >= page.originX() && system.cell().x() < page.originX() + page.width());
            assertTrue(system.cell().z() >= page.originZ() && system.cell().z() < page.originZ() + page.height());
        }
        assertThrows(UnsupportedOperationException.class, () -> page.systems().clear());
        assertThrows(IllegalArgumentException.class,
                () -> InfiniteUniverseField.page(1L, 0, 0, InfiniteUniverseField.MAX_PAGE_CELLS + 1, 1));
        assertThrows(IllegalArgumentException.class, () -> InfiniteUniverseField.page(1L, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> InfiniteUniverseField.page(1L, Long.MAX_VALUE, 0, 2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> InfiniteUniverseField.page(1L, 0, Long.MAX_VALUE, 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new InfiniteUniverseField.Page(Long.MAX_VALUE, 0, 2, 1, java.util.List.of()));
        var outside = new InfiniteUniverseField.StarSystem(new InfiniteUniverseField.Cell(5, 5), 1L,
                "fixture", UniverseField.StarClass.PALE_SUN, 0, UniverseField.PLANE_Y, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new InfiniteUniverseField.Page(0, 0, 1, 1, java.util.List.of(outside)));
        var inside = new InfiniteUniverseField.StarSystem(new InfiniteUniverseField.Cell(0, 0), 1L,
                "fixture", UniverseField.StarClass.PALE_SUN, 0, UniverseField.PLANE_Y, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new InfiniteUniverseField.Page(0, 0, 1, 1, java.util.List.of(inside, inside)));
    }
}
