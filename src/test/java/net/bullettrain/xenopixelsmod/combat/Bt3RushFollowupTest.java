package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bt3RushFollowupTest {

    @Test
    void onlyTheThirdSuccessfulHitArmsTheWindow() {
        UUID target = UUID.randomUUID();
        assertNull(Bt3RushFollowup.arm(2, target, 100));
        assertNull(Bt3RushFollowup.arm(4, target, 100));
        assertNull(Bt3RushFollowup.arm(3, null, 100));
        assertTrue(Bt3RushFollowup.accepts(Bt3RushFollowup.arm(3, target, 100), target, 100));
    }

    @Test
    void theWindowIsTargetBoundAndIncludesItsLastTick() {
        UUID target = UUID.randomUUID();
        Bt3RushFollowup.Gate gate = Bt3RushFollowup.arm(3, target, 40);
        assertTrue(Bt3RushFollowup.accepts(gate, target, 52));
        assertFalse(Bt3RushFollowup.accepts(gate, target, 53));
        assertFalse(Bt3RushFollowup.accepts(gate, UUID.randomUUID(), 45));
        assertFalse(Bt3RushFollowup.expired(gate, 52));
        assertTrue(Bt3RushFollowup.expired(gate, 53));
    }
}
