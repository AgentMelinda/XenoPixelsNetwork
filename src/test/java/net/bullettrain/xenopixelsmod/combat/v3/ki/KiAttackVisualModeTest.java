package net.bullettrain.xenopixelsmod.combat.v3.ki;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class KiAttackVisualModeTest {
    @Test void parsesOwnerCommandLabels() {
        assertEquals(KiAttackVisualMode.DMZ, KiAttackVisualMode.parse("dmz"));
        assertEquals(KiAttackVisualMode.AAA, KiAttackVisualMode.parse("aaa"));
        assertEquals(KiAttackVisualMode.NEWEFFECTS, KiAttackVisualMode.parse("neweffects"));
    }

    @Test void legacyBoolMapsToAaaOrDmz() {
        assertEquals(KiAttackVisualMode.AAA, KiAttackVisualMode.fromLegacy(true));
        assertEquals(KiAttackVisualMode.DMZ, KiAttackVisualMode.fromLegacy(false));
    }

    @Test void neweffectsUsesOwnedHdStackLikeAaa() {
        assertTrue(KiAttackVisualMode.NEWEFFECTS.usesOwnedHdVisuals());
        assertTrue(KiAttackVisualMode.AAA.usesOwnedHdVisuals());
        assertFalse(KiAttackVisualMode.DMZ.usesOwnedHdVisuals());
    }

    @Test void unknownLabelIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> KiAttackVisualMode.parse("particles"));
    }
}
