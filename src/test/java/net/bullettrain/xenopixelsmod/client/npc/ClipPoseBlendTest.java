package net.bullettrain.xenopixelsmod.client.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-10-02 owner: "from waving it resets head position when finished making it look glitchy". */
class ClipPoseBlendTest {

    @Test
    void theHeadEasesBackWhenTheClipEnds() {
        ClipPoseBlend blend = new ClipPoseBlend();
        blend.frame(true, 100.0);
        assertArrayEquals(new float[] {0f}, blend.apply("head", new float[] {0f}), "the clip's last frame");
        // The clip has ended and the idle has the head at 10: it must not be there at once.
        blend.frame(false, 100.5);
        assertTrue(blend.blending());
        float first = blend.apply("head", new float[] {10f})[0];
        assertTrue(first < 1f, "barely moved on the first frame, was " + first);
        blend.frame(false, 102.5);
        float half = blend.apply("head", new float[] {10f})[0];
        assertTrue(half > first && half < 10f);
        blend.frame(false, 106.0);
        assertFalse(blend.blending());
        assertEquals(10f, blend.apply("head", new float[] {10f})[0]);
    }

    @Test
    void theStartIsEasedToo() {
        ClipPoseBlend blend = new ClipPoseBlend();
        blend.frame(false, 10.0);
        blend.apply("head", new float[] {8f});
        blend.frame(true, 10.5);
        assertEquals(8f, blend.apply("head", new float[] {0f})[0], 0.01f);
    }

    @Test
    void nothingIsEasedOutsideAClipBoundary() {
        ClipPoseBlend blend = new ClipPoseBlend();
        blend.frame(false, 1.0);
        float[] raw = {3f, 4f};
        assertSame(raw, blend.apply("waist", raw));
        blend.frame(false, 2.0);
        assertFalse(blend.blending());
    }

    @Test
    void anNpcThatWasNotDrawnForAWhileDoesNotEaseFromAnOldPose() {
        ClipPoseBlend blend = new ClipPoseBlend();
        blend.frame(true, 10.0);
        blend.apply("head", new float[] {0f});
        blend.frame(false, 200.0);
        assertFalse(blend.blending());
        assertEquals(10f, blend.apply("head", new float[] {10f})[0]);
    }
}
