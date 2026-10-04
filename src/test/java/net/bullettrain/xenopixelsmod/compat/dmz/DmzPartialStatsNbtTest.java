package net.bullettrain.xenopixelsmod.compat.dmz;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzPartialStatsNbtTest {

    @Test
    void resourceSyncPayloadIsNotAFullStatsBlob() {
        CompoundTag nbt = new CompoundTag();
        nbt.put(DmzPartialStatsNbt.RESOURCES, new CompoundTag());
        nbt.put(DmzPartialStatsNbt.STATUS, new CompoundTag());
        assertFalse(DmzPartialStatsNbt.isFullBlob(nbt));
        assertTrue(DmzPartialStatsNbt.shouldMergePartially(nbt));
        assertNotNull(DmzPartialStatsNbt.resources(nbt));
        assertNotNull(DmzPartialStatsNbt.status(nbt));
    }

    @Test
    void statsSyncPayloadWithQuestsUsesTheFullLoader() {
        CompoundTag nbt = new CompoundTag();
        nbt.put(DmzPartialStatsNbt.RESOURCES, new CompoundTag());
        nbt.put(DmzPartialStatsNbt.STATUS, new CompoundTag());
        nbt.put(DmzPartialStatsNbt.PLAYER_QUEST_DATA, new CompoundTag());
        assertTrue(DmzPartialStatsNbt.isFullBlob(nbt));
        assertFalse(DmzPartialStatsNbt.shouldMergePartially(nbt));
    }

    @Test
    void nullOrEmptyNbtDoesNotLookLikeAFullBlob() {
        assertFalse(DmzPartialStatsNbt.isFullBlob(null));
        assertFalse(DmzPartialStatsNbt.shouldMergePartially(null));
        assertFalse(DmzPartialStatsNbt.isFullBlob(new CompoundTag()));
        assertTrue(DmzPartialStatsNbt.shouldMergePartially(new CompoundTag()));
        assertNull(DmzPartialStatsNbt.resources(new CompoundTag()));
        assertNull(DmzPartialStatsNbt.status(new CompoundTag()));
    }
}
