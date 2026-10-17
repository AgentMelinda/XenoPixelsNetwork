package net.bullettrain.xenopixelsmod.space.universe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniverseFrontierPerfProbeTest {
    @Test
    void reportFullSeededFieldSelectionAndBoundedBootstrapCosts() {
        long seed = 123456789L;
        List<UniverseField.Cell> occupied = new ArrayList<>();
        for (int x = -UniverseField.MAX_CELL; x <= UniverseField.MAX_CELL; x++)
            for (int z = -UniverseField.MAX_CELL; z <= UniverseField.MAX_CELL; z++) {
                var cell = new UniverseField.Cell(x, z);
                if (UniverseField.systemAt(seed, cell) != null) occupied.add(cell);
            }
        var revealed = new LinkedHashSet<>(occupied);
        report("full", seed, occupied, revealed);

        var scattered = new LinkedHashSet<UniverseField.Cell>();
        for (var cell : occupied)
            if ((UniverseField.mix(seed ^ ((long) cell.x() << 32) ^ (cell.z() & 0xFFFFFFFFL)) & 1L) == 0)
                scattered.add(cell);
        report("scattered", seed, occupied, scattered);
    }

    private static void report(String scenario, long seed, List<UniverseField.Cell> occupied,
                               LinkedHashSet<UniverseField.Cell> revealed) {
        var saved = new UniverseSavedData();
        for (var cell : revealed) saved.reveal(cell, UniverseSavedData.Source.ADMIN, 0, seed);

        for (int i = 0; i < 5; i++) UniverseGrowth.nextTimerCell(seed, revealed);

        long[] referenceNanos = new long[9];
        UniverseField.Cell reference = null;
        for (int i = 0; i < 5; i++) reference = UniverseGrowth.nextTimerCell(seed, revealed);
        assertEquals(reference, UniverseGrowth.nextTimerCell(seed, revealed));

        int batches = 0;
        long maxBatchNanos = 0;
        while (!saved.timerFrontierReady()) {
            long start = System.nanoTime();
            saved.advanceTimerFrontier(seed, 512);
            maxBatchNanos = Math.max(maxBatchNanos, System.nanoTime() - start);
            batches++;
        }
        assertEquals(reference, saved.nextTimerCell(seed));
        for (int i = 0; i < 5; i++) assertEquals(reference, saved.nextTimerCell(seed));
        long[] indexedNanos = new long[9];
        for (int i = 0; i < referenceNanos.length; i++) {
            long start = System.nanoTime();
            UniverseGrowth.nextTimerCell(seed, revealed);
            referenceNanos[i] = System.nanoTime() - start;
        }
        for (int i = 0; i < indexedNanos.length; i++) {
            long start = System.nanoTime();
            assertEquals(reference, saved.nextTimerCell(seed));
            indexedNanos[i] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(referenceNanos);
        java.util.Arrays.sort(indexedNanos);
        System.out.printf("UNIVERSE_FRONTIER scenario=%s seed=%d systems=%d revealed=%d maxCell=%d batches=%d batchBudget=%d referenceMedianMs=%.3f referenceMaxMs=%.3f indexedMedianMs=%.3f indexedMaxMs=%.3f maxBootstrapBatchMs=%.3f%n",
                scenario, seed, occupied.size(), revealed.size(), UniverseField.MAX_CELL, batches, 512,
                referenceNanos[4] / 1_000_000.0, referenceNanos[8] / 1_000_000.0,
                indexedNanos[4] / 1_000_000.0, indexedNanos[8] / 1_000_000.0,
                maxBatchNanos / 1_000_000.0);
    }
}
