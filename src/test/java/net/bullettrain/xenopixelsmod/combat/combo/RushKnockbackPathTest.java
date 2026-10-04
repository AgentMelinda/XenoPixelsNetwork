package net.bullettrain.xenopixelsmod.combat.combo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RushKnockbackPathTest {

    @Test
    void lookUpLaunchesDiagonallyUpward() {
        double[] v = RushKnockbackPath.impulse(0, 1, 0, 1, 0, 0, 1.5, 0.9, 0.8, 50.0, -80.0);
        assertEquals(1.5, v[0], 1.0e-6);
        assertEquals(0.9, v[1], 1.0e-6);
        assertEquals(0.0, v[2], 1.0e-6);
    }

    @Test
    void lookDownLaunchesDiagonallyDownward() {
        double[] v = RushKnockbackPath.impulse(0, -1, 0, 1, 0, 0, 1.5, 0.9, 0.8, 50.0, 80.0);
        assertEquals(1.5, v[0], 1.0e-6);
        assertEquals(-0.8, v[1], 1.0e-6);
        assertEquals(0.0, v[2], 1.0e-6);
    }

    @Test
    void shallowLookIsAlwaysDiagonalUp() {
        double[] v = RushKnockbackPath.impulse(1, 0.5, 0, 1, 0, 0, 2.0, 0.9, 0.8, 50.0, -20.0);
        assertTrue(v[0] > 0.0);
        assertEquals(0.9, v[1], 1.0e-6);
        assertEquals(0.0, v[2], 1.0e-6);
    }
}
