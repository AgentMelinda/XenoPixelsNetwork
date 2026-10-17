package net.bullettrain.xenopixelsmod.space;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The spatial index that replaces area queries over the level's entity sections.
 *
 * <p>The cases here are the ones that break quietly in production rather than loudly in a test:
 * negative coordinates (floor vs truncating division), a member sitting in a cell the query circle only
 * clips, and an id that moves between cells without being re-added. Each of those produces a plausible
 * looking index that simply misses entities near an origin, near a cell edge, or in flight.
 */
class SpaceSpatialIndexTest {

    private static final String VOIDSEA = "xenopixelsmod:voidsea";

    private static UUID id() {
        return UUID.randomUUID();
    }

    @Test void cellKeyRoundTripsBothHalvesIncludingNegatives() {
        for (int x : new int[]{0, 1, -1, 12345, -12345, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            for (int z : new int[]{0, 1, -1, 6789, -6789}) {
                long key = (long) x << 32 ^ (z & 0xFFFFFFFFL);
                assertEquals(x, SpaceSpatialIndex.cellX(key), "x half must round trip");
                assertEquals(z, SpaceSpatialIndex.cellZ(key), "z half must round trip");
            }
        }
    }

    @Test void aQueryFindsExactlyWhatIsInsideTheRadius() {
        SpaceSpatialIndex index = new SpaceSpatialIndex();
        UUID near = id(), far = id();
        index.put(near, VOIDSEA, 10.0, 10.0);
        index.put(far, VOIDSEA, 5_000.0, 5_000.0);

        List<UUID> out = new ArrayList<>();
        assertEquals(1, index.query(VOIDSEA, 0.0, 0.0, 100.0, out, 0));
        assertEquals(near, out.get(0));

        assertEquals(2, index.query(VOIDSEA, 0.0, 0.0, 10_000.0, out, 0));
        assertEquals(0, index.query(VOIDSEA, 0.0, 0.0, 1.0, out, 0), "nothing is within one block of the origin");
    }

    /**
     * The exact-distance check. Cells are square and the query is round, so a member in a cell the
     * circle clips must still be rejected — otherwise every radius query silently returns everything in
     * the bounding box, which is the bug a coarse-only index has.
     */
    @Test void aMemberInAClippedCellButOutsideTheRadiusIsRejected() {
        SpaceSpatialIndex index = new SpaceSpatialIndex(16);
        UUID diagonal = id();
        // 12,12 shares the 16-block cell with the origin's neighbourhood but sits ~16.97 blocks away.
        index.put(diagonal, VOIDSEA, 12.0, 12.0);

        List<UUID> out = new ArrayList<>();
        assertEquals(0, index.query(VOIDSEA, 0.0, 0.0, 15.0, out, 0),
                "16.97 blocks away must not match a 15-block radius even though the cell is touched");
        assertEquals(1, index.query(VOIDSEA, 0.0, 0.0, 17.0, out, 0),
                "and must match once the radius actually reaches it");
    }

    @Test void negativeCoordinatesIndexIntoNegativeCellsNotTowardZero() {
        SpaceSpatialIndex index = new SpaceSpatialIndex(16);
        UUID west = id(), east = id();
        index.put(west, VOIDSEA, -100.0, -100.0);
        index.put(east, VOIDSEA, 100.0, 100.0);

        List<UUID> out = new ArrayList<>();
        assertEquals(1, index.query(VOIDSEA, -100.0, -100.0, 10.0, out, 0));
        assertEquals(west, out.get(0));
        assertEquals(1, index.query(VOIDSEA, 100.0, 100.0, 10.0, out, 0));
        assertEquals(east, out.get(0));

        // west sits exactly on the query point, so even a 1-block radius finds it and nothing else.
        assertEquals(1, index.query(VOIDSEA, -100.0, -100.0, 1.0, out, 0));
        assertEquals(west, out.get(0));

        // The real floor-vs-truncate check: a negative coordinate must land in a negative cell. A
        // truncating cellOf would file both of these near cell zero and put them in the same cell.
        Long westCell = index.cellOf(west);
        Long eastCell = index.cellOf(east);
        assertNotNull(westCell);
        assertNotNull(eastCell);
        assertTrue(SpaceSpatialIndex.cellX(westCell) < 0,
                "x=-100 must index into a negative cell, got " + SpaceSpatialIndex.cellX(westCell));
        assertTrue(SpaceSpatialIndex.cellZ(westCell) < 0,
                "z=-100 must index into a negative cell, got " + SpaceSpatialIndex.cellZ(westCell));
        assertTrue(SpaceSpatialIndex.cellX(eastCell) > 0);
        assertNotEquals(westCell, eastCell, "the two must not collide in one cell");
        // floor(-100/16) = -7 and floor(100/16) = 6.
        assertEquals(-7, SpaceSpatialIndex.cellX(westCell));
        assertEquals(6, SpaceSpatialIndex.cellX(eastCell));
    }

    @Test void movingWithinACellUpdatesInPlaceWithoutDuplicating() {
        SpaceSpatialIndex index = new SpaceSpatialIndex(16);
        UUID ship = id();
        for (int step = 0; step < 10; step++) {
            index.put(ship, VOIDSEA, 1.0 + step * 0.5, 1.0);
        }
        assertEquals(1, index.totalIndexed(), "one ship must stay one entry");
        assertEquals(1, index.cellCountIn(VOIDSEA), "and must not leave stale cells behind");

        List<UUID> out = new ArrayList<>();
        // It ended at x=5.5, so a query centred on the start must now miss it.
        assertEquals(0, index.query(VOIDSEA, 1.0, 1.0, 2.0, out, 0),
                "the stored position must move with the entity, not stay at first insert");
        assertEquals(1, index.query(VOIDSEA, 5.5, 1.0, 2.0, out, 0));
    }

    @Test void movingAcrossCellsLeavesNoStaleMembership() {
        SpaceSpatialIndex index = new SpaceSpatialIndex(16);
        UUID ship = id();
        index.put(ship, VOIDSEA, 0.0, 0.0);
        Long firstCell = index.cellOf(ship);
        assertNotNull(firstCell);

        index.put(ship, VOIDSEA, 500.0, 500.0);
        assertEquals(1, index.totalIndexed());
        assertEquals(1, index.cellCountIn(VOIDSEA), "the old cell must have been pruned");
        assertNotEquals(firstCell, index.cellOf(ship));

        List<UUID> out = new ArrayList<>();
        assertEquals(0, index.query(VOIDSEA, 0.0, 0.0, 50.0, out, 0), "it must not still be at the origin");
        assertEquals(1, index.query(VOIDSEA, 500.0, 500.0, 50.0, out, 0));
    }

    @Test void changingDimensionMovesTheEntryCleanly() {
        SpaceSpatialIndex index = new SpaceSpatialIndex();
        UUID ship = id();
        index.put(ship, VOIDSEA, 0.0, 0.0);
        index.put(ship, "minecraft:overworld", 0.0, 0.0);

        assertEquals(1, index.totalIndexed());
        assertEquals(0, index.countIn(VOIDSEA), "the old dimension must be emptied");
        assertEquals(1, index.countIn("minecraft:overworld"));
        assertEquals(0, index.cellCountIn(VOIDSEA), "and its cell pruned, not left empty in the map");
    }

    @Test void dimensionsAreIsolatedFromEachOther() {
        SpaceSpatialIndex index = new SpaceSpatialIndex();
        UUID here = id(), there = id();
        index.put(here, VOIDSEA, 0.0, 0.0);
        index.put(there, "minecraft:overworld", 0.0, 0.0);

        List<UUID> out = new ArrayList<>();
        assertEquals(1, index.query(VOIDSEA, 0.0, 0.0, 1_000.0, out, 0));
        assertEquals(here, out.get(0), "a query must not reach into another dimension at the same coords");
    }

    @Test void removePrunesCellsAndDimensionsAndIsIdempotent() {
        SpaceSpatialIndex index = new SpaceSpatialIndex();
        UUID only = id();
        index.put(only, VOIDSEA, 0.0, 0.0);
        assertEquals(1, index.cellCountIn(VOIDSEA));

        index.remove(only);
        assertFalse(index.contains(only));
        assertEquals(0, index.totalIndexed());
        assertEquals(0, index.cellCountIn(VOIDSEA), "an emptied cell must be pruned");
        assertEquals(0, index.countIn(VOIDSEA));

        // Unload and death paths both call this, so a second call must be harmless.
        index.remove(only);
        index.remove(null);
        assertEquals(0, index.totalIndexed());
    }

    @Test void clearDimensionDropsItsMembersButLeavesOthersAlone() {
        SpaceSpatialIndex index = new SpaceSpatialIndex();
        UUID a = id(), b = id();
        index.put(a, VOIDSEA, 0.0, 0.0);
        index.put(b, "minecraft:overworld", 0.0, 0.0);

        index.clearDimension(VOIDSEA);
        assertFalse(index.contains(a));
        assertTrue(index.contains(b), "another dimension must be untouched");
        assertEquals(1, index.totalIndexed());
        index.clearDimension(null);
        index.clearDimension("never_indexed");
        assertEquals(1, index.totalIndexed());
    }

    @Test void aLimitCapsTheResultWithoutThrowing() {
        SpaceSpatialIndex index = new SpaceSpatialIndex();
        for (int i = 0; i < 50; i++) index.put(id(), VOIDSEA, i * 0.1, 0.0);
        List<UUID> out = new ArrayList<>();
        assertEquals(7, index.query(VOIDSEA, 0.0, 0.0, 100.0, out, 7));
        assertEquals(50, index.query(VOIDSEA, 0.0, 0.0, 100.0, out, 0), "zero means uncapped");
    }

    @Test void degenerateQueriesReturnEmptyRatherThanThrowing() {
        SpaceSpatialIndex index = new SpaceSpatialIndex();
        index.put(id(), VOIDSEA, 0.0, 0.0);
        List<UUID> out = new ArrayList<>();
        assertEquals(0, index.query(null, 0.0, 0.0, 100.0, out, 0));
        assertEquals(0, index.query("unindexed", 0.0, 0.0, 100.0, out, 0));
        assertEquals(0, index.query(VOIDSEA, 0.0, 0.0, -1.0, out, 0));
        assertEquals(0, index.query(VOIDSEA, 0.0, 0.0, Double.NaN, out, 0));
        assertNull(index.cellOf(null));
        index.put(null, VOIDSEA, 0.0, 0.0);
        index.put(id(), null, 0.0, 0.0);
        assertEquals(1, index.totalIndexed(), "a null id or dimension must be ignored, not indexed");
    }

    /**
     * §13's Q21 asks for under 5% tick delta at 200 active pods. The write path is what runs per pod per
     * tick, so that is what gets measured here — the query path is exercised by every test above.
     */
    @Test void updatingTwoHundredMovingPodsIsCheapEnoughForEveryTick() {
        SpaceSpatialIndex index = new SpaceSpatialIndex();
        int pods = 200;
        UUID[] ids = new UUID[pods];
        for (int i = 0; i < pods; i++) {
            ids[i] = id();
            index.put(ids[i], VOIDSEA, i * 64.0, 0.0);
        }

        // Warm up.
        for (int tick = 0; tick < 200; tick++) {
            for (int i = 0; i < pods; i++) index.put(ids[i], VOIDSEA, i * 64.0 + tick, tick * 0.5);
        }

        int ticks = 2_000;
        long start = System.nanoTime();
        for (int tick = 0; tick < ticks; tick++) {
            for (int i = 0; i < pods; i++) index.put(ids[i], VOIDSEA, i * 64.0 + tick, tick * 0.5);
        }
        long elapsed = System.nanoTime() - start;

        double perTickMillis = elapsed / 1_000_000.0 / ticks;
        assertEquals(pods, index.totalIndexed(), "every pod must still be indexed exactly once");
        // A 50 ms tick is the whole budget; repositioning 200 pods must be a rounding error in it.
        assertTrue(perTickMillis < 5.0,
                "repositioning 200 pods took " + perTickMillis + " ms per tick, too much of a 50 ms budget");
    }

    @Test void indexSizeTracksMembersNotPositionsEverVisited() {
        SpaceSpatialIndex index = new SpaceSpatialIndex(16);
        UUID ship = id();
        // Fly a long way, crossing many cells.
        for (int step = 0; step < 5_000; step++) index.put(ship, VOIDSEA, step * 16.0, 0.0);
        assertEquals(1, index.totalIndexed());
        assertEquals(1, index.cellCountIn(VOIDSEA),
                "cells behind a moving ship must be pruned, not accumulate for the server's uptime");
    }
}
