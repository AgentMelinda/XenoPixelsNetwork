package net.bullettrain.xenopixelsmod.combat.clone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zanzoken ring geometry, checked against the real {@link CloneFormation#ringOffset}.
 *
 * <p>This used to assert against a copy of the formula written inside the test, which guards
 * nothing: the shipped code could drift and the test would still pass. It now calls the same
 * function the technique does.
 */
class XenoCloneFormationTest {

    @Test
    void everySlotSitsOnTheCircleAtTheGivenRadius() {
        for (int i = 0; i < 7; i++) {
            double[] p = CloneFormation.ringOffset(i, 7, 3.0);
            assertEquals(3.0, Math.hypot(p[0], p[1]), 1.0e-9, "slot " + i + " must be on the ring");
        }
    }

    @Test
    void slotsAreEvenlySpacedAndTheRingCloses() {
        int count = 6;
        double[] a = CloneFormation.ringOffset(0, count, 3.0);
        double[] b = CloneFormation.ringOffset(1, count, 3.0);
        assertEquals(2.0 * Math.PI / count,
                Math.atan2(b[1], b[0]) - Math.atan2(a[1], a[0]), 1.0e-9);
        // Stepping a full turn returns to the start, so the ring has no seam.
        double[] wrapped = CloneFormation.ringOffset(count, count, 3.0);
        assertEquals(a[0], wrapped[0], 1.0e-9);
        assertEquals(a[1], wrapped[1], 1.0e-9);
    }

    @Test
    void anIndexOutsideTheRingWrapsRatherThanEscapingIt() {
        double[] wrapped = CloneFormation.ringOffset(-1, 4, 3.0);
        double[] equivalent = CloneFormation.ringOffset(3, 4, 3.0);
        assertEquals(equivalent[0], wrapped[0], 1.0e-9);
        assertEquals(equivalent[1], wrapped[1], 1.0e-9);
    }

    @Test
    void theDodgersSlotIsOneOfTheRingSlots() {
        // The whole disguise rests on this: the real body must be placed by the same maths as the
        // images, or its position alone identifies it.
        int slots = 7;
        for (int mine = 0; mine < slots; mine++) {
            double[] dodger = CloneFormation.ringOffset(mine, slots, 3.0);
            assertEquals(3.0, Math.hypot(dodger[0], dodger[1]), 1.0e-9);
            int occupied = 0;
            for (int i = 0; i < slots; i++) {
                if (i == mine) continue;
                double[] image = CloneFormation.ringOffset(i, slots, 3.0);
                // No image may share the dodger's slot.
                assertTrue(Math.hypot(image[0] - dodger[0], image[1] - dodger[1]) > 1.0e-6,
                        "image " + i + " collided with the dodger's slot");
                occupied++;
            }
            assertEquals(slots - 1, occupied, "one slot is the dodger's, the rest are images");
        }
    }

    @Test
    void aSingleSlotStillLandsOnTheCircle() {
        double[] p = CloneFormation.ringOffset(0, 1, 3.0);
        assertEquals(3.0, Math.hypot(p[0], p[1]), 1.0e-9);
    }
}
