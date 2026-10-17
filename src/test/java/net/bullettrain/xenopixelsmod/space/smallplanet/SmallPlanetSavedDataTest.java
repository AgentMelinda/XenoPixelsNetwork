package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import org.junit.jupiter.api.Test;

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
}
