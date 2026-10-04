package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZanzokenRingTest {

    @Test
    void onlyAPlayerCanDestroyAnImageWhenHitable() {
        assertTrue(ZanzokenRing.canDestroyImage(true, true));
        assertFalse(ZanzokenRing.canDestroyImage(false, true));
        assertFalse(ZanzokenRing.canDestroyImage(true, false));
        assertFalse(ZanzokenRing.canDestroyImage(false, false));
    }

    @Test
    void aPlayersStrikeResolvesTheWholeTrickWhenDisperseAll() {
        assertTrue(ZanzokenRing.disperseWholeRing(true, 5, true));
        assertTrue(ZanzokenRing.disperseWholeRing(true, 0, true));
    }

    @Test
    void aPlayersStrikePopsOnlyThatImageWhenDisperseAllIsOff() {
        assertFalse(ZanzokenRing.disperseWholeRing(true, 5, false));
        assertFalse(ZanzokenRing.disperseWholeRing(true, 1, false));
        assertTrue(ZanzokenRing.disperseWholeRing(true, 0, false));
    }

    @Test
    void anImageLostAnyOtherWayLeavesTheRingStanding() {
        assertFalse(ZanzokenRing.disperseWholeRing(false, 5, true));
        assertFalse(ZanzokenRing.disperseWholeRing(false, 1, false));
    }

    @Test
    void theRingEndsWhenNothingIsLeftStanding() {
        assertTrue(ZanzokenRing.disperseWholeRing(false, 0, true));
        assertTrue(ZanzokenRing.disperseWholeRing(false, -1, false));
    }
}
