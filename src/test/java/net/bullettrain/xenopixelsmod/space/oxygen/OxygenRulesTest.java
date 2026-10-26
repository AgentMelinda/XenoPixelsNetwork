package net.bullettrain.xenopixelsmod.space.oxygen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OxygenRulesTest {
    private static final int MAX = 30 * 20;

    @Test void chartExitOverrideDefinesTheOxygenBoundary() {
        assertFalse(OxygenRules.inVacuum(true, 2031.99, 2032));
        assertFalse(OxygenRules.inVacuum(true, 2032.0, 2032));
        assertTrue(OxygenRules.inVacuum(true, 2032.01, 2032));
        assertTrue(OxygenRules.inVacuum(false, -10_000.0, 2032), "non-breathable body remains vacuum throughout");
    }

    @Test void sidecarCanOptOutOfAboveExitOxygenAndSetRelativeDrainRate() {
        assertFalse(OxygenRules.inVacuum(true, 900, 700, false));
        assertTrue(OxygenRules.inVacuum(false, 900, 700, false), "non-breathable world remains vacuum");
        assertEquals(5, OxygenRules.drainTicks(10, "slow"));
        assertEquals(10, OxygenRules.drainTicks(10, "normal"));
        assertEquals(20, OxygenRules.drainTicks(10, "fast"));
        assertEquals(0, OxygenRules.drainTicks(0, "fast"));
        assertEquals(Integer.MAX_VALUE, OxygenRules.drainTicks(Integer.MAX_VALUE, "fast"));
    }

    @Test void sharedSurfaceWithoutTripUsesTheDimensionCeilingInsteadOfNan() {
        double exit = OxygenRules.sharedSurfaceExitAltitude(320, Double.NaN);
        assertEquals(304.0, exit);
        assertTrue(OxygenRules.inVacuum(true, 310.0, exit));
        assertEquals(280.0, OxygenRules.sharedSurfaceExitAltitude(320, 280.0));
    }

    @Test void airKeepsFullLungsSafe() {
        var s = OxygenRules.step(OxygenRules.State.full(MAX), false, 10, MAX, 10.0);
        assertEquals(MAX, s.breath());
        assertEquals(OxygenRules.Phase.SAFE, s.phase());
    }

    @Test void vacuumUsesSupplyBeforeBreath() {
        var s = OxygenRules.step(new OxygenRules.State(MAX, 15, OxygenRules.Phase.SAFE), true, 10, MAX, 10.0);
        assertEquals(5, s.reserve());
        assertEquals(MAX, s.breath());
        assertEquals(OxygenRules.Phase.SUPPLIED, s.phase());
        s = OxygenRules.step(s, true, 10, MAX, 10.0);
        assertEquals(0, s.reserve());
        assertEquals(MAX - 5, s.breath(), "the 5 ticks the supply did not cover come from the lungs");
        assertEquals(OxygenRules.Phase.HOLDING, s.phase());
    }

    @Test void breathRunsOutIntoSuffocationAndNeverGoesNegative() {
        var s = OxygenRules.State.full(MAX);
        for (int i = 0; i < 60; i++) s = OxygenRules.step(s, true, 10, MAX, 10.0);
        assertEquals(0, s.breath());
        assertEquals(OxygenRules.Phase.SUFFOCATING, s.phase());
        s = OxygenRules.step(s, true, 10, MAX, 10.0);
        assertEquals(0, s.breath());
    }

    @Test void airRefillsAndRecovers() {
        var s = new OxygenRules.State(0, 0, OxygenRules.Phase.SUFFOCATING);
        s = OxygenRules.step(s, false, 10, MAX, 10.0);
        assertEquals(100, s.breath());
        assertEquals(OxygenRules.Phase.RECOVERING, s.phase());
        for (int i = 0; i < 10; i++) s = OxygenRules.step(s, false, 10, MAX, 10.0);
        assertEquals(MAX, s.breath());
        assertEquals(OxygenRules.Phase.SAFE, s.phase());
    }

    @Test void capsuleSupplyIsCapped() {
        assertEquals(6000, OxygenRules.addReserve(0, 6000, 18000));
        assertEquals(18000, OxygenRules.addReserve(15000, 6000, 18000));
        assertEquals(100, OxygenRules.addReserve(100, -5, 18000));
    }

    @Test void activeAuraSubstitutesForOxygenWithoutConsumingCapsuleOrBreath() {
        var before = new OxygenRules.State(37, 250, OxygenRules.Phase.HOLDING);
        var after = OxygenRules.auraSubstitute(before, MAX);

        assertEquals(37, after.breath());
        assertEquals(250, after.reserve());
        assertEquals(OxygenRules.Phase.AURA, after.phase());
    }

    @Test void auraOxygenStatusRoundTripsItsNewPhaseValue() {
        var packet = new OxygenStatus(37, MAX, 250, OxygenRules.Phase.AURA.ordinal());
        assertEquals(OxygenRules.Phase.AURA, packet.phaseValue());
        assertEquals(OxygenRules.Phase.AURA.ordinal(), packet.phase());
    }

    @Test void auraPaymentIsProportionalToEachActiveIntervalEvenWhenAuraFlickers() {
        double kiPerSecond = 6.0;
        double kiCharged = 0.0;
        for (int pass = 0; pass < 10; pass++) {
            boolean auraActive = pass % 2 == 0;
            if (auraActive) kiCharged += OxygenRules.auraKiCost(kiPerSecond, 10);
        }
        assertEquals(15.0, kiCharged, 1.0e-9,
                "five active half-second samples in one second must pay for 2.5 active seconds");
        assertEquals(0.0, OxygenRules.auraKiCost(0.0, 10), 0.0,
                "a configured zero rate explicitly means free aura oxygen");
        assertTrue(OxygenRules.auraPaymentAllowsSubstitute(0.0, false),
                "a free configured rate does not depend on the resource adapter's zero-cost semantics");
        assertFalse(OxygenRules.auraPaymentAllowsSubstitute(3.0, false),
                "a failed positive-cost payment must not grant the substitute");
        assertTrue(OxygenRules.auraPaymentAllowsSubstitute(3.0, true));
        assertTrue(OxygenRules.auraCostRepresentable(0.0));
        assertTrue(OxygenRules.auraCostRepresentable(3.0));
        assertFalse(OxygenRules.auraCostRepresentable(1.0e-46),
                "a positive configured rate that underflows the DMZ float API must not be treated as free");
        assertThrows(IllegalArgumentException.class, () -> OxygenRules.auraKiCost(-1.0, 10));
        assertThrows(IllegalArgumentException.class, () -> OxygenRules.auraKiCost(1.0, -1));
        assertThrows(IllegalArgumentException.class, () -> OxygenRules.auraPaymentAllowsSubstitute(-1.0, true));
    }
}
