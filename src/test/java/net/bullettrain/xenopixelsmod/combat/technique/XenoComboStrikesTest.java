package net.bullettrain.xenopixelsmod.combat.technique;

import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import net.bullettrain.xenopixelsmod.combat.combo.ComboRouteCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoComboStrikesTest {

    @Test
    void registerPutsBothIdsInStrikeRegistry() {
        XenoComboStrikes.register();
        assertNotNull(PredefinedTechniques.STRIKE_REGISTRY.get(ComboRouteCatalog.RUSH_COMBO_STRIKE));
        assertNotNull(PredefinedTechniques.STRIKE_REGISTRY.get(ComboRouteCatalog.LIFT_COMBO_STRIKE));
        assertTrue(PredefinedTechniques.isPredefinedTechniqueId(ComboRouteCatalog.RUSH_COMBO_STRIKE));
        assertTrue(PredefinedTechniques.isPredefinedTechniqueId(ComboRouteCatalog.LIFT_COMBO_STRIKE));
    }

    @Test
    void isComboIdOnlyMatchesComboIds() {
        assertTrue(XenoComboStrikes.isComboId(ComboRouteCatalog.RUSH_COMBO_STRIKE));
        assertTrue(XenoComboStrikes.isComboId(ComboRouteCatalog.LIFT_COMBO_STRIKE));
        assertFalse(XenoComboStrikes.isComboId(XenoRushTechniques.RUSH_LEFT));
        assertFalse(XenoComboStrikes.isComboId("dragonminez:some_strike"));
        assertFalse(XenoComboStrikes.isComboId(null));
    }

    @Test
    void rushAutoUnlockFalseDoesNotTreatLoginAsGrant() {
        assertTrue(XenoRushTechniques.shouldUnlockRushKit(true, 0));
        assertTrue(XenoRushTechniques.shouldUnlockRushKit(false, 1));
        assertFalse(XenoRushTechniques.shouldUnlockRushKit(false, 0));
        assertEquals(false, XenoRushTechniques.shouldUnlockRushKit(false, 0));
    }

    @Test
    void comboUnlockRequiresGrantedSkill() {
        assertFalse(XenoComboStrikes.shouldUnlock(0));
        assertTrue(XenoComboStrikes.shouldUnlock(1));
    }

    @Test
    void comboIdsAreOwnedForCostOverride() {
        assertTrue(XenoComboStrikes.isComboId(XenoComboStrikes.RUSH_COMBO));
        assertTrue(XenoComboStrikes.isComboId(XenoComboStrikes.LIFT_COMBO));
    }

    @Test
    void castRefusesWhenFeatureOffOrUngrantedOrBusy() {
        assertEquals("disabled", XenoComboStrikes.refuseReason(false, true, true, true, false, true, true, true));
        assertEquals("route_off", XenoComboStrikes.refuseReason(true, false, true, true, false, true, true, true));
        assertEquals("ungranted", XenoComboStrikes.refuseReason(true, true, false, true, false, true, true, true));
        assertEquals("permission", XenoComboStrikes.refuseReason(true, true, true, false, false, true, true, true));
        assertEquals("busy", XenoComboStrikes.refuseReason(true, true, true, true, true, true, true, true));
        assertEquals("no_target", XenoComboStrikes.refuseReason(true, true, true, true, false, false, true, true));
        assertEquals("too_far", XenoComboStrikes.refuseReason(true, true, true, true, false, true, false, true));
        assertEquals("pvp_pve", XenoComboStrikes.refuseReason(true, true, true, true, false, true, true, false));
        assertEquals(null, XenoComboStrikes.refuseReason(true, true, true, true, false, true, true, true));
    }
}
