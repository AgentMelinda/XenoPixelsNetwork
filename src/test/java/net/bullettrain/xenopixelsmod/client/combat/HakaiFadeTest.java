package net.bullettrain.xenopixelsmod.client.combat;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HakaiFadeTest {
    @Test
    void amplifierZeroIsSolid() {
        assertEquals(1.0f, HakaiFade.alphaFromAmplifier(0), 1.0e-5f);
    }

    @Test
    void amplifierMidwayIsHalf() {
        assertEquals(1.0f - 128 / 255.0f, HakaiFade.alphaFromAmplifier(128), 1.0e-5f);
    }

    @Test
    void amplifierMaxIsAlmostGoneNotInvisible() {
        float alpha = HakaiFade.alphaFromAmplifier(255);
        assertTrue(alpha > 0.0f && alpha <= 0.02f);
    }

    @Test
    void alphaDecreasesMonotonicallyAcrossCharge() {
        float previous = HakaiFade.alphaFromAmplifier(0);
        for (int amplifier = 1; amplifier <= 255; amplifier++) {
            float current = HakaiFade.alphaFromAmplifier(amplifier);
            assertTrue(current <= previous,
                    "alpha must not increase as charge builds (amp=" + amplifier + ")");
            previous = current;
        }
    }

    @Test
    void amplifierBelowRangeClampsToSolid() {
        assertEquals(1.0f, HakaiFade.alphaFromAmplifier(-40), 1.0e-5f);
    }

    @Test
    void amplifierAboveRangeClampsToFloor() {
        assertEquals(HakaiFade.alphaFromAmplifier(255), HakaiFade.alphaFromAmplifier(4000), 1.0e-5f);
        assertTrue(HakaiFade.alphaFromAmplifier(4000) > 0.0f);
    }

    @Test
    void curveAboveOneFadesLate() {
        float linear = HakaiFade.alphaFor(0.5f, 0.0f, 1.0f);
        float late = HakaiFade.alphaFor(0.5f, 0.0f, 2.0f);
        assertTrue(late > linear, "curve > 1 must hold the body opaque longer");
    }

    @Test
    void curveBelowOneFadesEarly() {
        float linear = HakaiFade.alphaFor(0.5f, 0.0f, 1.0f);
        float early = HakaiFade.alphaFor(0.5f, 0.0f, 0.5f);
        assertTrue(early < linear, "curve < 1 must front-load the fade");
    }

    @Test
    void minAlphaZeroReachesInvisible() {
        assertEquals(0.0f, HakaiFade.alphaFor(1.0f, 0.0f, 1.0f), 1.0e-5f);
    }

    @Test
    void minAlphaFloorsTheCurve() {
        assertEquals(0.4f, HakaiFade.alphaFor(1.0f, 0.4f, 1.0f), 1.0e-5f);
        assertEquals(0.4f, HakaiFade.alphaFor(2.0f, 0.4f, 1.0f), 1.0e-5f);
    }

    @Test
    void progressOutsideRangeIsClamped() {
        assertEquals(1.0f, HakaiFade.alphaFor(-1.0f, 0.02f, 1.0f), 1.0e-5f);
        assertEquals(0.02f, HakaiFade.alphaFor(2.0f, 0.02f, 1.0f), 1.0e-5f);
    }

    @Test
    void alphaForIsMonotonicForAnyCurve() {
        for (float curve : new float[] {0.25f, 0.5f, 1.0f, 2.0f, 4.0f}) {
            float previous = HakaiFade.alphaFor(0.0f, 0.02f, curve);
            for (int step = 1; step <= 100; step++) {
                float current = HakaiFade.alphaFor(step / 100.0f, 0.02f, curve);
                assertTrue(current <= previous + 1.0e-6f,
                        "alpha must not increase (curve=" + curve + ", step=" + step + ")");
                previous = current;
            }
        }
    }

    @Test
    void defaultConfigReproducesTheLinearRamp() {
        assertEquals(1.0f, HakaiFade.alphaFor(0.0f, 0.02f, 1.0f), 1.0e-5f);
        assertEquals(1.0f - 128 / 255.0f, HakaiFade.alphaFor(128 / 255.0f, 0.02f, 1.0f), 1.0e-5f);
        assertEquals(0.02f, HakaiFade.alphaFor(255 / 255.0f, 0.02f, 1.0f), 1.0e-5f);
    }

    @Test
    void progressZeroIsSolidAtEveryHeight() {
        for (int step = 0; step <= 10; step++) {
            float y = step / 10.0f;
            assertEquals(1.0f, HakaiFade.alphaAtHeight(0.0f, y, 0.02f, 1.0f, 1.0f, 0.18f), 1.0e-5f);
        }
    }

    @Test
    void midChargeFadesHeadLeavesFeet() {
        float head = HakaiFade.alphaAtHeight(0.5f, 1.0f, 0.02f, 1.0f, 1.0f, 0.18f);
        float feet = HakaiFade.alphaAtHeight(0.5f, 0.0f, 0.02f, 1.0f, 1.0f, 0.18f);
        assertEquals(0.02f, head, 1.0e-5f);
        assertEquals(1.0f, feet, 1.0e-5f);
        assertTrue(head < feet);
    }

    @Test
    void fullChargeIsFloorAtEveryHeight() {
        for (int step = 0; step <= 10; step++) {
            float y = step / 10.0f;
            assertEquals(0.02f, HakaiFade.alphaAtHeight(1.0f, y, 0.02f, 1.0f, 1.0f, 0.18f), 1.0e-5f);
        }
    }

    @Test
    void speedTwoFinishesWipeAtHalfCharge() {
        assertEquals(1.0f, HakaiFade.charged(0.5f, 2.0f, 1.0f), 1.0e-5f);
        assertEquals(0.02f, HakaiFade.alphaAtHeight(0.5f, 0.0f, 0.02f, 1.0f, 2.0f, 0.18f), 1.0e-5f);
        assertEquals(0.02f, HakaiFade.alphaAtHeight(0.5f, 1.0f, 0.02f, 1.0f, 2.0f, 0.18f), 1.0e-5f);
    }

    @Test
    void cameraYOutsideBodyFallsBackToUniform() {
        float uniform = 0.4f;
        // localY = (cameraSpaceY + cameraY - feetY) / height. height=2, feet=0, cameraY=0
        // cameraSpaceY=4 → localY=2, outside [−0.25, 1.25]
        assertEquals(uniform, HakaiFade.alphaAtCameraY(
                0.5f, 4.0f, 0.0f, 0.0f, 2.0f, 0.02f, 1.0f, 1.0f, 0.18f, uniform), 1.0e-5f);
        // cameraSpaceY=1 → localY=0.5, mid-body, uses the wipe (not the uniform)
        float mid = HakaiFade.alphaAtCameraY(
                0.5f, 1.0f, 0.0f, 0.0f, 2.0f, 0.02f, 1.0f, 1.0f, 0.18f, uniform);
        assertTrue(mid > 0.02f && mid < 1.0f);
    }

    @Test
    void packetAmplifierIsStoredAndCleared() {
        HakaiFade.clear();
        try {
            HakaiFade.set(42, 128);
            assertEquals(128, HakaiFade.amplifierOf(42));
            HakaiFade.set(42, 0);
            assertNull(HakaiFade.amplifierOf(42));
            HakaiFade.set(7, 4000);
            assertEquals(255, HakaiFade.amplifierOf(7));
            HakaiFade.clear();
            assertNull(HakaiFade.amplifierOf(7));
        } finally {
            HakaiFade.clear();
        }
    }

    @Test
    void firstPacketSnapsAndLaterPacketsLerp() {
        HakaiFade.clear();
        try {
            HakaiFade.set(9, 64, 10L);
            assertEquals(64.0f, HakaiFade.lerpedAmplifier(9, 0.5f, 10L), 1.0e-4f);
            HakaiFade.set(9, 192, 11L);
            assertEquals(128.0f, HakaiFade.lerpedAmplifier(9, 0.5f, 11L), 1.0e-4f);
            assertEquals(192.0f, HakaiFade.lerpedAmplifier(9, 1.0f, 11L), 1.0e-4f);
        } finally {
            HakaiFade.clear();
        }
    }

    @Test
    void pitchedCameraMapsUpToWorldFeetOnShortMobs() {
        float identityWorldY = HakaiFade.worldYFromCameraSpace(0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f);
        assertEquals(1.0f, identityWorldY, 1.0e-5f);

        Quaternionf lookDown = new Quaternionf().rotationX((float) (Math.PI / 2.0));
        float pitchedWorldY = HakaiFade.worldYFromCameraSpace(0.0f, 1.0f, 0.0f, 0.0f,
                lookDown.x, lookDown.y, lookDown.z, lookDown.w);
        assertEquals(0.0f, pitchedWorldY, 1.0e-4f);

        float pigHeight = 0.9f;
        float identityTop = HakaiFade.alphaAtWorldY(0.5f, identityWorldY, 0.0f, pigHeight,
                0.02f, 1.0f, 1.0f, 0.30f, 0.4f);
        float pitchedFeet = HakaiFade.alphaAtWorldY(0.5f, pitchedWorldY, 0.0f, pigHeight,
                0.02f, 1.0f, 1.0f, 0.30f, 0.4f);
        assertEquals(0.02f, identityTop, 1.0e-4f);
        assertEquals(1.0f, pitchedFeet, 1.0e-4f);
        assertTrue(pitchedFeet > identityTop);
    }
}
