package net.bullettrain.xenopixelsmod.client.anim.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudioBoneSpaceTest {
    private static final float EPS = 1e-5f;

    @Test
    void xAndYAreNegatedLikeBedrockAndZIsNot() {
        assertEquals(Math.toRadians(-30), StudioBoneSpace.toBoneRadians(0, 30f, 0f), EPS);
        assertEquals(Math.toRadians(-30), StudioBoneSpace.toBoneRadians(1, 30f, 0f), EPS);
        assertEquals(Math.toRadians(30), StudioBoneSpace.toBoneRadians(2, 30f, 0f), EPS);
    }

    @Test
    void restPoseIsTheInitialSnapshotNotZero() {
        float init = 0.4f;
        assertEquals(init, StudioBoneSpace.toBoneRadians(0, 0f, init), EPS);
        assertEquals(init, StudioBoneSpace.toBoneRadians(2, 0f, init), EPS);
        assertEquals(0f, StudioBoneSpace.toStudioDegrees(1, init, init), EPS);
    }

    @Test
    void offsetIsAddedOnTopOfTheSnapshot() {
        float init = -0.25f;
        assertEquals(init + Math.toRadians(-90), StudioBoneSpace.toBoneRadians(0, 90f, init), EPS);
        assertEquals(init + Math.toRadians(45), StudioBoneSpace.toBoneRadians(2, 45f, init), EPS);
    }

    @Test
    void roundTripIsIdentityOnEveryAxis() {
        float[] inits = {0f, 0.3f, -1.1f};
        float[] degrees = {-180f, -37.5f, 0f, 12f, 179f};
        for (int axis = 0; axis < 3; axis++) {
            for (float init : inits) {
                for (float deg : degrees) {
                    float bone = StudioBoneSpace.toBoneRadians(axis, deg, init);
                    assertEquals(deg, StudioBoneSpace.toStudioDegrees(axis, bone, init), 1e-3f,
                            "axis " + axis + " init " + init + " deg " + deg);
                }
            }
        }
    }
}
