package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-10-02 owner: "and dummytrain not getting knockedbacked?" */
class NpcKnockbackGraceTest {

    @Test
    void theBrainStaysOffForAFewTicksAndThenTakesTheNpcBack() {
        long until = NpcKnockbackGrace.until(1000L);
        assertTrue(NpcKnockbackGrace.open(until, 1000L));
        assertTrue(NpcKnockbackGrace.open(until, 1000L + NpcKnockbackGrace.TICKS - 1));
        assertFalse(NpcKnockbackGrace.open(until, 1000L + NpcKnockbackGrace.TICKS));
        assertFalse(NpcKnockbackGrace.open(null, 1000L), "never knocked back");
    }
}
