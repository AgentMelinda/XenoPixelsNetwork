package net.bullettrain.xenopixelsmod.fx.aura;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 Motif table: brightness multipliers only (no edge/thunder density).
 * Form ids verified from JSON 2026-10-03.
 */
class AuraMotifTableTest {

    @Test
    void unknownKeysAreNeutralAndKnownKeysAreStable() {
        assertEquals("saiyan/xenopixels_gods_forms/ssb",
                AuraMotifTable.key("saiyan", "xenopixels_gods_forms", "ssb"));
        AuraMotifTable.Motif neu = AuraMotifTable.motif("nope/nope/nope");
        assertEquals(1.0f, neu.outerBrightness(), 1e-6f);
        assertEquals(1.0f, neu.innerBrightness(), 1e-6f);
        AuraMotifTable.Motif rose = AuraMotifTable.motif(
                AuraMotifTable.key("saiyan", "xenopixels_gods_forms", "ssrose"));
        assertTrue(rose.innerBrightness() >= 1.0f);

        assertEquals("saiyan/xenopixels_saga_forms/trunks_ikari",
                AuraMotifTable.key("saiyan", "xenopixels_saga_forms", "trunks_ikari"));
        AuraMotifTable.Motif ikari = AuraMotifTable.motif(
                AuraMotifTable.key("saiyan", "xenopixels_saga_forms", "trunks_ikari"));
        assertTrue(ikari.outerBrightness() >= 1.0f);
    }

    @Test
    void seededFormsMatchPhase1Multipliers() {
        AuraMotifTable.Motif ssb = AuraMotifTable.motif(
                AuraMotifTable.key("saiyan", "xenopixels_gods_forms", "ssb"));
        assertEquals(1.0f, ssb.outerBrightness(), 1e-6f);
        assertEquals(1.0f, ssb.innerBrightness(), 1e-6f);

        AuraMotifTable.Motif rose = AuraMotifTable.motif(
                AuraMotifTable.key("saiyan", "xenopixels_gods_forms", "ssrose"));
        assertEquals(1.0f, rose.outerBrightness(), 1e-6f);
        assertEquals(1.05f, rose.innerBrightness(), 1e-6f);

        AuraMotifTable.Motif ui = AuraMotifTable.motif(
                AuraMotifTable.key("saiyan", "xenopixels_gods_forms", "ui"));
        assertEquals(1.02f, ui.outerBrightness(), 1e-6f);
        assertEquals(1.0f, ui.innerBrightness(), 1e-6f);

        AuraMotifTable.Motif ikari = AuraMotifTable.motif(
                AuraMotifTable.key("saiyan", "xenopixels_saga_forms", "trunks_ikari"));
        assertEquals(1.08f, ikari.outerBrightness(), 1e-6f);
        assertEquals(1.0f, ikari.innerBrightness(), 1e-6f);
    }

    @Test
    void nullOrBlankPartsNeverThrowAndStayNeutralWhenUnknown() {
        assertEquals("//", AuraMotifTable.key(null, null, null));
        assertEquals(1.0f, AuraMotifTable.motif(null).outerBrightness(), 1e-6f);
        assertEquals(1.0f, AuraMotifTable.motif("").innerBrightness(), 1e-6f);
        assertEquals(1.0f, AuraMotifTable.motif(AuraMotifTable.key("saiyan", "", "")).outerBrightness(),
                1e-6f);
    }
}
