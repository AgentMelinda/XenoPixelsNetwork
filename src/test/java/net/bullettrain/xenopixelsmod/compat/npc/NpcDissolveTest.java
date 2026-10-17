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

    @Test
    void rateIsPerTickEvenWhenAcceptedWritesAreNotConsecutive() {
        assertEquals(0.02f, NpcDissolve.measuredRate(0.30f, 100L, 0.32f, 101L), 1.0e-6f);
        // A live channel can defer a source's writes for several ticks; the slope must survive the gap.
        assertEquals(0.02f, NpcDissolve.measuredRate(0.30f, 100L, 0.36f, 103L), 1.0e-6f);
    }

    @Test
    void firstMeasuredProgressAndReversalKeepTheirSigns() {
        assertEquals(0.0f, NpcDissolve.measuredRate(-1.0f, Long.MIN_VALUE, 0.05f, 100L), 1.0e-6f);
        assertEquals(-0.05f, NpcDissolve.measuredRate(0.40f, 100L, 0.30f, 102L), 1.0e-6f);
    }
}
