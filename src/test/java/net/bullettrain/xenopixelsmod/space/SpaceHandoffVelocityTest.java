package net.bullettrain.xenopixelsmod.space;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpaceHandoffVelocityTest {
    @Test void convertsSourceLocalVelocityIntoDestinationOrbitalFrame() {
        Vec3 result = SpaceHandoffVelocity.convert(
                new Vec3(0.25, 0.5, 0.0),
                new Vec3(0.0, 20.0, -40.0),
                Vec3.ZERO,
                true);

        assertEquals(new Vec3(0.25, 1.5, -2.0), result);
    }

    @Test void enteringMovingPlanetSubtractsItsOrbitalVelocityInBlocksPerTick() {
        Vec3 result = SpaceHandoffVelocity.convert(
                new Vec3(1.25, 0.2, 0.0),
                Vec3.ZERO,
                new Vec3(20.0, 0.0, -10.0),
                true);

        assertEquals(new Vec3(0.25, 0.2, 0.5), result);
    }

    @Test void disabledCarryoverKeepsTheZeroVelocityHandoffBehavior() {
        assertEquals(Vec3.ZERO, SpaceHandoffVelocity.convert(
                new Vec3(1.25, 0.2, 0.0),
                new Vec3(20.0, 0.0, -10.0),
                Vec3.ZERO,
                false));
    }
}
