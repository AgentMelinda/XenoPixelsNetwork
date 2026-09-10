package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one piece of new arithmetic in Sparking: the bar is the timer. */
class Bt3SparkingDrainTest {

    @Test
    void aFullBarLastsExactlyTheConfiguredDuration() {
        float max = 5000f;
        int duration = 200;
        float drain = Bt3SparkingSystem.drainPerTick(max, duration);
        assertEquals(max, drain * duration, 0.01f,
                "a full bar must empty in exactly the configured number of ticks");
    }

    @Test
    void aLongerDurationDrainsSlower() {
        float max = 5000f;
        assertTrue(Bt3SparkingSystem.drainPerTick(max, 400)
                        < Bt3SparkingSystem.drainPerTick(max, 200),
                "duration is what sets the drain speed");
    }

    @Test
    void aBiggerBarDrainsFasterSoTheDurationHolds() {
        // The point of dividing by the bar rather than using a fixed rate: a stronger player with a
        // larger pool gets the same Sparking length, not a longer one.
        int duration = 200;
        assertEquals(Bt3SparkingSystem.drainPerTick(1000f, duration) * duration, 1000f, 0.01f);
        assertEquals(Bt3SparkingSystem.drainPerTick(90000f, duration) * duration, 90000f, 1f);
    }

    @Test
    void degenerateInputsDoNotDivideByZeroOrDrainNegatively() {
        assertEquals(0f, Bt3SparkingSystem.drainPerTick(0f, 200), 0f);
        assertEquals(0f, Bt3SparkingSystem.drainPerTick(-5f, 200), 0f);
        assertTrue(Bt3SparkingSystem.drainPerTick(1000f, 0) > 0f);
        assertTrue(Bt3SparkingSystem.drainPerTick(1000f, -10) > 0f);
    }
}
