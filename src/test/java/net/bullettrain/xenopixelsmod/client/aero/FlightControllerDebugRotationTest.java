package net.bullettrain.xenopixelsmod.client.aero;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightControllerDebugRotationTest {

    @AfterEach
    void reset() {
        FlightControllerDebugRotation.reset();
    }

    @Test
    void resetClearsEveryAxis() {
        FlightControllerDebugRotation.extraX = 90;
        FlightControllerDebugRotation.extraY = -45;
        FlightControllerDebugRotation.extraZ = 180;
        FlightControllerDebugRotation.reset();
        assertEquals(0, FlightControllerDebugRotation.extraX);
        assertEquals(0, FlightControllerDebugRotation.extraY);
        assertEquals(0, FlightControllerDebugRotation.extraZ);
    }
}
