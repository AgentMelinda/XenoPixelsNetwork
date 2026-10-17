package net.bullettrain.xenopixelsmod.space.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildMassDeferralPolicyTest {
    @Test void deferredPhysicsMassAppliesOnlyToNonIncrementalPlotLoadBuilds() {
        assertTrue(BuildMassDeferralPolicy.deferPhysicsTickMass(true, false));
        assertFalse(BuildMassDeferralPolicy.deferPhysicsTickMass(true, true));
        assertFalse(BuildMassDeferralPolicy.deferPhysicsTickMass(false, false));
    }
}
