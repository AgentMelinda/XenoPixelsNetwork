package net.bullettrain.xenopixelsmod.combat.combo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboRouteRangeTest {

    @Test
    void zeroOrNegativeRangeIsUnlimited() {
        assertTrue(ComboRouteMachine.withinRange(0.0, 0.0));
        assertTrue(ComboRouteMachine.withinRange(10_000.0, 0.0));
        assertTrue(ComboRouteMachine.withinRange(10_000.0, -1.0));
    }

    @Test
    void positiveRangeGatesDistance() {
        assertTrue(ComboRouteMachine.withinRange(16.0, 16.0));
        assertTrue(ComboRouteMachine.withinRange(4.5, 16.0));
        assertFalse(ComboRouteMachine.withinRange(16.01, 16.0));
    }
}
