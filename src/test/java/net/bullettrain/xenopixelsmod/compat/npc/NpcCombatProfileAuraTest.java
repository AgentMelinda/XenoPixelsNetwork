package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCombatProfileAuraTest {
    @Test
    void explicitBlackAuraIsDifferentFromUnsetAndSurvivesNbt() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.setAuraColor("#000000");

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(source.toTag());
        assertEquals(0, decoded.auraColor);
        assertEquals("#000000", decoded.auraColorHex);
        assertEquals(0x000000, NpcAuraResolver.baseRgb(decoded));
    }

    @Test
    void visualOptionsRoundTripActiveStackAndIndependentEffects() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.selectedFormGroup = "super_saiyan";
        source.selectedFormId = "ssj2";
        source.stackGroup = "kaioken";
        source.stackId = "x20";
        source.selectedStackGroup = "kaioken";
        source.selectedStackId = "x20";
        source.auraOn = true;
        source.haloOn = true;
        source.auraRocks = false;
        source.auraSparking = false;
        source.auraLightning = true;

        NpcAuraStyle stack = source.auraStyle(true, "kaioken", "x20", true);
        stack.enabled = true;
        stack.primaryColor = "#CC0000";
        stack.extraConfigured = true;
        stack.extraEnabled = true;

        NpcCombatProfile decoded = new NpcCombatProfile();
        decoded.applyVisualOptions(source.visualOptionsTag());
        assertEquals("super_saiyan", decoded.selectedFormGroup);
        assertEquals("ssj2", decoded.selectedFormId);
        assertEquals("kaioken", decoded.stackGroup);
        assertEquals("x20", decoded.stackId);
        assertTrue(decoded.auraOn);
        assertTrue(decoded.haloOn);
        assertFalse(decoded.auraRocks);
        assertFalse(decoded.auraSparking);
        assertTrue(decoded.auraLightning);
        NpcAuraStyle restored = decoded.auraStyle(true, "kaioken", "x20", false);
        assertNotNull(restored);
        assertEquals("#CC0000", restored.primaryColor);
        assertTrue(restored.extraEnabled);
    }

    @Test
    void auraKeysAreCaseInsensitive() {
        NpcCombatProfile profile = new NpcCombatProfile();
        NpcAuraStyle created = profile.auraStyle(false, "God Forms", "Blue", true);
        assertEquals(created, profile.auraStyle(false, "god forms", "blue", false));
    }
}
