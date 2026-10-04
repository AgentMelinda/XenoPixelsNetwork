package net.bullettrain.xenopixelsmod.client.gui;

import net.bullettrain.xenopixelsmod.aero.AeroAutopilotMode;
import net.bullettrain.xenopixelsmod.aero.AeroBus;
import net.bullettrain.xenopixelsmod.aero.AeroStateSnapshot;
import net.bullettrain.xenopixelsmod.aero.ControllerMode;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightPlannerV2ScreenTest {

    @Test
    void engageUiIsFlightOnly() {
        assertFalse(FlightPlannerV2Screen.isFlightUi(null));
        assertFalse(FlightPlannerV2Screen.isFlightUi(snapshot(ControllerMode.MISSILE)));
        assertTrue(FlightPlannerV2Screen.isFlightUi(snapshot(ControllerMode.FLIGHT)));
    }

    @Test
    void bodyTabIsOnTheV2Bar() {
        assertTrue(FlightPlannerV2Screen.tabNames().contains("BODY"));
        assertTrue(FlightPlannerV2Screen.tabNames().contains("FLIGHT"));
        assertEquals(5, FlightPlannerV2Screen.tabNames().size());
    }

    @Test
    void bodyChooserRejectsDuplicateAnchors() {
        BlockPos a = new BlockPos(1, 2, 3);
        BlockPos b = new BlockPos(4, 5, 6);
        BlockPos c = new BlockPos(7, 8, 9);
        assertNull(FlightPlannerV2Screen.distinctBodyError(a, b, c));
        assertEquals("Choose three different occupied blocks",
                FlightPlannerV2Screen.distinctBodyError(a, a, c));
        assertEquals(new BlockPos(10, 20, 30),
                FlightPlannerV2Screen.parseBlockPos("10 20 30", BlockPos.ZERO));
    }

    @Test
    void guideYParsesWholeWorldAltitude() {
        assertEquals(0, FlightPlannerV2Screen.parseWorldY(""));
        assertEquals(0, FlightPlannerV2Screen.parseWorldY("0"));
        assertEquals(320, FlightPlannerV2Screen.parseWorldY("320"));
        assertNull(FlightPlannerV2Screen.parseWorldY("auto"));
    }

    private static AeroStateSnapshot snapshot(ControllerMode mode) {
        return new AeroStateSnapshot(
                BlockPos.ZERO, mode, 0, 0.0, 0.0, 0.0, 0.0, false,
                AeroAutopilotMode.MANUAL, 0, 0, 0.0, 0.0,
                AeroBus.PowerTier.NOMINAL, 0, 0, 0, 0,
                0.0, 0.0, false, false, "test");
    }
}
