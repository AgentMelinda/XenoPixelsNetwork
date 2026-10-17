package net.bullettrain.xenopixelsmod.space.universe;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UniverseTest {
    private static final long SEED = 123456789L;

    @Test void homeCellIsAlwaysSol() {
        var sol = UniverseField.systemAt(SEED, new UniverseField.Cell(0, 0));
        assertEquals("Sol", sol.name());
        assertEquals(0, sol.x());
        assertEquals(0, sol.z());
    }

    @Test void systemsAreDeterministicAndStayInsideTheirCell() {
        int occupied = 0;
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++) {
                var cell = new UniverseField.Cell(x, z);
                var a = UniverseField.systemAt(SEED, cell);
                assertEquals(a, UniverseField.systemAt(SEED, cell));
                if (a == null || cell.home()) continue;
                occupied++;
                assertEquals(cell, UniverseField.cellAt(a.x(), a.z()), "a star must lie in its own cell");
                assertTrue(Math.abs(a.x() - x * (double) UniverseField.CELL_SIZE) <= UniverseField.JITTER * UniverseField.CELL_SIZE);
            }
        double share = occupied / 440.0;
        assertTrue(share > 0.45 && share < 0.8, "occupancy " + share);
    }

    @Test void edgeCellsStayInsideTheWorldBorder() {
        for (int x : new int[]{-UniverseField.MAX_CELL, UniverseField.MAX_CELL})
            for (int z = -UniverseField.MAX_CELL; z <= UniverseField.MAX_CELL; z += 7) {
                var s = UniverseField.systemAt(SEED, new UniverseField.Cell(x, z));
                if (s != null) assertTrue(Math.abs(s.x()) + 3000 < 29_999_984 && Math.abs(s.z()) < 29_999_984);
            }
        assertThrows(IllegalArgumentException.class, () -> new UniverseField.Cell(UniverseField.MAX_CELL + 1, 0));
        assertNull(UniverseField.cellAt(30_000_000, 0));
    }

    @Test void everyPositionAtTheVanillaWorldBorderMapsToTheEdgeCell() {
        double border = UniverseField.WORLD_BORDER_LIMIT;
        assertEquals(UniverseField.MAX_CELL, UniverseField.cellAt(border, 0).x());
        assertEquals(-UniverseField.MAX_CELL, UniverseField.cellAt(-border, 0).x());
        assertEquals(UniverseField.MAX_CELL, UniverseField.cellAt(0, border).z());
        assertEquals(-UniverseField.MAX_CELL, UniverseField.cellAt(0, -border).z());
        assertNull(UniverseField.cellAt(border + 1, 0));
    }

    @Test void timerGrowsOutwardDeterministically() {
        Set<UniverseField.Cell> revealed = new HashSet<>(Set.of(new UniverseField.Cell(0, 0)));
        int lastRing = 0;
        for (int i = 0; i < 30; i++) {
            var next = UniverseGrowth.nextTimerCell(SEED, revealed);
            assertNotNull(next);
            assertEquals(next, UniverseGrowth.nextTimerCell(SEED, revealed), "same state, same next cell");
            assertNotNull(UniverseField.systemAt(SEED, next), "only occupied cells are revealed");
            assertFalse(revealed.contains(next));
            assertTrue(next.ring() <= lastRing + 1, "growth stays on the frontier");
            lastRing = Math.max(lastRing, next.ring());
            revealed.add(next);
        }
    }

    @Test void timerDoesNotStallWhenSolsNeighbourhoodIsEmpty() {
        long seed = -1;
        for (long s = 0; s < 2_000_000 && seed < 0; s++) {
            boolean empty = true;
            for (int dx = -1; dx <= 1 && empty; dx++)
                for (int dz = -1; dz <= 1 && empty; dz++)
                    if ((dx != 0 || dz != 0) && UniverseField.systemAt(s, new UniverseField.Cell(dx, dz)) != null) empty = false;
            if (empty) seed = s;
        }
        assertTrue(seed >= 0, "found a seed whose eight cells around Sol are empty");
        var next = UniverseGrowth.nextTimerCell(seed, Set.of(new UniverseField.Cell(0, 0)));
        assertNotNull(next, "fallback must find a farther system");
        assertTrue(next.ring() >= 2);
        assertNotNull(UniverseField.systemAt(seed, next));
        assertEquals(next, UniverseGrowth.nextTimerCell(seed, Set.of(new UniverseField.Cell(0, 0))));
    }

    @Test void extremeAndNonFiniteInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new UniverseField.Cell(Integer.MIN_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> new UniverseField.Cell(0, Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new UniverseField.Cell(Integer.MAX_VALUE, 0));
        assertNull(UniverseField.cellAt(Double.NaN, 0));
        assertNull(UniverseField.cellAt(0, Double.POSITIVE_INFINITY));
        assertNull(UniverseField.cellAt(-1.0e300, 0));
        assertNull(UniverseField.cellAt(1.0e300, 0));
        assertFalse(UniverseField.Cell.inRange(Integer.MIN_VALUE));
        assertEquals(new UniverseField.Cell(0, 0), UniverseField.cellAt(0, 0));
    }

    @Test void snapshotRoundTripsAndIsBounded() {
        var snap = new UniverseSnapshot(42L, true, java.util.List.of(new UniverseField.Cell(0, 0), new UniverseField.Cell(-7, 12)));
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            UniverseSnapshot.encode(snap, buf);
            var decoded = UniverseSnapshot.decode(buf);
            assertEquals(snap, decoded);
            assertEquals(snap.revealed(), decoded.revealed());
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }

        var duplicate = new UniverseField.Cell(4, -3);
        assertThrows(IllegalArgumentException.class,
                () -> new UniverseSnapshot(1L, false, java.util.List.of(duplicate, duplicate)));

        var wrongLength = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            wrongLength.writeLong(1L); wrongLength.writeBoolean(false);
            wrongLength.writeVarInt(1); wrongLength.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> UniverseSnapshot.decode(wrongLength));
        } finally { wrongLength.release(); }

        var outOfGrid = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            byte[] bits = new byte[(UniverseSnapshot.MAX_CELLS + 7) >>> 3];
            bits[bits.length - 1] = (byte) 0x02; // Only bit zero is inside the 457x457 grid's final byte.
            outOfGrid.writeLong(1L); outOfGrid.writeBoolean(false);
            outOfGrid.writeVarInt(bits.length); outOfGrid.writeBytes(bits);
            assertThrows(IllegalArgumentException.class, () -> UniverseSnapshot.decode(outOfGrid));
        } finally { outOfGrid.release(); }
    }

    @Test void fullUniverseSnapshotIsCompleteWithinFixedSmallPayload() {
        var all = new java.util.ArrayList<UniverseField.Cell>(UniverseSnapshot.MAX_CELLS);
        for (int x = -UniverseField.MAX_CELL; x <= UniverseField.MAX_CELL; x++)
            for (int z = -UniverseField.MAX_CELL; z <= UniverseField.MAX_CELL; z++)
                all.add(new UniverseField.Cell(x, z));
        var snapshot = new UniverseSnapshot(12L, true, all);
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            UniverseSnapshot.encode(snapshot, buf);
            assertTrue(buf.writerIndex() < 27_000, "full-border snapshot should stay under 27 KB");
            var decoded = UniverseSnapshot.decode(buf);
            assertEquals(snapshot, decoded);
            assertEquals(UniverseSnapshot.MAX_CELLS, decoded.revealedCount());
            assertTrue(decoded.isRevealed(UniverseField.index(-UniverseField.MAX_CELL, -UniverseField.MAX_CELL)));
            assertTrue(decoded.isRevealed(UniverseField.index(UniverseField.MAX_CELL, UniverseField.MAX_CELL)));
            int[] visited = {0, -1, -1};
            decoded.forEachRevealedIndex(index -> {
                if (visited[0] == 0) visited[1] = index;
                visited[2] = index;
                visited[0]++;
            });
            assertEquals(UniverseSnapshot.MAX_CELLS, visited[0]);
            assertEquals(0, visited[1]);
            assertEquals(UniverseSnapshot.MAX_CELLS - 1, visited[2]);
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }

    @Test void singleCellRevealPacketRoundTrips() {
        var packet = new UniverseCellReveal(0x1234L, true, new UniverseField.Cell(-UniverseField.MAX_CELL, UniverseField.MAX_CELL));
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            UniverseCellReveal.encode(packet, buf);
            assertEquals(packet, UniverseCellReveal.decode(buf));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }

    @Test void mapLodStrideRespectsMarkerBudgetAcrossWideAndNarrowViewports() {
        int[][] viewports = {{441, 441}, {441, 1}, {1, 441}, {320, 180}, {12, 8}};
        int[] budgets = {1, 100, 8192};
        for (var viewport : viewports) for (int budget : budgets) {
            int width = viewport[0], height = viewport[1];
            int stride = UniverseMapLod.stride(width, height, budget);
            long area = (long) width * height;
            long sampled = (area + stride - 1) / stride;
            assertTrue(sampled <= budget, width + "x" + height + " with budget " + budget + " sampled " + sampled);
            assertEquals(1, gcd(stride, width), "stride should not alias a fixed column pattern");
        }
        int skyStride = UniverseMapLod.stride1d(UniverseField.GRID_CELL_COUNT, 8_192);
        assertTrue((UniverseField.GRID_CELL_COUNT + (long) skyStride - 1) / skyStride <= 8_192);
        assertThrows(IllegalArgumentException.class, () -> UniverseMapLod.stride(0, 10, 100));
    }

    private static int gcd(int a, int b) {
        while (b != 0) { int r = a % b; a = b; b = r; }
        return Math.abs(a);
    }

    @Test void discoveryLimitsAndModes() {
        assertTrue(UniverseGrowth.mayDiscover(0, 8, 10, 512));
        assertFalse(UniverseGrowth.mayDiscover(8, 8, 10, 512));
        assertFalse(UniverseGrowth.mayDiscover(0, 8, 512, 512));
        assertEquals(UniverseGrowth.Mode.BOTH, UniverseGrowth.Mode.parse("Both"));
        assertEquals(UniverseGrowth.Mode.OFF, UniverseGrowth.Mode.parse("nonsense"));
        assertTrue(UniverseGrowth.Mode.BOTH.timer() && UniverseGrowth.Mode.BOTH.discovery());
        assertEquals(1, UniverseGrowth.day(24_000));
        assertEquals(-1, UniverseGrowth.day(-1));
    }
}
