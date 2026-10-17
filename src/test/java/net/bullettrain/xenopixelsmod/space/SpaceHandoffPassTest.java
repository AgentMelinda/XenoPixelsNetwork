package net.bullettrain.xenopixelsmod.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SpaceHandoffPass}'s pure altitude rule — the arithmetic that decides who
 * crosses and where they arrive. Nothing here touches a server, a level, or an event, which is the whole
 * point of keeping those five statics Minecraft-type-free: the round trip that must never oscillate, the
 * host that is too short to express an exit altitude, and the frame conversion between the Voidsea and a
 * planet are all checkable without booting anything.
 *
 * <p>The live half of the feature — a climbing survival player actually being moved, the arrival sitting
 * over the body's proxy, the transition screen staying invisible — is <b>not verified</b> by this file;
 * it needs a running server and is gated on the plan's runtime capture.
 */
class SpaceHandoffPassTest {
    @Test void earthOrbitHandoffWaitsForLivePhysicalAnchorUnlessEarthWasErased() {
        assertTrue(SpaceHandoffPass.requiresLiveEarthOrbitAnchor("xenopixelsmod:earth", true, false));
        assertFalse(SpaceHandoffPass.requiresLiveEarthOrbitAnchor("xenopixelsmod:earth", true, true));
        assertFalse(SpaceHandoffPass.requiresLiveEarthOrbitAnchor("xenopixelsmod:mars", true, false));
        assertFalse(SpaceHandoffPass.requiresLiveEarthOrbitAnchor("xenopixelsmod:earth", false, false));
    }

    @Test void outboundAnchorWarmUpStartsAtConfiguredAltitude() {
        assertFalse(SpaceHandoffPass.warmUpReached(399.99, 400));
        assertTrue(SpaceHandoffPass.warmUpReached(400, 400));
        assertTrue(SpaceHandoffPass.warmUpReached(700, 400));
        assertFalse(SpaceHandoffPass.warmUpReached(Double.NaN, 400));
    }

    @Test void inboundAnchorWarmUpMirrorsThePlanetWarmUpSpanFromHostArrival() {
        // exit 700, 128-block band -> host arrival 764; body-side warm-up span is 300, so begin by Y1064.
        assertFalse(SpaceHandoffPass.inboundPrewarmReached(1064.01, 700, 128, 400));
        assertTrue(SpaceHandoffPass.inboundPrewarmReached(1064, 700, 128, 400));
        assertTrue(SpaceHandoffPass.inboundPrewarmReached(764, 700, 128, 400));
        assertTrue(SpaceHandoffPass.inboundPrewarmReached(700, 700, 128, 400));
    }

    @Test void inboundAnchorWarmUpRejectsInvalidOrInvertedConfiguration() {
        assertFalse(SpaceHandoffPass.inboundPrewarmReached(764, 700, 0, 400));
        assertFalse(SpaceHandoffPass.inboundPrewarmReached(764, 700, -1, 400));
        assertFalse(SpaceHandoffPass.inboundPrewarmReached(764, 700, 128, 700));
        assertFalse(SpaceHandoffPass.inboundPrewarmReached(Double.NaN, 700, 128, 400));
        assertFalse(SpaceHandoffPass.inboundPrewarmReached(764, Double.NaN, 128, 400));
    }

    @Test void surfaceEntryWarmUpStartsBeforeContactAndRejectsNonFiniteGaps() {
        assertTrue(SpaceHandoffPass.withinInboundSurfaceWarmup(512.0, 512.0));
        assertTrue(SpaceHandoffPass.withinInboundSurfaceWarmup(0.0, 512.0));
        assertTrue(SpaceHandoffPass.withinInboundSurfaceWarmup(-2.0, 512.0));
        assertFalse(SpaceHandoffPass.withinInboundSurfaceWarmup(512.01, 512.0));
        assertFalse(SpaceHandoffPass.withinInboundSurfaceWarmup(Double.NaN, 512.0));
        assertFalse(SpaceHandoffPass.withinInboundSurfaceWarmup(1.0, Double.NaN));
    }
    @Test void configuredWarmUpMustRemainBelowTheExit() {
        assertNull(SpaceHandoffPass.warmUpProblem(400, 700));
        assertNotNull(SpaceHandoffPass.warmUpProblem(700, 700));
        assertNotNull(SpaceHandoffPass.warmUpProblem(800, 700));
        assertNotNull(SpaceHandoffPass.warmUpProblem(Double.NaN, 700));
    }
    @Test void earthExitAboveBuildHeightStillHasAValidRoundTrip() {
        assertNull(SpaceHandoffPass.exitProblem(700, 64, -64, 384, 128, 0, 1024));
    }

    @Test void exitValidatesAnchorAndAllowsConfiguredHighAltitude() {
        assertNotNull(SpaceHandoffPass.exitProblem(64, 64, -64, 384, 128, 0, 1024));
        assertNull(SpaceHandoffPass.exitProblem(958, 64, -64, 384, 128, 0, 1024));
        // Exit altitude is a flight/handoff coordinate; it is allowed above either dimension's build ceiling.
        assertNull(SpaceHandoffPass.exitProblem(2032, 64, -64, 384, 128, 0, 1024));
        assertNotNull(SpaceHandoffPass.exitProblem(-100, -200, -64, 384, 128, 0, 1024));
        assertNotNull(SpaceHandoffPass.exitProblem(Double.NaN, 64, -64, 384, 128, 0, 1024));
    }
    private static final double NaN = Double.NaN;

    // --- leaving / returning are one altitude, mirrored, with strict inequality on the up leg ---

    @Test void climbingStrictlyPastTheExitLeavesStandingOnItDoesNot() {
        assertTrue(SpaceHandoffPass.leaving(701.0, 700.0));
        assertFalse(SpaceHandoffPass.leaving(700.0, 700.0), "on the line is not a crossing");
        assertFalse(SpaceHandoffPass.leaving(699.0, 700.0));
    }

    @Test void descendingAtOrBelowTheExitReturnsFromAboveItDoesNot() {
        assertTrue(SpaceHandoffPass.returning(700.0, 700.0), "the down leg is inclusive so it cannot be straddled");
        assertTrue(SpaceHandoffPass.returning(600.0, 700.0));
        assertFalse(SpaceHandoffPass.returning(701.0, 700.0));
    }

    @Test void nonFiniteInputsAreNeverACrossing() {
        assertFalse(SpaceHandoffPass.leaving(NaN, 700.0));
        assertFalse(SpaceHandoffPass.leaving(700.0, NaN));
        assertFalse(SpaceHandoffPass.returning(NaN, 700.0));
        assertFalse(SpaceHandoffPass.returning(700.0, NaN));
    }

    // --- hostArrivalY: half a band above the exit, refused when the host cannot hold it ---

    @Test void hostArrivalSitsHalfABandAboveTheExit() {
        // Earth exits at 700 in a height-1024 host (min_y 0): 700 + 128/2 = 764, under the 1022 ceiling.
        assertEquals(764.0, SpaceHandoffPass.hostArrivalY(700.0, 128.0, 1024.0), 1e-6);
    }

    @Test void hostArrivalRemainsAFlightCoordinateAboveBuildCeiling() {
        // The player may fly above build height; the configured exit+half-band is not clamped to block height.
        assertEquals(764.0, SpaceHandoffPass.hostArrivalY(700.0, 128.0, 384.0), 1e-6);
        // A Y=2032 exit arrives at 2096 in the host, regardless of its block build ceiling.
        assertEquals(2096.0, SpaceHandoffPass.hostArrivalY(2032.0, 128.0, 1024.0), 1e-6);
        assertEquals(2096.0, SpaceHandoffPass.hostArrivalY(2032.0, 128.0, 2032.0), 1e-6);
    }

    @Test void hostArrivalRejectsDegenerateBandAndTop() {
        assertTrue(Double.isNaN(SpaceHandoffPass.hostArrivalY(700.0, 0.0, 1024.0)));
        assertTrue(Double.isNaN(SpaceHandoffPass.hostArrivalY(700.0, -10.0, 1024.0)));
        assertTrue(Double.isNaN(SpaceHandoffPass.hostArrivalY(700.0, 128.0, NaN)));
        assertTrue(Double.isNaN(SpaceHandoffPass.hostArrivalY(NaN, 128.0, 1024.0)));
    }

    @Test void surfaceModeArrivesJustOutsideThePlanetSphereInsteadOfAtTheExitAltitude() {
        // A 2032-block planet-side exit must not place the player 64 blocks above that threshold in Voidsea.
        assertEquals(233.0, SpaceHandoffPass.hostSurfaceArrivalY(0.0, 230.0, 2.0), 1e-6);
        assertEquals(733.0, SpaceHandoffPass.hostSurfaceArrivalY(500.0, 230.0, 2.0), 1e-6,
                "arrival follows a live body's vertical center");
        assertTrue(SpaceHandoffPass.surfaceGap(0.0, 233.0, 0.0, 0.0, 0.0, 0.0, 230.0) > 2.0,
                "arrival must be outside the surface-entry contact margin");
        assertEquals(2096.0, SpaceHandoffPass.hostArrivalY(2032.0, 128.0, 1024.0), 1e-6,
                "altitude-mode helper remains unchanged");
    }

    @Test void surfaceModeArrivalRejectsInvalidSphereInputs() {
        assertTrue(Double.isNaN(SpaceHandoffPass.hostSurfaceArrivalY(Double.NaN, 230.0, 2.0)));
        assertTrue(Double.isNaN(SpaceHandoffPass.hostSurfaceArrivalY(0.0, 0.0, 2.0)));
        assertTrue(Double.isNaN(SpaceHandoffPass.hostSurfaceArrivalY(0.0, 230.0, -1.0)));
    }

    @Test void outboundHostArrivalSelectsSphereForSurfaceModeAndBandForAltitudeMode() {
        assertEquals(233.0, SpaceHandoffPass.outboundHostArrivalY(
                true, 0.0, 230.0, 2.0, 2032.0, 128.0, 1024.0), 1e-6);
        assertEquals(2096.0, SpaceHandoffPass.outboundHostArrivalY(
                false, 0.0, 230.0, 2.0, 2032.0, 128.0, 1024.0), 1e-6);
    }

    // --- bodyArrivalY: the anchor, clamped below the exit and into the build volume, or refused ---

    @Test void reentryUsesExitMinus25EvenAboveBuildHeight() {
        // Earth returns at 675, above build height, with 25 blocks of boundary separation.
        assertEquals(675.0, SpaceHandoffPass.bodyArrivalY(64.0, 700.0, -64, 384), 1e-6);
        assertEquals(2007.0, SpaceHandoffPass.bodyArrivalY(64.0, 2032.0, -64, 384), 1e-6);
        assertTrue(SpaceHandoffPass.returning(2007.0, 2032.0));
    }

    @Test void anchorOnBoundaryDoesNotChangeMinus25Arrival() {
        // The oscillation the plan is built to prevent: a row authoring anchor == exit would otherwise
        // arrive on the trigger that fires them back out. It must land strictly under the exit instead.
        double arrival = SpaceHandoffPass.bodyArrivalY(700.0, 700.0, -64, 1024);
        assertTrue(arrival < 700.0, "arrival " + arrival + " must be below the exit to terminate");
        assertEquals(675.0, arrival, 1e-6);
        // And the mirrored up-leg from that arrival does not fire: below exit is not "leaving".
        assertFalse(SpaceHandoffPass.leaving(arrival, 700.0));
    }

    @Test void aLowAnchorDoesNotChangeAirborneReentry() {
        // A hand-edited anchor under min_y would teleport the descending player into the void they escaped.
        double arrival = SpaceHandoffPass.bodyArrivalY(-500.0, 700.0, -64, 384);
        assertTrue(arrival >= -64.0, "clamped to the floor, got " + arrival);
    }

    @Test void aBodyWhoseWholeVolumeSitsAboveItsExitIsRefused() {
        // exit below the dimension floor: nothing in [min_y, top) can be under the trigger, so a descent
        // could never hand them in — say so with NaN rather than bouncing.
        assertTrue(Double.isNaN(SpaceHandoffPass.bodyArrivalY(10.0, 0.0, 64, 320)));
    }

    @Test void bodyArrivalRejectsNonFiniteExit() {
        assertTrue(Double.isNaN(SpaceHandoffPass.bodyArrivalY(64.0, NaN, -64, 384)));
    }

    @Test void noAnchorIsRequiredForAirborneReentry() {
        // NaN anchor (a body with no handoff_anchor_y authored) arrives at the ceiling, not the exit — but
        // only in a dimension tall enough to hold it. The overworld (top 320) cannot hold 699 and is
        // refused (see aBodyWhoseWholeVolumeSitsAboveItsExitIsRefused); a tall authored body dimension can.
        assertEquals(675.0, SpaceHandoffPass.bodyArrivalY(NaN, 700.0, -64, 1024), 1e-6);
        // Flying arrivals above the overworld build ceiling remain valid.
        assertEquals(675.0, SpaceHandoffPass.bodyArrivalY(NaN, 700.0, -64, 384));
    }

    // --- toBody: the Voidsea is chart coordinates, the planet is its own origin (§19.4) ---

    @Test void bodyFrameIsTheHostCoordinateMinusTheBodyChartColumn() {
        assertEquals(0.0, SpaceHandoffPass.toBody(1000.0, 1000.0), 1e-6, "directly above the column lands at planet origin");
        assertEquals(-1500.0, SpaceHandoffPass.toBody(-500.0, 1000.0), 1e-6);
        assertEquals(300.0, SpaceHandoffPass.toBody(300.0, 0.0), 1e-6);
    }
}
