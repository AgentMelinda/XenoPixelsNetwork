package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bt3SparkingChargeTest {

    @Test
    void defaultChargeCompletesOnTickOneHundred() {
        assertFalse(Bt3SparkingCharge.complete(99, 100));
        assertTrue(Bt3SparkingCharge.complete(100, 100));
        assertEquals(8, Bt3SparkingCharge.litSegments(100, 100));
    }

    @Test
    void segmentsLightFromLeftToRightAcrossTheCharge() {
        assertEquals(0, Bt3SparkingCharge.litSegments(1, 100));
        assertEquals(1, Bt3SparkingCharge.litSegments(13, 100));
        assertEquals(4, Bt3SparkingCharge.litSegments(50, 100));
        assertEquals(7, Bt3SparkingCharge.litSegments(99, 100));
        assertEquals(8, Bt3SparkingCharge.litSegments(100, 100));
    }

    @Test
    void advanceClampsAtCompletionAndSanitizesInputs() {
        assertEquals(1, Bt3SparkingCharge.advance(-5, 100));
        assertEquals(100, Bt3SparkingCharge.advance(100, 100));
        assertEquals(1, Bt3SparkingCharge.requiredTicks(0));
        assertEquals(0, Bt3SparkingCharge.litSegments(0, 100));
    }
}
