package net.bullettrain.xenopixelsmod.client.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HakaiSweepRenderPolicyTest {
    @Test void cutRingRemainsVisibleAtFullyErasedEndpoint() {
        assertTrue(HakaiSweepRenderPolicy.shouldRenderRing(1.0f));
    }

    @Test void cutRingIsHiddenOutsideAnActiveSweep() {
        assertFalse(HakaiSweepRenderPolicy.shouldRenderRing(0.0f));
        assertFalse(HakaiSweepRenderPolicy.shouldRenderRing(-0.1f));
        assertFalse(HakaiSweepRenderPolicy.shouldRenderRing(1.1f));
        assertFalse(HakaiSweepRenderPolicy.shouldRenderRing(Float.NaN));
    }

    @Test void terminalPacketKeepsRingOnlyWhenPreviousClockReachedCompletedEndpoint() {
        assertTrue(HakaiSweepRenderPolicy.shouldHoldEndRing(1.0f));
        assertFalse(HakaiSweepRenderPolicy.shouldHoldEndRing(0.999f));
        assertFalse(HakaiSweepRenderPolicy.shouldHoldEndRing(Float.NaN));
    }

    @Test void duplicateTerminalClearKeepsUnexpiredCapturedRing() {
        assertTrue(HakaiSweepRenderPolicy.shouldKeepExistingEndRing(true, 10.0, 25.0));
        assertFalse(HakaiSweepRenderPolicy.shouldKeepExistingEndRing(false, 10.0, 25.0));
        assertFalse(HakaiSweepRenderPolicy.shouldKeepExistingEndRing(true, 25.0, 25.0));
        assertFalse(HakaiSweepRenderPolicy.shouldKeepExistingEndRing(true, Double.NaN, 25.0));
    }

    @Test void movingRingRadiusDoesNotSnapOrCollapseWhenModelContourChanges() {
        float enlarged = HakaiSweepRenderPolicy.stableRingRadius(0.5f, 1.5f, 0.4f);
        assertEquals(0.75f, enlarged, 1.0e-6f);

        float narrowed = HakaiSweepRenderPolicy.stableRingRadius(enlarged, 0.2f, 0.4f);
        assertEquals(0.6625f, narrowed, 1.0e-6f);
        assertTrue(narrowed >= 0.4f);
    }

    @Test void invalidRingMeasurementsFallBackToTheStableEntityMinimum() {
        assertEquals(0.6f, HakaiSweepRenderPolicy.stableRingRadius(Float.NaN, Float.NaN, 0.6f), 1.0e-6f);
        assertEquals(0.1875f, HakaiSweepRenderPolicy.stableRingRadius(0.2f, 0.1f, Float.NaN), 1.0e-6f);
    }

    @Test void visibleRingBandHasAStableMinimumAndReadableThickness() {
        float[] band = HakaiSweepRenderPolicy.visibleRingBand(0.5f);
        assertEquals(0.525f, band[0], 1.0e-6f);
        assertEquals(0.825f, band[1], 1.0e-6f);
        assertTrue(band[0] > 0.5f, "the ring should clear the mob's measured half-width");
        assertTrue(band[1] - band[0] > 0.25f);

        float[] fallback = HakaiSweepRenderPolicy.visibleRingBand(Float.NaN);
        assertEquals(0.1575f, fallback[0], 1.0e-6f);
        assertEquals(0.2475f, fallback[1], 1.0e-6f);
        assertTrue(fallback[0] > 0.15f);
    }

    @Test void contourSegmentIdentityDoesNotUseCollisionProneArithmeticPairing() {
        var first = HakaiSweepFx.SegmentKey.of(0L, 33L);
        var collidingUnderOldPairFormula = HakaiSweepFx.SegmentKey.of(1L, 2L);

        assertNotEquals(first, collidingUnderOldPairFormula);
        assertEquals(HakaiSweepFx.SegmentKey.of(33L, 0L), first);
    }

    @Test void nonFiniteCutSegmentsAreIgnored() {
        var extent = new HakaiSweepFx.Extent(0.0, 0.0, 0.0);
        extent.segment(Float.NaN, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
        extent.segment(0.0f, 0.0f, 0.0f, Float.POSITIVE_INFINITY, 1.0f, 1.0f);

        assertEquals(0, extent.segmentCount);
    }

    @Test void measuredWorldHeightMovesWithEntityTranslationWhileKeepingModelRelativeHeight() {
        float[] first = HakaiSweepRenderPolicy.fittedWorldBounds(
                9.5f, -60.0f, 19.5f, 10.5f, -57.0f, 20.5f, -58.0,
                new Vec3(10.0, -58.0, 20.0), 0.8f, 2.0f);
        float[] moved = HakaiSweepRenderPolicy.fittedWorldBounds(
                9.5f, -60.0f, 19.5f, 10.5f, -57.0f, 20.5f, -58.0,
                new Vec3(17.0, -55.0, 23.0), 0.8f, 2.0f);

        assertEquals(-60.0f, first[0], 1.0e-6f);
        assertEquals(-57.0f, first[1], 1.0e-6f);
        assertEquals(-57.0f, moved[0], 1.0e-6f);
        assertEquals(-54.0f, moved[1], 1.0e-6f);
        assertEquals(17.0f, moved[2], 1.0e-6f);
        assertEquals(23.0f, moved[3], 1.0e-6f);
        assertEquals(first[4], moved[4], 1.0e-6f);
    }

    @Test void fittedBoundsUseTheCurrentRenderSampleAgainstTheMeasuredAnchor() {
        float[] moved = HakaiSweepRenderPolicy.fittedWorldBounds(
                9.5f, -60.0f, 19.5f, 10.5f, -57.0f, 20.5f, -58.0,
                new Vec3(10.0, -55.5, 20.0), 0.8f, 2.0f);

        assertEquals(-57.5f, moved[0], 1.0e-6f);
        assertEquals(-54.5f, moved[1], 1.0e-6f);
    }

    @Test void unmeasuredModelFallsBackToCurrentEntityBounds() {
        float[] fallback = HakaiSweepRenderPolicy.fittedWorldBounds(
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Double.NaN,
                new Vec3(4.0, -10.0, 9.0), 0.8f, 2.0f);

        assertEquals(-10.0f, fallback[0], 1.0e-6f);
        assertEquals(-8.0f, fallback[1], 1.0e-6f);
        assertEquals(4.0f, fallback[2], 1.0e-6f);
        assertEquals(9.0f, fallback[3], 1.0e-6f);
        assertEquals(0.4f, fallback[4], 1.0e-6f);
    }

    @Test void ringFitUsesTheClosedCutContourInsteadOfWholeModelBounds() {
        float[] fit = HakaiSweepRenderPolicy.fittedRing(new float[]{
                10, 20, 12, 18, 10, 16, 8, 18, 10, 20
        });

        assertEquals(10.0f, fit[0], 1.0e-6f);
        assertEquals(18.0f, fit[1], 1.0e-6f);
        assertEquals(2.0f, fit[2], 1.0e-6f);
        assertEquals(8.0f, fit[3], 1.0e-6f);
    }

    @Test void ringFitRejectsOpenOrDegenerateContours() {
        assertNull(HakaiSweepRenderPolicy.fittedRing(new float[]{0, 0, 2, 0, 2, 2}));
        assertNull(HakaiSweepRenderPolicy.fittedRing(new float[]{0, 0, 1, 0, 2, 0, 0, 0}));
    }

    @Test void retainedContourTracksTheCurrentMovingCutPlane() {
        assertEquals(-0.75f, HakaiSweepRenderPolicy.contourYOffset(12.25f, 13.0f), 1.0e-6f);
        assertEquals(0.0f, HakaiSweepRenderPolicy.contourYOffset(Float.NaN, 13.0f), 1.0e-6f);
    }

    @Test void correctionKeepsTheScreenValueSoTheCutNeverSnapsBackUp() {
        assertEquals(0.34f, HakaiSweepRenderPolicy.reanchorProgress(0.02f, 0.34f, 0.30f, false), 1.0e-6f);
    }

    @Test void serverAuthorityAheadOfTheProjectionIsTaken() {
        assertEquals(0.5f, HakaiSweepRenderPolicy.reanchorProgress(0.02f, 0.34f, 0.5f, false), 1.0e-6f);
        // The first line for this entity has nothing on screen to preserve.
        assertEquals(0.05f, HakaiSweepRenderPolicy.reanchorProgress(0.02f, 0.9f, 0.05f, true), 1.0e-6f);
    }

    @Test void restoreAndGrossOverrunObeyTheServer() {
        // A negative slope is a real cancel: the body must visibly regrow to the server's value.
        assertEquals(0.2f, HakaiSweepRenderPolicy.reanchorProgress(-0.05f, 0.8f, 0.2f, false), 1.0e-6f);
        // Past the latency the client is allowed to lead by, the projection is wrong and gets corrected.
        assertEquals(0.1f, HakaiSweepRenderPolicy.reanchorProgress(0.02f, 0.9f, 0.1f, false), 1.0e-6f);
    }
}
