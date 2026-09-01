package net.bullettrain.xenopixelsmod.combat.technique;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrageMasteryTest {

    @Test
    void levelZeroAddsNothing() {
        assertEquals(0, BarrageMastery.extraTicks(0));
        assertEquals(50, BarrageMastery.extendLife(50, 0));
    }

    @Test
    void higherLevelAddsMoreTicks() {
        assertTrue(BarrageMastery.extraTicks(1) > 0);
        assertTrue(BarrageMastery.extraTicks(2) > BarrageMastery.extraTicks(1));
        assertTrue(BarrageMastery.extraTicks(3) > BarrageMastery.extraTicks(2));
    }

    @Test
    void extendLifeNeverDropsBelowOne() {
        assertEquals(1, BarrageMastery.extendLife(0, 0));
    }
}
