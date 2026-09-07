package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RushCameraTest {

    @Test
    void theAssistIsSilentAtContactAndFullAtRange() {
        // A rush lands at contact. There is nothing to correct there, and it is exactly where the
        // angular maths stops being trustworthy.
        assertEquals(0.0f, RushCamera.assistStrength(0.0), 1.0e-6);
        assertEquals(0.0f, RushCamera.assistStrength(RushCamera.CONTACT_RANGE), 1.0e-6);
        assertEquals(1.0f, RushCamera.assistStrength(RushCamera.FULL_ASSIST_RANGE), 1.0e-6);
        assertEquals(1.0f, RushCamera.assistStrength(40.0), 1.0e-6);
    }

    @Test
    void theAssistRampsInWithoutAnEdge() {
        float near = RushCamera.assistStrength(RushCamera.CONTACT_RANGE + 0.5);
        float mid = RushCamera.assistStrength(
                (RushCamera.CONTACT_RANGE + RushCamera.FULL_ASSIST_RANGE) / 2.0);
        float far = RushCamera.assistStrength(RushCamera.FULL_ASSIST_RANGE - 0.5);
        assertTrue(near < mid && mid < far, "strength must increase with distance");
        assertEquals(0.5f, mid, 1.0e-6, "smoothstep is symmetric about its midpoint");
    }

    @Test
    void pitchCannotDivergeWhenTheTargetIsOnTopOfYou() {
        // The bug: -atan2(dy, ~0) runs to +/-90 and swings wildly from centimetres of jostling.
        float pitchOnTop = RushCamera.wantPitch(2.0, 0.001);
        assertTrue(Math.abs(pitchOnTop) < 46.0f,
                "pitch must stay shallow at zero horizontal distance, was " + pitchOnTop);
        // A few centimetres of movement must not swing the target angle far.
        float a = RushCamera.wantPitch(2.0, 0.05);
        float b = RushCamera.wantPitch(2.0, 0.35);
        assertTrue(Math.abs(a - b) < 1.0f, "tiny movement must not swing pitch, was " + (a - b));
    }

    @Test
    void oneStepNeverExceedsTheCap() {
        assertTrue(RushCamera.step(180f, 1.0f, 1.0f) <= RushCamera.MAX_STEP_DEG);
        assertTrue(RushCamera.step(-180f, 1.0f, 1.0f) >= -RushCamera.MAX_STEP_DEG);
        // Authority scales the step, so a distant assist still moves and a near one barely does.
        assertTrue(RushCamera.step(30f, RushCamera.YAW_EASE, 0.25f)
                < RushCamera.step(30f, RushCamera.YAW_EASE, 1.0f));
    }

    @Test
    void smallErrorsAndZeroAuthorityAreLeftAlone() {
        assertFalse(RushCamera.shouldSteer(1.0f, 1.0f), "inside the deadzone");
        assertFalse(RushCamera.shouldSteer(90.0f, 0.0f), "no authority at contact");
        assertTrue(RushCamera.shouldSteer(45.0f, 1.0f));
    }

    @Test
    void yawFollowsMinecraftConvention() {
        // Facing +Z is yaw 0 in Minecraft's basis.
        assertEquals(0.0f, RushCamera.wantYaw(0.0, 1.0), 1.0e-4);
        assertEquals(-90.0f, RushCamera.wantYaw(1.0, 0.0), 1.0e-4);
    }
}
