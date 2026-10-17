package net.bullettrain.xenopixelsmod.space.asteroid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FractureVelocityTrackerTest {
    @Test
    void measuresVelocityChangeFromTickSpacedPositions() {
        FractureVelocityTracker tracker = new FractureVelocityTracker();
        assertNull(tracker.sample(0, 0, 0, 10));
        var firstVelocity = tracker.sample(1, 0, 0, 11);
        assertNotNull(firstVelocity);
        assertEquals(20.0, firstVelocity.velocityX(), 1e-9);
        assertEquals(0.0, firstVelocity.deltaVX(), 1e-9);

        var impact = tracker.sample(1.5, 0.5, 0, 12);
        assertNotNull(impact);
        assertEquals(10.0, impact.velocityX(), 1e-9);
        assertEquals(10.0, impact.velocityY(), 1e-9);
        assertEquals(Math.sqrt(200.0), impact.deltaV(), 1e-9);
        assertTrue(impact.exceeds(14.0));
        assertFalse(impact.exceeds(15.0));
    }

    @Test
    void rejectsBadSamplesAndResetsAfterBackwardsClock() {
        FractureVelocityTracker tracker = new FractureVelocityTracker();
        assertNull(tracker.sample(Double.NaN, 0, 0, 10));
        assertNull(tracker.sample(0, 0, 0, 10));
        assertNull(tracker.sample(5, 0, 0, 9));
        var recovered = tracker.sample(6, 0, 0, 10);
        assertNotNull(recovered);
        assertEquals(0.0, recovered.deltaV(), 1e-9, "first interval after reset establishes velocity baseline");
    }
}
