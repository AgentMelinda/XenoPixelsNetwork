package net.bullettrain.xenopixelsmod.client.combat.v2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.bullettrain.xenopixelsmod.client.combat.v2.ChargeGesture.Action.*;

class ChargeGestureTest {
    @Test void quickClickProducesOneTapOnlyOnRelease() {
        ChargeGesture g = new ChargeGesture();
        assertEquals(NONE, g.update(true, true));
        assertEquals(TAP, g.update(false, true));
        assertEquals(NONE, g.update(false, true));
    }
    @Test void holdStartsOnceAndReleasesOnceWithoutAnExtraTap() {
        ChargeGesture g = new ChargeGesture();
        for (int i = 1; i < 8; i++) assertEquals(NONE, g.update(true, true));
        assertEquals(START, g.update(true, true));
        for (int i = 0; i < 19; i++) assertEquals(NONE, g.update(true, true));
        assertTrue(g.progress() < 1);
        assertEquals(NONE, g.update(true, true));
        assertEquals(1, g.progress());
        assertEquals(RELEASE, g.update(false, true));
        assertFalse(g.charging());
        assertEquals(NONE, g.update(false, true));
    }
    @Test void droppedLockOrScreenCancelsAndNeedsFreshPress() {
        ChargeGesture g = new ChargeGesture();
        for (int i = 0; i < 10; i++) g.update(true, true);
        assertEquals(CANCEL, g.update(true, false));
        for (int i = 0; i < 30; i++) assertEquals(NONE, g.update(true, true));
        assertEquals(NONE, g.update(false, true));
        g.update(true, true);
        assertEquals(TAP, g.update(false, true));
    }
    @Test void PressStartedOutsideCombatCannotBecomeAChargeAfterLocking() {
        ChargeGesture g = new ChargeGesture();
        g.update(true, false);
        for (int i = 0; i < 30; i++) assertEquals(NONE, g.update(true, true));
        assertEquals(NONE, g.update(false, true));
    }
}
