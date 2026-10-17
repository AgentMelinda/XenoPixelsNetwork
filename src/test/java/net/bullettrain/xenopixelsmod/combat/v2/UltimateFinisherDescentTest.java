package net.bullettrain.xenopixelsmod.combat.v2;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UltimateFinisherDescentTest {
    @Test void descendOnlyAndDoNotOvershootStartingHeight() {
        assertEquals(-.35, UltimateFinisherDescent.step(80, 64));
        assertEquals(-.1, UltimateFinisherDescent.step(64.1, 64), 1e-9);
        assertEquals(0, UltimateFinisherDescent.step(63, 64));
        assertEquals(0, UltimateFinisherDescent.step(Double.NaN, 64));
    }
}
