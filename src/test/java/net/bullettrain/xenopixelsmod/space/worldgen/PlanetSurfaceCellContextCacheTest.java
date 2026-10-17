package net.bullettrain.xenopixelsmod.space.worldgen;

import net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanetSurfaceCellContextCacheTest {
    @Test void reusesCellContextAcrossPositiveAndNegativeCoordinates() {
        var cache = new PlanetSurfaceCellContextCache();
        var firstAddress = PlanetSurfaceCells.addressAtBlock(-1, 8_191).orElseThrow();
        assertTrue(cache.needsRefresh(-1, 8_191));
        cache.store(-1, 8_191, firstAddress, 123L);

        assertFalse(cache.needsRefresh(-8_192, 0));
        assertSame(firstAddress, cache.address());
        assertEquals(123L, cache.seed());
    }

    @Test void refreshesAtExactCellBoundariesAndReplacesSeed() {
        var cache = new PlanetSurfaceCellContextCache();
        var firstAddress = PlanetSurfaceCells.addressAtBlock(8_191, 0).orElseThrow();
        cache.store(8_191, 0, firstAddress, 123L);

        assertTrue(cache.needsRefresh(8_192, 0));
        var nextAddress = PlanetSurfaceCells.addressAtBlock(8_192, 0).orElseThrow();
        cache.store(8_192, 0, nextAddress, 456L);
        assertSame(nextAddress, cache.address());
        assertEquals(456L, cache.seed());
    }

    @Test void cachesAnOutOfRangeCellWithoutReusingThePriorAddress() {
        var cache = new PlanetSurfaceCellContextCache();
        var address = PlanetSurfaceCells.addressAtBlock(0, 0).orElseThrow();
        cache.store(0, 0, address, 789L);

        long outside = (long) (UniverseField.MAX_CELL * 10 + 10) * PlanetSurfaceCells.CELL_SIZE;
        assertTrue(cache.needsRefresh(outside, 0));
        cache.store(outside, 0, null, 0L);
        assertFalse(cache.needsRefresh(outside + 1, 1));
        assertNull(cache.address());
        assertEquals(0L, cache.seed());
    }
}
