package net.bullettrain.xenopixelsmod.missile;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MissileChunkLoadManagerTest {
    @Test void existingForcedChunksAreNeverClaimedForLaterRelease() {
        assertFalse(MissileChunkLoadManager.acquireTicket(true, () -> {
            throw new AssertionError("must not overwrite administrator ownership");
        }));
        assertTrue(MissileChunkLoadManager.acquireTicket(false, () -> true));
        assertFalse(MissileChunkLoadManager.acquireTicket(false, () -> false));
    }
}
