package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcAuraStyleTest {
    @Test
    void roundTripsEveryLayerOverride() {
        NpcAuraStyle source = new NpcAuraStyle();
        source.enabled = true;
        source.primaryColor = "#000000";
        source.primaryType = "kakarot";
        source.primaryLayer = 3;
        source.extraConfigured = true;
        source.extraEnabled = true;
        source.extraColor = "#AABBCC";
        source.extraType = "god";
        source.extraLayer = 5;
        source.lightningConfigured = true;
        source.lightningEnabled = false;
        source.lightningColor = "#102030";

        NpcAuraStyle decoded = NpcAuraStyle.load(source.save());
        assertTrue(decoded.enabled);
        assertEquals("#000000", decoded.primaryColor);
        assertEquals("kakarot", decoded.primaryType);
        assertEquals(3, decoded.primaryLayer);
        assertTrue(decoded.extraConfigured);
        assertTrue(decoded.extraEnabled);
        assertEquals("#AABBCC", decoded.extraColor);
        assertEquals("god", decoded.extraType);
        assertEquals(5, decoded.extraLayer);
        assertTrue(decoded.lightningConfigured);
        assertFalse(decoded.lightningEnabled);
        assertEquals("#102030", decoded.lightningColor);
    }

    @Test
    void clampsLayerIndexesToDmzRange() {
        assertEquals(-1, NpcAuraStyle.clampLayer(-50));
        assertEquals(0, NpcAuraStyle.clampLayer(0));
        assertEquals(6, NpcAuraStyle.clampLayer(99));
    }
}
