package net.bullettrain.xenopixelsmod.space.universe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InfiniteUniverseSavedDataTest {
    @Test void fullWidthCoordinatesAndRevealMetadataSurviveNbtRoundTrip() {
        var data = InfiniteUniverseSavedData.load(new CompoundTag(), null);
        assertEquals(InfiniteUniverseSavedData.RevealResult.ADDED,
                data.reveal(Long.MIN_VALUE, Long.MAX_VALUE, InfiniteUniverseSavedData.Source.DISCOVERY, 91,
                        InfiniteUniverseSavedData.MAX_REVEALED));
        assertEquals(InfiniteUniverseSavedData.RevealResult.ADDED,
                data.reveal(229, -229, InfiniteUniverseSavedData.Source.ADMIN, 92,
                        InfiniteUniverseSavedData.MAX_REVEALED));

        var encoded = data.save(new CompoundTag(), null);
        var restored = InfiniteUniverseSavedData.load(encoded, null);

        assertEquals(2, restored.size());
        assertTrue(restored.isRevealed(Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(new InfiniteUniverseSavedData.Entry(InfiniteUniverseSavedData.Source.DISCOVERY, 91),
                restored.entries().get(new InfiniteUniverseField.Cell(Long.MIN_VALUE, Long.MAX_VALUE)));
        assertEquals(new InfiniteUniverseSavedData.Entry(InfiniteUniverseSavedData.Source.ADMIN, 92),
                restored.entries().get(new InfiniteUniverseField.Cell(229, -229)));
    }

    @Test void revealIsIdempotentAndHonorsHardConfiguredCap() {
        var data = InfiniteUniverseSavedData.load(new CompoundTag(), null);
        assertEquals(InfiniteUniverseSavedData.RevealResult.ADDED,
                data.reveal(500, 700, InfiniteUniverseSavedData.Source.ADMIN, 1, 1));
        assertEquals(InfiniteUniverseSavedData.RevealResult.ALREADY_REVEALED,
                data.reveal(500, 700, InfiniteUniverseSavedData.Source.DISCOVERY, 2, 1));
        assertEquals(InfiniteUniverseSavedData.RevealResult.CAP_REACHED,
                data.reveal(501, 700, InfiniteUniverseSavedData.Source.ADMIN, 2, 1));
        assertEquals(1, data.size());
        assertThrows(IllegalArgumentException.class,
                () -> data.reveal(0, 0, InfiniteUniverseSavedData.Source.ADMIN, 1,
                        InfiniteUniverseSavedData.MAX_REVEALED + 1));
    }

    @Test void malformedOrOversizedArraysAreBoundedAndRepairable() {
        var malformed = new CompoundTag();
        malformed.putLongArray("Coordinates", new long[]{1, 2, 3});
        malformed.putByteArray("Sources", new byte[]{99});
        malformed.putLongArray("Days", new long[0]);
        var repaired = InfiniteUniverseSavedData.load(malformed, null);
        assertEquals(1, repaired.size());
        assertEquals(new InfiniteUniverseSavedData.Entry(InfiniteUniverseSavedData.Source.LEGACY, 0),
                repaired.entries().get(new InfiniteUniverseField.Cell(1, 2)));
        assertNotNull(repaired.save(new CompoundTag(), null));
    }

    @Test void oldFiniteDiscoveriesCanBeImportedOnlyOnce() {
        var oldTag = new CompoundTag();
        var oldEntries = new ListTag();
        var home = new CompoundTag();
        home.putInt("x", 0);
        home.putInt("z", 0);
        home.putString("source", "HOME");
        home.putLong("day", 0);
        oldEntries.add(home);
        oldTag.put("Revealed", oldEntries);
        var old = UniverseSavedData.load(oldTag, null);
        assertEquals(1, old.size());
        var data = InfiniteUniverseSavedData.load(new CompoundTag(), null);
        data.importLegacy(old);
        assertTrue(data.legacyImported());
        assertTrue(data.isRevealed(0, 0));
        assertEquals(1, data.size());
    }

    @Test void legacyImportRespectsRemainingHardCapacityWithoutDeletingTheLegacySave() {
        var oldTag = new CompoundTag();
        var oldEntries = new ListTag();
        for (int i = 0; i < 4; i++) {
            var entry = new CompoundTag();
            entry.putInt("x", i + 10);
            entry.putInt("z", -i - 10);
            entry.putString("source", "DISCOVERY");
            entry.putLong("day", i);
            oldEntries.add(entry);
        }
        oldTag.put("Revealed", oldEntries);
        var legacy = UniverseSavedData.load(oldTag, null);
        var logical = InfiniteUniverseSavedData.load(new CompoundTag(), null);
        assertEquals(InfiniteUniverseSavedData.RevealResult.ADDED,
                logical.reveal(1_000, 1_000, InfiniteUniverseSavedData.Source.ADMIN, 7, 2));

        logical.importLegacy(legacy, 2);

        assertEquals(2, logical.size());
        assertTrue(logical.isRevealed(1_000, 1_000));
        assertTrue(logical.isRevealed(10, -10));
        assertFalse(logical.isRevealed(11, -11));
        assertEquals(4, legacy.size());
        assertTrue(logical.legacyImported());
    }
}
