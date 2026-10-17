package net.bullettrain.xenopixelsmod.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpaceHandoffCoordinatesTest {
    @Test void outboundPreservesOffsetFromPlanetSpatialAnchorAtMovingBodyCentre() {
        var mapped = SpaceHandoffCoordinates.outbound(5400.25, -3000.75,
                400.0, -100.0, 1000.0, 250.0);
        assertNotNull(mapped);
        assertEquals(6000.25, mapped.x(), 1.0e-9);
        assertEquals(-2650.75, mapped.z(), 1.0e-9);
    }

    @Test void originAnchorMapsDirectlyAboveTheChartColumn() {
        var mapped = SpaceHandoffCoordinates.outbound(0.5, -2.5, 0.0, 0.0, 1000.0, 0.0);
        assertEquals(new SpaceHandoffCoordinates.Horizontal(1000.5, -2.5), mapped);
    }

    @Test void invalidAndOutOfRangeMappingsAreRefusedInsteadOfClamped() {
        assertNull(SpaceHandoffCoordinates.outbound(Double.NaN, 0, 0, 0, 0, 0));
        assertNull(SpaceHandoffCoordinates.outbound(29_999_999, 0, -1, 0, 1, 0));
    }
}
