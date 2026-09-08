package net.bullettrain.xenopixelsmod.client.pad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PadVanishGestureTest {
    @Test
    void guardAndDirectionProduceOneSideSpecificGesture() {
        PadVanishGesture gesture = new PadVanishGesture(0.68f, 0.32f);
        assertEquals(-1, gesture.sample(-0.8f, true));
        assertEquals(0, gesture.sample(-1f, true));
        assertEquals(0, gesture.sample(0f, true));
        assertEquals(1, gesture.sample(0.8f, true));
    }

    @Test
    void directionWithoutGuardDoesNotConsumeTheArm() {
        PadVanishGesture gesture = new PadVanishGesture(0.68f, 0.32f);
        assertEquals(0, gesture.sample(0.9f, false));
        assertEquals(1, gesture.sample(0.9f, true));
    }

    @Test
    void subThresholdNoiseDoesNotTrigger() {
        PadVanishGesture gesture = new PadVanishGesture(0.68f, 0.32f);
        assertEquals(0, gesture.sample(0.5f, true));
        assertEquals(0, gesture.sample(-0.6f, true));
    }
}
