package net.bullettrain.xenopixelsmod.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aura tunables' ceilings.
 *
 * <p>These were raised because the old limit of 12 was reachable in ordinary play and there was no
 * way past it. What is pinned here is that raising them did not turn into removing them: aura size
 * multiplies DragonMineZ's own model scale and feeds a shader draw, so an unbounded value is a
 * frozen client rather than a large aura.
 */
class XenoAuraConfigLimitsTest {

    @Test
    void theCeilingsAreFarAboveAnythingPlayableButStillFinite() {
        assertTrue(XenoAuraConfig.MAX_POWER >= 64.0,
                "the aura size ceiling should be far past the old 12");
        assertTrue(XenoAuraConfig.MAX_CHARGE_HEIGHT >= 64.0,
                "the transform height ceiling should be far past the old 12");
        for (double ceiling : new double[] {XenoAuraConfig.MAX_POWER,
                XenoAuraConfig.MAX_CHARGE_HEIGHT, XenoAuraConfig.MAX_CHARGE_WIDTH,
                XenoAuraConfig.MAX_GAIN, XenoAuraConfig.MAX_PIVOT, XenoAuraConfig.MAX_RAMP}) {
            assertTrue(Double.isFinite(ceiling) && ceiling > 0.0,
                    "every ceiling has to stay finite and positive; was " + ceiling);
        }
    }

    /**
     * The shipped defaults sit inside the ceilings.
     *
     * <p>Otherwise {@code reset()} would hand back a value the config then clamps to something else,
     * and resetting would not restore the shipped look.
     */
    @Test
    void theDefaultsAreWithinTheirOwnLimits() {
        XenoAuraConfig.reset();
        assertTrue(XenoAuraConfig.powerMax <= XenoAuraConfig.MAX_POWER);
        assertTrue(XenoAuraConfig.chargeHeight <= XenoAuraConfig.MAX_CHARGE_HEIGHT);
        assertTrue(XenoAuraConfig.chargeWidth <= XenoAuraConfig.MAX_CHARGE_WIDTH);
        assertTrue(XenoAuraConfig.kiChargeHeight <= XenoAuraConfig.MAX_CHARGE_HEIGHT);
        assertTrue(XenoAuraConfig.kiChargeWidth <= XenoAuraConfig.MAX_CHARGE_WIDTH);
        assertTrue(XenoAuraConfig.powerGain <= XenoAuraConfig.MAX_GAIN);
        assertTrue(XenoAuraConfig.powerPivot <= XenoAuraConfig.MAX_PIVOT);
        assertTrue(XenoAuraConfig.rampTicks <= XenoAuraConfig.MAX_RAMP);
    }

    /** The transform height is the knob the user asked for; it must genuinely have moved. */
    @Test
    void theTransformHeightCeilingActuallyRose() {
        assertTrue(XenoAuraConfig.MAX_CHARGE_HEIGHT > 12.0,
                "chargeHeight was capped at 12 and that was the complaint");
    }

    @Test
    void resetRestoresTheShippedLook() {
        XenoAuraConfig.powerMax = 99.0;
        XenoAuraConfig.chargeHeight = 99.0;
        XenoAuraConfig.kiChargeHeight = 99.0;
        XenoAuraConfig.kiChargeWidth = 19.0;
        XenoAuraConfig.reset();
        assertEquals(2.5, XenoAuraConfig.powerMax, 1.0e-9);
        assertEquals(1.8, XenoAuraConfig.chargeHeight, 1.0e-9);
        assertEquals(1.8, XenoAuraConfig.kiChargeHeight, 1.0e-9);
        assertEquals(0.25, XenoAuraConfig.kiChargeWidth, 1.0e-9);
    }
}
