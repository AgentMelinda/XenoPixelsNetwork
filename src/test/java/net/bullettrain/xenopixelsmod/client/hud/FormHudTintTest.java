package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.features.transformation.passive.FormPassives;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-09-29 owner: "when hakaishin transform ... his ki bars and hp turns purple or the color of
 * the aura", like Sparking turns the ki bar gold.
 */
class FormHudTintTest {

    @Test
    void theTintIsTheFormsAuraColour() {
        assertEquals(0xFF6A1B9A, FormHudTint.parse("#6A1B9A"));
        assertEquals(0xFF6A1B9A, FormHudTint.parse("6a1b9a"));
        assertEquals(FormHudTint.FALLBACK, FormHudTint.parse(""), "no aura colour: purple");
        assertEquals(FormHudTint.FALLBACK, FormHudTint.parse("not a colour"));
        assertEquals(FormHudTint.FALLBACK, FormHudTint.parse(null));
    }

    @Test
    void aDarkAuraIsLiftedSoTheBarStaysReadable() {
        int lifted = FormHudTint.readable(0xFF200030);
        int r = (lifted >> 16) & 0xFF, b = lifted & 0xFF;
        assertTrue(Math.max(r, b) >= 160, "a near-black aura still shows as a bright bar");
        assertEquals(0xFFE040FB, FormHudTint.readable(0xFFE040FB), "a bright one is left alone");
    }

    @Test
    void hakaishinTintsTheHudAndUltraEgoDoesNot() throws Exception {
        try (var r = new InputStreamReader(FormHudTintTest.class.getResourceAsStream(
                "/data/xenopixelsmod/form_passives.json"), StandardCharsets.UTF_8)) {
            var table = FormPassives.parse(r);
            assertTrue(table.get("xenopixels_hakaishin.hakaishin").hudTint());
            assertFalse(table.get("xenopixels_gods_forms.ue").hudTint());
        }
    }
}
