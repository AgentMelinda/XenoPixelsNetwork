package net.bullettrain.xenopixelsmod.npc.job;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The five social clips, and the two toggles that decide whether an NPC uses them.
 *
 * <p>The clips are checked by parsing the shipped files. A clip that will not parse, or that names
 * a bone the rig does not have, fails at animation-load time with a message that does not name the
 * file — so it is worth catching here, where the failure says which clip and which bone.
 */
class SocialClipsTest {

    /**
     * {@code XenoRig.COMBAT}, which is what {@code XenoAnimClip.orderedBones} exports against.
     *
     * <p>Copied rather than imported because {@code XenoRig} sits in the client source set. If the
     * rig ever gains a bone, this list is the thing to update — and a clip using a bone that is
     * not here would silently animate nothing.
     */
    private static final Set<String> RIG = Set.of(
            "root", "waist", "head", "right_arm", "left_arm", "right_leg", "left_leg");

    private static final String DIR =
            "src/main/resources/assets/xenopixelsmod/animations/social";

    /** Every clip this mod ships, in the order {@code NpcSocialBehaviour} and scenes name them. */
    private static final List<String> CLIPS =
            List.of("wave", "hi_wave", "nod", "spin", "idle_shift");

    private static JsonObject clip(String name) throws IOException {
        Path file = RepoRoot.of(DIR, name + ".animation.json");
        assertTrue(Files.exists(file), name + " is not shipped");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    /** The one animation inside a clip file, with its name. */
    private static Map.Entry<String, JsonObject> only(JsonObject root) {
        JsonObject animations = root.getAsJsonObject("animations");
        assertEquals(1, animations.size(), "one animation per file");
        var entry = animations.entrySet().iterator().next();
        return Map.entry(entry.getKey(), entry.getValue().getAsJsonObject());
    }

    // ------------------------------------------------------------ the format

    @Test
    void everyClipParsesAndCarriesTheFormatTheStudioWrites() throws IOException {
        for (String name : CLIPS) {
            JsonObject root = clip(name);
            assertEquals("1.8.0", root.get("format_version").getAsString(), name);
            assertTrue(root.has("animations"), name);
        }
    }

    @Test
    void everyClipIsNamedTheWayTheAnimApiResolvesIt() throws IOException {
        // XenoAnimApi.PREFIX is "combat.xeno_"; a clip named anything else cannot be played.
        for (String name : CLIPS) {
            assertEquals("combat.xeno_" + name, only(clip(name)).getKey());
        }
    }

    @Test
    void noClipNamesABoneTheRigDoesNotHave() throws IOException {
        // The failure this exists for: a bone name copied from DragonMineZ's own file rather than
        // from XenoRig. "waist" is in both; "bone3" and the hand-item bones are DMZ's only, and a
        // clip using one would load fine and animate nothing.
        for (String name : CLIPS) {
            JsonObject bones = only(clip(name)).getValue().getAsJsonObject("bones");
            for (String bone : bones.keySet()) {
                assertTrue(RIG.contains(bone), name + " uses '" + bone + "', which is not in the rig");
            }
        }
    }

    @Test
    void everyKeyframeIsAVectorOfThree() throws IOException {
        for (String name : CLIPS) {
            JsonObject bones = only(clip(name)).getValue().getAsJsonObject("bones");
            for (String bone : bones.keySet()) {
                JsonObject channels = bones.getAsJsonObject(bone);
                for (String channel : channels.keySet()) {
                    JsonObject times = channels.getAsJsonObject(channel);
                    assertFalse(times.keySet().isEmpty(), name + "/" + bone + " has no keys");
                    for (String at : times.keySet()) {
                        var vector = times.getAsJsonObject(at).getAsJsonArray("vector");
                        assertEquals(3, vector.size(), name + "/" + bone + " at " + at);
                    }
                }
            }
        }
    }

    @Test
    void everyClipHasARealLength() throws IOException {
        for (String name : CLIPS) {
            assertTrue(only(clip(name)).getValue().get("animation_length").getAsFloat() > 0f, name);
        }
    }

    // ------------------------------------------------------------ what each one is for

    @Test
    void onlyTheIdleLoops() throws IOException {
        // A looping greeting would wave forever at somebody who had already walked away.
        for (String name : CLIPS) {
            boolean loop = only(clip(name)).getValue().get("loop").getAsBoolean();
            assertEquals("idle_shift".equals(name), loop, name + " loop flag");
        }
    }

    @Test
    void theSpinTurnsAFullCircleTheLongWayRound() throws IOException {
        // A single 0 to 360 key pair can be interpolated the short way, which is no turn at all.
        // The intermediate keys are what force it round.
        JsonObject rotation = only(clip("spin")).getValue()
                .getAsJsonObject("bones").getAsJsonObject("root").getAsJsonObject("rotation");
        assertTrue(rotation.keySet().size() >= 3, "a full turn needs intermediate keys");
        double last = -1;
        for (String at : rotation.keySet()) {
            double yaw = rotation.getAsJsonObject(at).getAsJsonArray("vector").get(1).getAsDouble();
            assertTrue(yaw >= last, "the turn must only ever go one way");
            last = yaw;
        }
        assertEquals(360.0, last, 0.001, "and come all the way round");
    }

    @Test
    void theNodCarriesTheParticleThatStandsInForASmile() throws IOException {
        // The rig has no face, so warmth comes from the body plus this.
        JsonObject nod = only(clip("nod")).getValue();
        assertTrue(nod.has("particle_effects"), "the nod is the smile substitute");
        JsonObject effects = nod.getAsJsonObject("particle_effects");
        assertFalse(effects.keySet().isEmpty());
        for (String at : effects.keySet()) {
            assertTrue(effects.getAsJsonObject(at).has("effect"), "a particle needs an effect id");
        }
    }

    @Test
    void theGreetingIsLongEnoughToBeSeen() throws IOException {
        // It fires when somebody walks up, so it has to read from across a room. The plain wave is
        // shorter on purpose - it plays far more often.
        float greet = only(clip(NpcSocialBehaviour.CLIP_GREET)).getValue()
                .get("animation_length").getAsFloat();
        float wave = only(clip("wave")).getValue().get("animation_length").getAsFloat();
        assertTrue(greet > wave, "the greeting should outlast the plain wave");
    }

    @Test
    void theBehaviourOnlyNamesClipsThatExist() {
        // A typo here is an NPC that silently never gestures.
        for (String clip : List.of(NpcSocialBehaviour.CLIP_GREET, NpcSocialBehaviour.CLIP_NOD,
                NpcSocialBehaviour.CLIP_IDLE)) {
            assertTrue(CLIPS.contains(clip), clip + " is not one of the shipped clips");
        }
    }

    // ------------------------------------------------------------ the toggles

    @Test
    void aNewNpcStandsStillAndIsSociable() {
        NpcCombatProfile fresh = new NpcCombatProfile();
        assertTrue(fresh.stayHome);
        assertTrue(fresh.socialGestures);
    }

    @Test
    void anNpcSavedBeforeThisShippedIsUnchanged() {
        // The asymmetry that matters. An NPC already in a world has a profile tag - the wand
        // writes one the moment it places it - but no key for either of these, and getBoolean
        // answers false for a key that is not there. So it keeps wandering and keeps quiet until
        // somebody ticks the box.
        //
        // The tag must be non-empty: fromTag short-circuits an empty one to fresh defaults,
        // because "no profile at all" means a brand new NPC rather than an old one.
        CompoundTag existing = new NpcCombatProfile().toTag();
        existing.remove("StayHome");
        existing.remove("SocialGestures");

        NpcCombatProfile old = NpcCombatProfile.fromTag(existing);
        assertFalse(old.stayHome, "an existing NPC must not suddenly stop wandering");
        assertFalse(old.socialGestures, "nor suddenly start waving");
    }

    @Test
    void anNpcWithNoProfileAtAllCountsAsNew() {
        // fromTag treats an empty tag as "never configured", which is the right reading: an entity
        // with no profile has never been edited, so it takes today's defaults rather than 2024's.
        NpcCombatProfile none = NpcCombatProfile.fromTag(new CompoundTag());
        assertTrue(none.stayHome);
        assertTrue(none.socialGestures);
    }

    @Test
    void bothTogglesSurviveASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.stayHome = false;
        profile.socialGestures = true;
        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertFalse(read.stayHome);
        assertTrue(read.socialGestures);
    }
}
