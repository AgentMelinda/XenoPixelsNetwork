package net.bullettrain.xenopixelsmod.space.universe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniverseSavedDataTest {
    @Test void coordinatePairsSurviveGridExpansionAndNewEdgeCoordinatesRoundTrip() {
        var root = new CompoundTag();
        var cells = new ListTag();
        cells.add(entry(220, -220)); // coordinate pair from the previous ±220 field
        cells.add(entry(UniverseField.MAX_CELL, -UniverseField.MAX_CELL));
        root.put("Revealed", cells);

        var loaded = UniverseSavedData.load(root, null);
        assertTrue(loaded.isRevealed(new UniverseField.Cell(220, -220)));
        assertTrue(loaded.isRevealed(new UniverseField.Cell(UniverseField.MAX_CELL, -UniverseField.MAX_CELL)));

        var savedAgain = UniverseSavedData.load(loaded.save(new CompoundTag(), null), null);
        assertTrue(savedAgain.isRevealed(new UniverseField.Cell(220, -220)));
        assertTrue(savedAgain.isRevealed(new UniverseField.Cell(UniverseField.MAX_CELL, -UniverseField.MAX_CELL)));
    }

    @Test void timerFrontierRebuildsInBoundedBatchesAndMatchesReferenceSelection() {
        long seed = 123456789L;
        var revealed = new LinkedHashSet<UniverseField.Cell>();
        revealed.add(new UniverseField.Cell(0, 0));
        for (int ring = 1; ring <= 8 && revealed.size() < 40; ring++)
            for (int x = -ring; x <= ring && revealed.size() < 40; x++)
                for (int z = -ring; z <= ring && revealed.size() < 40; z++)
                    if (Math.max(Math.abs(x), Math.abs(z)) == ring) {
                        var cell = new UniverseField.Cell(x, z);
                        if (UniverseField.systemAt(seed, cell) != null) revealed.add(cell);
                    }
        var data = load(revealed);
        assertFalse(data.timerFrontierReady());
        assertFalse(data.timerFrontierStarted());
        assertEquals(0, data.timerFrontierProcessed());
        assertEquals(revealed.size(), data.timerFrontierTotal());
        assertFalse(data.advanceTimerFrontier(seed, 3));
        assertTrue(data.timerFrontierStarted());
        assertEquals(3, data.timerFrontierProcessed());

        var discoveredDuringRebuild = UniverseGrowth.nextTimerCell(seed, revealed);
        assertTrue(data.reveal(discoveredDuringRebuild, UniverseSavedData.Source.DISCOVERY, 1, seed));
        assertEquals(revealed.size() + 1, data.timerFrontierTotal());
        revealed.add(discoveredDuringRebuild);

        int batches = 0;
        while (!data.advanceTimerFrontier(seed, 3)) batches++;
        assertTrue(batches > 1, "large saved field should require multiple bounded batches");
        assertTrue(data.timerFrontierReady());
        assertEquals(data.timerFrontierTotal(), data.timerFrontierProcessed());
        assertEquals(UniverseGrowth.nextTimerCell(seed, revealed), data.nextTimerCell(seed));
    }

    @Test void newlyRevealedCellUpdatesAnAlreadyBuiltTimerFrontier() {
        long seed = 987654321L;
        var revealed = new LinkedHashSet<UniverseField.Cell>();
        revealed.add(new UniverseField.Cell(0, 0));
        var data = load(revealed);
        assertTrue(data.advanceTimerFrontier(seed, 8));

        var next = UniverseGrowth.nextTimerCell(seed, revealed);
        assertTrue(data.reveal(next, UniverseSavedData.Source.TIMER, 1, seed));
        revealed.add(next);
        assertEquals(UniverseGrowth.nextTimerCell(seed, revealed), data.nextTimerCell(seed));
    }

    @Test void saveLoadRebuildsTransientFrontierFromDurableRevealRecords() {
        long seed = 246813579L;
        var revealed = occupiedCells(seed, 32);
        var beforeSave = load(revealed);
        beforeSave.lastTimerDay = 81;
        beforeSave.discoveryDay = 82;
        beforeSave.discoveredToday = 3;
        assertFalse(beforeSave.advanceTimerFrontier(seed, 7));

        var restored = UniverseSavedData.load(beforeSave.save(new CompoundTag(), null), null);
        assertFalse(restored.timerFrontierStarted(), "the ordered index is transient and must rebuild after load");
        assertEquals(0, restored.timerFrontierProcessed());
        assertEquals(revealed.size(), restored.timerFrontierTotal());
        assertEquals(81, restored.lastTimerDay);
        assertEquals(82, restored.discoveryDay);
        assertEquals(3, restored.discoveredToday);

        int batches = 0;
        while (!restored.advanceTimerFrontier(seed, 5)) batches++;
        assertTrue(batches > 1);
        assertEquals(UniverseGrowth.nextTimerCell(seed, revealed), restored.nextTimerCell(seed));
    }

    @Test void changingFieldSeedRebuildsTheFrontierWithTheNewOrdering() {
        long originalSeed = 13579L;
        long newSeed = 97531L;
        var revealed = occupiedCells(originalSeed, 24);
        var data = load(revealed);
        assertTrue(data.advanceTimerFrontier(originalSeed, revealed.size()));

        assertFalse(data.advanceTimerFrontier(newSeed, 2));
        assertEquals(2, data.timerFrontierProcessed(), "a new seed starts a bounded rebuild from the beginning");
        while (!data.advanceTimerFrontier(newSeed, 4)) { }
        assertEquals(UniverseGrowth.nextTimerCell(newSeed, revealed), data.nextTimerCell(newSeed));
    }

    private static LinkedHashSet<UniverseField.Cell> occupiedCells(long seed, int wanted) {
        var cells = new LinkedHashSet<UniverseField.Cell>();
        cells.add(new UniverseField.Cell(0, 0));
        for (int ring = 1; ring <= 16 && cells.size() < wanted; ring++)
            for (int x = -ring; x <= ring && cells.size() < wanted; x++)
                for (int z = -ring; z <= ring && cells.size() < wanted; z++)
                    if (Math.max(Math.abs(x), Math.abs(z)) == ring) {
                        var cell = new UniverseField.Cell(x, z);
                        if (UniverseField.systemAt(seed, cell) != null) cells.add(cell);
                    }
        assertEquals(wanted, cells.size(), "fixture must contain the requested deterministic occupied cells");
        return cells;
    }

    private static UniverseSavedData load(Iterable<UniverseField.Cell> revealed) {
        var root = new CompoundTag();
        var cells = new ListTag();
        revealed.forEach(cell -> cells.add(entry(cell.x(), cell.z())));
        root.put("Revealed", cells);
        return UniverseSavedData.load(root, null);
    }

    private static CompoundTag entry(int x, int z) {
        var entry = new CompoundTag();
        entry.putInt("x", x);
        entry.putInt("z", z);
        entry.putString("source", UniverseSavedData.Source.ADMIN.name());
        entry.putLong("day", 17);
        return entry;
    }
}
