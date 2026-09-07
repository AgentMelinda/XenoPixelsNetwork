package net.bullettrain.xenopixelsmod.client.compat.npc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcAnimationClientTest {

    @AfterEach
    void clearQueue() {
        NpcAnimationClient.clear();
    }

    @Test
    void pendingAnimationRemainsUntilSuccessfulConsumption() {
        UUID npc = UUID.randomUUID();
        NpcAnimationClient.queue(npc, "combat.xeno_body_punch_left", 1.0f);

        NpcAnimationClient.Pending pending = NpcAnimationClient.peek(npc);

        assertEquals("combat.xeno_body_punch_left", pending.animation());
        assertEquals(pending, NpcAnimationClient.peek(npc));
        assertTrue(NpcAnimationClient.consume(npc, pending));
        assertNull(NpcAnimationClient.peek(npc));
    }

    @Test
    void consumingOldDeliveryDoesNotRemoveNewerAnimation() {
        UUID npc = UUID.randomUUID();
        NpcAnimationClient.queue(npc, "combat.xeno_body_punch_left", 1.0f);
        NpcAnimationClient.Pending old = NpcAnimationClient.peek(npc);
        NpcAnimationClient.queue(npc, "combat.xeno_body_punch_right_v2", 1.0f);

        assertFalse(NpcAnimationClient.consume(npc, old));
        assertEquals("combat.xeno_body_punch_right_v2",
                NpcAnimationClient.peek(npc).animation());
    }
}
