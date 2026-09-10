package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How long a Zanzoken ring survives losing an image.
 *
 * <p>Pinned here because the old rule — any removal of any image ends the whole ring — was only
 * wrong in a fight, where something is always swinging. A unit test states the intent in a place
 * that does not need a fight to check it.
 */
class ZanzokenRingTest {

    @Test
    void onlyAPlayerCanDestroyAnImage() {
        assertTrue(ZanzokenRing.canDestroyImage(true));
        // A mob, splash damage, the environment: they swing, and the image stands. Being drawn onto
        // an image and kept there is the entire point of the technique.
        assertFalse(ZanzokenRing.canDestroyImage(false));
    }

    @Test
    void aPlayersStrikeResolvesTheWholeTrick() {
        // They committed and guessed, so the ring goes even with five bodies left standing.
        assertTrue(ZanzokenRing.disperseWholeRing(true, 5));
        assertTrue(ZanzokenRing.disperseWholeRing(true, 0));
    }

    @Test
    void anImageLostAnyOtherWayLeavesTheRingStanding() {
        assertFalse(ZanzokenRing.disperseWholeRing(false, 5));
        assertFalse(ZanzokenRing.disperseWholeRing(false, 1));
    }

    @Test
    void theRingEndsWhenNothingIsLeftStanding() {
        assertTrue(ZanzokenRing.disperseWholeRing(false, 0));
        // Defensive: a miscounted ring must end, not linger with a negative population.
        assertTrue(ZanzokenRing.disperseWholeRing(false, -1));
    }
}
