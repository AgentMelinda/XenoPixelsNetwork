package net.bullettrain.xenopixelsmod.anim;

import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoTechniqueAnimBindingsTest {

    @Test
    void jsonRoundTripsHakaiAndComboSlots() {
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("HAKAI_HOLD", "my_hold");
        bindings.put("JAB_LEFT", "my_jab");

        Map<String, String> parsed = XenoTechniqueAnimBindings.parse(
                XenoTechniqueAnimBindings.write(bindings));

        assertEquals(2, parsed.size());
        assertEquals("my_hold", parsed.get("HAKAI_HOLD"));
        assertEquals("my_jab", parsed.get("JAB_LEFT"));
    }

    @Test
    void unknownSlotsAreIgnored() {
        Map<String, String> parsed = XenoTechniqueAnimBindings.parse(
                "{\"NOT_A_SLOT\": \"clip\", \"HOOK_RIGHT\": \"hook_edit\"}");
        assertEquals(1, parsed.size());
        assertEquals("hook_edit", parsed.get("HOOK_RIGHT"));
    }

    @Test
    void clipNamesAreSanitised() {
        Map<String, String> parsed = XenoTechniqueAnimBindings.parse(
                "{\"HAKAI_FIRE\": \"My Fire!\"}");
        assertEquals("my_fire_", parsed.get("HAKAI_FIRE"));
    }

    @Test
    void resolveFallsBackToShippedHakaiNames() {
        assertEquals(DmzAnimHelper.HAKAI_HOLD, TechniqueAnimSlot.HAKAI_HOLD.defaultAnim());
        assertEquals(DmzAnimHelper.HAKAI_FIRE, TechniqueAnimSlot.HAKAI_FIRE.defaultAnim());
        assertEquals("combat.xeno_hakai_hold", XenoTechniqueAnimBindings.defaultAnim("hakai_hold"));
    }

    @Test
    void normalizeAcceptsIntentAndHakaiCaseInsensitively() {
        assertEquals("JAB_RIGHT", XenoTechniqueAnimBindings.normalizeSlot("jab_right"));
        assertEquals("HAKAI_HOLD", XenoTechniqueAnimBindings.normalizeSlot("  hakai_hold "));
        assertEquals(Bt3AnimationIntent.HOOK_LEFT, XenoTechniqueAnimBindings.intentOf("hook_left"));
        assertNull(XenoTechniqueAnimBindings.normalizeSlot("nope"));
        assertNull(XenoTechniqueAnimBindings.normalizeSlot(null));
    }

    @Test
    void malformedJsonParsesToNothing() {
        assertTrue(XenoTechniqueAnimBindings.parse("not json").isEmpty());
        assertTrue(XenoTechniqueAnimBindings.parse("").isEmpty());
    }
}
