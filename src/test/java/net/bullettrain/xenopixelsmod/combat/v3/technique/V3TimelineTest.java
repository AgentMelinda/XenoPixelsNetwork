package net.bullettrain.xenopixelsmod.combat.v3.technique;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3Beat.Kind;
import org.junit.jupiter.api.Test;

class V3TimelineTest {
    @Test void scriptedShovesHonorSideAndVerticalDirections() {
        var forward = new net.minecraft.world.phys.Vec3(0, 3, 5);
        assertVector(0, 0, 1, V3TechniqueRuntime.shoveDirection("FORWARD", forward));
        assertVector(0, 0, -1, V3TechniqueRuntime.shoveDirection("BACK", forward));
        assertVector(1, 0, 0, V3TechniqueRuntime.shoveDirection("LEFT", forward));
        assertVector(-1, 0, 0, V3TechniqueRuntime.shoveDirection("RIGHT", forward));
        assertVector(0, 1, 0, V3TechniqueRuntime.shoveDirection("UP", forward));
        assertVector(0, -1, 0, V3TechniqueRuntime.shoveDirection("DOWN", forward));
        assertVector(0, 0, 1, V3TechniqueRuntime.shoveDirection("unknown", net.minecraft.world.phys.Vec3.ZERO));
    }

    @Test void meleeFallbackCameraFollowsRushThenFramesTheThrownVictim() {
        var technique = new V3TechniqueDefinition("xenopixelsmod:bt3_camera_test", "Camera Test", "Test", "rush",
                20, 0, 0, null, "archetype_placeholder", List.of(), timelineBeats());
        var shots = V3TechniqueRuntime.cameraFor(technique, true);
        assertEquals(2, shots.size());
        assertEquals(0, shots.getFirst().tick());
        assertEquals(10, shots.getFirst().duration());
        assertEquals(0f, shots.getFirst().focus());
        assertEquals(10, shots.getLast().tick());
        assertEquals(10, shots.getLast().duration());
        assertEquals(0.5f, shots.getLast().focus());
        assertTrue(shots.getLast().position().length() > shots.getFirst().position().length());
        assertEquals(shots, V3CameraBeat.validate(shots, 20));
        assertTrue(V3TechniqueRuntime.cameraFor(technique, false).isEmpty(), "pure ki keeps its existing camera contract");
        var authored = new V3TechniqueDefinition(technique.id(), technique.name(), technique.sourceLabel(), technique.type(),
                20, 0, 0, null, technique.animationStatus(), List.of(), timelineBeats(), List.of(shots.getFirst()));
        assertEquals(authored.camera(), V3TechniqueRuntime.cameraFor(authored, true));
    }

    @Test void openingHoldTicksLengthensTheCloseAngleAndStealsFromTheWideShot() {
        var technique = new V3TechniqueDefinition("xenopixelsmod:bt3_camera_hold_test", "Hold Test", "Test", "rush",
                40, 0, 0, null, "archetype_placeholder", List.of(), timelineBeats());
        var baseline = V3TechniqueRuntime.cameraFor(technique, true, 0);
        assertEquals(10, baseline.getFirst().duration());
        var held = V3TechniqueRuntime.cameraFor(technique, true, 24);
        assertEquals(24, held.getFirst().duration(), "opening close angle uses the configured hold");
        assertEquals(24, held.getLast().tick());
        assertEquals(16, held.getLast().duration(), "wide shot keeps the remaining technique time");
        assertEquals(held, V3CameraBeat.validate(held, 40));
        var authored = new V3TechniqueDefinition(technique.id(), technique.name(), technique.sourceLabel(),
                technique.type(), 40, 0, 0, null, technique.animationStatus(), List.of(), timelineBeats(),
                List.of(new V3CameraBeat(0, 8, new net.minecraft.world.phys.Vec3(2.8, 1.0, -4.5),
                                net.minecraft.world.phys.Vec3.ZERO, 0.25f, V3CameraBeat.Easing.CUT),
                        new V3CameraBeat(8, 32, new net.minecraft.world.phys.Vec3(5.0, 2.0, -5.5),
                                net.minecraft.world.phys.Vec3.ZERO, 0.55f, V3CameraBeat.Easing.SMOOTH)));
        var kiVolley = V3TechniqueRuntime.cameraFor(authored, false, 20);
        assertEquals(20, kiVolley.getFirst().duration());
        assertEquals(20, kiVolley.getLast().tick());
        assertEquals(20, kiVolley.getLast().duration());
        assertEquals(kiVolley, V3CameraBeat.validate(kiVolley, 40));
    }

    @Test void openingHoldDoesNotStretchRushApproachCameras() {
        var rushBeats = List.of(
                new V3Beat(Kind.APPROACH, 0, 8, "", 0),
                new V3Beat(Kind.POSE, 2, 0, "a", 0),
                new V3Beat(Kind.STRIKE, 12, 0, "", 1),
                new V3Beat(Kind.SHOVE, 14, 0, "FORWARD", 0),
                new V3Beat(Kind.END, 40, 0, "", 0));
        var rush = new V3TechniqueDefinition("xenopixelsmod:bt3_camera_rush_hold", "Rush Hold", "Test", "melee",
                40, 0, 0, null, "archetype_placeholder", List.of(), rushBeats,
                List.of(new V3CameraBeat(0, 10, new net.minecraft.world.phys.Vec3(1.5, 1.4, -2.5),
                                net.minecraft.world.phys.Vec3.ZERO, 0.15f, V3CameraBeat.Easing.CUT),
                        new V3CameraBeat(10, 30, new net.minecraft.world.phys.Vec3(5.2, 2.1, -5.8),
                                net.minecraft.world.phys.Vec3.ZERO, 0.55f, V3CameraBeat.Easing.SMOOTH)));
        var held = V3TechniqueRuntime.cameraFor(rush, true, 80);
        assertEquals(10, held.getFirst().duration(), "rush/APPROACH keeps authored opening length");
        assertEquals(10, held.getLast().tick());
    }

    private static void assertVector(double x, double y, double z, net.minecraft.world.phys.Vec3 actual) {
        assertArrayEquals(new double[] {x, y, z}, new double[] {actual.x, actual.y, actual.z}, 1e-6);
    }

    @Test void approachGateDoesNotConsumeTheContactWaitingBehindIt() {
        var approach = new V3Beat(Kind.APPROACH, 0, 10, "", 0);
        var contact = new V3Beat(Kind.STRIKE, 0, 0, "", 2);
        var end = new V3Beat(Kind.END, 20, 0, "", 0);
        var timeline = new V3TechniqueRuntime.Timeline(List.of(approach, contact, end), 100);
        assertEquals(approach, timeline.take(100, null));
        timeline.delay(10);
        assertNull(timeline.take(109, null));
        assertEquals(contact, timeline.take(110, null), "rush pause must retain its contact beat");
        assertEquals(List.of(end), timeline.due(130));
    }

    @Test void mixedRushControlsItsVictimButPureBeamDoesNot() {
        var strike = new V3Beat(Kind.STRIKE, 2, 0, "", 1);
        var charge = new V3Beat(Kind.KI_CHARGE, 4, 0, "", 0);
        var release = new V3Beat(Kind.KI_RELEASE, 6, 0, "", 0);
        assertTrue(V3TechniqueRuntime.controlsVictim(List.of(strike, charge, release)));
        assertFalse(V3TechniqueRuntime.controlsVictim(List.of(charge, release)));
    }

    @Test void headBreakerOccurrencesUseHeadContactAndWideCameraWithoutClaimingReferenceParity() {
        var entries = V3TechniqueCatalog.loadBundled().stream().filter(t -> t.name().equals("Dodoria Head Breaker")).toList();
        assertEquals(2, entries.size());
        for (var entry : entries) {
            // Re-authored 2026-10-08 from the local source frames: timed from the video, not yet
            // compared against a rendered clip, so it stays an explicitly unverified draft.
            assertEquals("reference_timed_unverified", entry.animationStatus());
            assertFalse(entry.choreographyComplete());
            var poses = entry.beats().stream().filter(b -> b.kind() == Kind.POSE).map(V3Beat::payload).toList();
            assertTrue(poses.contains("combat.xeno_bt3_v3_dodoria_head_breaker_dive"), "torpedo dive plays through the fly-in");
            assertTrue(poses.contains("combat.xeno_bt3_v3_dodoria_head_breaker"), "contact clip plays on arrival");
            assertEquals(Kind.POSE, entry.beats().getFirst().kind(), "dive pose is sent before the approach starts");
            assertEquals(Kind.APPROACH, entry.beats().get(1).kind());
            assertEquals(1, entry.beats().stream().filter(b -> b.kind() == Kind.STRIKE).count());
            assertTrue(V3TechniqueRuntime.freezeTicks(entry.beats()) >= 6);
            var camera = V3TechniqueRuntime.cameraFor(entry, true);
            // Rush shot rides close behind the attacker; the wide shot after the throw frames both fighters.
            assertEquals(0f, camera.getFirst().focus());
            assertTrue(camera.getFirst().position().length() < 10);
            assertEquals(0.5f, camera.getLast().focus());
            assertTrue(camera.getLast().position().x >= 20);
        }
    }

    @Test void heldWindupGateDefersReleasePoseAndPreservesItsThreeTickLead() {
        var beats = List.of(
                new V3Beat(Kind.KI_CHARGE, 2, 0, "", 0),
                new V3Beat(Kind.KI_HOLD, 36, 0, "", 0),
                new V3Beat(Kind.POSE, 36, 0, "release_pose", 0),
                new V3Beat(Kind.KI_RELEASE, 39, 0, "", 0),
                new V3Beat(Kind.END, 61, 0, "", 0));
        var gate = V3TechniqueRuntime.holdKind(beats);
        assertEquals(Kind.KI_HOLD, gate);
        var timeline = new V3TechniqueRuntime.Timeline(beats, 100);
        assertEquals(List.of(Kind.KI_CHARGE), timeline.due(135, gate).stream().map(V3Beat::kind).toList());
        for (long now = 136; now < 156; now++) {
            assertTrue(timeline.due(now, gate).isEmpty(), "held slot cannot begin the release wind-up");
            assertTrue(timeline.due(gate, now));
            timeline.delay(1);
        }
        assertEquals(36, timeline.elapsed(156), "camera and contact clocks share the hold delay");
        assertEquals(List.of(Kind.KI_HOLD, Kind.POSE), timeline.due(156).stream().map(V3Beat::kind).toList());
        assertTrue(timeline.due(158).isEmpty());
        assertEquals(List.of(Kind.KI_RELEASE), timeline.due(159).stream().map(V3Beat::kind).toList());
        assertTrue(timeline.due(180).isEmpty());
        assertEquals(List.of(Kind.END), timeline.due(181).stream().map(V3Beat::kind).toList());
    }

    @Test void oldTimelinesKeepTheirReleaseGateAndKindOrderStaysStable() {
        var beats = List.of(new V3Beat(Kind.KI_CHARGE, 2, 0, "", 0),
                new V3Beat(Kind.KI_RELEASE, 10, 0, "", 0),
                new V3Beat(Kind.END, 20, 0, "", 0));
        assertEquals(Kind.KI_RELEASE, V3TechniqueRuntime.holdKind(beats));
        assertEquals(8, Kind.END.ordinal());
        assertEquals(9, Kind.KI_HOLD.ordinal(), "new internal kind is appended");
    }

    @Test void authoredKamehamehaReleasePoseWaitsWithTheHeldBeam() {
        var technique = V3TechniqueCatalog.loadBundled().stream()
                .filter(entry -> entry.id().equals("xenopixelsmod:bt3_early_kid_goku_kamehameha_15"))
                .findFirst().orElseThrow();
        var timeline = new V3TechniqueRuntime.Timeline(technique.beats(), 100);
        var preparation = timeline.due(135, Kind.KI_HOLD);
        assertTrue(preparation.stream().anyMatch(beat -> beat.kind() == Kind.POSE
                && beat.payload().endsWith("_charge")));
        for (long now = 136; now < 176; now++) {
            assertTrue(timeline.due(now, Kind.KI_HOLD).isEmpty(), "release pose must not bypass the held windup");
            assertTrue(timeline.due(Kind.KI_HOLD, now));
            timeline.delay(1);
        }
        var released = timeline.due(176);
        assertEquals(List.of(Kind.KI_HOLD, Kind.POSE), released.stream().map(V3Beat::kind).toList());
        assertTrue(released.getLast().payload().endsWith("_release"));
        assertTrue(timeline.due(178).isEmpty());
        assertEquals(Kind.KI_RELEASE, timeline.due(179).getFirst().kind());
        timeline.cancel();
        assertTrue(timeline.due(999).isEmpty());
    }

    private static V3TechniqueRuntime.Timeline timeline() {
        return new V3TechniqueRuntime.Timeline(List.of(
                new V3Beat(Kind.POSE, 0, 0, "a", 0f),
                new V3Beat(Kind.STRIKE, 6, 0, "", 0.5f),
                new V3Beat(Kind.STRIKE, 10, 0, "", 0.5f),
                new V3Beat(Kind.SHOVE, 10, 0, "FORWARD", 0f),
                new V3Beat(Kind.END, 20, 0, "", 0f)), 100);
    }

    @Test void contactBeatsFireOnceInOrder() {
        var t = timeline();
        assertEquals(List.of(Kind.POSE), t.due(100).stream().map(V3Beat::kind).toList());
        assertTrue(t.due(100).isEmpty());
        assertTrue(t.due(105).isEmpty());
        assertEquals(List.of(Kind.STRIKE), t.due(106).stream().map(V3Beat::kind).toList());
        assertEquals(List.of(Kind.STRIKE, Kind.SHOVE), t.due(110).stream().map(V3Beat::kind).toList());
        assertTrue(t.due(110).isEmpty());
        assertFalse(t.finished());
        assertEquals(List.of(Kind.END), t.due(120).stream().map(V3Beat::kind).toList());
        assertTrue(t.finished());
    }

    @Test void skippedServerTicksDoNotLoseContactBeats() {
        var t = timeline();
        assertEquals(List.of(Kind.POSE, Kind.STRIKE, Kind.STRIKE, Kind.SHOVE),
                t.due(115).stream().map(V3Beat::kind).toList());
        assertEquals(List.of(Kind.END), t.due(500).stream().map(V3Beat::kind).toList());
        assertTrue(t.due(501).isEmpty());
    }

    @Test void interruptionCancelsEveryFutureHit() {
        var t = timeline();
        t.due(106);
        t.cancel();
        assertTrue(t.due(110).isEmpty());
        assertTrue(t.due(999).isEmpty());
        assertTrue(t.finished());
        t.cancel();
        assertTrue(t.finished());
    }

    @Test void aClockThatRunsBackwardsFiresNothing() {
        var t = timeline();
        assertTrue(t.due(50).isEmpty());
        assertFalse(t.finished());
    }

    @Test void castAdmissionSpendsOnceAndRespectsCooldown() {
        var gate = new V3TechniqueRuntime.CastGate();
        assertTrue(gate.admit("xenopixelsmod:bt3_x", 100, 200, false));
        // Already casting: nothing starts and nothing is charged.
        assertFalse(gate.admit("xenopixelsmod:bt3_y", 101, 200, true));
        assertFalse(gate.admit("xenopixelsmod:bt3_x", 299, 200, false));
        assertTrue(gate.admit("xenopixelsmod:bt3_x", 300, 200, false));
        assertTrue(gate.admit("xenopixelsmod:bt3_y", 300, 200, false));
    }
    @Test void victimStaysFrozenUntilTheAttackThrowsThemOrEnds() {
        // A rush holds its victim through every hit and lets go at the shove that finishes it.
        assertEquals(10, V3TechniqueRuntime.freezeTicks(timelineBeats()));
        // A beam or ball has no shove: the victim is held until the technique ends.
        assertEquals(46, V3TechniqueRuntime.freezeTicks(List.of(
                new V3Beat(Kind.KI_CHARGE, 2, 0, "", 0f),
                new V3Beat(Kind.KI_RELEASE, 16, 0, "", 0f),
                new V3Beat(Kind.END, 46, 0, "", 0f))));
        assertEquals(0, V3TechniqueRuntime.freezeTicks(List.of()));
    }

    @Test void everyShippedAttackFreezesItsVictimForSomeTime() {
        for (V3TechniqueDefinition technique : V3TechniqueCatalog.loadBundled()) {
            assertTrue(V3TechniqueRuntime.freezeTicks(technique.beats()) >= 6, technique.id());
        }
    }

    @Test void closeControlIsBoundedBeforeResourcesAreSpent() {
        List<V3Beat> rush = List.of(
                new V3Beat(Kind.APPROACH, 0, 0, "", 0f),
                new V3Beat(Kind.STRIKE, 6, 0, "", 1f));
        assertTrue(V3TechniqueRuntime.requiresCloseControl(rush));
        double defaultApproach = net.bullettrain.xenopixelsmod.combat.v3.V3Config.Values.DEFAULT_STRIKE_APPROACH;
        assertTrue(V3TechniqueRuntime.inCastRange(true, defaultApproach));
        assertFalse(V3TechniqueRuntime.inCastRange(true, defaultApproach + 0.01));
        assertFalse(V3TechniqueRuntime.inCastRange(true, Double.NaN));
        assertTrue(V3TechniqueRuntime.inCastRange(false, 500.0), "ranged ki keeps its own projectile limit");
        // /xenoset v3.strikeApproachRange moves the bound without touching the pure rule.
        assertTrue(V3TechniqueRuntime.inCastRange(true, 40.0, 48.0));
        assertFalse(V3TechniqueRuntime.inCastRange(true, 40.0, 32.0));
    }

    @Test void controlOnlyMovesAreAuthorizedWithoutInventingADamageHit() {
        List<V3Beat> hold = List.of(
                new V3Beat(Kind.POSE, 0, 0, "hold", 0f),
                new V3Beat(Kind.HOLD_TARGET, 4, 20, "", 0f),
                new V3Beat(Kind.END, 24, 0, "", 0f));
        assertTrue(V3TechniqueRuntime.requiresCloseControl(hold));
        assertTrue(V3TechniqueRuntime.controlOnly(hold));
        assertFalse(V3TechniqueRuntime.controlOnly(timelineBeats()));
    }

    @Test void frozenVictimIsPulledBackOnlyWhenItActuallyDrifted() {
        assertFalse(V3TechniqueRuntime.drifted(0.0));
        assertFalse(V3TechniqueRuntime.drifted(0.2 * 0.2));
        assertTrue(V3TechniqueRuntime.drifted(0.5 * 0.5));
        assertTrue(V3TechniqueRuntime.drifted(Double.NaN));
    }

    private static List<V3Beat> timelineBeats() {
        return List.of(
                new V3Beat(Kind.POSE, 0, 0, "a", 0f),
                new V3Beat(Kind.STRIKE, 6, 0, "", 0.5f),
                new V3Beat(Kind.STRIKE, 10, 0, "", 0.5f),
                new V3Beat(Kind.SHOVE, 10, 0, "FORWARD", 0f),
                new V3Beat(Kind.END, 20, 0, "", 0f));
    }

    @Test void aHeldKiReleaseWaitsWhileTheRestOfTheTimelineMovesWithIt() {
        var beats = java.util.List.of(new V3Beat(V3Beat.Kind.KI_CHARGE, 2, 0, "", 0f),
                new V3Beat(V3Beat.Kind.KI_RELEASE, 10, 0, "", 0f), new V3Beat(V3Beat.Kind.END, 20, 0, "", 0f));
        var timeline = new V3TechniqueRuntime.Timeline(beats, 100);
        assertEquals(1, timeline.due(102).size());
        assertFalse(timeline.due(V3Beat.Kind.KI_RELEASE, 109), "not time yet");
        assertTrue(timeline.due(V3Beat.Kind.KI_RELEASE, 110));
        // Held for 30 ticks: one tick of delay each tick the release is due.
        for (long now = 110; now < 140; now++) {
            assertTrue(timeline.due(V3Beat.Kind.KI_RELEASE, now));
            timeline.delay(1);
            assertTrue(timeline.due(now).isEmpty(), "nothing fires while held");
        }
        assertEquals(V3Beat.Kind.KI_RELEASE, timeline.due(140).getFirst().kind());
        assertTrue(timeline.due(149).isEmpty(), "the end moved with the release");
        assertEquals(V3Beat.Kind.END, timeline.due(150).getFirst().kind());
        assertFalse(timeline.due(V3Beat.Kind.KI_RELEASE, 200), "a finished timeline holds nothing");
    }

    @Test void holdingTheSlotKeyChargesFromATapToDoubleAndNoFurther() {
        assertEquals(1.0f, V3TechniqueRuntime.chargePower(0));
        assertEquals(1.5f, V3TechniqueRuntime.chargePower(V3TechniqueRuntime.FULL_CHARGE_TICKS / 2), 1.0e-6f);
        assertEquals(2.0f, V3TechniqueRuntime.chargePower(V3TechniqueRuntime.FULL_CHARGE_TICKS));
        assertEquals(2.0f, V3TechniqueRuntime.chargePower(V3TechniqueRuntime.MAX_HOLD_TICKS));
        assertEquals(1.0f, V3TechniqueRuntime.chargePower(-5));
        assertTrue(V3TechniqueRuntime.MAX_HOLD_TICKS > V3TechniqueRuntime.FULL_CHARGE_TICKS);
        var tap = net.bullettrain.xenopixelsmod.combat.v3.ki.V3KiStyle.of("beam", "kamehameha");
        var full = tap.charged(2f);
        assertEquals(tap.damage() * 2f, full.damage(), 1.0e-6f);
        assertTrue(full.size() > tap.size());
        assertEquals(tap, tap.charged(1f));
        assertEquals(tap, tap.charged(Float.NaN));
        assertEquals(net.bullettrain.xenopixelsmod.combat.v3.V3Input.TECHNIQUE_RELEASE,
                net.bullettrain.xenopixelsmod.combat.v3.V3Input.decode(16), "appended, never reordered");
    }

    @Test void sameTickPoseCannotSkipAHeldReleaseOrAdvanceTheCameraClock() {
        var timeline = new V3TechniqueRuntime.Timeline(List.of(
                new V3Beat(Kind.KI_CHARGE, 2, 0, "", 0),
                new V3Beat(Kind.POSE, 10, 0, "release_pose", 0),
                new V3Beat(Kind.KI_RELEASE, 10, 0, "", 0),
                new V3Beat(Kind.END, 20, 0, "", 0)), 100);
        assertEquals(List.of(Kind.KI_CHARGE, Kind.POSE), timeline.due(110, Kind.KI_RELEASE).stream().map(V3Beat::kind).toList());
        assertTrue(timeline.due(Kind.KI_RELEASE, 110));
        timeline.delay(1);
        assertTrue(timeline.due(110, Kind.KI_RELEASE).isEmpty());
        assertEquals(10, timeline.elapsed(111), "held camera and attack share the shifted start time");
        assertEquals(List.of(Kind.KI_RELEASE), timeline.due(111).stream().map(V3Beat::kind).toList());
    }
}
