package net.bullettrain.xenopixelsmod.space.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanetTrackingRangeTest {
    @Test void defaultAltitudeHandoffCanReceiveRealEarth() {
        assertTrue(PlanetTrackingRange.contains(1000.5,764,.5,1000.5,128.5,.5));
    }
    @Test void rangeIsBoundedInThreeDimensions() {
        assertTrue(PlanetTrackingRange.contains(0,1024,0,0,0,0));
        assertFalse(PlanetTrackingRange.contains(0,1024.01,0,0,0,0));
        assertFalse(PlanetTrackingRange.contains(800,800,0,0,0,0));
        assertTrue(PlanetTrackingRange.contains(-500,-500,-500,0,0,0));
        assertFalse(PlanetTrackingRange.contains(Double.NaN,0,0,0,0,0));
        assertFalse(PlanetTrackingRange.contains(Double.POSITIVE_INFINITY,0,0,0,0,0));
    }
}
