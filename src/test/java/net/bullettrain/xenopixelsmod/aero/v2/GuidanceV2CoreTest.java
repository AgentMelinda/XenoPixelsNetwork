package net.bullettrain.xenopixelsmod.aero.v2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidanceV2CoreTest {

    @Test
    void flapsOnlyNeverRequestsThrusterForce() {
        assertFalse(GuidanceV2Core.requestsThrusterForce(GuidanceV2SurfaceMode.FLAPS_ONLY, 1.0));
        assertFalse(GuidanceV2Core.requestsThrusterForce(GuidanceV2SurfaceMode.FLAPS_ONLY, 0.5));
        assertEquals(0.0, GuidanceV2Core.appliedThrottle(GuidanceV2SurfaceMode.FLAPS_ONLY, 1.0));
        assertEquals(0.0, GuidanceV2Core.appliedThrottle(null, 0.8));
    }

    @Test
    void thrustAndFlapsRequestsForceWhenThrottlePositive() {
        assertTrue(GuidanceV2Core.requestsThrusterForce(GuidanceV2SurfaceMode.THRUST_AND_FLAPS, 0.2));
        assertFalse(GuidanceV2Core.requestsThrusterForce(GuidanceV2SurfaceMode.THRUST_AND_FLAPS, 0.0));
        assertEquals(0.4, GuidanceV2Core.appliedThrottle(GuidanceV2SurfaceMode.THRUST_AND_FLAPS, 0.4));
    }

    @Test
    void busSnapshotZerosAppliedThrottleInFlapsOnly() {
        GuidanceV2Bus.Flags flags = new GuidanceV2Bus.Flags(true, true, true, false, 3);
        GuidanceV2Bus bus = GuidanceV2Bus.empty(net.minecraft.core.BlockPos.ZERO,
                GuidanceV2SurfaceMode.FLAPS_ONLY, flags);
        assertTrue(bus.flapsOnly());
        assertEquals(0.0, bus.appliedThrottle());
        assertTrue(bus.cannotBleedSpeed());
        assertFalse(bus.missingAttitudeSurfaces());
    }
}
