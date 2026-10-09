package net.bullettrain.xenopixelsmod.client.combat.v2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DragonDashGestureTest {
    @Test void firstDashKeepsItsChargeAndFiresOnRelease() {
        var gesture = new DragonDashGesture();
        for (int tick = 0; tick < 20; tick++) assertEquals(-1, gesture.update(true, true, false));
        assertEquals(100, gesture.update(false, true, false));
        assertEquals(-1, gesture.update(false, true, false));
    }

    @Test void continuationFiresOnPressAndNeverAgainOnHoldOrRelease() {
        var gesture = new DragonDashGesture();
        assertEquals(0, gesture.update(true, true, true));
        for (int tick = 0; tick < 80; tick++) assertEquals(-1, gesture.update(true, true, true));
        assertEquals(-1, gesture.update(false, true, true));
        assertEquals(0, gesture.update(true, true, true));
    }

    @Test void aWindowOpeningMidHoldDoesNotCreateAnExtraPress() {
        var gesture = new DragonDashGesture();
        assertEquals(-1, gesture.update(true, true, false));
        assertEquals(-1, gesture.update(true, true, true));
        assertEquals(10, gesture.update(false, true, true));
    }

    @Test void interruptedHoldMustBeReleasedBeforeTheNextDash() {
        var gesture = new DragonDashGesture();
        gesture.update(true, true, false);
        gesture.update(true, false, false);
        assertEquals(-1, gesture.update(true, true, true));
        assertEquals(-1, gesture.update(false, true, true));
        assertEquals(0, gesture.update(true, true, true));
    }

    @Test void holdingOutsideCombatDoesNotActivateWhenLockAppears() {
        var gesture = new DragonDashGesture();
        gesture.update(true, false, false);
        assertEquals(-1, gesture.update(true, true, true));
        assertEquals(-1, gesture.update(false, true, true));
        assertEquals(0, gesture.update(true, true, true));
    }
}
