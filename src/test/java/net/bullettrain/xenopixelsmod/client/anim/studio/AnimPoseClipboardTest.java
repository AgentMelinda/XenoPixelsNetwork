package net.bullettrain.xenopixelsmod.client.anim.studio;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimPoseClipboardTest {

    @Test
    void mirrorSwapsSidesAndFlipsTheSideDependentAxes() {
        AnimBonePose arm = AnimBonePose.rot(-40, 25, 10);
        arm.setPosition(2f, 1f, 0.5f);
        Map<String, AnimBonePose> pose = new LinkedHashMap<>();
        pose.put("right_arm", arm);

        Map<String, AnimBonePose> mirrored = AnimPoseClipboard.mirror(pose);

        AnimBonePose left = mirrored.get("left_arm");
        assertNotNull(left, "right_arm must land on left_arm");
        assertFalse(mirrored.containsKey("right_arm"));
        assertEquals(-40f, left.rotX, 0.001f, "pitch is side-neutral");
        assertEquals(-25f, left.rotY, 0.001f);
        assertEquals(-10f, left.rotZ, 0.001f);
        assertEquals(-2f, left.posX, 0.001f);
        assertEquals(1f, left.posY, 0.001f, "vertical translation is side-neutral");
    }

    @Test
    void centreBonesKeepTheirName() {
        assertEquals("head", AnimPoseClipboard.mirrorBone("head"));
        assertEquals("root", AnimPoseClipboard.mirrorBone("root"));
        assertEquals("right_leg", AnimPoseClipboard.mirrorBone("left_leg"));
    }

    @Test
    void mirroringTwiceIsTheOriginal() {
        Map<String, AnimBonePose> pose = new LinkedHashMap<>();
        pose.put("left_leg", AnimBonePose.rot(12, -34, 56));
        AnimBonePose back = AnimPoseClipboard.mirror(AnimPoseClipboard.mirror(pose)).get("left_leg");
        assertEquals(12f, back.rotX, 0.001f);
        assertEquals(-34f, back.rotY, 0.001f);
        assertEquals(56f, back.rotZ, 0.001f);
    }

    @Test
    void copyIsADeepCopy() {
        AnimBonePose head = AnimBonePose.rot(10, 0, 0);
        Map<String, AnimBonePose> pose = new LinkedHashMap<>();
        pose.put("head", head);
        AnimPoseClipboard.copy(pose);
        head.rotX = 99f;
        assertEquals(10f, AnimPoseClipboard.paste().get("head").rotX, 0.001f);
        assertFalse(AnimPoseClipboard.isEmpty());
    }

    @Test
    void scaleSurvivesAMirror() {
        AnimBonePose arm = AnimBonePose.scale(2f, 2f, 2f);
        Map<String, AnimBonePose> pose = new LinkedHashMap<>();
        pose.put("right_arm", arm);
        AnimBonePose left = AnimPoseClipboard.mirror(pose).get("left_arm");
        assertEquals(2f, left.scaleX, 0.001f);
        assertTrue(left.hasScale());
    }
}
