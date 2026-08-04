package net.bullettrain.xenopixelsmod.compat.thruster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalThrusterCompatTest {
    @Test
    void recognizesKnownPropulsionNamespacesAndEngineNames() {
        assertTrue(ExternalThrusterCompat.isKnownNamespacePath("createpropulsion", "creative_thruster"));
        assertTrue(ExternalThrusterCompat.isKnownNamespacePath("vsch", "redstone_air_thruster"));
        assertTrue(ExternalThrusterCompat.isKnownNamespacePath("vs_clockwork", "propeller_bearing"));
        assertTrue(ExternalThrusterCompat.isKnownNamespacePath("genesis", "ion_engine"));
        assertTrue(ExternalThrusterCompat.isKnownNamespacePath("zps", "rocket_engine"));
        assertTrue(ExternalThrusterCompat.isKnownNamespacePath("zero_point_systems", "ion_engine"));
        assertTrue(ExternalThrusterCompat.isKnownNamespacePath("warium", "jet_engine"));
    }

    @Test
    void doesNotPairUnrelatedBlocksByKeywordAlone() {
        assertFalse(ExternalThrusterCompat.isKnownNamespacePath("minecraft", "piston"));
        assertFalse(ExternalThrusterCompat.isKnownNamespacePath("decorative", "thruster_trophy"));
        assertFalse(ExternalThrusterCompat.isKnownNamespacePath("warium", "steel_bricks"));
    }
}
