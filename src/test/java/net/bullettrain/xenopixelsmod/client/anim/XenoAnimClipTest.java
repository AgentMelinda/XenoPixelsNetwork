package net.bullettrain.xenopixelsmod.client.anim;

import net.bullettrain.xenopixelsmod.client.anim.studio.AnimBonePose;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimBoneTrack;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimKey;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoAnimClipTest {

    @Test
    void geckoExportHasFormatAndBones() {
        XenoAnimClip clip = new XenoAnimClip("jab_test");
        clip.add(new XenoAnimClip.Frame(0, 0, 0, 0, false));
        clip.add(new XenoAnimClip.Frame(5, 20, 10, 0.5f, false));
        String json = clip.toGeckoJson();
        assertTrue(json.contains("\"format_version\": \"1.8.0\""));
        assertTrue(json.contains("combat.xeno_jab_test"));
        assertTrue(json.contains("\"root\""));
        assertTrue(json.contains("\"right_arm\""));
    }

    @Test
    void boneKeysExportGeckoLibCombatBones() {
        XenoAnimClip clip = new XenoAnimClip("pose");
        clip.keyPose(0.0, Map.of("right_arm", AnimBonePose.rot(-40, 12, 0)));
        clip.keyPose(0.5, Map.of("right_arm", AnimBonePose.rot(-80, 0, 0)));
        String json = clip.toGeckoJson();
        assertTrue(json.contains("\"right_arm\""));
        assertTrue(json.contains("combat.xeno_pose"));
    }

    @Test
    void loadRoundTripKeepsArmKey() {
        XenoAnimClip clip = new XenoAnimClip("wave");
        clip.keyPose(0.0, Map.of("right_arm", AnimBonePose.rot(-40, 0, 0)));
        clip.keyPose(0.5, Map.of("right_arm", AnimBonePose.rot(-80, 5, 0)));
        XenoAnimClip loaded = XenoAnimClip.fromGeckoJson("wave", clip.toGeckoJson());
        AnimBoneTrack track = loaded.trackIfPresent("right_arm");
        assertNotNull(track);
        assertEquals(2, track.rotation.size());
        assertEquals(-80f, track.rotation.at(0.5).x, 0.01f);
    }

    /**
     * The limitation this model rewrite existed to remove: two bones keyed at times that are not
     * the same, and not even on the same tick.
     */
    @Test
    void twoBonesCanBeKeyedAtDifferentTimes() {
        XenoAnimClip clip = new XenoAnimClip("offset");
        clip.keyBone(0.25, "right_arm", AnimBonePose.rot(-60, 0, 0));
        clip.keyBone(0.31, "head", AnimBonePose.rot(15, 0, 0));

        assertEquals(2, clip.keyCount());
        assertTrue(clip.hasKeyAt("right_arm", 0.25));
        assertFalse(clip.hasKeyAt("right_arm", 0.31));
        assertTrue(clip.hasKeyAt("head", 0.31));

        XenoAnimClip loaded = XenoAnimClip.fromGeckoJson("offset", clip.toGeckoJson());
        assertEquals(0.25, loaded.trackIfPresent("right_arm").rotation.firstTime(), 0.0001);
        assertEquals(0.31, loaded.trackIfPresent("head").rotation.firstTime(), 0.0001);
    }

    @Test
    void keyingTheSameTimeTwiceReplacesRatherThanDuplicates() {
        XenoAnimClip clip = new XenoAnimClip("edit");
        clip.keyPose(0.5, Map.of("head", AnimBonePose.rot(10, 0, 0)));
        clip.keyPose(0.5, Map.of("head", AnimBonePose.rot(45, 0, 0)));
        assertEquals(1, clip.keyCount());
        assertEquals(45f, clip.trackIfPresent("head").rotation.at(0.5).x, 0.01f);
    }

    @Test
    void keyingOneBoneLeavesTheOthersAtThatTimeAlone() {
        XenoAnimClip clip = new XenoAnimClip("merge");
        clip.keyPose(0.5, Map.of(
                "head", AnimBonePose.rot(10, 0, 0),
                "right_arm", AnimBonePose.rot(-30, 0, 0)));
        clip.keyBone(0.5, "head", AnimBonePose.rot(60, 0, 0));
        assertEquals(1, clip.keyCount());
        assertEquals(60f, clip.trackIfPresent("head").rotation.at(0.5).x, 0.01f);
        assertEquals(-30f, clip.trackIfPresent("right_arm").rotation.at(0.5).x, 0.01f);
    }

    @Test
    void keyTimesAreOrderedAndRemovable() {
        XenoAnimClip clip = new XenoAnimClip("sorted");
        clip.keyPose(1.5, Map.of("head", new AnimBonePose()));
        clip.keyPose(0.25, Map.of("head", new AnimBonePose()));
        clip.keyPose(0.9, Map.of("head", new AnimBonePose()));
        assertEquals(java.util.List.of(0.25, 0.9, 1.5),
                java.util.List.copyOf(clip.keyTimes()));

        assertEquals(0.9, clip.nextKeyTime(0.25), 0.0001);
        assertEquals(0.9, clip.prevKeyTime(1.5), 0.0001);
        assertTrue(clip.removeKeysAt(0.9));
        assertFalse(clip.removeKeysAt(0.9));
        assertEquals(1.5, clip.nextKeyTime(0.25), 0.0001);
        assertNull(clip.nextKeyTime(1.5));
    }

    @Test
    void loopAndSceneLengthReachTheJson() {
        XenoAnimClip clip = new XenoAnimClip("held");
        clip.keyPose(0.0, Map.of("head", AnimBonePose.rot(0, 0, 0)));
        clip.keyPose(0.5, Map.of("head", AnimBonePose.rot(30, 0, 0)));
        String json = clip.toGeckoJson(true, 60);
        assertTrue(json.contains("\"loop\": true"));
        assertEquals(60, XenoAnimClip.durationTicksOf(json), "a trailing hold must survive export");
        assertTrue(XenoAnimClip.loopOf(json));
    }

    @Test
    void sceneLengthIsNeverShorterThanTheLastKey() {
        XenoAnimClip clip = new XenoAnimClip("long");
        clip.keyPose(4.0, Map.of("head", AnimBonePose.rot(30, 0, 0)));
        assertEquals(80, XenoAnimClip.durationTicksOf(clip.toGeckoJson(false, 20)));
    }

    @Test
    void positionAndScaleRoundTrip() {
        XenoAnimClip clip = new XenoAnimClip("channels");
        AnimBonePose start = AnimBonePose.rot(0, 0, 0);
        start.setPosition(0, 0, 0);
        start.setScale(1, 1, 1);
        AnimBonePose end = AnimBonePose.rot(15, 0, 0);
        end.setPosition(2.5f, -1f, 0.5f);
        end.setScale(1.5f, 1.5f, 1.5f);
        clip.keyPose(0.0, Map.of("waist", start));
        clip.keyPose(0.5, Map.of("waist", end));

        String json = clip.toGeckoJson();
        assertTrue(json.contains("\"position\""));
        assertTrue(json.contains("\"scale\""));

        XenoAnimClip loaded = XenoAnimClip.fromGeckoJson("channels", json);
        AnimBonePose back = loaded.poseAt("waist", 0.5);
        assertNotNull(back);
        assertEquals(2.5f, back.posX, 0.001f);
        assertEquals(-1f, back.posY, 0.001f);
        assertEquals(1.5f, back.scaleX, 0.001f);
        assertTrue(back.hasPosition());
        assertTrue(back.hasScale());
    }

    @Test
    void aRotationOnlyClipStillExportsRotationOnly() {
        XenoAnimClip clip = new XenoAnimClip("rot_only");
        clip.keyPose(0.0, Map.of("head", AnimBonePose.rot(5, 0, 0)));
        String json = clip.toGeckoJson();
        assertTrue(json.contains("\"rotation\""));
        assertFalse(json.contains("\"position\""));
        assertFalse(json.contains("\"scale\""));
    }

    @Test
    void easingRoundTripsAndLinearStaysImplicit() {
        XenoAnimClip clip = new XenoAnimClip("eased");
        clip.keyPose(0.0, Map.of("head", AnimBonePose.rot(0, 0, 0)));
        clip.keyPose(0.5, Map.of("head", AnimBonePose.rot(40, 0, 0)));
        clip.setEasingAt(0.5, "easeinoutsine");
        String json = clip.toGeckoJson();
        assertTrue(json.contains("\"easing\": \"easeinoutsine\""));

        XenoAnimClip loaded = XenoAnimClip.fromGeckoJson("eased", json);
        assertEquals("easeinoutsine", loaded.easingAt(0.5));
        assertEquals("linear", loaded.easingAt(0.0),
                "linear is the default and is not written out");
    }

    @Test
    void visibilityRidesInASidecarAndComesBack() {
        XenoAnimClip clip = new XenoAnimClip("vanish");
        AnimBonePose hidden = AnimBonePose.rot(0, 0, 0);
        hidden.setVisible(false);
        clip.keyPose(0.4, Map.of("left_arm", hidden));

        String json = clip.toGeckoJson();
        assertTrue(json.contains(XenoAnimClip.VISIBILITY_KEY));

        XenoAnimClip loaded = XenoAnimClip.fromGeckoJson("vanish", json);
        assertFalse(loaded.poseAt("left_arm", 0.5).visible);
        assertTrue(loaded.poseAt("left_arm", 0.5).hasVisibility());
    }

    @Test
    void eventKeyframesRoundTrip() {
        XenoAnimClip clip = new XenoAnimClip("noisy");
        clip.keyPose(0.0, Map.of("head", AnimBonePose.rot(0, 0, 0)));
        clip.sounds.add(new XenoAnimClip.SoundEvent(0.25, "minecraft:entity.player.attack.strong"));
        clip.particles.add(new XenoAnimClip.ParticleEvent(0.5, "minecraft:crit", "right_arm", null));
        clip.instructions.add(new XenoAnimClip.InstructionEvent(0.75, "xeno:shockwave"));

        String json = clip.toGeckoJson();
        assertTrue(json.contains("\"sound_effects\""));
        assertTrue(json.contains("\"particle_effects\""));
        assertTrue(json.contains("\"timeline\""));

        XenoAnimClip loaded = XenoAnimClip.fromGeckoJson("noisy", json);
        assertEquals(1, loaded.sounds.size());
        assertEquals("minecraft:entity.player.attack.strong", loaded.sounds.get(0).sound());
        assertEquals(1, loaded.particles.size());
        assertEquals("right_arm", loaded.particles.get(0).locator());
        assertEquals(1, loaded.instructions.size());
        assertEquals("xeno:shockwave", loaded.instructions.get(0).instruction());
    }

    @Test
    void blendTimesRoundTripWhenTheyAreNotTheDefault() {
        XenoAnimClip clip = new XenoAnimClip("soft");
        clip.keyPose(0.0, Map.of("head", AnimBonePose.rot(0, 0, 0)));
        clip.blendInTicks = 8;
        clip.blendOutTicks = 12;
        XenoAnimClip loaded = XenoAnimClip.fromGeckoJson("soft", clip.toGeckoJson());
        assertEquals(8, loaded.blendInTicks);
        assertEquals(12, loaded.blendOutTicks);
    }

    @Test
    void renameCopiesTheContent() {
        XenoAnimClip clip = new XenoAnimClip("before");
        clip.loop = true;
        clip.durationTicks = 40;
        clip.keyPose(0.25, Map.of("head", AnimBonePose.rot(10, 0, 0)));
        XenoAnimClip renamed = clip.renamed("After Name");
        assertEquals("after_name", renamed.name);
        assertEquals(1, renamed.keyCount());
        assertTrue(renamed.loop);
        assertEquals(40, renamed.durationTicks);
        assertEquals("combat.xeno_after_name", renamed.animationName());
    }

    /** Clips written by the old shared-tick model are just clips whose channels share times. */
    @Test
    void aSharedTickFileFromTheOldModelStillLoads() {
        String json = """
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "combat.xeno_legacy": {
                      "loop": false,
                      "animation_length": 0.5,
                      "bones": {
                        "right_arm": {
                          "rotation": {
                            "0.000": { "vector": [0, 0, 0] },
                            "0.500": { "vector": [-80, 0, 0] }
                          }
                        },
                        "head": {
                          "rotation": {
                            "0.000": { "vector": [5, 0, 0] },
                            "0.500": { "vector": [10, 0, 0] }
                          }
                        }
                      }
                    }
                  }
                }
                """;
        XenoAnimClip clip = XenoAnimClip.fromGeckoJson("legacy", json);
        assertEquals("legacy", clip.name);
        assertEquals(2, clip.keyCount());
        assertEquals(-40f, clip.poseAt("right_arm", 0.25).rotX, 0.01f);
        assertEquals(10, clip.durationTicks);
    }

    @Test
    void aBareVectorArrayKeyframeStillImports() {
        String json = """
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "combat.xeno_bare": {
                      "loop": false,
                      "animation_length": 0.5,
                      "bones": { "head": { "rotation": { "0.250": [10, 0, 0] } } }
                    }
                  }
                }
                """;
        XenoAnimClip clip = XenoAnimClip.fromGeckoJson("bare", json);
        assertEquals(1, clip.keyCount());
        assertEquals(0.25, clip.keyTimes().first(), 0.0001);
        assertEquals(10f, clip.poseAt("head", 0.25).rotX, 0.01f);
        assertEquals(10, clip.durationTicks);
    }

    @Test
    void oneAnimationCanBeReadOutOfAFileHoldingMany() {
        XenoAnimClip clip = new XenoAnimClip("x");
        clip.keyPose(0.1, Map.of("head", AnimBonePose.rot(3, 0, 0)));
        com.google.gson.JsonObject anim = XenoAnimClip.GSON
                .fromJson(clip.toGeckoJson(), com.google.gson.JsonObject.class)
                .getAsJsonObject("animations")
                .getAsJsonObject("combat.xeno_x");

        XenoAnimClip picked = XenoAnimClip.fromAnimationObject("fallback", "combat.xeno_hook_right", anim);
        assertEquals("hook_right", picked.name);
        assertEquals(3f, picked.poseAt("head", 0.1).rotX, 0.01f);
    }

    @Test
    void keyingAPoseKeepsTheEasingAlreadyAtThatTime() {
        XenoAnimClip clip = new XenoAnimClip("keep");
        clip.keyPose(0.5, Map.of("head", AnimBonePose.rot(0, 0, 0)));
        clip.setEasingAt(0.5, "step");
        clip.keyPose(0.5, Map.of("head", AnimBonePose.rot(30, 0, 0)));
        assertEquals("step", clip.easingAt(0.5));
        AnimKey key = clip.trackIfPresent("head").rotation.at(0.5);
        assertEquals(30f, key.x, 0.001f);
    }
}
