package net.bullettrain.xenopixelsmod.combat.combo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboKnockTravelTest {

    @Test
    void constructorClampsNegativesToZero() {
        ComboKnockTravel t = new ComboKnockTravel(-1.2, -0.5, -3.0);
        assertEquals(0.0, t.distance(), 1.0e-9);
        assertEquals(0.0, t.up(), 1.0e-9);
        assertEquals(0.0, t.down(), 1.0e-9);
        assertTrue(t.isZero());
    }

    @Test
    void isZeroOnlyWhenAllAxesAreZero() {
        assertTrue(new ComboKnockTravel(0, 0, 0).isZero());
        assertFalse(new ComboKnockTravel(0.1, 0, 0).isZero());
        assertFalse(new ComboKnockTravel(0, 0.1, 0).isZero());
        assertFalse(new ComboKnockTravel(0, 0, 0.1).isZero());
    }

    @Test
    void zeroImpulseLeavesVictimUnmoved() {
        double[] v = RushKnockbackPath.impulse(0, 1, 0, 1, 0, 0, 0, 0, 0, 50.0, -80.0);
        assertEquals(0.0, v[0], 1.0e-9);
        assertEquals(0.0, v[1], 1.0e-9);
        assertEquals(0.0, v[2], 1.0e-9);
    }

    @Test
    void knockTravelSelectsLiftTrioForLiftCombo() {
        ComboKnockTravel rush = new ComboKnockTravel(1.65, 1.85, 0.8);
        ComboKnockTravel lift = new ComboKnockTravel(0.4, 0.9, 0.8);
        ComboKnockTravel chosen = ComboRouteMachine.knockTravel(
                ComboRouteCatalog.bySkillId("liftcombo"), rush, lift);
        assertEquals(0.4, chosen.distance(), 1.0e-9);
        assertEquals(0.9, chosen.up(), 1.0e-9);
        assertEquals(0.8, chosen.down(), 1.0e-9);
    }

    @Test
    void knockTravelSelectsRushTrioForRushCombo() {
        ComboKnockTravel rush = new ComboKnockTravel(1.65, 1.85, 0.8);
        ComboKnockTravel lift = new ComboKnockTravel(0.4, 0.9, 0.8);
        ComboKnockTravel chosen = ComboRouteMachine.knockTravel(
                ComboRouteCatalog.bySkillId("rushcombo"), rush, lift);
        assertEquals(1.65, chosen.distance(), 1.0e-9);
        assertEquals(1.85, chosen.up(), 1.0e-9);
        assertEquals(0.8, chosen.down(), 1.0e-9);
    }

    @Test
    void changingLiftTrioDoesNotChangeRushSelection() {
        ComboKnockTravel rush = new ComboKnockTravel(2.5, 3.0, 1.0);
        ComboKnockTravel lift = new ComboKnockTravel(9.0, 9.0, 9.0);
        ComboKnockTravel chosen = ComboRouteMachine.knockTravel(
                ComboRouteCatalog.bySkillId("rushcombo"), rush, lift);
        assertEquals(2.5, chosen.distance(), 1.0e-9);
        assertEquals(3.0, chosen.up(), 1.0e-9);
        assertEquals(1.0, chosen.down(), 1.0e-9);
    }

    @Test
    void nullLiftOnLiftComboIsZero() {
        ComboKnockTravel rush = new ComboKnockTravel(1.65, 1.85, 0.8);
        ComboKnockTravel chosen = ComboRouteMachine.knockTravel(
                ComboRouteCatalog.bySkillId("liftcombo"), rush, null);
        assertTrue(chosen.isZero());
    }
}
