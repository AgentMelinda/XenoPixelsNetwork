package net.bullettrain.xenopixelsmod.combat.v2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V2ChargeRulesTest {
    @Test void timingClampsAtFullAndCannotCompleteEarly() {
        assertEquals(0, V2ChargeRules.progress(-100));
        assertEquals(0.5f, V2ChargeRules.progress(10));
        assertFalse(V2ChargeRules.eligible(V2ChargeRules.progress(19), 10));
        assertEquals(1, V2ChargeRules.progress(20));
        assertEquals(1, V2ChargeRules.progress(100));
    }
    @Test void distanceBoundariesAreInclusiveAndFinite() {
        assertTrue(V2ChargeRules.eligible(1, 7));
        assertTrue(V2ChargeRules.eligible(1, 20));
        assertFalse(V2ChargeRules.eligible(1, 6.999));
        assertFalse(V2ChargeRules.eligible(1, 20.001));
        assertFalse(V2ChargeRules.eligible(1, Double.NaN));
        assertFalse(V2ChargeRules.eligible(1, Double.POSITIVE_INFINITY));
    }
    @Test void exactlyEveryFourthEligibleReleaseTriggersAcrossMultipleCycles() {
        int count = 0, triggers = 0;
        for (int release = 1; release <= 40; release++) {
            count = V2ChargeRules.nextCount(count);
            assertEquals(release % 4 == 0, count == 0);
            if (count == 0) triggers++;
        }
        assertEquals(10, triggers);
    }
    @Test void kickArcTravelsTwentyBlocksWithSixBlockApex() {
        assertArrayEquals(new double[]{0, 0}, V2ChargeRules.arcOffset(0), 1e-9);
        assertArrayEquals(new double[]{10, 6}, V2ChargeRules.arcOffset(12), 1e-9);
        assertArrayEquals(new double[]{20, 0}, V2ChargeRules.arcOffset(24), 1e-9);
        assertArrayEquals(new double[]{20, 0}, V2ChargeRules.arcOffset(100), 1e-9);
    }
}
