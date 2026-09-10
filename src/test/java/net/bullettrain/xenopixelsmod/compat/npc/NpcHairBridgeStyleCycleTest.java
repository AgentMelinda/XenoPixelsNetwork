package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hair-style cycle, which has to stay inside {@code 0..count} in both directions.
 *
 * <p>Zero is not "no style" to be skipped past — it is the custom hair code, and a builder has to be
 * able to get back to it from either end of the list.
 */
class NpcHairBridgeStyleCycleTest {

    @Test
    void forwardWrapsPastTheLastStyleBackToTheCustomCode() {
        assertEquals(1, NpcHairBridge.cycleStyle(0, 1, 3));
        assertEquals(2, NpcHairBridge.cycleStyle(1, 1, 3));
        assertEquals(3, NpcHairBridge.cycleStyle(2, 1, 3));
        assertEquals(0, NpcHairBridge.cycleStyle(3, 1, 3));
    }

    @Test
    void backwardFromTheCustomCodeLandsOnTheLastStyle() {
        assertEquals(3, NpcHairBridge.cycleStyle(0, -1, 3));
        assertEquals(0, NpcHairBridge.cycleStyle(1, -1, 3));
    }

    @Test
    void withNoStylesRegisteredItStaysOnTheCustomCode() {
        // DragonMineZ registers presets at runtime, so a client that has none must not offer a
        // style the renderer would then fail to resolve.
        assertEquals(0, NpcHairBridge.cycleStyle(0, 1, 0));
        assertEquals(0, NpcHairBridge.cycleStyle(0, -1, 0));
    }

    @Test
    void everyStepStaysInRange() {
        for (int count : new int[]{0, 1, 5, 40}) {
            int id = 0;
            for (int step = 0; step < 200; step++) {
                id = NpcHairBridge.cycleStyle(id, step % 3 == 0 ? -1 : 1, count);
                assertTrue(id >= 0 && id <= count, "id " + id + " outside 0.." + count);
            }
        }
    }

    @Test
    void aStoredIdBeyondTheListIsStillBrokenOutOfByCycling() {
        // A profile written when more presets existed must not trap the button.
        int id = NpcHairBridge.cycleStyle(99, 1, 3);
        assertTrue(id >= 0 && id <= 3, "cycling from a stale id must land back in range, got " + id);
    }
}
