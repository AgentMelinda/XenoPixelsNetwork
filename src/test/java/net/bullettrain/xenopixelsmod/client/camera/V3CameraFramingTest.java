package net.bullettrain.xenopixelsmod.client.camera;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class V3CameraFramingTest {
    @org.junit.jupiter.api.Test void actionWideDoesNotWaitForBlendedFocusAndDistantKiAlwaysFits() {
        org.junit.jupiter.api.Assertions.assertTrue(V3CameraFraming.shouldFitBoth(.5, 4, false));
        org.junit.jupiter.api.Assertions.assertTrue(V3CameraFraming.shouldFitBoth(.2, 20, false));
        org.junit.jupiter.api.Assertions.assertTrue(V3CameraFraming.shouldFitBoth(.2, 4, true));
        org.junit.jupiter.api.Assertions.assertFalse(V3CameraFraming.shouldFitBoth(.2, 4, false));
    }
    @Test void distantAirbornePairFitsEvenAtNarrowFovAndPortraitAspect() {
        Vec3 a = new Vec3(0, 1, 0), b = new Vec3(0, 15, 30);
        var frame = V3CameraFraming.fit(a, 2, b, 3, new Vec3(-5, 4, -6), Vec3.ZERO, 40, .6);
        assertEquals(a.lerp(b, .5), frame.lookAt());
        assertInFrame(frame, a, 2, 40, .6);
        assertInFrame(frame, b, 3, 40, .6);
    }

    @Test void widerAuthoredShotStaysAtItsAuthoredDistance() {
        Vec3 a = new Vec3(0, 1, 0), b = new Vec3(0, 1, 2), center = a.lerp(b, .5);
        Vec3 position = center.add(-30, 10, -30);
        var frame = V3CameraFraming.fit(a, 2, b, 2, position, center, 70, 16.0/9);
        assertEquals(position, frame.position());
        assertInFrame(frame, a, 2, 70, 16.0/9);
        assertInFrame(frame, b, 2, 70, 16.0/9);
    }

    private static void assertInFrame(V3CameraFraming.Frame frame, Vec3 center, double height, double fov, double aspect) {
        Vec3 forward = frame.lookAt().subtract(frame.position()).normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(forward).normalize();
        double vertical = Math.tan(Math.toRadians(fov/2));
        for (double y : new double[]{-height*.65, height*.65}) {
            Vec3 ray = center.add(0, y, 0).subtract(frame.position());
            double depth = ray.dot(forward);
            assertTrue(depth > 0);
            assertTrue(Math.abs(ray.dot(up)) < depth*vertical);
            assertTrue(Math.abs(ray.dot(right)) < depth*vertical*aspect);
        }
    }
}
