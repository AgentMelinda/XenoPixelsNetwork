package net.bullettrain.xenopixelsmod.combat.v2;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class UltimateFinisherBeamAimTest {
    @Test void renderCorrectionCancelsOldWaveRotationBeforeApplyingCurrentTargetRay() {
        var aim = UltimateFinisherBeamAim.toward(new Vec3(2, 4, -3), new Vec3(-8, 9, 15), new Vec3(0, 0, 1));
        var desired = new org.joml.Quaternionf().rotationY((float)Math.toRadians(-aim.yaw()))
                .mul(new org.joml.Quaternionf().rotationX((float)Math.toRadians(aim.pitch())));
        var old = new org.joml.Quaternionf().rotationY((float)Math.toRadians(-42))
                .mul(new org.joml.Quaternionf().rotationX((float)Math.toRadians(18)));
        var correction = new org.joml.Quaternionf(desired).mul(new org.joml.Quaternionf(old).conjugate());
        var actual = correction.mul(old).transform(new org.joml.Vector3f(0, 0, 1));
        assertEquals(aim.direction().x, actual.x, 1e-6);
        assertEquals(aim.direction().y, actual.y, 1e-6);
        assertEquals(aim.direction().z, actual.z, 1e-6);
    }
    @Test void centeredRayIntersectsBothLeftAndRightRaisedTargets() {
        Vec3 eye = new Vec3(3, 1.6, -4);
        for (Vec3 target : new Vec3[]{new Vec3(-5, 7, 15), new Vec3(9, .9, 15)}) {
            var aim = UltimateFinisherBeamAim.toward(eye, target, new Vec3(0, -1, 0));
            Vec3 direction = Vec3.directionFromRotation(aim.pitch(), aim.yaw());
            Vec3 rayEnd = aim.origin().add(direction.scale(aim.origin().distanceTo(target)));
            // Minecraft's directionFromRotation uses Mth's 65536-entry sine
            // table. Millimeter error at this range is expected quantization.
            assertEquals(target.x, rayEnd.x, .005);
            assertEquals(target.y, rayEnd.y, .005);
            assertEquals(target.z, rayEnd.z, .005);
        }
    }
    @Test void closeVictimCannotBeOvershotByTheMuzzle() {
        var aim = UltimateFinisherBeamAim.toward(Vec3.ZERO, new Vec3(0, 0, .2), new Vec3(0, 0, 1));
        assertTrue(aim.origin().z < .2);
        assertTrue(aim.direction().z > 0);
    }
}
