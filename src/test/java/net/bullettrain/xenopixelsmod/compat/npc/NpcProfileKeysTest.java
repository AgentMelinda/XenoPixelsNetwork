package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-09-28 owner request: every DMZ-tab setting settable and toggleable from scripts. */
class NpcProfileKeysTest {
    private static CompoundTag sample() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Schema", 3);
        tag.putBoolean("AuraOn", false);
        tag.putInt("Strength", 10);
        tag.putFloat("AuraScale", 1.0f);
        tag.putString("Race", "human");
        CompoundTag appearance = new CompoundTag();
        appearance.putBoolean("SaiyanTail", false);
        tag.put("DmzAppearance", appearance);
        return tag;
    }

    @Test
    void setsValuesUsingTheStoredType() {
        CompoundTag tag = sample();
        assertTrue(NpcProfileKeys.set(tag, "AuraOn", true));
        assertTrue(NpcProfileKeys.set(tag, "Strength", 42.7));
        assertTrue(NpcProfileKeys.set(tag, "AuraScale", 2));
        assertTrue(NpcProfileKeys.set(tag, "Race", "saiyan"));
        assertEquals(true, NpcProfileKeys.get(tag, "AuraOn"));
        assertEquals(42, NpcProfileKeys.get(tag, "Strength"));
        assertEquals(2.0f, NpcProfileKeys.get(tag, "AuraScale"));
        assertEquals("saiyan", NpcProfileKeys.get(tag, "Race"));
    }

    @Test
    void keysAreCaseInsensitiveAndNestedByDots() {
        CompoundTag tag = sample();
        assertTrue(NpcProfileKeys.set(tag, "dmzappearance.saiyantail", true));
        assertEquals(true, NpcProfileKeys.get(tag, "DmzAppearance.SaiyanTail"));
        assertTrue(NpcProfileKeys.toggle(tag, "auraon"));
        assertEquals(true, NpcProfileKeys.get(tag, "AuraOn"));
    }

    @Test
    void rejectsUnknownProtectedAndMistypedKeys() {
        CompoundTag tag = sample();
        assertFalse(NpcProfileKeys.set(tag, "NoSuchKey", 1));
        assertFalse(NpcProfileKeys.set(tag, "Schema", 9));
        assertFalse(NpcProfileKeys.set(tag, "Strength", "lots"));
        assertFalse(NpcProfileKeys.set(tag, "DmzAppearance", 1));
        assertFalse(NpcProfileKeys.toggle(tag, "Strength"));
        assertNull(NpcProfileKeys.get(tag, "NoSuchKey"));
        assertEquals(10, NpcProfileKeys.get(tag, "Strength"));
    }

    @Test
    void listsLeafKeysWithDottedPaths() {
        var keys = NpcProfileKeys.keys(sample());
        assertTrue(keys.contains("AuraOn"));
        assertTrue(keys.contains("DmzAppearance.SaiyanTail"));
        assertFalse(keys.contains("Schema"));
        assertFalse(keys.contains("DmzAppearance"));
    }
}
