package net.bullettrain.xenopixelsmod.client.camera;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CinematicCameraMotionTest {
    @Test void smoothingDoesNotDependOnRenderRate() {
        Vec3 end = new Vec3(12, 0, -8);
        Vec3 a = advance(30, end), b = advance(144, end);
        assertEquals(a.x, b.x, 1e-8);
        assertEquals(a.z, b.z, 1e-8);
    }
    private Vec3 advance(int fps, Vec3 end) {
        var motion = new CinematicCameraMotion();
        motion.update(Vec3.ZERO, Vec3.ZERO, 0);
        CinematicCameraMotion.Frame frame = null;
        for (int i = 1; i <= fps; i++) frame = motion.update(end, Vec3.ZERO, i * 20.0 / fps);
        return frame.position();
    }
    @Test void terrainSpikeIsLimitedAndRepeatedTimeDoesNotMoveAgain() {
        var motion = new CinematicCameraMotion();
        motion.update(Vec3.ZERO, Vec3.ZERO, 0);
        var next = motion.update(new Vec3(0, 100, 0), Vec3.ZERO, 1);
        assertEquals(.6, next.position().y, 1e-8);
        assertEquals(next, motion.update(new Vec3(0, -100, 0), Vec3.ZERO, 1));
        assertEquals(Vec3.ZERO, motion.update(new Vec3(0, -100, 0), Vec3.ZERO, 2).position());
    }
}
