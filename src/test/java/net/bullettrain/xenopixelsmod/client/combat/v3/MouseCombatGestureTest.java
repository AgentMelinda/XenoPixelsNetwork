package net.bullettrain.xenopixelsmod.client.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.bullettrain.xenopixelsmod.client.combat.v3.MouseCombatGesture.Action;
import org.junit.jupiter.api.Test;

class MouseCombatGestureTest {
    private static List<Action> hold(MouseCombatGesture gesture, int ticks) {
        List<Action> seen = new ArrayList<>();
        for (int i = 0; i < ticks; i++) seen.add(gesture.update(true, true));
        return seen;
    }

    @Test void releaseAfterSevenHeldTicksIsExactlyOneTap() {
        var gesture = new MouseCombatGesture();
        assertTrue(hold(gesture, 7).stream().allMatch(a -> a == Action.NONE));
        assertEquals(Action.TAP, gesture.update(false, true));
        assertEquals(Action.NONE, gesture.update(false, true));
    }

    @Test void eighthHeldTickStartsChargeOnceAndReleaseIsNeverATap() {
        var gesture = new MouseCombatGesture();
        List<Action> seen = hold(gesture, 12);
        assertEquals(Action.START, seen.get(7));
        assertEquals(1, seen.stream().filter(a -> a == Action.START).count());
        assertTrue(gesture.charging());
        assertEquals(Action.RELEASE, gesture.update(false, true));
        assertFalse(gesture.charging());
        assertEquals(Action.NONE, gesture.update(false, true));
    }

    @Test void lockLostOnTheEighthTickYieldsNoChargeAndNoTap() {
        var gesture = new MouseCombatGesture();
        hold(gesture, 7);
        assertEquals(Action.NONE, gesture.update(true, false));
        assertFalse(gesture.charging());
        assertEquals(Action.NONE, gesture.update(true, true));
        assertEquals(Action.NONE, gesture.update(false, true));
    }

    @Test void interruptionDuringChargeCancelsAndHeldButtonCannotRestart() {
        var gesture = new MouseCombatGesture();
        hold(gesture, 10);
        assertEquals(Action.CANCEL, gesture.update(true, false));
        assertTrue(hold(gesture, 30).stream().allMatch(a -> a == Action.NONE));
        assertEquals(Action.NONE, gesture.update(false, true));
        hold(gesture, 3);
        assertEquals(Action.TAP, gesture.update(false, true));
    }

    @Test void progressFillsOverTwentyChargingTicks() {
        var gesture = new MouseCombatGesture();
        hold(gesture, 8);
        assertEquals(0f, gesture.progress(), 1e-6);
        hold(gesture, 10);
        assertEquals(0.5f, gesture.progress(), 1e-6);
        hold(gesture, 40);
        assertEquals(1f, gesture.progress(), 1e-6);
    }

    @Test void simultaneousHoldsHaveOneOwnerUntilBothAreReleased() {
        var pair = new MouseCombatGesture.Pair();
        assertEquals(Action.NONE, pair.update(true, false, true).left());
        for (int i = 0; i < 6; i++) {
            var both = pair.update(true, true, true);
            assertEquals(Action.NONE, both.right());
        }
        var start = pair.update(true, true, true);
        assertEquals(Action.START, start.left());
        assertEquals(Action.NONE, start.right());
        var release = pair.update(false, true, true);
        assertEquals(Action.RELEASE, release.left());
        // The other button was held through someone else's gesture: it must come up first.
        for (int i = 0; i < 20; i++) assertEquals(Action.NONE, pair.update(false, true, true).right());
        assertEquals(Action.NONE, pair.update(false, false, true).right());
        pair.update(false, true, true);
        assertEquals(Action.TAP, pair.update(false, false, true).right());
    }

    @Test void resetClearsBothButtons() {
        var pair = new MouseCombatGesture.Pair();
        for (int i = 0; i < 9; i++) pair.update(true, false, true);
        assertTrue(pair.left().charging());
        pair.reset();
        assertFalse(pair.left().charging());
        assertFalse(pair.right().charging());
        var after = pair.update(false, false, true);
        assertEquals(Action.NONE, after.left());
        assertEquals(Action.NONE, after.right());
    }
}
