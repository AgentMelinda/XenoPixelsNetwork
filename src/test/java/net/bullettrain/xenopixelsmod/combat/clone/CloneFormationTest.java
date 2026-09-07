package net.bullettrain.xenopixelsmod.combat.clone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CloneFormationTest {

    @Test
    void theFightersOwnSlotSitsBehindTheirFacing() {
        // Facing +Z (yaw 0), the back slot must be at -Z: away from whoever is being faced.
        double[] back = CloneFormation.slotOffset(0f, CloneFormation.backSlot(4), 4, 2.2);
        assertEquals(0.0, back[0], 1.0e-6);
        assertEquals(-2.2, back[1], 1.0e-6);
    }

    @Test
    void everyBodyStandsOnTheFormationCircle() {
        for (int slot = 0; slot < 4; slot++) {
            double[] off = CloneFormation.slotOffset(37f, slot, 4, 2.2);
            assertEquals(2.2, Math.hypot(off[0], off[1]), 1.0e-6, "slot " + slot);
        }
    }

    @Test
    void bodiesStartInsideTheFighterAndArriveAtTheirSlot() {
        assertEquals(0.0, CloneFormation.travelEase(0f), 1.0e-6);
        assertEquals(1.0, CloneFormation.travelEase(1f), 1.0e-6);
        // Thrown clear rather than walked out: most of the distance is covered early.
        assertTrue(CloneFormation.travelEase(0.5f) > 0.5);
        // Out of range input cannot push a body past its slot.
        assertEquals(1.0, CloneFormation.travelEase(9f), 1.0e-6);
        assertEquals(0.0, CloneFormation.travelEase(-9f), 1.0e-6);
    }

    @Test
    void displacedOwnerAndCopiesShareTheOriginalCenterAtEveryYaw() {
        for (float yaw : new float[]{0f, 37f, 90f, -145f}) {
            for (int bodies : new int[]{2, 3, 4, 8}) {
                double[] back = CloneFormation.slotOffset(yaw, 0, bodies, 2.2);
                double sumX = back[0];
                double sumZ = back[1];
                for (int slot = 1; slot < bodies; slot++) {
                    double[] relative = CloneFormation.offsetFromOwner(yaw, slot, bodies, 2.2);
                    double[] expected = CloneFormation.slotOffset(yaw, slot, bodies, 2.2);
                    assertEquals(expected[0], back[0] + relative[0], 1e-9);
                    assertEquals(expected[1], back[1] + relative[1], 1e-9);
                    assertEquals(2.2, Math.hypot(back[0] + relative[0], back[1] + relative[1]), 1e-9);
                    sumX += back[0] + relative[0];
                    sumZ += back[1] + relative[1];
                }
                assertEquals(0.0, sumX, 1e-9);
                assertEquals(0.0, sumZ, 1e-9);
            }
        }
    }

    @Test
    void dividingCostsPowerUntilItIsMastered() {
        assertEquals(0.25f, CloneFormation.damageShare(4, 0), 1.0e-6);
        assertEquals(1.0f, CloneFormation.damageShare(4, CloneFormation.PERFECT_MASTERY), 1.0e-6);
        // Halfway to mastery is halfway between a quarter and full.
        assertEquals(0.625f, CloneFormation.damageShare(4, CloneFormation.PERFECT_MASTERY / 2), 1.0e-3);
        // Mastery beyond the cap cannot exceed full power.
        assertEquals(1.0f, CloneFormation.damageShare(4, 99999), 1.0e-6);
    }

    @Test
    void anUndividedFighterIsUnaffected() {
        assertEquals(1.0f, CloneFormation.damageShare(1, 0), 1.0e-6);
        assertFalse(CloneFormation.isPerfected(999));
        assertTrue(CloneFormation.isPerfected(CloneFormation.PERFECT_MASTERY));
    }
}
