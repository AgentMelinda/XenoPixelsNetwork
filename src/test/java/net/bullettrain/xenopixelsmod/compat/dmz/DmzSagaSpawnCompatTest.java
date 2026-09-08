package net.bullettrain.xenopixelsmod.compat.dmz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DmzSagaSpawnCompatTest {
    @Test
    void missingCountSubtractsProgressAndLivingQuestEnemies() {
        assertEquals(3, DmzSagaSpawnCompat.missingCount(5, 1, 1));
        assertEquals(0, DmzSagaSpawnCompat.missingCount(1, 1, 0));
        assertEquals(0, DmzSagaSpawnCompat.missingCount(1, 0, 2));
    }
}
