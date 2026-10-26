package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SmallPlanetSavedDataTest {
    @Test void detachedIdentityIgnoresDerivedSectorAndRoundTripsThroughNbt() {
        var data = new SmallPlanetSavedData();
        var id = new SmallPlanetField.ObjectId(new UniverseField.Cell(-17, 29), 2, 4, -3);

        assertTrue(data.detach(id));
        assertTrue(data.isDetached(new SmallPlanetField.ObjectId(id.systemCell(), id.slot(), -4, 3)));
        assertFalse(data.isDetached(new SmallPlanetField.ObjectId(id.systemCell(), 1, 4, -3)));

        CompoundTag saved = data.save(new CompoundTag(), null);
        SmallPlanetSavedData loaded = SmallPlanetSavedData.load(saved, null);
        assertEquals(1, loaded.size());
        assertTrue(loaded.isDetached(id));
    }

    @Test void loadRejectsInvalidAndHomeSystemIdentities() {
        ListTag entries = new ListTag();
        entries.add(new LongArrayTag(new long[]{0, 0, 0}));
        entries.add(new LongArrayTag(new long[]{1, 1, 3}));
        entries.add(new LongArrayTag(new long[]{(long) UniverseField.MAX_CELL + 1, 1, 0}));
        entries.add(new LongArrayTag(new long[]{1}));
        CompoundTag tag = new CompoundTag();
        tag.put("Detached", entries);

        SmallPlanetSavedData loaded = SmallPlanetSavedData.load(tag, null);
        assertEquals(1, loaded.size());
        assertTrue(loaded.isDetached(new SmallPlanetField.ObjectId(new UniverseField.Cell(1, 1), 3, 0, 0)));
    }

    @Test void quarantineReleaseDecisionIsIdempotentAndPersistsByBodyUuid() {
        var data = new SmallPlanetSavedData();
        UUID id = UUID.fromString("34000000-0000-0000-0000-000000000017");
        assertTrue(data.markQuarantineReleased(id));
        assertTrue(data.markQuarantineReleased(id));
        assertEquals(1, data.releasedQuarantineCount());

        SmallPlanetSavedData loaded = SmallPlanetSavedData.load(data.save(new CompoundTag(), null), null);
        assertTrue(loaded.isQuarantineReleased(id));
        assertEquals(java.util.Set.of(id), loaded.releasedQuarantineIds());
    }

    @Test void loadIgnoresMalformedQuarantineReleaseUuids() {
        ListTag released = new ListTag();
        released.add(new LongArrayTag(new long[]{1}));
        released.add(new LongArrayTag(new long[]{2, 3, 4}));
        CompoundTag tag = new CompoundTag();
        tag.put("ReleasedQuarantines", released);
        assertEquals(0, SmallPlanetSavedData.load(tag, null).releasedQuarantineCount());
    }

    @Test void loadingOversizedLedgersPreservesExistingDecisionsAndStillRejectsGrowth() {
        ListTag detached = new ListTag();
        ListTag released = new ListTag();
        int width = UniverseField.MAX_CELL * 2 + 1;
        for (int index = 1; index <= SmallPlanetSavedData.MAX_DETACHED + 1; index++) {
            int x = -UniverseField.MAX_CELL + index / width;
            int z = -UniverseField.MAX_CELL + index % width;
            if (x == 0 && z == 0) z++;
            detached.add(new LongArrayTag(new long[]{x, z, 0}));
            released.add(new LongArrayTag(new long[]{0, index}));
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Detached", detached);
        tag.put("ReleasedQuarantines", released);

        SmallPlanetSavedData loaded = SmallPlanetSavedData.load(tag, null);

        assertEquals(SmallPlanetSavedData.MAX_DETACHED + 1, loaded.size());
        assertEquals(SmallPlanetSavedData.MAX_RELEASED_QUARANTINES + 1,
                loaded.releasedQuarantineCount());
        assertFalse(loaded.detach(new SmallPlanetField.ObjectId(new UniverseField.Cell(17, -29), 1, 0, 0)));
        assertFalse(loaded.markQuarantineReleased(UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff")));

        SmallPlanetSavedData roundTripped = SmallPlanetSavedData.load(loaded.save(new CompoundTag(), null), null);
        assertEquals(SmallPlanetSavedData.MAX_DETACHED + 1, roundTripped.size());
        assertEquals(SmallPlanetSavedData.MAX_RELEASED_QUARANTINES + 1,
                roundTripped.releasedQuarantineCount());
    }

    @Test void pruningVerifiedReleaseDecisionMarksSavedDataDirty() {
        var data = new SmallPlanetSavedData();
        UUID id = UUID.fromString("34000000-0000-0000-0000-000000000018");
        assertTrue(data.markQuarantineReleased(id));
        data.setDirty(false);

        assertTrue(data.pruneQuarantineReleasedAfterVerifiedBodyReadback(id));
        assertFalse(data.isQuarantineReleased(id));
        assertTrue(data.isDirty());
        assertFalse(data.pruneQuarantineReleasedAfterVerifiedBodyReadback(id));
    }
}
