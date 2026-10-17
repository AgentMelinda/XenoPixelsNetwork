package net.bullettrain.xenopixelsmod.space.asteroid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsteroidSplitCapacityTest {
    @Test void globalLimitCountsCurrentBodiesBuildReservationsAndNewChildren() {
        assertTrue(AsteroidSplitCapacity.fitsGlobal(63, 0, 0, 1, 64));
        assertTrue(AsteroidSplitCapacity.fitsGlobal(62, 1, 0, 1, 64));
        assertFalse(AsteroidSplitCapacity.fitsGlobal(63, 0, 0, 2, 64));
        assertFalse(AsteroidSplitCapacity.fitsGlobal(63, 1, 0, 1, 64));
    }

    @Test void nearbyViewerLimitCountsExistingBodiesAndEveryPendingChild() {
        assertTrue(AsteroidSplitCapacity.fitsNearbyViewer(5, 1, 6));
        assertFalse(AsteroidSplitCapacity.fitsNearbyViewer(5, 2, 6));
        assertTrue(AsteroidSplitCapacity.fitsNearbyViewer(6, 0, 6),
                "a no-op split remains valid when occupancy is already at the limit");
    }

    @Test void negativeCountsAndIntegerOverflowFailClosed() {
        assertFalse(AsteroidSplitCapacity.fitsGlobal(Integer.MAX_VALUE, 1, 0, 1, Integer.MAX_VALUE));
        assertFalse(AsteroidSplitCapacity.fitsGlobal(-1, 0, 0, 0, 64));
        assertFalse(AsteroidSplitCapacity.fitsNearbyViewer(Integer.MAX_VALUE, 1, Integer.MAX_VALUE));
        assertFalse(AsteroidSplitCapacity.fitsNearbyViewer(0, -1, 64));
    }
}
