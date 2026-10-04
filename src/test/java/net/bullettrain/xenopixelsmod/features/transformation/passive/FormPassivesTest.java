package net.bullettrain.xenopixelsmod.features.transformation.passive;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-09-29 owner: Ultra Ego negates weaker opponents' damage, pierces defense, destroys weaker ki
 * and deletes weaker projectiles; Hakaishin erases every attack; Ultra Instinct dodges by mastery,
 * and "if 2 ppl have UI their dodge chances are gone".
 */
class FormPassivesTest {

    @Test
    void weakerIsABattlePowerRatio() {
        assertTrue(FormPassives.weaker(79.0, 100.0, 0.8f));
        assertFalse(FormPassives.weaker(80.0, 100.0, 0.8f), "exactly at the ratio is not weaker");
        assertFalse(FormPassives.weaker(150.0, 100.0, 0.8f));
        assertFalse(FormPassives.weaker(0.0, 0.0, 0.8f), "nothing known: never weaker");
        assertFalse(FormPassives.weaker(50.0, 0.0, 0.8f), "the defender has no power level: never weaker");
        assertFalse(FormPassives.weaker(0.0, 100.0, 0.8f), "the attacker's is unknown: not a guess");
    }

    @Test
    void dodgeGrowsWithMastery() {
        assertEquals(0.20f, FormPassives.dodgeChance(0.20f, 0.60f, 0.0, 100.0), 1e-6);
        assertEquals(0.40f, FormPassives.dodgeChance(0.20f, 0.60f, 50.0, 100.0), 1e-6);
        assertEquals(0.60f, FormPassives.dodgeChance(0.20f, 0.60f, 100.0, 100.0), 1e-6);
        assertEquals(0.60f, FormPassives.dodgeChance(0.20f, 0.60f, 500.0, 100.0), 1e-6, "clamped");
        assertEquals(0.20f, FormPassives.dodgeChance(0.20f, 0.60f, 50.0, 0.0), 1e-6,
                "no max mastery known: the floor");
    }

    @Test
    void twoUltraInstinctUsersCancelEachOthersDodge() {
        FormPassive ui = FormPassive.builder().dodge(0.2f, 0.6f).build();
        assertTrue(FormPassives.mayDodge(ui, FormPassive.NONE));
        assertFalse(FormPassives.mayDodge(ui, ui));
        assertFalse(FormPassives.mayDodge(FormPassive.NONE, FormPassive.NONE));
    }

    @Test
    void theBundledTableGivesEachGodFormItsPassives() throws Exception {
        Map<String, FormPassive> table;
        try (Reader r = new InputStreamReader(FormPassivesTest.class.getResourceAsStream(
                "/data/xenopixelsmod/form_passives.json"), StandardCharsets.UTF_8)) {
            table = FormPassives.parse(r);
        }
        FormPassive ue = table.get("xenopixels_gods_forms.ue");
        assertNotNull(ue);
        assertTrue(ue.weakerImmunity());
        assertEquals(0.8f, ue.weakerRatio(), 1e-6);
        assertEquals(0.35f, ue.defPenBonus(), 1e-6);
        assertTrue(ue.deleteWeakProjectiles());
        assertTrue(ue.punchBreaksWeakKi());
        assertFalse(ue.hakaiMantle());

        assertEquals(0.10f, table.get("xenopixels_gods_forms.ui_sign").dodgeMin(), 1e-6);
        assertEquals(0.35f, table.get("xenopixels_gods_forms.ui_sign").dodgeMax(), 1e-6);
        assertEquals(0.60f, table.get("xenopixels_gods_forms.ui").dodgeMax(), 1e-6);
        assertTrue(table.get("xenopixels_hakaishin.hakaishin").hakaiMantle());
    }

    @Test
    void anUnknownFormHasNoPassives() {
        Map<String, FormPassive> table = FormPassives.parse(new StringReader("{\"a.b\":{\"hakaiMantle\":true}}"));
        assertSame(FormPassive.NONE, table.getOrDefault("x.y", FormPassive.NONE));
        assertSame(FormPassive.NONE, FormPassives.lookup(table, "", ""));
        assertTrue(FormPassives.lookup(table, "A", "B").hakaiMantle(), "ids are case-insensitive");
        assertSame(FormPassive.NONE, FormPassives.parse(new StringReader("not json")).getOrDefault("a.b",
                FormPassive.NONE), "a broken file is ignored, not a crash");
    }
}
