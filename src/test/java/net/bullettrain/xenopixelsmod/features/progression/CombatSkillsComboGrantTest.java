package net.bullettrain.xenopixelsmod.features.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Combo-route skills are exclusive: players cannot buy them with skill points.
 * Only {@code /xenoskill grant} (administrator path) may unlock them.
 */
class CombatSkillsComboGrantTest {

    @Test
    void exclusiveIncludesRushComboAndLiftCombo() {
        assertTrue(CombatSkills.exclusive("rushcombo"));
        assertTrue(CombatSkills.exclusive("liftcombo"));
        assertFalse(CombatSkills.exclusive("power"));
        assertFalse(CombatSkills.exclusive(null));
    }

    @Test
    void comboSkillsAreRegisteredAsOneShotUnlocks() {
        CombatSkills.SkillDef rush = CombatSkills.DEFS.get("rushcombo");
        CombatSkills.SkillDef lift = CombatSkills.DEFS.get("liftcombo");
        assertNotNull(rush);
        assertNotNull(lift);
        assertEquals(3, rush.maxLevel());
        assertEquals(3, lift.maxLevel());
        assertEquals("Rush Combo", rush.title());
        assertEquals("Lift Combo", lift.title());
    }

    @Test
    void grantedPlayerCanBuyRushComboMastery() {
        assertNull(CombatSkills.selfUnlockRefusal("rushcombo", false, 1));
        assertNull(CombatSkills.selfUnlockRefusal("liftcombo", false, 2));
    }

    @Test
    void playerCannotSelfUnlockRushComboWithSkillPoints() {
        String refused = CombatSkills.selfUnlockRefusal("rushcombo", false);
        assertNotNull(refused);
        assertTrue(refused.contains("exclusive"));
        assertTrue(refused.contains("administrator"));
    }

    @Test
    void playerCannotSelfUnlockLiftComboWithSkillPoints() {
        String refused = CombatSkills.selfUnlockRefusal("liftcombo", false);
        assertNotNull(refused);
        assertTrue(refused.contains("exclusive"));
    }

    @Test
    void grantPathDoesNotRefuseExclusiveComboSkills() {
        assertNull(CombatSkills.selfUnlockRefusal("rushcombo", true));
        assertNull(CombatSkills.selfUnlockRefusal("liftcombo", true));
    }

    @Test
    void ordinarySkillsRemainSelfUnlockable() {
        assertNull(CombatSkills.selfUnlockRefusal("power", false));
    }
}
