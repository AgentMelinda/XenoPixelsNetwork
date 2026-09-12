package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcDissolveTest {
    @Test
    void progressMapsOntoTheVanillaAmplifierByte() {
        assertEquals(0, NpcDissolve.amplifierForProgress(0.0f));
        assertEquals(255, NpcDissolve.amplifierForProgress(1.0f));
        assertEquals(128, NpcDissolve.amplifierForProgress(128 / 255.0f));
    }

    @Test
    void outOfRangeProgressClamps() {
        assertEquals(0, NpcDissolve.amplifierForProgress(-2.0f));
        assertEquals(255, NpcDissolve.amplifierForProgress(4.0f));
    }
}
