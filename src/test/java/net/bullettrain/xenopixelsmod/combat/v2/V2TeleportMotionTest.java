package net.bullettrain.xenopixelsmod.combat.v2;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2TeleportMotionTest {
    @Test
    void vanishClearsOldMotionAndQueuesTheResetForTheClient() {
        // Marker uses the real Entity motion fields without needing a running world.
        Marker fighter = new Marker(EntityType.MARKER, null);
        fighter.setDeltaMovement(0.8, 0.3, -0.5);
        fighter.fallDistance = 12f;
        fighter.hurtMarked = false;
        fighter.hasImpulse = false;

        V2Moves.resetTeleportMotion(fighter);

        assertEquals(Vec3.ZERO, fighter.getDeltaMovement());
        assertTrue(fighter.hurtMarked, "the tracker must send zero velocity to the fighter's client");
        assertTrue(fighter.hasImpulse);
        assertEquals(0f, fighter.fallDistance);
    }
}
