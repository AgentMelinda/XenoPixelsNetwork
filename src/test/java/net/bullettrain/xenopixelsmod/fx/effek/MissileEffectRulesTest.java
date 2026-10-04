package net.bullettrain.xenopixelsmod.fx.effek;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MissileEffectRulesTest {
    @Test
    void thrusterPulsesEveryEightTicks() {
        int n = 0;
        for (long t = 0; t < 80; t++) if (MissileEffectRules.thrusterPulseDue(t)) n++;
        assertEquals(10, n);
    }

    @Test
    void explosionScaleFollowsBlastPowerWithinBounds() {
        assertEquals(0.5f, MissileEffectRules.explosionScale(0f), 1e-6);
        assertEquals(1.5f, MissileEffectRules.explosionScale(4f), 1e-6);
        assertEquals(6f, MissileEffectRules.explosionScale(1_000f), 1e-6);
    }

    @Test
    void theNozzleIsBehindTheFlightDirection() {
        Vec3 n = MissileEffectRules.nozzle(new Vec3(0, 100, 0), new Vec3(0, 0, 10), 3);
        assertEquals(new Vec3(0, 100, -3), n);
        assertEquals(new Vec3(0, 97, 0), MissileEffectRules.nozzle(new Vec3(0, 100, 0), Vec3.ZERO, 3));
    }

    /**
     * The bound missile plume hangs 1.25 effect units behind the missile's centre, which is the
     * tail only when the effect is scaled by exactly 0.4 x the missile's length.
     */
    @org.junit.jupiter.api.Test
    void theBoundPlumeScaleKeepsTheNozzleAtTheTail() {
        for (float length : new float[] {0.8f, 1.5f, 3.0f, 6.0f}) {
            float scale = MissileEffectRules.boundThrusterScale(length);
            org.junit.jupiter.api.Assertions.assertEquals(length * 0.5f, 1.25f * scale, 1e-5f, "length " + length);
        }
    }
}
