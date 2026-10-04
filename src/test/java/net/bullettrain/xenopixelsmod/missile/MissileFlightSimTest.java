package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissileFlightSimTest {

    @Test
    void ejectAlwaysKinematic() {
        assertTrue(MissileFlightSim.kinematic(true, true));
        assertTrue(MissileFlightSim.kinematic(true, false));
    }

    @Test
    void coastUsesPhysicsOnlyInAReadyChunk() {
        assertFalse(MissileFlightSim.kinematic(false, true));
        assertTrue(MissileFlightSim.kinematic(false, false));
    }

    @Test
    void phantomBorderCollisionDoesNotDetonate() {
        assertFalse(MissileFlightSim.impactDetonation(false, true, false));
        assertFalse(MissileFlightSim.impactDetonation(true, true, true));
        assertTrue(MissileFlightSim.impactDetonation(false, true, true));
        assertFalse(MissileFlightSim.impactDetonation(false, false, true));
    }

    @Test
    void fastRoundStillHitsWhenItOvershootsTheTarget() {
        Vec3 from = new Vec3(0, 80, 0);
        Vec3 to = new Vec3(20, 80, 0);
        assertTrue(MissileFlightSim.reachedTarget(from, to, 8, 80, 0, 1.5));
        assertFalse(MissileFlightSim.reachedTarget(from, to, 40, 80, 0, 1.5));
    }
}
