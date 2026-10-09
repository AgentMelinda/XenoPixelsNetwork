package net.bullettrain.xenopixelsmod.client.combat.v2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two key gestures behind "tap E for the inventory, hold it to guard" and "double-tap a
 * forward to chase".
 */
class TapGestureTest {

    // ---- tap versus hold ----

    @Test
    void aQuickPressIsATap() {
        assertTrue(TapGesture.isTap(1, false));
        assertTrue(TapGesture.isTap(TapGesture.TAP_TICKS, false));
    }

    @Test
    void aLongPressIsAHold() {
        assertFalse(TapGesture.isTap(TapGesture.TAP_TICKS + 1, false));
        assertFalse(TapGesture.isTap(40, false));
    }

    /** Blocking a hit or throwing a grab off the guard key must never also open the inventory. */
    @Test
    void aPressThatDidItsJobIsNeverATap() {
        assertFalse(TapGesture.isTap(1, true));
        assertFalse(TapGesture.isTap(TapGesture.TAP_TICKS, true));
    }

    @Test
    void aKeyThatWasNeverDownIsNotATap() {
        assertFalse(TapGesture.isTap(0, false));
    }

    // ---- double tap ----

    /** Feeds one tick per character: 'x' the key is down, '.' it is up. Returns the firing ticks. */
    private static String run(TapGesture.DoubleTap gesture, String keys) {
        StringBuilder fired = new StringBuilder();
        for (int i = 0; i < keys.length(); i++) {
            fired.append(gesture.update(keys.charAt(i) == 'x') ? '!' : '.');
        }
        return fired.toString();
    }

    @Test
    void twoQuickPressesFireOnTheSecond() {
        assertTrue(run(new TapGesture.DoubleTap(), "x.x").endsWith("!"));
    }

    @Test
    void holdingAKeyIsOnePressHoweverLongItIsHeld() {
        assertFalse(run(new TapGesture.DoubleTap(), "xxxxxxxxxxxxxxxx").contains("!"));
    }

    @Test
    void twoPressesTooFarApartAreTwoSinglePresses() {
        String gap = ".".repeat(TapGesture.DOUBLE_TAP_TICKS + 1);
        assertFalse(run(new TapGesture.DoubleTap(), "x" + gap + "x").contains("!"));
    }

    @Test
    void theSecondPressMayLandOnTheLastTickOfTheWindow() {
        String gap = ".".repeat(TapGesture.DOUBLE_TAP_TICKS - 1);
        assertTrue(run(new TapGesture.DoubleTap(), "x" + gap + "x").endsWith("!"));
    }

    /** Three quick presses are one double-tap and the start of another, not two double-taps. */
    @Test
    void aThirdPressStartsANewGesture() {
        assertTrue(run(new TapGesture.DoubleTap(), "x.x.x").equals("..!.."));
        assertTrue(run(new TapGesture.DoubleTap(), "x.x.x.x").equals("..!...!"));
    }

    @Test
    void clearingForgetsAPendingFirstPress() {
        TapGesture.DoubleTap gesture = new TapGesture.DoubleTap();
        gesture.update(true);
        gesture.update(false);
        gesture.clear();
        assertFalse(gesture.update(true), "the press after a clear is a first press");
    }

    @Test
    void pressedPeeksWithoutAdvancing() {
        TapGesture.DoubleTap gesture = new TapGesture.DoubleTap();
        assertTrue(gesture.pressed(true));
        assertTrue(gesture.pressed(true), "asking twice must not consume the press");
        gesture.update(true);
        assertFalse(gesture.pressed(true), "a key still held is not a fresh press");
        assertFalse(gesture.pressed(false));
    }
}
