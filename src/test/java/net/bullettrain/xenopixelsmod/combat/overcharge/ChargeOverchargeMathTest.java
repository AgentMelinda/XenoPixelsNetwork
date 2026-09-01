package net.bullettrain.xenopixelsmod.combat.overcharge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargeOverchargeMathTest {

    @Test
    void costAtOneHundredIsOne() {
        assertEquals(1.0f, ChargeOverchargeMath.costMultiplier(100.0f), 1.0e-6f);
    }

    @Test
    void costAtDmzCapIsTwo() {
        // 1 + 75/75 = 2. The original DMZ formula. Widening the constant would break this.
        assertEquals(2.0f, ChargeOverchargeMath.costMultiplier(175.0f), 1.0e-6f);
    }

    @Test
    void costKeepsClimbingPastDmzCap() {
        assertTrue(ChargeOverchargeMath.costMultiplier(500.0f) > 2.0f);
        assertTrue(ChargeOverchargeMath.costMultiplier(1000.0f)
                > ChargeOverchargeMath.costMultiplier(500.0f));
        // 1 + 900/75 = 13
        assertEquals(13.0f, ChargeOverchargeMath.costMultiplier(1000.0f), 1.0e-5f);
    }

    @Test
    void costDeltaIsZeroWhenPercentDoesNotMove() {
        assertEquals(0.0f, ChargeOverchargeMath.chargeCostDelta(175.0f, 175.0f, 40.0f), 1.0e-6f);
    }

    @Test
    void costDeltaMatchesHalfBaseTimesMultiplierGap() {
        float delta = ChargeOverchargeMath.chargeCostDelta(175.0f, 200.0f, 40.0f);
        float expected = 0.5f * 40.0f * (ChargeOverchargeMath.costMultiplier(200.0f)
                - ChargeOverchargeMath.costMultiplier(175.0f));
        assertEquals(expected, delta, 1.0e-5f);
        assertTrue(delta > 0.0f);
    }

    @Test
    void incrementRateMatchesDmzTierOverBaseTicks() {
        assertEquals(25.0f, ChargeOverchargeMath.incrementRate(1), 1.0e-6f);
        assertEquals(2.5f, ChargeOverchargeMath.incrementRate(10), 1.0e-6f);
        assertEquals(25.0f, ChargeOverchargeMath.incrementRate(0), 1.0e-6f);
    }

    @Test
    void damageFactorAtDmzCapIsOne() {
        assertEquals(1.0f, ChargeOverchargeMath.damageFactor(175.0f, 4.0f, false), 1.0e-6f);
        assertEquals(1.0f, ChargeOverchargeMath.damageFactor(100.0f, 4.0f, false), 1.0e-6f);
    }

    @Test
    void damageFactorSoftCapsUnlessFullGameplay() {
        float raw = 1000.0f / 175.0f;
        assertEquals(4.0f, ChargeOverchargeMath.damageFactor(1000.0f, 4.0f, false), 1.0e-5f);
        assertEquals(raw, ChargeOverchargeMath.damageFactor(1000.0f, 4.0f, true), 1.0e-5f);
    }

    @Test
    void excessScaleIsOneAtOrBelowCap() {
        assertEquals(1.0f, ChargeOverchargeMath.excessScale(175.0f, 0.004f), 1.0e-6f);
        assertEquals(1.0f, ChargeOverchargeMath.excessScale(100.0f, 0.004f), 1.0e-6f);
    }

    @Test
    void excessScaleGrowsLinearlyPastCap() {
        assertEquals(1.0f + 825.0f * 0.004f,
                ChargeOverchargeMath.excessScale(1000.0f, 0.004f), 1.0e-5f);
    }

    @Test
    void visualScaleStartsAtOneHundred() {
        assertEquals(1.0f, ChargeOverchargeMath.chargeVisualScale(100.0f, 0.004f), 1.0e-6f);
        assertTrue(ChargeOverchargeMath.chargeVisualScale(175.0f, 0.004f) > 1.0f);
    }

    @Test
    void formSizeScaleNeverReturnsTheRawMultiplier() {
        float scale = ChargeOverchargeMath.formSizeScale(50.0, 0.25f, 4.0f);
        assertTrue(scale >= 1.0f);
        assertTrue(scale < 50.0f, "form scale leaked the raw multiplier: " + scale);
        // ln(50) ≈ 3.91, min with 4, * 0.25 ≈ 0.98 → ~1.98
        assertEquals(1.0f + 0.25f * (float) Math.log(50.0), scale, 1.0e-5f);
    }

    @Test
    void formSizeScaleIsOneForBaseOrInvalid() {
        assertEquals(1.0f, ChargeOverchargeMath.formSizeScale(1.0, 0.25f, 4.0f), 1.0e-6f);
        assertEquals(1.0f, ChargeOverchargeMath.formSizeScale(0.5, 0.25f, 4.0f), 1.0e-6f);
        assertEquals(1.0f, ChargeOverchargeMath.formSizeScale(Double.NaN, 0.25f, 4.0f), 1.0e-6f);
        assertEquals(1.0f, ChargeOverchargeMath.formSizeScale(20.0, 0.0f, 4.0f), 1.0e-6f);
    }

    @Test
    void nanAndInfInputsDoNotPropagate() {
        assertEquals(0.0f, ChargeOverchargeMath.costMultiplier(Float.NaN), 1.0e-6f);
        assertEquals(0.0f, ChargeOverchargeMath.chargeCostDelta(Float.POSITIVE_INFINITY, 200.0f, 10.0f),
                1.0e-6f);
        assertEquals(1.0f, ChargeOverchargeMath.excessScale(Float.NaN, 0.004f), 1.0e-6f);
        assertEquals(1.0f, ChargeOverchargeMath.damageFactor(Float.POSITIVE_INFINITY, 4.0f, true),
                1.0e-6f);
        assertEquals(0, ChargeOverchargeMath.cameraTier(Float.NaN));
    }

    @Test
    void cameraTiersRiseAtTheDocumentedStops() {
        assertEquals(0, ChargeOverchargeMath.cameraTier(175.0f));
        assertEquals(0, ChargeOverchargeMath.cameraTier(200.0f));
        assertEquals(1, ChargeOverchargeMath.cameraTier(400.0f));
        assertEquals(2, ChargeOverchargeMath.cameraTier(600.0f));
        assertEquals(3, ChargeOverchargeMath.cameraTier(800.0f));
        assertEquals(4, ChargeOverchargeMath.cameraTier(1000.0f));
    }

    @Test
    void typeRouting() {
        assertTrue(ChargeOverchargeMath.scalesSize("WAVE"));
        assertTrue(ChargeOverchargeMath.scalesSize("DISK"));
        assertTrue(ChargeOverchargeMath.scalesSize("GIANT_BALL"));
        assertTrue(ChargeOverchargeMath.scalesSize("EXPLOSION"));
        assertFalse(ChargeOverchargeMath.scalesSize("SHIELD"));
        assertFalse(ChargeOverchargeMath.scalesSize("BARRAGE"));
        assertTrue(ChargeOverchargeMath.scalesLife("WAVE"));
        assertFalse(ChargeOverchargeMath.scalesLife("DISK"));
        assertTrue(ChargeOverchargeMath.isInstant("SMALL_BALL"));
        assertTrue(ChargeOverchargeMath.isInstant("LASER"));
        assertFalse(ChargeOverchargeMath.isInstant("WAVE"));
    }
}
