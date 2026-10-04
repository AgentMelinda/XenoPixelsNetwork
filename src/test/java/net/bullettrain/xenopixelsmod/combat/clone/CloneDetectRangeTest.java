package net.bullettrain.xenopixelsmod.combat.clone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloneDetectRangeTest {
    @Test
    void clampFallsBackToTheOldThirtyTwoAndCapsAtOneTwentyEight() {
        assertEquals(32.0, CloneDetectRange.clamp(0), 0.01);
        assertEquals(32.0, CloneDetectRange.clamp(Double.NaN), 0.01);
        assertEquals(1.0, CloneDetectRange.clamp(0.1), 0.01);
        assertEquals(48.0, CloneDetectRange.clamp(48), 0.01);
        assertEquals(128.0, CloneDetectRange.clamp(999), 0.01);
    }

    @Test
    void withinUsesTheClampedLimit() {
        assertTrue(CloneDetectRange.within(32, 32));
        assertFalse(CloneDetectRange.within(33, 32));
        assertTrue(CloneDetectRange.within(33, 64));
        assertTrue(CloneDetectRange.withinSqr(32 * 32, 32));
        assertFalse(CloneDetectRange.withinSqr(33 * 33, 32));
        assertFalse(CloneDetectRange.withinSqr(16 * 16 + 1, 16));
    }
}
