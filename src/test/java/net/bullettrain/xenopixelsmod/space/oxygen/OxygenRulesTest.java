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
}
