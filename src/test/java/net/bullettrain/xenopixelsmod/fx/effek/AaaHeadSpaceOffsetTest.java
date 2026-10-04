package net.bullettrain.xenopixelsmod.fx.effek;

import mod.chloeprime.aaaparticles.common.util.Basis;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs AAA 2.3.1's own Basis the way its head-space update does (ParticleEmitterInfo.spawnInWorld:
 * fromEuler(-pitch, PI - yaw, rotZ).toGlobal(entitySpaceRelativePosition)), to learn which way an
 * entity-space offset goes relative to the look. The flight aura is anchored with such an offset
 * so it grows around the body when scaled.
 */
class AaaHeadSpaceOffsetTest {
    static Vec3 offset(float yawDeg, float pitchDeg, Vec3 local) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        return Basis.fromEuler(new Vec3(-pitch, Math.PI - yaw, 0)).toGlobal(local);
    }

    static Vec3 look(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    @Test
    void anOffsetAlongLocalZPointsAgainstTheLook() {
        for (float[] v : new float[][] {{0, 0}, {90, 0}, {-90, 0}, {180, 0}, {37, 25}, {0, -80}, {120, 60}}) {
            Vec3 o = offset(v[0], v[1], new Vec3(0, 0, 1));
            Vec3 l = look(v[0], v[1]);
            // Measured 2026-09-29: +Z of the offset is exactly behind the eyes, so +0.9 is the body centre.
            assertEquals(-1.0, o.dot(l), 1e-9, "yaw " + v[0] + " pitch " + v[1]);
        }
    }
}
