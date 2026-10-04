package net.bullettrain.xenopixelsmod.dmz.form;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-10-02 owner: "make ikari only stack abale with ssjforms not ss gods forms ... ssj1 to 3". */
class IkariStackRulesTest {

    @Test
    void superSaiyanOneToThree() {
        assertTrue(IkariStackRules.allows("ssgrades", "supersaiyan"));
        assertTrue(IkariStackRules.allows("ssgrades", "supersaiyangrade3"));
        assertTrue(IkariStackRules.allows("supersaiyan", "supersaiyanmastered"));
        assertTrue(IkariStackRules.allows("supersaiyan", "supersaiyan2"));
        assertTrue(IkariStackRules.allows("SuperSaiyan", "SuperSaiyan3"));
    }

    @Test
    void nothingElse() {
        assertFalse(IkariStackRules.allows("supersaiyan", "supersaiyan4"));
        assertFalse(IkariStackRules.allows("xenopixels_gods_forms", "ssb"));
        assertFalse(IkariStackRules.allows("xenopixels_gods_forms", "ssb3"));
        assertFalse(IkariStackRules.allows("xenopixels_fan_ss", "ssj5"));
        assertFalse(IkariStackRules.allows("legendaryforms", "ikari"));
        assertFalse(IkariStackRules.allows("oozaru", "oozaru"));
        assertFalse(IkariStackRules.allows("", ""), "base form");
        assertFalse(IkariStackRules.allows(null, null));
    }

    @Test
    void onlyTheIkariGroupIsRuled() {
        assertTrue(IkariStackRules.isIkari("xenopixels_ikari"));
        assertFalse(IkariStackRules.isIkari("kaioken"));
        assertFalse(IkariStackRules.isIkari(null));
    }
}
