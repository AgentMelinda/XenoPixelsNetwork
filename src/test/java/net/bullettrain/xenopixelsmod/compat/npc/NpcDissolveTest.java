package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDissolveTest {
    @Test
    void ownerIdentityIncludesDimensionWhenRuntimeEntityIdsMatch() {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> overworld = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"));
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> nether = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("the_nether"));

        assertTrue(NpcDissolve.sameOwnerKey(overworld, 17, overworld, 17));
        assertFalse(NpcDissolve.sameOwnerKey(overworld, 17, nether, 17));
    }

    @Test
    void progressMapsOntoTheVanillaAmplifierByte() {
        assertEquals(0, NpcDissolve.amplifierForProgress(0.0f));
        assertEquals(255, NpcDissolve.amplifierForProgress(1.0f));
        assertEquals(128, NpcDissolve.amplifierForProgress(128 / 255.0f));
    }

    @Test
    void outOfRangeProgressClamps() {
        assertEquals(0, NpcDissolve.amplifierForProgress(-2.0f));
        assertEquals(255, NpcDissolve.amplifierForProgress(4.0f));
    }

    @Test
    void rateIsPerTickEvenWhenAcceptedWritesAreNotConsecutive() {
        assertEquals(0.02f, NpcDissolve.measuredRate(0.30f, 100L, 0.32f, 101L), 1.0e-6f);
        // A live channel can defer a source's writes for several ticks; the slope must survive the gap.
        assertEquals(0.02f, NpcDissolve.measuredRate(0.30f, 100L, 0.36f, 103L), 1.0e-6f);
    }

    @Test
    void firstMeasuredProgressAndReversalKeepTheirSigns() {
        assertEquals(0.0f, NpcDissolve.measuredRate(-1.0f, Long.MIN_VALUE, 0.05f, 100L), 1.0e-6f);
        assertEquals(-0.05f, NpcDissolve.measuredRate(0.40f, 100L, 0.30f, 102L), 1.0e-6f);
    }

    @Test void fadeSyncMatchesClientInterpolationAndAlwaysSendsFirstSample() {
        assertTrue(NpcDissolve.fadeSyncDue(Long.MIN_VALUE, 10));
        assertTrue(NpcDissolve.fadeSyncDue(10, 12));
        assertFalse(NpcDissolve.fadeSyncDue(10, 11));
        assertFalse(NpcDissolve.fadeSyncDue(10, 10));
    }

    @Test void timingSyncUsesCorrectionOrFiveTickRefreshInsteadOfSendingEveryApply() {
        assertFalse(NpcDissolve.timingSyncDue(false, 0.30f, 0.30f, 0.02f, 0.02f, 100L, 101L));
        assertTrue(NpcDissolve.timingSyncDue(false, 0.30f, 0.30f, 0.04f, 0.02f, 100L, 101L));
        assertTrue(NpcDissolve.timingSyncDue(false, 0.30f, 0.30f, -0.02f, 0.02f, 100L, 101L));
        assertTrue(NpcDissolve.timingSyncDue(false, 0.30f, 0.33f, 0.02f, 0.02f, 100L, 101L));
        assertTrue(NpcDissolve.timingSyncDue(false, 0.40f, 0.40f, 0.02f, 0.02f, 100L, 105L));
        assertTrue(NpcDissolve.timingSyncDue(true, 0.0f, 0.05f, 0.0f, 0.0f, Long.MIN_VALUE, 100L));
        assertTrue(NpcDissolve.timingSyncDue(false, 1.0f, 1.0f, 0.01f, 0.01f, 97L, 100L),
                "Completion must reach delayed clients before the immediate clear");
    }

    @Test void firstSourceAnchorsTheOriginWhileNobodyIsErasing() {
        assertTrue(NpcDissolve.originCaptureAllowed(false, false, Long.MIN_VALUE, 100L));
        assertTrue(NpcDissolve.originCaptureAllowed(false, false, 99L, 100L));
    }

    @Test void aForeignSweepCannotReAnchorUnderALiveChannel() {
        // HakaiChannelSystem, HakaiAreaSystem and NpcHakai all write the origin at channel start; only this check
        // keeps the last of them from teleporting an ongoing sweep.
        assertFalse(NpcDissolve.originCaptureAllowed(true, false, 100L, 100L));
        assertFalse(NpcDissolve.originCaptureAllowed(true, false, 100L, 103L));
    }

    @Test void aFreshCastOfTheSameSweepReAnchorsWhileItIsStillLive() {
        // Codex Q1: re-pressing the same channel on the same target may take the new crosshair.
        assertTrue(NpcDissolve.originCaptureAllowed(true, true, 100L, 100L));
        assertTrue(NpcDissolve.originCaptureAllowed(true, true, 100L, 101L));
    }

    @Test void originReAnchorsOnceNoChannelHasRaisedTheTarget() {
        assertTrue(NpcDissolve.originCaptureAllowed(true, false, 100L, 104L));
        // A target that was never raised (no channel ran) must not be permanently frozen by clock arithmetic.
        assertTrue(NpcDissolve.originCaptureAllowed(true, false, Long.MIN_VALUE, 100L));
    }

    @Test void sameSweepIdentityNeedsBothTheCasterAndTheChannelKind() {
        java.util.UUID a = java.util.UUID.randomUUID(), b = java.util.UUID.randomUUID();
        NpcDissolve.SweepKind channel = NpcDissolve.SweepKind.CHANNEL, area = NpcDissolve.SweepKind.AREA;
        assertTrue(NpcDissolve.sameSweepSource(a, channel, a, channel));
        // Codex Q1: the same caster crossing single-target to area must not move an in-flight ring.
        assertFalse(NpcDissolve.sameSweepSource(a, channel, a, area));
        assertFalse(NpcDissolve.sameSweepSource(a, area, a, channel));
        assertFalse(NpcDissolve.sameSweepSource(a, area, b, area));
        // An origin taken by the progress path is attributed to nobody, so no channel counts as a re-cast of it.
        assertFalse(NpcDissolve.sameSweepSource(null, null, a, area));
        assertFalse(NpcDissolve.sameSweepSource(a, area, null, area));
    }

    @Test void crossKindHandoverStaysStableThenReleases() {
        java.util.UUID caster = java.util.UUID.randomUUID();
        boolean sameSweep = NpcDissolve.sameSweepSource(caster, NpcDissolve.SweepKind.CHANNEL, caster,
                NpcDissolve.SweepKind.AREA);
        // Four-argument form: the arbitration law alone, which is what the gate delegates to when it is on.
        assertFalse(NpcDissolve.originCaptureAllowed(true, sameSweep, 100L, 101L)); // ring must not jump
        assertTrue(NpcDissolve.originCaptureAllowed(true, sameSweep, 100L, 104L));  // expired: free to capture
    }

    @Test void arbitrationOffRestoresTheLegacyAlwaysCapture() {
        // /xenoset hakaiOriginArbitration false: every cast rewrites the point, even a foreign one mid-sweep.
        assertTrue(NpcDissolve.originCaptureAllowedWithArbitration(false, true, false, 100L, 101L));
        assertTrue(NpcDissolve.originCaptureAllowedWithArbitration(false, true, true, 100L, 101L));
        // With the gate on, those same inputs keep the arbitration law.
        assertFalse(NpcDissolve.originCaptureAllowedWithArbitration(true, true, false, 100L, 101L));
        assertTrue(NpcDissolve.originCaptureAllowedWithArbitration(true, true, true, 100L, 101L));
    }

    @Test void arbitrationOnKeepsTheFirstCaptureAndTheExpiry() {
        assertTrue(NpcDissolve.originCaptureAllowedWithArbitration(true, false, false, 100L, 100L));
        assertTrue(NpcDissolve.originCaptureAllowedWithArbitration(true, true, false, 100L, 104L));
        assertTrue(NpcDissolve.originCaptureAllowedWithArbitration(true, true, false, Long.MIN_VALUE, 100L));
    }
}
