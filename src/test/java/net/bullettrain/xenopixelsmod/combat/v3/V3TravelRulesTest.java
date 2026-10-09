package net.bullettrain.xenopixelsmod.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class V3TravelRulesTest {
    @Test void timeoutCoversTheDistanceAtSpeedPlusTwentyTicks() {
        assertEquals((int) Math.ceil(999 / 3.0) + 20, V3Travel.timeoutTicks(999, 3.0));
        assertEquals(999 + 20, V3Travel.timeoutTicks(999, 1.0));
        assertEquals(21, V3Travel.timeoutTicks(0.5, 3.0));
        // No leftover 128-block or 600-tick clamp from older controllers.
        assertTrue(V3Travel.timeoutTicks(999, 1.0) > 600);
    }

    @Test void timeoutIsBoundedAndRefusesNonsense() {
        assertEquals(12000, V3Travel.timeoutTicks(999, 0.01));
        assertEquals(0, V3Travel.timeoutTicks(999, 0));
        assertEquals(0, V3Travel.timeoutTicks(Double.NaN, 3));
        assertEquals(0, V3Travel.timeoutTicks(999, Double.POSITIVE_INFINITY));
        assertEquals(0, V3Travel.timeoutTicks(-1, 3));
    }

    @Test void stepNeverOvershootsAndIsAlwaysFinite() {
        double[] far = V3Travel.step(999, 0, 0, 3.0);
        assertEquals(3.0, far[0], 1e-9);
        double[] near = V3Travel.step(0, 1.0, 0, 3.0);
        assertEquals(1.0, near[1], 1e-9);
        double[] diagonal = V3Travel.step(300, 400, 0, 5.0);
        assertEquals(5.0, Math.sqrt(diagonal[0] * diagonal[0] + diagonal[1] * diagonal[1]), 1e-9);
        for (double[] bad : new double[][] {V3Travel.step(Double.NaN, 0, 0, 3), V3Travel.step(1, 1, 1, Double.NaN),
                V3Travel.step(0, 0, 0, 3), V3Travel.step(Double.POSITIVE_INFINITY, 0, 0, 3)}) {
            assertArrayEquals(new double[] {0, 0, 0}, bad);
        }
    }

    @Test void lookAheadNeedsAtMostNineChunksAndHoldsNoTickets() {
        // A player-sized box inflated by one block touches at most a 2x2 chunk corner.
        assertTrue(V3ChunkWindow.chunkCount(15.2, 15.2, 16.8, 16.8) <= 9);
        assertEquals(1, V3ChunkWindow.chunkCount(4, 4, 5, 5));
        assertEquals(4, V3ChunkWindow.chunkCount(15, 15, 17, 17));
        assertEquals(2, V3ChunkWindow.chunkCount(-1, 4, 1, 5));
        assertEquals(0, V3ChunkWindow.HELD_TICKETS);
    }

    @Test void missingWorldWaitsFortyTicksThenGivesUp() {
        assertFalse(V3Travel.waitExpired(40));
        assertTrue(V3Travel.waitExpired(41));
    }
}
