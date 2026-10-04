package net.bullettrain.xenopixelsmod.anim;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.client.anim.XenoAnimClip;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimBoneTrack;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimChannel;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimKey;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the shipped wave clips actually do, read off their keyframes.
 *
 * <p>{@code SocialClipsTest} checks the envelope: a file that parses, bones the rig has, one
 * animation per file. This checks the choreography, because the whole point of the 2026-09-26
 * rewrite is the motion - raise the hand forward, sweep it side to side three times, come back to
 * rest without a snap. A clip can satisfy every structural rule and still be a stiff arm pumping
 * up and down, which is exactly what the previous wave was.
 *
 * <p>The rig has no hand or forearm bone, so "the hand" is {@code right_arm}: the sweep lives in
 * its rotation Y (yaw swings a raised arm sideways) with a matching position X sway. Forward is
 * negative rotation X and negative position Z, the convention {@code docs/xeno-anim-studio.md}
 * records from the jab clip.
 */
class WaveClipContentTest {

    private static final String DIR =
            "src/main/resources/assets/xenopixelsmod/animations/social";

    private static final List<String> WAVES = List.of("wave", "hi_wave");

    /** {@code XenoRig.COMBAT}; copied because the rig is client-only source. */
    private static final Set<String> RIG = Set.of(
            "root", "waist", "head", "right_arm", "left_arm", "right_leg", "left_leg");

    /** Every easing in {@code AnimEasing.CYCLE} except the two that read as a snap. */
    private static final Set<String> SMOOTH = Set.of(
            "easeinsine", "easeoutsine", "easeinoutsine", "easeinoutcubic", "catmullrom");

    /** The pre-rewrite shipped files, whose hashes the library must still recognise. */
    private static final String OLD_WAVE =
            "ab23a030c6ec315f650502d443f53192cc31af574d12ba8ec5cbb3646699626f";
    private static final String OLD_HI_WAVE =
            "58b60296dcf22c0ede829257fff8c0d1ae2a4b16539f1e6e9172ef5a0fc4764d";
    private static final String PREVIOUS_SHIPPED_HI_WAVE =
            "65279581e7769dc312a67bbdee17b6bb2bf0e787d99dbe6e94bd80758a14ded";

    // ------------------------------------------------------------------ helpers

    private static Path file(String name) {
        return RepoRoot.of(DIR, name + ".animation.json");
    }

    private static String raw(String name) throws IOException {
        assertTrue(Files.exists(file(name)), name + " is not shipped");
        return Files.readString(file(name), StandardCharsets.UTF_8);
    }

    private static XenoAnimClip clip(String name) throws IOException {
        XenoAnimClip parsed = XenoAnimClip.fromGeckoJson(name, raw(name));
        assertNotNull(parsed, name + " did not parse");
        assertFalse(parsed.isEmpty(), name + " parsed to nothing");
        return parsed;
    }

    private static AnimChannel arm(String name, AnimChannel.Kind kind) throws IOException {
        AnimBoneTrack arm = clip(name).trackIfPresent("right_arm");
        assertNotNull(arm, name + " has no right_arm track");
        AnimChannel channel = arm.channel(kind);
        assertFalse(channel.isEmpty(), name + " has no right_arm " + kind);
        return channel;
    }

    private static List<AnimKey> keys(AnimChannel channel) {
        List<AnimKey> out = new ArrayList<>();
        for (Map.Entry<Double, AnimKey> entry : channel.entries()) out.add(entry.getValue());
        return out;
    }

    /**
     * Times where the channel changes direction along one axis. Held (flat) segments are skipped,
     * so a key that only repeats the previous value is not mistaken for an extremum.
     */
    private static List<Double> turningPoints(AnimChannel channel, int axis) {
        List<Double> times = new ArrayList<>(channel.times());
        int[] slope = new int[times.size() - 1];
        for (int i = 0; i < slope.length; i++) {
            float d = component(channel, times.get(i + 1), axis)
                    - component(channel, times.get(i), axis);
            slope[i] = d > 0f ? 1 : (d < 0f ? -1 : 0);
        }
        List<Double> out = new ArrayList<>();
        for (int i = 1; i < times.size() - 1; i++) {
            int before = 0;
            for (int j = i - 1; j >= 0 && before == 0; j--) before = slope[j];
            int after = 0;
            for (int j = i; j < slope.length && after == 0; j++) after = slope[j];
            if (before != 0 && after != 0 && before != after) out.add(times.get(i));
        }
        return out;
    }

    private static float component(AnimChannel channel, double seconds, int axis) {
        return channel.at(seconds).component(axis);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> oldWaveHashes() throws Exception {
        var field = XenoClipLibrary.class.getDeclaredField("OLD_WAVE_HASHES");
        field.setAccessible(true);
        return (Map<String, Set<String>>) field.get(null);
    }

    private static String sha256(String name) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file(name)));
        return HexFormat.of().formatHex(digest);
    }

    // ------------------------------------------------------------------ the motion

    @Test
    void theRewrittenWavesStillPassTheLibraryGate() throws IOException {
        for (String name : WAVES) {
            assertNull(XenoClipLibrary.validate(name, raw(name)), name + " is not usable");
            for (String bone : clip(name).bones.keySet()) {
                assertTrue(RIG.contains(bone), name + " names a bone the rig lacks: " + bone);
            }
        }
    }

    @Test
    void theHandGoesForwardBeforeTheFirstWave() throws Exception {
        for (String name : WAVES) {
            AnimChannel rot = arm(name, AnimChannel.Kind.ROTATION);
            AnimChannel pos = arm(name, AnimChannel.Kind.POSITION);

            Double raised = null;
            for (Map.Entry<Double, AnimKey> entry : rot.entries()) {
                if (entry.getValue().x < -40f) {
                    raised = entry.getKey();
                    break;
                }
            }
            assertNotNull(raised, name + " never swings the arm forward");
            assertTrue(component(pos, raised, 2) < 0f,
                    name + " should reach the hand forward as it raises it");

            List<Double> turns = turningPoints(rot, 1);
            if ("wave".equals(name)) {
                assertEquals(6, turns.size(), "wave should sweep three times before coming down");
            } else {
                assertTrue(turns.size() >= 4,
                        "the authored greeting should show several arm turns before coming down");
            }
            assertTrue(turns.get(0) > raised, name + " starts swinging before the hand is forward");
            assertTrue(turns.get(turns.size() - 1) < rot.lastTime(),
                    name + " leaves no room to return to rest");
        }
    }

    @Test
    void theWaveSweepsTheHandSideToSideExactlyThreeTimes() throws Exception {
        AnimChannel rot = arm("wave", AnimChannel.Kind.ROTATION);
        List<Double> turns = turningPoints(rot, 1);

        // Three full cycles = six extrema, alternating sides, each one a real swing.
        assertEquals(6, turns.size(), "three waves means six side-to-side extrema: " + turns);
        int left = 0;
        int right = 0;
        for (int i = 0; i < turns.size(); i++) {
            float y = component(rot, turns.get(i), 1);
            assertTrue(Math.abs(y) >= 10f, "the swing at " + turns.get(i) + " is too small to read");
            if (i > 0) {
                float previous = component(rot, turns.get(i - 1), 1);
                assertTrue((y > 0f) != (previous > 0f),
                        "wave " + (i / 2 + 1) + " never comes back across");
            }
            if (y > 0f) left++; else right++;
        }
        assertEquals(3, left);
        assertEquals(3, right);
    }

    @Test
    void theArmSwingsWithTheSweep() throws Exception {
        AnimChannel pos = arm("wave", AnimChannel.Kind.POSITION);
        List<Double> turns = turningPoints(pos, 0);
        assertTrue(turns.size() >= 6,
                "the arm should sway sideways with the sweep, not ride rigidly: " + turns);
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (AnimKey key : keys(pos)) {
            min = Math.min(min, key.x);
            max = Math.max(max, key.x);
        }
        assertTrue(max - min >= 1.0f, "the sideways sway is too small to see");
    }

    @Test
    void everySegmentEasesSmoothly() throws Exception {
        for (String name : WAVES) {
            for (AnimBoneTrack track : clip(name).bones.values()) {
                for (AnimChannel.Kind kind : AnimChannel.Kind.values()) {
                    AnimChannel channel = track.channel(kind);
                    if (channel.isEmpty()) continue;
                    List<Double> times = new ArrayList<>(channel.times());
                    for (int i = 1; i < times.size(); i++) {
                        String easing = channel.at(times.get(i)).easing;
                        assertNotNull(easing, name + " leaves a segment with no easing");
                        assertTrue(SMOOTH.contains(easing),
                                name + " snaps into " + times.get(i) + " with " + easing);
                    }
                }
            }
        }
    }

    @Test
    void theArmStartsAndEndsAtRest() throws Exception {
        for (String name : WAVES) {
            for (AnimChannel.Kind kind : List.of(AnimChannel.Kind.ROTATION,
                    AnimChannel.Kind.POSITION)) {
                AnimChannel channel = arm(name, kind);
                for (double t : List.of(channel.firstTime(), channel.lastTime())) {
                    AnimKey key = channel.at(t);
                    assertEquals(channel.kind().restX, key.x, 0.01f, name + " " + kind + " at " + t);
                    assertEquals(channel.kind().restY, key.y, 0.01f, name + " " + kind + " at " + t);
                    assertEquals(channel.kind().restZ, key.z, 0.01f, name + " " + kind + " at " + t);
                }
            }
        }
    }

    @Test
    void theGreetingOutlastsThePlainWave() throws Exception {
        int wave = XenoAnimClip.durationTicksOf(raw("wave"));
        int greet = XenoAnimClip.durationTicksOf(raw("hi_wave"));
        assertEquals(50, wave, "a 2.5 s wave is 50 ticks");
        assertEquals(60, greet, "a 3.0 s greeting is 60 ticks");
        assertTrue(greet > wave);
    }

    // ------------------------------------------------------------------ the upgrade path

    @Test
    void thePreviouslyShippedWavesAreRegisteredForUpgrade() throws Exception {
        Map<String, Set<String>> hashes = oldWaveHashes();
        assertTrue(hashes.get("wave").contains(OLD_WAVE),
                "an install holding the old wave would never be upgraded");
        assertTrue(hashes.get("hi_wave").contains(OLD_HI_WAVE),
                "an install holding the old hi_wave would never be upgraded");
        assertTrue(hashes.get("hi_wave").contains(PREVIOUS_SHIPPED_HI_WAVE),
                "the previous shipped hi_wave would never be upgraded to the run-client edit");
    }

    @Test
    void theRewrittenClipsAreNotMistakenForOldOnes() throws Exception {
        // A shipped file that matches its own upgrade hash would be re-seeded over any operator
        // edit that happened to match, and the guard would never stop applying.
        Map<String, Set<String>> hashes = oldWaveHashes();
        for (String name : WAVES) {
            assertFalse(hashes.get(name).contains(sha256(name)),
                    name + " still lists itself as a clip to replace");
        }
    }
}
