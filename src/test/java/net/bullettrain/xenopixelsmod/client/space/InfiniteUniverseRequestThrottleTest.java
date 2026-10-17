package net.bullettrain.xenopixelsmod.client.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InfiniteUniverseRequestThrottleTest {
    @Test void suppressesRequestsOnlyInsideTheRetryWindow() {
        assertTrue(InfiniteUniverseRequestThrottle.isActive(99, 0, 100));
        assertFalse(InfiniteUniverseRequestThrottle.isActive(100, 0, 100));
    }

    @Test void aRewoundClientClockStartsANewThrottleEpoch() {
        assertFalse(InfiniteUniverseRequestThrottle.isActive(5, 100, 100));
    }

    @Test void elapsedSubtractionOverflowDoesNotStrandRequests() {
        assertFalse(InfiniteUniverseRequestThrottle.isActive(Long.MAX_VALUE, Long.MIN_VALUE + 1, 100));
    }

    @Test void nonPositiveIntervalsNeverThrottle() {
        assertFalse(InfiniteUniverseRequestThrottle.isActive(5, 5, 0));
        assertFalse(InfiniteUniverseRequestThrottle.isActive(5, 5, -1));
    }
}
