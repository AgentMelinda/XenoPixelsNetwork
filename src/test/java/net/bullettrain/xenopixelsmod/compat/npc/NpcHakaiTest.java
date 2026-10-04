package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NpcHakaiTest {
    @Test
    void cancelAndQueryWithoutAnEntityDoNotThrow() {
        assertFalse(NpcHakai.cancel(null));
        assertFalse(NpcHakai.isChanneling(null));
        NpcHakai.forget(null);
    }
}
