package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCombatBrainVersionTest {
    @Test
    void byNameAcceptsVPrefixAndDefaultsToV1() {
        assertEquals(NpcCombatBrainVersion.V1, NpcCombatBrainVersion.byName(null));
        assertEquals(NpcCombatBrainVersion.V1, NpcCombatBrainVersion.byName(""));
        assertEquals(NpcCombatBrainVersion.V1, NpcCombatBrainVersion.byName("1"));
        assertEquals(NpcCombatBrainVersion.V1, NpcCombatBrainVersion.byName("v1"));
        assertEquals(NpcCombatBrainVersion.V1, NpcCombatBrainVersion.byName("other"));
        assertEquals(NpcCombatBrainVersion.V2, NpcCombatBrainVersion.byName("2"));
        assertEquals(NpcCombatBrainVersion.V2, NpcCombatBrainVersion.byName("v2"));
        assertEquals(NpcCombatBrainVersion.V2, NpcCombatBrainVersion.byName("V2"));
        assertEquals(NpcCombatBrainVersion.V3, NpcCombatBrainVersion.byName("v3"));
        assertEquals(NpcCombatBrainVersion.V4, NpcCombatBrainVersion.byName("v4"));
        assertEquals(NpcCombatBrainVersion.V5, NpcCombatBrainVersion.byName("v5"));
        assertEquals(NpcCombatBrainVersion.V6, NpcCombatBrainVersion.byName("v6"));
        assertEquals(NpcCombatBrainVersion.V7, NpcCombatBrainVersion.byName("7"));
        assertEquals(NpcCombatBrainVersion.V9, NpcCombatBrainVersion.byName("v9"));
        assertEquals(NpcCombatBrainVersion.V4, NpcCombatBrainVersion.byLegacyName("v5"));
        assertEquals(NpcCombatBrainVersion.V4, NpcCombatBrainVersion.V5.legacyCompatible());
    }

    @Test
    void storedThreeIsV3AndCycleIsV1V2V3() {
        assertEquals(NpcCombatBrainVersion.V1, NpcCombatBrainVersion.fromStored(0));
        assertEquals(NpcCombatBrainVersion.V1, NpcCombatBrainVersion.fromStored(1));
        assertEquals(NpcCombatBrainVersion.V2, NpcCombatBrainVersion.fromStored(2));
        assertEquals(NpcCombatBrainVersion.V3, NpcCombatBrainVersion.fromStored(3));
        assertEquals(NpcCombatBrainVersion.V4, NpcCombatBrainVersion.fromStored(4));
        assertEquals(NpcCombatBrainVersion.V5, NpcCombatBrainVersion.fromStored(5));
        assertEquals(NpcCombatBrainVersion.V6, NpcCombatBrainVersion.fromStored(6));
        assertEquals(NpcCombatBrainVersion.V7, NpcCombatBrainVersion.fromStored(7));
        assertEquals(NpcCombatBrainVersion.V8, NpcCombatBrainVersion.fromStored(8));
        assertEquals(NpcCombatBrainVersion.V9, NpcCombatBrainVersion.fromStored(9));
        assertEquals(1, NpcCombatBrainVersion.V1.stored());
        assertEquals(2, NpcCombatBrainVersion.V2.stored());
        assertEquals(3, NpcCombatBrainVersion.V3.stored());
        assertEquals(4, NpcCombatBrainVersion.V4.stored());
        assertEquals(5, NpcCombatBrainVersion.V5.stored());
        assertEquals(6, NpcCombatBrainVersion.V6.stored());
        assertEquals(7, NpcCombatBrainVersion.V7.stored());
        assertEquals(8, NpcCombatBrainVersion.V8.stored());
        assertEquals(9, NpcCombatBrainVersion.V9.stored());
        assertTrue(NpcCombatBrainVersion.V2.isV2());
        assertTrue(NpcCombatBrainVersion.V3.isV2());
        assertTrue(NpcCombatBrainVersion.V3.isV3());
        assertFalse(NpcCombatBrainVersion.V1.isV2());
        assertFalse(NpcCombatBrainVersion.V2.isV3());
        assertEquals("v1", NpcCombatBrainVersion.V1.label());
        assertEquals("v2", NpcCombatBrainVersion.V2.label());
        assertEquals("v3", NpcCombatBrainVersion.V3.label());
        assertEquals(NpcCombatBrainVersion.V2, NpcCombatBrainVersion.V1.next());
        assertEquals(NpcCombatBrainVersion.V3, NpcCombatBrainVersion.V2.next());
        assertEquals(NpcCombatBrainVersion.V4, NpcCombatBrainVersion.V3.next());
        // The cycle now runs on through the three DragonMineZ brains before wrapping. V8 is the
        // V8 preserves the old ported tree; V9 adds explicit action switches to that tree.
        assertEquals(NpcCombatBrainVersion.V6, NpcCombatBrainVersion.V4.next());
        assertEquals(NpcCombatBrainVersion.V7, NpcCombatBrainVersion.V6.next());
        assertEquals(NpcCombatBrainVersion.V8, NpcCombatBrainVersion.V7.next());
        assertEquals(NpcCombatBrainVersion.V9, NpcCombatBrainVersion.V8.next());
        assertEquals(NpcCombatBrainVersion.V1, NpcCombatBrainVersion.V9.next());
        assertEquals(NpcCombatBrainVersion.V5, NpcCombatBrainVersion.V4.nextNative());
        assertEquals(NpcCombatBrainVersion.V6, NpcCombatBrainVersion.V5.nextNative());
        assertEquals(NpcCombatBrainVersion.V7, NpcCombatBrainVersion.V6.nextNative());
        assertEquals(NpcCombatBrainVersion.V8, NpcCombatBrainVersion.V7.nextNative());
        assertEquals(NpcCombatBrainVersion.V9, NpcCombatBrainVersion.V8.nextNative());
        assertEquals(NpcCombatBrainVersion.V1, NpcCombatBrainVersion.V9.nextNative());
        assertTrue(NpcCombatBrainVersion.V9.usesSagaTree());
        assertTrue(NpcCombatBrainVersion.V9.isDmzPort());
        assertTrue(NpcCombatBrainVersion.V9.honoursToggles());
        assertEquals("v9", NpcCombatBrainVersion.V9.label());
        assertEquals(NpcCombatBrainVersion.V1, NpcCombatProfile.fromTag(new CompoundTag()).brainVersion);
    }
}
