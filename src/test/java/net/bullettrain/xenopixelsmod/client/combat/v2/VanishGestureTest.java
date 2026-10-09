package net.bullettrain.xenopixelsmod.client.combat.v2;

import net.bullettrain.xenopixelsmod.combat.v2.V2Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VanishGestureTest {
    @Test
    void eachSideFiresOnItsSecondPress() {
        for (boolean left : new boolean[]{true, false}) {
            VanishGesture gesture = new VanishGesture();
            assertEquals(V2Direction.NONE, gesture.update(true, left, !left, 1000));
            gesture.update(true, false, false, 1100);
            assertEquals(left ? V2Direction.LEFT : V2Direction.RIGHT,
                    gesture.update(true, left, !left, 1200));
        }
    }

    @Test
    void timingMatchesTheLegacy280MillisecondWindow() {
        for (long interval : new long[]{280, 281}) {
            VanishGesture gesture = new VanishGesture();
            gesture.update(true, true, false, 1000);
            gesture.update(true, false, false, 1100);
            assertEquals(interval == 280 ? V2Direction.LEFT : V2Direction.NONE,
                    gesture.update(true, true, false, 1000 + interval));
        }
    }

    @Test
    void holdingIsNotASecondPress() {
        VanishGesture gesture = new VanishGesture();
        gesture.update(true, true, false, 1000);
        assertEquals(V2Direction.NONE, gesture.update(true, true, false, 1100));
        assertEquals(V2Direction.NONE, gesture.update(true, true, false, 1200));
    }

    @Test
    void alternatingSidesNeverPairsDifferentKeys() {
        VanishGesture gesture = new VanishGesture();
        gesture.update(true, true, false, 1000);
        gesture.update(true, false, false, 1050);
        assertEquals(V2Direction.NONE, gesture.update(true, false, true, 1100));
        gesture.update(true, false, false, 1150);
        assertEquals(V2Direction.LEFT, gesture.update(true, true, false, 1200));
    }

    @Test
    void aCompletedPairDoesNotFireAgainOnTheThirdPress() {
        VanishGesture gesture = new VanishGesture();
        gesture.update(true, true, false, 1000);
        gesture.update(true, false, false, 1050);
        assertEquals(V2Direction.LEFT, gesture.update(true, true, false, 1100));
        gesture.update(true, false, false, 1150);
        assertEquals(V2Direction.NONE, gesture.update(true, true, false, 1200));
    }

    @Test
    void walkingWithoutALockDoesNotArmVanish() {
        VanishGesture gesture = new VanishGesture();
        gesture.update(false, true, false, 1000);
        gesture.update(false, false, false, 1050);
        assertEquals(V2Direction.NONE, gesture.update(true, true, false, 1100));
    }

    @Test
    void losingALockForgetsTheFirstTap() {
        VanishGesture gesture = new VanishGesture();
        gesture.update(true, true, false, 1000);
        gesture.update(false, false, false, 1050);
        assertEquals(V2Direction.NONE, gesture.update(true, true, false, 1100));
    }

    @Test
    void aKeyHeldIntoLockOnIsNotAFreshTap() {
        VanishGesture gesture = new VanishGesture();
        gesture.update(false, true, false, 1000);
        gesture.update(true, true, false, 1050);
        gesture.update(true, false, false, 1100);
        assertEquals(V2Direction.NONE, gesture.update(true, true, false, 1150));
    }

    @Test
    void resetDropsAPendingGesture() {
        VanishGesture gesture = new VanishGesture();
        gesture.update(true, true, false, 1000);
        gesture.update(true, false, false, 1050);
        gesture.reset();
        assertEquals(V2Direction.NONE, gesture.update(true, true, false, 1100));
    }
}
