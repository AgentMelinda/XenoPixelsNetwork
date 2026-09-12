package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcProfilePersistenceTest {
    @Test
    void cnpcKeyIsUnnamespacedSoCloneConvertersKeepIt() {
        assertFalse(NpcProfilePersistence.CNPC_KEY.contains(":"));
    }

    @Test
    void profileCompoundCopiesThroughTheCnpcKey() {
        CompoundTag profile = new CompoundTag();
        profile.putInt("Vitality", 10_000);
        profile.putInt("Strength", 500);
        CompoundTag npcTag = new CompoundTag();
        npcTag.put(NpcProfilePersistence.CNPC_KEY, profile.copy());

        CompoundTag restored = npcTag.getCompound(NpcProfilePersistence.CNPC_KEY);
        assertEquals(10_000, restored.getInt("Vitality"));
        assertEquals(500, restored.getInt("Strength"));
        assertTrue(npcTag.contains(NpcProfilePersistence.CNPC_KEY));
    }

    @Test
    void unnamespacedBackupIsDistinctFromNamespacedKey() {
        assertFalse(NpcCombatProfile.NBT_KEY.equals(NpcProfilePersistence.CNPC_KEY));
        assertFalse(NpcProfilePersistence.CNPC_KEY.contains(":"));
    }
}
