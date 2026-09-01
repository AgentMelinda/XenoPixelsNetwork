package net.bullettrain.xenopixelsmod.combat.technique;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KiGuidanceMathTest {

    @Test
    void levelZeroHasStockRangeAndASoftTurn() {
        assertEquals(30.0, KiGuidanceMath.range(0), 1.0e-9);
        assertEquals(160, KiGuidanceMath.extendedTicks(0));
        assertEquals(192.0, KiGuidanceMath.controlRange(0), 1.0e-9);
        assertEquals(0.32, KiGuidanceMath.turnRate(0), 1.0e-9);
        assertEquals(30, KiGuidanceMath.graceTicks());
    }

    @Test
    void higherLevelGivesMoreRangeWindowAndTurn() {
        assertTrue(KiGuidanceMath.range(3) > KiGuidanceMath.range(1));
        assertTrue(KiGuidanceMath.controlRange(3) > KiGuidanceMath.controlRange(1));
        assertTrue(KiGuidanceMath.controlRange(1) > KiGuidanceMath.range(1));
        assertTrue(KiGuidanceMath.extendedTicks(3) > KiGuidanceMath.extendedTicks(1));
        assertTrue(KiGuidanceMath.turnRate(3) > KiGuidanceMath.turnRate(1));
    }

    @Test
    void turnRateNeverExceedsTheCap() {
        assertTrue(KiGuidanceMath.turnRate(99) <= KiGuidanceMath.TURN_CAP + 1.0e-9);
    }

    @Test
    void turnRateOverrideWinsUntilCleared() {
        double stock = KiGuidanceMath.turnRate(0);
        KiGuidanceMath.turnRateOverride = 0.2;
        assertEquals(0.2, KiGuidanceMath.turnRate(0), 1.0e-9);
        KiGuidanceMath.turnRateOverride = 0.0;
        assertEquals(stock, KiGuidanceMath.turnRate(0), 1.0e-9);
    }

    @Test
    void steerDirMovesTowardWant() {
        double[] next = KiGuidanceMath.steerDir(1, 0, 0, 0, 0, 1, 3);
        assertTrue(next[2] > 0.0, "should pick up some +Z");
        assertTrue(next[0] > 0.0, "should keep some +X");
        double len = Math.sqrt(next[0] * next[0] + next[1] * next[1] + next[2] * next[2]);
        assertEquals(1.0, len, 1.0e-9);
    }

    @Test
    void negativeLevelBehavesLikeZero() {
        assertEquals(KiGuidanceMath.range(0), KiGuidanceMath.range(-4), 1.0e-9);
        assertEquals(KiGuidanceMath.extendedTicks(0), KiGuidanceMath.extendedTicks(-4));
    }

    @Test
    void slerpDirStaysBetweenCurrentAndWant() {
        double[] next = KiGuidanceMath.slerpDir(1, 0, 0, 0, 0, 1, 0.35);
        assertTrue(next[0] > 0.0 && next[0] < 1.0);
        assertTrue(next[2] > 0.0 && next[2] < 1.0);
        double len = Math.sqrt(next[0] * next[0] + next[1] * next[1] + next[2] * next[2]);
        assertEquals(1.0, len, 1.0e-9);
    }

    @Test
    void npcStyleSlerpDoesNotSnapPitch() {
        // Horizontal (y=0) toward straight up (y=1) at the spawn fraction 0.32.
        double[] next = KiGuidanceMath.slerpDir(0, 0, 1, 0, 1, 0, 0.32);
        assertTrue(next[1] > 0.0 && next[1] < 0.9, "should lift, not go fully vertical");
        assertTrue(next[2] > 0.0, "should keep forward");
    }

    @Test
    void lerpVecKeepsMagnitudeBlend() {
        double[] next = KiGuidanceMath.lerpVec(2, 0, 0, 0, 0, 4, 0.5);
        assertEquals(1.0, next[0], 1.0e-9);
        assertEquals(2.0, next[2], 1.0e-9);
    }

    @Test
    void lookRayPointSitsAheadOnTheAimLine() {
        double[] p = KiGuidanceMath.lookRayPoint(0, 0, 0, 0, 0, 1, 1, 0, 5, 1);
        assertEquals(0.0, p[0], 1.0e-6);
        assertTrue(p[2] > 5.0);
    }

    @Test
    void lookRayPointFromHighYGoesDownTheReticle() {
        double[] p = KiGuidanceMath.lookRayPoint(0, 200, 0, 0, -1, 0, 8, 200, 3, 1);
        assertTrue(p[1] < 200.0 - 7.0, "should send the shot down, not park 4 blocks below the camera");
        assertTrue(p[1] > 200.0 - 40.0, "must not slam to full control range");
    }

    @Test
    void slerpDirTurnsPastNinetyDegrees() {
        double[] next = KiGuidanceMath.slerpDir(1, 0, 0, -1, 0, 0, 0.35);
        assertTrue(next[0] < 0.0, "should turn toward the rear, not flip want back onto +X");
    }

    @Test
    void leadPointAdvancesAlongVelocity() {
        double[] lead = KiGuidanceMath.leadPoint(0, 0, 0, 0, 0, 1, 0, 0, -10, 2);
        assertEquals(0.0, lead[0], 1.0e-9);
        assertEquals(0.0, lead[1], 1.0e-9);
        assertTrue(lead[2] > 0.0);
        assertTrue(lead[2] <= KiGuidanceMath.LEAD_TICK_CAP);
    }
}
