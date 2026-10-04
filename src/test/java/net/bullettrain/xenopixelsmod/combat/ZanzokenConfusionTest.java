package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZanzokenConfusionTest {

    @Test
    void combatBrainIsFooledWhenTheBrainPageAllowsIt() {
        assertTrue(ZanzokenConfusion.confuse(true, true, true, true));
    }

    @Test
    void combatBrainSeesThroughWhenTheBrainPageRefuses() {
        assertFalse(ZanzokenConfusion.confuse(true, true, true, false));
    }

    @Test
    void vanillaMobsStillGetFooledWhenTheFlagIsOn() {
        assertTrue(ZanzokenConfusion.confuse(true, true, false, false));
        assertTrue(ZanzokenConfusion.confuse(true, true, false, true));
    }

    @Test
    void nobodyIsFooledWhenZanzokenOrTheFlagIsOff() {
        assertFalse(ZanzokenConfusion.confuse(false, true, true, true));
        assertFalse(ZanzokenConfusion.confuse(true, false, false, true));
        assertFalse(ZanzokenConfusion.confuse(true, false, true, true));
    }

    @Test
    void onlyAttackersInsideDetectRangeAreFooled() {
        assertTrue(net.bullettrain.xenopixelsmod.combat.clone.CloneDetectRange.withinSqr(16 * 16, 32));
        assertFalse(net.bullettrain.xenopixelsmod.combat.clone.CloneDetectRange.withinSqr(33 * 33, 32));
        assertTrue(net.bullettrain.xenopixelsmod.combat.clone.CloneDetectRange.withinSqr(48 * 48, 64));
    }
}
