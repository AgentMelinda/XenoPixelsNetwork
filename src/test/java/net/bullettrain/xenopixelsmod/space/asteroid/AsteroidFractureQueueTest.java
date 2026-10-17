package net.bullettrain.xenopixelsmod.space.asteroid;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidFractureQueueTest {
    @Test void oneQueuedCutPerBodyCanBeObservedAndCanceledWithoutDroppingOtherWork() {
        var queue = new AsteroidFractureQueue<String>();
        UUID bodyA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bodyB = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertTrue(queue.enqueue(bodyA, "cut-a"));
        assertFalse(queue.enqueue(bodyA, "duplicate-cut-a"));
        assertTrue(queue.enqueue(bodyB, "cut-b"));
        assertTrue(queue.hasBody(bodyA));
        assertEquals(1, queue.cancelBody(bodyA));
        assertFalse(queue.hasBody(bodyA));
        assertTrue(queue.hasBody(bodyB));
        assertEquals("cut-b", queue.first().payload());
        assertEquals(1, queue.size());
    }
}
