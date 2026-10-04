package net.bullettrain.xenopixelsmod.fx.effek;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 2026-09-29 owner: "i want punch effects to happen at crosshair level y". */
class PunchCrosshairTest {
    // Target 1.8 tall standing at y 64, 3 blocks away along +Z.
    private static final Vec3 CENTER = new Vec3(0, 64.9, 3);

    @Test
    void levelAimHitsAtEyeHeight() {
        Vec3 eye = new Vec3(0, 65.62, 0);
        assertEquals(65.62, PunchEffectRules.crosshairY(eye, new Vec3(0, 0, 1), CENTER, 64.0, 65.8), 1e-9);
    }

    @Test
    void aimingDownHitsLowerOnTheBody() {
        Vec3 eye = new Vec3(0, 65.62, 0);
        Vec3 look = new Vec3(0, -0.3, 1).normalize();
        assertEquals(65.62 - 0.9, PunchEffectRules.crosshairY(eye, look, CENTER, 64.0, 65.8), 1e-9);
    }

    @Test
    void theEffectStaysOnTheBody() {
        Vec3 eye = new Vec3(0, 65.62, 0);
        assertEquals(64.0, PunchEffectRules.crosshairY(eye, new Vec3(0, -1, 0.2).normalize(), CENTER, 64.0, 65.8), 1e-9,
                "aiming at the feet or below: at the feet");
        assertEquals(65.8, PunchEffectRules.crosshairY(eye, new Vec3(0, 1, 0.2).normalize(), CENTER, 64.0, 65.8), 1e-9,
                "aiming over the head: at the top of the head");
        assertEquals(65.62, PunchEffectRules.crosshairY(eye, new Vec3(0, -1, 0), new Vec3(0, 64.9, 0), 64.0, 65.8), 1e-9,
                "straight down onto someone underneath: eye height, kept on the body");
    }
}
