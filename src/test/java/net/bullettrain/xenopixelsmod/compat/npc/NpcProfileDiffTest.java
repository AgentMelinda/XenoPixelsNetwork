package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-28: DMZ screens saved their whole client-side profile copy, which is never synced from
 * the server, so a save reverted everything a script (or another screen) had set. Saves now carry
 * the baseline the screen started from, and only the keys the screen changed are applied.
 */
class NpcProfileDiffTest {
    private static CompoundTag tag(Object... kv) {
        CompoundTag t = new CompoundTag();
        for (int i = 0; i < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v instanceof Integer n) t.putInt((String) kv[i], n);
            else t.putString((String) kv[i], (String) v);
        }
        return t;
    }

    @Test
    void onlyKeysTheScreenChangedAreApplied() {
        CompoundTag baseline = tag("Strength", 10, "KiColor", "blue", "Vitality", 5);
        CompoundTag edited = tag("Strength", 20, "KiColor", "blue", "Vitality", 5);
        // Meanwhile a script raised Vitality and set KiColor on the server.
        CompoundTag current = tag("Strength", 10, "KiColor", "red", "Vitality", 99, "Techs", "kamehameha");
        CompoundTag merged = NpcProfileDiff.merge(current, baseline, edited);
        assertEquals(20, merged.getInt("Strength"), "the screen's edit applies");
        assertEquals("red", merged.getString("KiColor"), "untouched keys keep the server value");
        assertEquals(99, merged.getInt("Vitality"));
        assertEquals("kamehameha", merged.getString("Techs"), "keys the screen never knew about survive");
    }

    @Test
    void aKeyTheScreenClearedIsRemoved() {
        CompoundTag baseline = tag("HairCode", "abc", "Strength", 1);
        CompoundTag edited = tag("Strength", 1);
        CompoundTag current = tag("HairCode", "abc", "Strength", 1);
        assertFalse(NpcProfileDiff.merge(current, baseline, edited).contains("HairCode"));
    }

    @Test
    void anUnchangedSaveChangesNothing() {
        CompoundTag baseline = tag("Strength", 10);
        CompoundTag current = tag("Strength", 77, "Extra", "x");
        assertEquals(current, NpcProfileDiff.merge(current, baseline, baseline.copy()));
        assertTrue(NpcProfileDiff.changedKeys(baseline, baseline.copy()).isEmpty());
    }

    @Test
    void theInputsAreNotMutated() {
        CompoundTag baseline = tag("Strength", 10);
        CompoundTag edited = tag("Strength", 20);
        CompoundTag current = tag("Strength", 10);
        NpcProfileDiff.merge(current, baseline, edited);
        assertEquals(10, current.getInt("Strength"));
        assertEquals(10, baseline.getInt("Strength"));
    }
}
