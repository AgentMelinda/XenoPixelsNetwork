package net.bullettrain.xenopixelsmod.space.physics;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanetTrackingRangeTest {
    @Test void defaultAltitudeHandoffCanReceiveRealEarth() {
        assertTrue(PlanetTrackingRange.contains(1000.5,764,.5,1000.5,128.5,.5));
    }

    @Test void copiedPoseCentresMatchByCoordinates() {
        assertTrue(PlanetTrackingRange.sameCentre(new Vector3d(10, 20, 30), new Vector3d(10, 20, 30)));
        assertFalse(PlanetTrackingRange.sameCentre(new Vector3d(10, 20, 30), new Vector3d(10, 20, 31)));
        assertFalse(PlanetTrackingRange.sameCentre(null, new Vector3d(10, 20, 30)));
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
