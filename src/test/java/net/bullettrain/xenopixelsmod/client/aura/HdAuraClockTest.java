package net.bullettrain.xenopixelsmod.client.aura;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-10-02 owner: "sometimes on client lag aura resets ... also with alot of mods". */
class HdAuraClockTest {

    @Test
    void ticksWithNoFrameBetweenThemDoNotAgeTheAura() {
        // A freeze: the client then runs its missed ticks back to back, no frame drawn between.
        assertFalse(HdAuraClient.clockAdvances(120L, 120L));
        assertTrue(HdAuraClient.clockAdvances(121L, 120L), "a frame was drawn: time moves on");
    }
}
