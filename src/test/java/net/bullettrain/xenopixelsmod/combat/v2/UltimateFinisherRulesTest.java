package net.bullettrain.xenopixelsmod.combat.v2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UltimateFinisherRulesTest {
    @Test void sequenceHasSixPunchesBeforeGrabThrowChargeAndBeam() {
        assertEquals(UltimateFinisherRules.Phase.APPROACH, UltimateFinisherRules.phase(-1));
        for (int hit = 0; hit < 6; hit++) {
            assertEquals(hit + 1, UltimateFinisherRules.punchesDue(hit * 6));
            assertEquals(UltimateFinisherRules.Phase.COMBO, UltimateFinisherRules.phase(hit * 6));
        }
        assertEquals(6, UltimateFinisherRules.punchesDue(500));
        assertEquals(UltimateFinisherRules.Phase.GRAB, UltimateFinisherRules.phase(36));
        assertEquals(UltimateFinisherRules.Phase.THROW, UltimateFinisherRules.phase(44));
        assertEquals(UltimateFinisherRules.Phase.CHARGE, UltimateFinisherRules.phase(64));
        assertEquals(UltimateFinisherRules.Phase.BEAM, UltimateFinisherRules.phase(104));
        assertEquals(UltimateFinisherRules.Phase.BEAM, UltimateFinisherRules.phase(144),
                "post-wave freeze still counts as BEAM so the victim stays locked");
        assertEquals(UltimateFinisherRules.Phase.BEAM, UltimateFinisherRules.phase(223));
        assertEquals(UltimateFinisherRules.Phase.STOP, UltimateFinisherRules.phase(224));
        assertEquals(80, UltimateFinisherRules.POST_BEAM_FREEZE_TICKS, "owner: at least 4 more seconds after the wave");
    }

    @Test void throwTravelsFifteenBlocksThroughAnAirborneArc() {
        assertArrayEquals(new double[] {0, 0}, UltimateFinisherRules.throwOffset(0), 1e-9);
        assertArrayEquals(new double[] {7.5, 4}, UltimateFinisherRules.throwOffset(10), 1e-9);
        assertArrayEquals(new double[] {15, 0}, UltimateFinisherRules.throwOffset(20), 1e-9);
        for (int tick = 1; tick < 20; tick++) assertTrue(UltimateFinisherRules.throwOffset(tick)[1] > 0);
        assertArrayEquals(new double[] {15, 0}, UltimateFinisherRules.throwOffset(100), 1e-9);
    }

    @Test void flightRestoresCombatFlyButRespectsNativeDisableOrModeChange() {
        assertTrue(net.bullettrain.xenopixelsmod.network.ChaseFlightOwnership.mayRestoreSearchMode(true, 0));
        assertFalse(net.bullettrain.xenopixelsmod.network.ChaseFlightOwnership.mayRestoreSearchMode(false, 0));
        assertFalse(net.bullettrain.xenopixelsmod.network.ChaseFlightOwnership.mayRestoreSearchMode(true, 1));
    }
}
