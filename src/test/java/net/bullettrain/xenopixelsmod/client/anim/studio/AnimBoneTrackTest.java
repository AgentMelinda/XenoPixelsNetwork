package net.bullettrain.xenopixelsmod.client.anim.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimBoneTrackTest {

    @Test
    void channelsKeepIndependentTimes() {
        AnimBoneTrack track = new AnimBoneTrack();
        track.rotation.put(0.25, 45, 0, 0);
        track.position.put(0.40, 0, 2, 0);
        track.scale.put(0.90, 2, 2, 2);

        assertEquals(3, track.times().size());
        assertEquals(0.90, track.lastTime(), 0.0001);
        assertTrue(track.hasKeyAt(0.40));
        assertFalse(track.hasKeyAt(0.41));
    }

    @Test
    void aRotationOnlyTrackLeavesPositionAndScaleAlone() {
        AnimBoneTrack track = new AnimBoneTrack();
        track.rotation.put(0.0, 10, 0, 0);
        AnimBonePose pose = track.poseAt(0.0);
        assertEquals(10f, pose.rotX, 0.001f);
        assertFalse(pose.hasPosition(), "an untouched channel must not claim the bone");
        assertFalse(pose.hasScale());
    }

    @Test
    void poseAtReadsEveryKeyedChannel() {
        AnimBoneTrack track = new AnimBoneTrack();
        track.rotation.put(0.0, 0, 0, 0);
        track.rotation.put(1.0, 90, 0, 0);
        track.position.put(0.0, 0, 0, 0);
        track.position.put(1.0, 0, 4, 0);
        track.scale.put(0.0, 1, 1, 1);
        track.scale.put(1.0, 3, 3, 3);
        // A channel holds its first key backwards, so hiding from 0.6 needs a visible key before
        // it - exactly as a single rotation key applies for the whole clip.
        track.visibility.put(0.0, 1, 0, 0);
        track.visibility.put(0.6, 0, 0, 0);

        AnimBonePose half = track.poseAt(0.5);
        assertEquals(45f, half.rotX, 0.001f);
        assertEquals(2f, half.posY, 0.001f);
        assertEquals(2f, half.scaleX, 0.001f);
        assertTrue(half.visible, "hidden only from 0.6 onwards");
        assertFalse(track.poseAt(0.7).visible);
    }

    @Test
    void keyingAPoseWritesOnlyTheChannelsItOwns() {
        AnimBoneTrack track = new AnimBoneTrack();
        AnimBonePose pose = AnimBonePose.rot(20, 0, 0);
        track.key(0.5, pose, AnimEasing.LINEAR);
        assertEquals(1, track.rotation.size());
        assertTrue(track.position.isEmpty());
        assertTrue(track.scale.isEmpty());

        pose.setPosition(1, 0, 0);
        track.key(0.75, pose, AnimEasing.LINEAR);
        assertEquals(1, track.position.size());
        assertEquals(0.75, track.position.firstTime(), 0.0001);
    }

    @Test
    void removingAKeyClearsEveryChannelAtThatTime() {
        AnimBoneTrack track = new AnimBoneTrack();
        track.rotation.put(0.5, 1, 0, 0);
        track.position.put(0.5, 1, 0, 0);
        track.scale.put(0.9, 2, 2, 2);
        assertTrue(track.removeKeysAt(0.5));
        assertTrue(track.rotation.isEmpty());
        assertTrue(track.position.isEmpty());
        assertEquals(1, track.scale.size(), "a key at another time survives");
        assertFalse(track.removeKeysAt(0.5));
    }

    @Test
    void copyIsIndependent() {
        AnimBoneTrack track = new AnimBoneTrack();
        track.rotation.put(0.5, 10, 0, 0);
        AnimBoneTrack copy = track.copy();
        track.rotation.put(0.5, 99, 0, 0);
        assertEquals(10f, copy.rotation.at(0.5).x, 0.001f);
    }
}
