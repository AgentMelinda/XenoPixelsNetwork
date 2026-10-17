package net.bullettrain.xenopixelsmod.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceEntryTest {
    private static final double CX = 1000.5, CY = 128.5, CZ = 0.5, R = 100, MARGIN = 2;

    @Test void standingOnTheShellEnters() {
        double gap = SpaceHandoffPass.surfaceGap(CX, CY + R + 0.6, CZ, CX, CY, CZ, R);
        assertEquals(0.6, gap, 1e-9);
        assertTrue(SpaceHandoffPass.touching(gap, MARGIN));
    }

    @Test void theOldAltitudeLineNoLongerEnters() {
        // Y=699 above Earth's centre column used to trigger the altitude rule; it is 470 blocks off the surface.
        double gap = SpaceHandoffPass.surfaceGap(CX, 699, CZ, CX, CY, CZ, R);
        assertFalse(SpaceHandoffPass.touching(gap, MARGIN));
    }

    @Test void approachFromTheSideOrBelowEnters() {
        assertTrue(SpaceHandoffPass.touching(SpaceHandoffPass.surfaceGap(CX + R + 1.5, CY, CZ, CX, CY, CZ, R), MARGIN));
        assertTrue(SpaceHandoffPass.touching(SpaceHandoffPass.surfaceGap(CX, CY - R - 1, CZ, CX, CY, CZ, R), MARGIN));
    }

    @Test void outboundArrivalIsOutsideTheMargin() {
        // Outbound places the player half a band (64) above the surface.
        double gap = SpaceHandoffPass.surfaceGap(CX, CY + R + 64, CZ, CX, CY, CZ, R);
        assertFalse(SpaceHandoffPass.touching(gap, MARGIN));
    }

    @Test void nonFiniteNeverEnters() {
        assertFalse(SpaceHandoffPass.touching(Double.NaN, MARGIN));
    }
}
