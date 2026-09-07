package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZanzokenWindowTest {

    @Test
    void aPressIsLiveOnlyInsideItsWindow() {
        assertTrue(ZanzokenWindow.armed(100, 108));
        assertTrue(ZanzokenWindow.armed(108, 108));
        assertFalse(ZanzokenWindow.armed(109, 108));
        // Never pressed at all.
        assertFalse(ZanzokenWindow.armed(100, null));
    }

    @Test
    void cooldownBlocksTheNextPressUntilItElapses() {
        assertTrue(ZanzokenWindow.ready(100, null));
        assertFalse(ZanzokenWindow.ready(100, 140));
        assertTrue(ZanzokenWindow.ready(140, 140));
    }

    @Test
    void aStampLeftOverFromARestartedClockDoesNotBrickTheTechnique() {
        // The server tick counter restarts at zero on every world load, while the cooldown map is
        // static and outlives the server. A stamp from a long previous session used to sit far
        // ahead of the new clock and refuse Zanzoken until the new server caught up.
        int staleFromLastSession = 36000;
        assertTrue(ZanzokenWindow.ready(0, staleFromLastSession));
        assertTrue(ZanzokenWindow.ready(40, staleFromLastSession));
        // An ordinary cooldown just ahead of now is still honoured.
        assertFalse(ZanzokenWindow.ready(100, 140));
    }

    @Test
    void aStaleWindowStampNeverCountsAsArmed() {
        assertFalse(ZanzokenWindow.armed(0, 36000));
        assertTrue(ZanzokenWindow.armed(100, 108));
    }

    @Test
    void onlyARealHitFromALivingAttackerIsDodged() {
        assertTrue(ZanzokenWindow.dodges(true, true, true, true));
        // Disabled on the server.
        assertFalse(ZanzokenWindow.dodges(false, true, true, true));
        // Mistimed press.
        assertFalse(ZanzokenWindow.dodges(true, false, true, true));
        // Nothing to vanish behind: fall damage, drowning, a falling anvil.
        assertFalse(ZanzokenWindow.dodges(true, true, false, true));
        // A hit that would not have hurt anyway is not worth the technique.
        assertFalse(ZanzokenWindow.dodges(true, true, true, false));
    }
}
