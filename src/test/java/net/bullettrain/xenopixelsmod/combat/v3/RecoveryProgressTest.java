package net.bullettrain.xenopixelsmod.combat.v3;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RecoveryProgressTest {
    @Test void mashAddsProgressWithoutResettingNaturalRecovery() {
        var clock = new RecoveryProgress(100, 80);
        assertEquals(10, clock.completed(110));
        assertTrue(clock.press(110));
        assertEquals(14, clock.completed(110));
        assertFalse(clock.press(110));
        assertFalse(clock.press(111));
        assertTrue(clock.press(112));
        assertEquals(20, clock.completed(112));
        assertEquals(60, clock.remaining(112));
        assertEquals(0, clock.remaining(180));
    }
    @Test void repeatedPressesFinishEarlierAndHeldRepeatDoesNotExceedTheTickCap() {
        var clock = new RecoveryProgress(0, 60);
        for (int tick=0; tick<20; tick+=2) assertTrue(clock.press(tick));
        assertEquals(0, clock.remaining(20));
        assertEquals(60, clock.completed(999));
    }
}
