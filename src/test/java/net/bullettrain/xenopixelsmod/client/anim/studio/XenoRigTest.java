package net.bullettrain.xenopixelsmod.client.anim.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoRigTest {
    @Test
    void combatRigHasTheGeckoLibBodyParts() {
        assertTrue(XenoRig.COMBAT.contains("root"));
        assertTrue(XenoRig.COMBAT.contains("waist"));
        assertTrue(XenoRig.COMBAT.contains("head"));
        assertTrue(XenoRig.COMBAT.contains("right_arm"));
        assertTrue(XenoRig.COMBAT.contains("left_arm"));
        assertTrue(XenoRig.COMBAT.contains("right_leg"));
        assertTrue(XenoRig.COMBAT.contains("left_leg"));
        assertEquals(7, XenoRig.COMBAT.size());
    }

    @Test
    void poseLerpIsHalfway() {
        AnimBonePose mid = AnimBonePose.lerp(AnimBonePose.rot(0, 0, 0), AnimBonePose.rot(10, 20, 30), 0.5f);
        assertEquals(5f, mid.rotX, 0.01f);
        assertEquals(10f, mid.rotY, 0.01f);
        assertEquals(15f, mid.rotZ, 0.01f);
    }
}
