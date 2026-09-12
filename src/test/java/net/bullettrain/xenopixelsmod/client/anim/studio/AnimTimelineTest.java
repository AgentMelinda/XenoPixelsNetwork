package net.bullettrain.xenopixelsmod.client.anim.studio;

import net.bullettrain.xenopixelsmod.client.anim.XenoAnimClip;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimTimelineTest {

    /**
     * The regression the whole studio rested on: the scene length used to be the last key, so an
     * empty clip clamped every seek to 0 and no key could ever be placed anywhere else.
     */
    @Test
    void playheadMovesOnAnEmptyClip() {
        AnimTimeline timeline = new AnimTimeline();
        timeline.bind(new XenoAnimClip("fresh"));
        timeline.seekTicks(25);
        assertEquals(25, timeline.playheadTicks());
        assertTrue(timeline.lengthTicks() >= AnimTimeline.DEFAULT_DURATION);
    }

    @Test
    void seekClampsToTheSceneLength() {
        AnimTimeline timeline = new AnimTimeline();
        timeline.bind(new XenoAnimClip("fresh"));
        timeline.setDuration(40);
        timeline.seekTicks(999);
        assertEquals(40, timeline.playheadTicks());
        timeline.seekTicks(-5);
        assertEquals(0, timeline.playheadTicks());
    }

    @Test
    void rebindKeepsThePlayheadWhileBindRewinds() {
        XenoAnimClip clip = new XenoAnimClip("clip");
        AnimTimeline timeline = new AnimTimeline();
        timeline.bind(clip);
        timeline.seekTicks(30);

        timeline.rebind(clip);
        assertEquals(30, timeline.playheadTicks(), "rebind must not move the playhead");

        timeline.bind(clip);
        assertEquals(0, timeline.playheadTicks(), "bind is the rewinding one");
    }

    @Test
    void keyingPastTheEndGrowsTheScene() {
        XenoAnimClip clip = new XenoAnimClip("clip");
        AnimTimeline timeline = new AnimTimeline();
        timeline.bind(clip);
        clip.keyPose(7.5, Map.of("head", AnimBonePose.rot(10, 0, 0)));
        timeline.rebind(clip);
        assertTrue(timeline.duration() >= 150);
        timeline.seekTicks(150);
        assertEquals(150, timeline.playheadTicks());
    }

    @Test
    void snappingKeepsThePlayheadOnWholeTicks() {
        AnimTimeline timeline = new AnimTimeline();
        timeline.bind(new XenoAnimClip("snap"));
        timeline.seek(0.263);
        assertEquals(0.25, timeline.playhead(), 0.0001, "snapped to the nearest tick");

        timeline.snap(false);
        timeline.seek(0.263);
        assertEquals(0.263, timeline.playhead(), 0.0001, "free scrubbing keeps the exact time");
    }

    @Test
    void keyNavigationWalksTheKeys() {
        XenoAnimClip clip = new XenoAnimClip("clip");
        clip.keyPose(0.0, Map.of("head", AnimBonePose.rot(0, 0, 0)));
        clip.keyPose(1.0, Map.of("head", AnimBonePose.rot(10, 0, 0)));
        clip.keyPose(2.25, Map.of("head", AnimBonePose.rot(20, 0, 0)));
        AnimTimeline timeline = new AnimTimeline();
        timeline.bind(clip);

        assertTrue(timeline.toNextKey());
        assertEquals(1.0, timeline.playhead(), 0.0001);
        assertTrue(timeline.hasKeyAtPlayhead());
        assertTrue(timeline.toNextKey());
        assertEquals(2.25, timeline.playhead(), 0.0001);
        assertFalse(timeline.toNextKey());

        assertTrue(timeline.toPrevKey());
        assertEquals(1.0, timeline.playhead(), 0.0001);
    }

    /**
     * A bone missing from the surrounding keys used to lerp null against null and snap to rest,
     * so keying one arm silently flattened the rest of the rig.
     */
    @Test
    void aBoneAbsentFromTheBracketingKeysHoldsItsValue() {
        XenoAnimClip clip = new XenoAnimClip("partial");
        clip.keyPose(0.0, Map.of(
                "right_arm", AnimBonePose.rot(-40, 0, 0),
                "head", AnimBonePose.rot(15, 0, 0)));
        clip.keyPose(1.0, Map.of("right_arm", AnimBonePose.rot(-80, 0, 0)));

        AnimTimeline timeline = new AnimTimeline();
        timeline.bind(clip);
        Map<String, AnimBonePose> pose = timeline.poseAt(0.5);

        assertEquals(-60f, pose.get("right_arm").rotX, 0.01f);
        assertNotNull(pose.get("head"));
        assertEquals(15f, pose.get("head").rotX, 0.01f,
                "head should hold its only key, not snap to 0");
    }

    @Test
    void aBoneThatWasNeverKeyedIsLeftAlone() {
        XenoAnimClip clip = new XenoAnimClip("partial");
        clip.keyPose(0.0, Map.of("right_arm", AnimBonePose.rot(-40, 0, 0)));
        AnimTimeline timeline = new AnimTimeline();
        timeline.bind(clip);
        assertNull(timeline.poseAt(0.0).get("left_leg"),
                "an unkeyed bone must stay out of the pose so DragonMineZ keeps driving it");
    }

    @Test
    void stepEasingHoldsUntilTheNextKey() {
        XenoAnimClip clip = new XenoAnimClip("stepped");
        clip.keyPose(0.0, Map.of("head", AnimBonePose.rot(0, 0, 0)));
        clip.keyPose(1.0, Map.of("head", AnimBonePose.rot(90, 0, 0)));
        clip.setEasingAt(1.0, "step");
        AnimTimeline timeline = new AnimTimeline();
        timeline.bind(clip);
        assertEquals(0f, timeline.poseAt(0.95).get("head").rotX, 0.01f);
        assertEquals(90f, timeline.poseAt(1.0).get("head").rotX, 0.01f);
    }

    @Test
    void bindTakesTheSceneLengthFromTheClipItLoads() {
        XenoAnimClip clip = new XenoAnimClip("short");
        clip.keyPose(0.5, Map.of("head", AnimBonePose.rot(0, 0, 0)));
        clip.durationTicks = 10;
        AnimTimeline timeline = new AnimTimeline();
        timeline.bind(clip);
        assertEquals(10, timeline.duration(), "a 10-tick clip must not play for the default 60");
    }
}
