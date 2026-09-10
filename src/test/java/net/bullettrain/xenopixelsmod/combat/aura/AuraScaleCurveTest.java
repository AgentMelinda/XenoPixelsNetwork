package net.bullettrain.xenopixelsmod.combat.aura;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuraScaleCurveTest {

    private static final double PIVOT = 1.0e6;
    private static final double GAIN = 0.25;
    private static final double MAX = 2.5;

    private static float power(double battlePower) {
        return AuraScaleCurve.fromBattlePower(battlePower, PIVOT, GAIN, MAX);
    }

    @Test
    void aFreshCharacterGetsNoBonusAtAll() {
        assertEquals(1.0f, power(0), 1.0e-6f);
        assertEquals(1.0f, power(-5), 1.0e-6f);
    }

    @Test
    void theAuraGrowsWithPower() {
        assertTrue(power(1_000_000) > power(1_000));
        assertTrue(power(1_000_000_000L) > power(1_000_000));
    }

    @Test
    void growthIsLogarithmicNotProportional() {
        // A thousandfold more power must not mean a thousand times the aura; the whole point of the
        // curve is that a late character reads as bigger without filling the sky.
        float small = power(1_000_000);
        float huge = power(1_000_000_000L);
        assertTrue(huge < small * 2.0f, "expected " + huge + " to stay close to " + small);
    }

    @Test
    void theCeilingHolds() {
        assertEquals((float) MAX, power(1.0e30), 1.0e-4f);
    }

    @Test
    void aBadlyConfiguredCurveIsInertRatherThanBroken() {
        assertEquals(1.0f, AuraScaleCurve.fromBattlePower(1.0e9, 0, GAIN, MAX), 1.0e-6f);
        assertEquals(1.0f, AuraScaleCurve.fromBattlePower(1.0e9, PIVOT, 0, MAX), 1.0e-6f);
    }

    @Test
    void theRampRisesWhileChargingAndStopsAtOne() {
        float ramp = 0f;
        for (int i = 0; i < 40; i++) {
            ramp = AuraScaleCurve.advanceRamp(ramp, true, 1f, 20f);
        }
        assertEquals(1.0f, ramp, 1.0e-6f);
    }

    @Test
    void theRampFallsBackWhenChargingStops() {
        float ramp = AuraScaleCurve.advanceRamp(1.0f, false, 1f, 20f);
        assertTrue(ramp < 1.0f && ramp > 0.9f, "one tick should ease it down, not drop it: " + ramp);
        for (int i = 0; i < 80; i++) {
            ramp = AuraScaleCurve.advanceRamp(ramp, false, 1f, 20f);
        }
        assertEquals(0.0f, ramp, 1.0e-6f);
    }

    @Test
    void theFallIsSlowerThanTheRise() {
        float up = AuraScaleCurve.advanceRamp(0.5f, true, 1f, 20f) - 0.5f;
        float down = 0.5f - AuraScaleCurve.advanceRamp(0.5f, false, 1f, 20f);
        assertTrue(up > down, "flare fast, subside slow: rise " + up + " fall " + down);
    }

    @Test
    void aNaNRampRecoversToZero() {
        assertEquals(0.0f, AuraScaleCurve.advanceRamp(Float.NaN, false, 1f, 20f), 1.0e-6f);
    }

    @Test
    void heightClimbsFurtherThanWidth() {
        // The recognisable silhouette is a pillar, so height has to outrun width by a long way.
        assertTrue(AuraScaleCurve.chargeHeight(1f, 1.8) > AuraScaleCurve.chargeWidth(1f, 0.25) * 2f);
    }

    @Test
    void anIdleRampChangesNothing() {
        assertEquals(1.0f, AuraScaleCurve.chargeHeight(0f, 1.8), 1.0e-6f);
        assertEquals(1.0f, AuraScaleCurve.chargeWidth(0f, 0.25), 1.0e-6f);
    }
}
