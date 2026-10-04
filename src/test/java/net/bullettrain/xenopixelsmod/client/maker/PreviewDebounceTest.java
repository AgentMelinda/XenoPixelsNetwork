package net.bullettrain.xenopixelsmod.client.maker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure debounce for race form-group maker live preview (PR-D6b, ≤50 ms).
 */
class PreviewDebounceTest {
    @Test
    void defaultDelayIsAtMostFiftyMs() {
        assertEquals(50L, PreviewDebounce.DEFAULT_MS);
        assertTrue(new PreviewDebounce().delayMs() <= 50L);
    }

    @Test
    void firesAfterFiftyMs() {
        PreviewDebounce d = new PreviewDebounce(50);
        d.markChanged(0L);
        assertFalse(d.shouldFire(49L));
        assertTrue(d.shouldFire(50L));
    }

    @Test
    void doesNotFireUntilWindowElapses() {
        PreviewDebounce debounce = new PreviewDebounce(50L);
        assertFalse(debounce.shouldFire(1_000L));

        debounce.schedule(1_000L);
        assertTrue(debounce.pending());
        assertFalse(debounce.shouldFire(1_049L));
        assertTrue(debounce.shouldFire(1_050L));
    }

    @Test
    void keystrokesInsideTheWindowRestartTheTimer() {
        PreviewDebounce debounce = new PreviewDebounce(50L);
        debounce.schedule(1_000L);
        debounce.schedule(1_020L);
        debounce.schedule(1_040L);
        assertFalse(debounce.shouldFire(1_080L), "window restarts on each schedule");
        assertTrue(debounce.shouldFire(1_090L));
    }

    @Test
    void clearStopsPendingFire() {
        PreviewDebounce debounce = new PreviewDebounce(50L);
        debounce.schedule(1_000L);
        debounce.clear();
        assertFalse(debounce.pending());
        assertFalse(debounce.shouldFire(9_000L));
    }
}
