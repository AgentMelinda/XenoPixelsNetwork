package net.bullettrain.xenopixelsmod.combat.anim;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.api.registry.Bt3RushDefinition;
import net.bullettrain.xenopixelsmod.api.registry.RushRegistry;
import net.bullettrain.xenopixelsmod.combat.Bt3RushResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the animation catalog, which is the single place a combat beat is turned into an animation
 * name for either side of the network.
 *
 * <p>Two things are easy to break here and invisible in a diff of the caller. A generation can lose
 * an entry and silently fall back, and a clip length can drift away from the file it describes,
 * which would make {@code Bt3AnimationBinding.mashSpeed} scale against a number that is no longer
 * true. Both are checked against the shipped JSON rather than against another copy of the table.
 */
class Bt3AnimationCatalogTest {

    /** Off the classpath, not a relative path: the test JVM does not run in the project root. */
    private static final String ANIMATION_FILE =
            "/assets/xenopixelsmod/animations/entity/bt3_combat.animation.json";
    /** The held-mash beat these clips are scaled to fit. */
    private static final int BEAT_TICKS = 6;

    private static JsonObject animations() throws IOException {
        try (InputStream in = Bt3AnimationCatalogTest.class.getResourceAsStream(ANIMATION_FILE)) {
            assertNotNull(in, ANIMATION_FILE + " is not on the test classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
        }
    }

    private static JsonObject nativeAnimations() throws IOException {
        // NeoForge's test module class loader does not expose another mod's asset resources.
        String projectDir = System.getProperty("xenopixels.projectDir");
        assertNotNull(projectDir, "Gradle must provide xenopixels.projectDir");
        Path root = Path.of(projectDir);
        try (ZipFile jar = new ZipFile(root.resolve("libs/dragonminez-2.1.3.jar").toFile())) {
            var entry = jar.getEntry("assets/dragonminez/animations/entity/races/combat.animation.json");
            assertNotNull(entry);
            try (InputStream in = jar.getInputStream(entry)) {
                return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("animations");
            }
        }
    }

    @Test
    void everyIntentResolvesInEveryGeneration() {
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            for (int gen = Bt3AnimationCatalog.GEN_MIN; gen <= Bt3AnimationCatalog.GEN_MAX; gen++) {
                Bt3AnimationCatalog.Clip clip = Bt3AnimationCatalog.clipFor(intent, gen);
                assertNotNull(clip, intent + " has no clip in generation " + gen);
                assertFalse(clip.name().isBlank(), intent + " generation " + gen + " has a blank name");
                assertTrue(clip.seconds() > 0.0f,
                        intent + " generation " + gen + " has no length");
            }
        }
    }

    @Test
    void anOutOfRangeGenerationIsClampedRatherThanCrashing() {
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            assertNotNull(Bt3AnimationCatalog.clipFor(intent, 0));
            assertNotNull(Bt3AnimationCatalog.clipFor(intent, 99));
            assertNotNull(Bt3AnimationCatalog.clipFor(intent, -3));
        }
    }

    /** Our own clips have to exist in the file we ship, or DragonMineZ resolves them to nothing. */
    @Test
    void everyXenoClipTheCatalogNamesIsInTheShippedFile() throws IOException {
        JsonObject animations = animations();
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            for (int gen = Bt3AnimationCatalog.GEN_MIN; gen <= Bt3AnimationCatalog.GEN_MAX; gen++) {
                String name = Bt3AnimationCatalog.clipFor(intent, gen).name();
                if (name.startsWith("combat.xeno_")) {
                    assertTrue(animations.has(name),
                            name + " (" + intent + " generation " + gen + ") is not in "
                                    + ANIMATION_FILE);
                }
            }
        }
        for (String name : Bt3AnimationCatalog.customAnimationNames()) {
            assertTrue(animations.has(name), name + " is registered but not in the animation file");
        }
    }

    /** A length that has drifted from the file makes every speed calculation wrong. */
    @Test
    void catalogClipLengthsMatchTheFile() throws IOException {
        JsonObject animations = animations();
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            for (int gen = Bt3AnimationCatalog.GEN_MIN; gen <= Bt3AnimationCatalog.GEN_MAX; gen++) {
                Bt3AnimationCatalog.Clip clip = Bt3AnimationCatalog.clipFor(intent, gen);
                if (!clip.name().startsWith("combat.xeno_")) {
                    continue;
                }
                float actual = animations.getAsJsonObject(clip.name())
                        .get("animation_length").getAsFloat();
                assertTrue(Math.abs(actual - clip.seconds()) < 1.0e-4f,
                        clip.name() + " is " + actual + "s in the file but " + clip.seconds()
                                + "s in the catalog");
            }
        }
    }

    /**
     * The measurement that motivated authoring generation 3 at all. Generation 1's spins are 0.70s
     * against a 0.30s beat, so the mash has to run them at 2.4x and they read as a smear; every v3
     * clip has to stay inside a scaling that still looks like a movement.
     */
    @Test
    void generationThreeNeedsOnlyGentleSpeedUpToFitTheBeat() {
        float worst = 0.0f;
        String worstName = "";
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            Bt3AnimationCatalog.Clip clip = Bt3AnimationCatalog.clipFor(intent, 3);
            if (!clip.name().endsWith("_v3")) {
                continue;   // no v3 authored for this beat; generation 1 is the documented fallback
            }
            float needed = clip.seconds() * 20.0f / BEAT_TICKS;
            if (needed > worst) {
                worst = needed;
                worstName = clip.name();
            }
        }
        assertTrue(worst > 0.0f, "no v3 clips found - the catalog lost its generation 3");
        assertTrue(worst <= 1.5f,
                worstName + " needs " + worst + "x to fit a " + BEAT_TICKS + "-tick beat");
    }

    @Test
    void generationOneStillPointsAtTheClipsThatShippedBeforeV3() {
        assertEquals("combat.xeno_jab_left",
                Bt3AnimationCatalog.clipFor(Bt3AnimationIntent.JAB_LEFT, 1).name());
        assertEquals("combat.xeno_jab_right",
                Bt3AnimationCatalog.clipFor(Bt3AnimationIntent.JAB_RIGHT, 1).name());
        assertEquals("combat.xeno_jab_left_v2",
                Bt3AnimationCatalog.clipFor(Bt3AnimationIntent.JAB_LEFT, 2).name());
        assertEquals("combat.xeno_jab_right_v2",
                Bt3AnimationCatalog.clipFor(Bt3AnimationIntent.JAB_RIGHT, 2).name());
        assertEquals("combat.xeno_jab_left_v3",
                Bt3AnimationCatalog.clipFor(Bt3AnimationIntent.JAB_LEFT, 3).name());
        assertEquals("combat.xeno_jab_right_v3",
                Bt3AnimationCatalog.clipFor(Bt3AnimationIntent.JAB_RIGHT, 3).name());
        assertEquals("combat.xeno_cross_right",
                Bt3AnimationCatalog.clipFor(Bt3AnimationIntent.CROSS_RIGHT, 1).name());
        assertEquals("combat.xeno_cross_left_v2",
                Bt3AnimationCatalog.clipFor(Bt3AnimationIntent.CROSS_LEFT, 2).name());
        assertTrue(Bt3AnimationCatalog.clipFor(Bt3AnimationIntent.UPPERCUT_LEFT, 1).name()
                .equals("combat.one_handed_uppercut_left"));
        assertTrue(Bt3AnimationCatalog.clipFor(Bt3AnimationIntent.SPIN_KICK_RIGHT, 1).name()
                .equals("combat.xeno_spin_kick_right"));
    }

    @Test
    void generationFourIsFullyAuthored() {
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            assertTrue(Bt3AnimationCatalog.clipFor(intent, 4).name().endsWith("_v4"),
                    intent + " fell back instead of using its generation 4 clip");
        }
    }

    @Test
    void generationFourPunchArmCorrectionPreservesNativeTimingAndLegs() throws IOException {
        JsonObject ours = animations();
        JsonObject nativeClips = nativeAnimations();
        for (String side : new String[]{"left", "right"}) {
            JsonObject source = nativeClips.getAsJsonObject("combat.one_handed_punch_" + side);
            JsonObject corrected = ours.getAsJsonObject("combat.xeno_dmz_punch_" + side + "_v4");
            assertEquals(source.get("animation_length"), corrected.get("animation_length"));
            for (var bone : source.getAsJsonObject("bones").entrySet()) {
                if (bone.getKey().equals("left_arm") || bone.getKey().equals("right_arm")) continue;
                assertEquals(bone.getValue(), corrected.getAsJsonObject("bones").get(bone.getKey()), bone.getKey());
            }
        }
    }

    @Test
    void crossV1AndV2UseDmzsExactLeftAndRightMovement() throws IOException {
        JsonObject ours = animations();
        JsonObject nativeClips = nativeAnimations();
        for (String side : new String[]{"left", "right"}) {
            JsonObject source = nativeClips.getAsJsonObject("combat.one_handed_punch_" + side);
            assertEquals(source, ours.getAsJsonObject("combat.xeno_cross_" + side));
            assertEquals(source, ours.getAsJsonObject("combat.xeno_cross_" + side + "_v2"));
        }
    }

    @Test void newCombatArmsDoNotTranslateBehindTheShoulderOrSwingBackDuringKicks() throws IOException {
        for (var entry : animations().entrySet()) {
            String name = entry.getKey();
            if (!(name.endsWith("_v3") || name.endsWith("_v4") || name.startsWith("combat.xeno_charge_")
                    || name.startsWith("combat.xeno_cinematic_rush_"))) continue;
            JsonObject bones = entry.getValue().getAsJsonObject().getAsJsonObject("bones");
            for (String arm : new String[]{"left_arm", "right_arm"}) {
                JsonObject channels = bones.getAsJsonObject(arm);
                if (channels == null) continue;
                for (String channel : new String[]{"rotation", "position"}) {
                    JsonObject frames = channels.getAsJsonObject(channel);
                    if (frames == null) continue;
                    for (var frame : frames.entrySet()) {
                        var vector = frame.getValue().getAsJsonObject().getAsJsonArray("vector");
                        int axis = channel.equals("position") ? 2 : 0;
                        assertTrue(vector.get(axis).getAsDouble() <= 0,
                                name + " " + arm + " " + channel + " moves backward at " + frame.getKey());
                    }
                }
            }
        }
    }

    @Test
    void generationFourNonSpinBeatsStayOnTheCrosshair() throws IOException {
        JsonObject animations = animations();
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            if (intent.isBodyYawPose()) continue;
            JsonObject rotation = animations
                    .getAsJsonObject(Bt3AnimationCatalog.clipFor(intent, 4).name())
                    .getAsJsonObject("bones")
                    .getAsJsonObject("root")
                    .getAsJsonObject("rotation");
            for (Map.Entry<String, com.google.gson.JsonElement> frame : rotation.entrySet()) {
                double yaw = frame.getValue().getAsJsonObject()
                        .getAsJsonArray("vector").get(1).getAsDouble();
                assertEquals(0.0, yaw, 1.0e-6,
                        intent + " steers " + yaw + " degrees at " + frame.getKey());
            }
        }
    }

    @Test
    void generationFourNamedSpinsCompleteAFullTurn() throws IOException {
        JsonObject animations = animations();
        Set<Bt3AnimationIntent> spins = Set.of(
                Bt3AnimationIntent.SPINNING_BACK_KICK,
                Bt3AnimationIntent.SPIN_HOOK_LEFT,
                Bt3AnimationIntent.SPIN_HOOK_RIGHT,
                Bt3AnimationIntent.SPIN_UPPERCUT_LEFT,
                Bt3AnimationIntent.SPIN_KICK_RIGHT);
        for (Bt3AnimationIntent intent : spins) {
            JsonObject rotation = animations
                    .getAsJsonObject(Bt3AnimationCatalog.clipFor(intent, 4).name())
                    .getAsJsonObject("bones")
                    .getAsJsonObject("root")
                    .getAsJsonObject("rotation");
            double maximumYaw = 0.0;
            for (Map.Entry<String, com.google.gson.JsonElement> frame : rotation.entrySet()) {
                maximumYaw = Math.max(maximumYaw, Math.abs(frame.getValue().getAsJsonObject()
                        .getAsJsonArray("vector").get(1).getAsDouble()));
            }
            assertTrue(maximumYaw >= 359.0, intent + " only turns " + maximumYaw + " degrees");
        }
    }

    @Test
    void chargedClipsHoldTheirChamberAndReleaseFromTheSameWholeBodyPose() throws IOException {
        JsonObject animations = animations();
        Set<String> rig = Set.of("root", "waist", "head", "left_arm", "right_arm", "left_leg", "right_leg");
        for (String kind : new String[]{"punch", "kick"}) {
            String name = "combat.xeno_charge_" + kind;
            String recovery = kind.equals("punch") ? "0.95" : "1.05";
            JsonObject hold = animations.getAsJsonObject(name + "_hold").getAsJsonObject("bones");
            JsonObject fire = animations.getAsJsonObject(name + "_fire").getAsJsonObject("bones");
            assertEquals(rig, hold.keySet());
            assertEquals(rig, fire.keySet());
            for (String bone : rig) {
                JsonObject chamber = hold.getAsJsonObject(bone).getAsJsonObject("rotation");
                JsonObject release = fire.getAsJsonObject(bone).getAsJsonObject("rotation");
                assertEquals(chamber.get("0.55"), release.get("0.0"), kind + " snaps its " + bone + " on release");
                assertEquals(chamber.get("0.0"), release.get(recovery), kind + " fails to recover its " + bone);
                if (hold.getAsJsonObject(bone).has("position")) {
                    JsonObject heldPosition = hold.getAsJsonObject(bone).getAsJsonObject("position");
                    JsonObject releasedPosition = fire.getAsJsonObject(bone).getAsJsonObject("position");
                    assertEquals(heldPosition.get("0.55"), releasedPosition.get("0.0"),
                            kind + " jumps its " + bone + " position on release");
                    assertEquals(heldPosition.get("0.0"), releasedPosition.get(recovery),
                            kind + " fails to recover its " + bone + " position");
                }
                assertFalse(hold.getAsJsonObject(bone).has("scale"));
                assertFalse(fire.getAsJsonObject(bone).has("scale"));
            }
            assertTrue(Bt3AnimationCatalog.customAnimationNames().contains(name + "_hold"));
            assertTrue(Bt3AnimationCatalog.customAnimationNames().contains(name + "_fire"));
        }
    }

    /** What the scripting API validates against, so an unknown name comes back as a false. */
    @Test
    void playableNamesCoverWhatWeShipAndNothingElse() {
        Set<String> playable = Bt3AnimationCatalog.playableAnimationNames();
        assertTrue(playable.contains("combat.xeno_spin_kick_right_v3"));
        assertTrue(playable.contains("combat.xeno_spin_kick_right_v4"));
        assertTrue(playable.contains("combat.xeno_dmz_punch_left_v4"));
        assertTrue(playable.contains("combat.xeno_dmz_punch_right_v4"));
        assertTrue(playable.contains("combat.xeno_cross_right"));
        assertTrue(Bt3AnimationCatalog.isPlayable("combat.xeno_heavy_finish_v3"));
        assertTrue(Bt3AnimationCatalog.isPlayable("  combat.xeno_jab_left_v3  "),
                "a script's stray whitespace should not reject a real clip");
        assertFalse(Bt3AnimationCatalog.isPlayable("combat.does_not_exist"));
        assertFalse(Bt3AnimationCatalog.isPlayable(""));
        assertFalse(Bt3AnimationCatalog.isPlayable(null));
    }

    @Test
    void cinematicRushProfilesShipWithServerAlignedCosmeticKeyframes() throws IOException {
        JsonObject animations = animations();
        Set<String> expectedTimes = Set.of("0.25", "0.5", "0.8", "1.15");
        for (Bt3RushDefinition definition : RushRegistry.builtIns()) {
            JsonObject animation = animations.getAsJsonObject(definition.animation());
            assertNotNull(animation, definition.animation());
            assertEquals(definition.durationTicks() / 20.0,
                    animation.get("animation_length").getAsDouble(), 1.0e-6, definition.id());
            assertEquals(expectedTimes, animation.getAsJsonObject("sound_effects").keySet(), definition.id());
            assertEquals(expectedTimes, animation.getAsJsonObject("particle_effects").keySet(), definition.id());
            assertEquals(expectedTimes, animation.getAsJsonObject("timeline").keySet(), definition.id());
            assertTrue(Bt3AnimationCatalog.customAnimationNames().contains(definition.animation()));
        }
    }
}
