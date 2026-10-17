package net.bullettrain.xenopixelsmod.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HandoffOverlayTimingTest {
    @Test void waitsForDestinationButNeverLongerThanOneSecond() {
        assertFalse(HandoffOverlayTiming.shouldStartFade(false,
                HandoffOverlayTiming.MAX_HOLD_NANOS - 1, 0));
        assertTrue(HandoffOverlayTiming.shouldStartFade(true, 50, 0));
        assertTrue(HandoffOverlayTiming.shouldStartFade(false,
                HandoffOverlayTiming.MAX_HOLD_NANOS, 0));
    }

    @Test void crossFadeRunsForSixHundredMillisecondsAndClamps() {
        assertEquals(1.0f, HandoffOverlayTiming.alpha(0), 1.0e-6f);
        assertEquals(0.5f, HandoffOverlayTiming.alpha(300), 1.0e-6f);
        assertEquals(0.0f, HandoffOverlayTiming.alpha(600), 1.0e-6f);
        assertEquals(0.0f, HandoffOverlayTiming.alpha(900), 1.0e-6f);
        assertEquals(1.0f, HandoffOverlayTiming.alpha(-50), 1.0e-6f);
    }
}
