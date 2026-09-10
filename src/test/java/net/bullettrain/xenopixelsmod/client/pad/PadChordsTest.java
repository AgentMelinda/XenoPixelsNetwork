package net.bullettrain.xenopixelsmod.client.pad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PadChordsTest {

    @Test
    void noModifierIsTheBaseLayer() {
        assertEquals(PadChords.Layer.BASE, PadChords.active(false, false, false));
    }

    @Test
    void eachModifierSelectsItsOwnLayer() {
        assertEquals(PadChords.Layer.CHARGE, PadChords.active(true, false, false));
        assertEquals(PadChords.Layer.LOCK, PadChords.active(false, true, false));
        assertEquals(PadChords.Layer.DESCEND, PadChords.active(false, false, true));
    }

    @Test
    void chargeWinsWhenBothAreHeld() {
        // The trigger is held for seconds at a time while powering up, so it is the modifier most
        // likely to still be down by accident. Resolving in its favour makes the ambiguous case
        // predictable instead of depending on which was pressed first.
        assertEquals(PadChords.Layer.CHARGE, PadChords.active(true, true, false));
        assertEquals(PadChords.Layer.CHARGE, PadChords.active(true, false, true));
        assertEquals(PadChords.Layer.CHARGE, PadChords.active(true, true, true));
    }

    @Test
    void lockOutranksDescend() {
        // The right trigger is held continuously all the way down a descent, so a descending
        // player who reaches for a lock-on chord means the lock-on move.
        assertEquals(PadChords.Layer.LOCK, PadChords.active(false, true, true));
    }

    @Test
    void exactlyOneLayerCanEverAct() {
        // This is the property the whole chord scheme rests on: several bindings share one face
        // button, and if two could ever pass their gate at once that button would fire two moves.
        // Descend is included because it was not a modifier at first, and RT+Y consequently fired
        // the charged kick and the base ki blast together.
        for (boolean charge : new boolean[]{false, true}) {
            for (boolean lock : new boolean[]{false, true}) {
                for (boolean descend : new boolean[]{false, true}) {
                    int allowed = 0;
                    for (PadChords.Layer layer : PadChords.Layer.values()) {
                        if (PadChords.allows(layer, charge, lock, descend)) allowed++;
                    }
                    assertEquals(1, allowed,
                            "charge=" + charge + " lock=" + lock + " descend=" + descend
                                    + " must allow exactly one layer");
                }
            }
        }
    }

    @Test
    void aHeldModifierWithholdsThePlainMove() {
        // Holding a modifier has to take the base move away, not merely add the modified one --
        // otherwise one press would both melee and launch the Ultimate.
        assertTrue(PadChords.allows(PadChords.Layer.BASE, false, false, false));
        assertFalse(PadChords.allows(PadChords.Layer.BASE, true, false, false));
        assertFalse(PadChords.allows(PadChords.Layer.BASE, false, true, false));
        assertFalse(PadChords.allows(PadChords.Layer.BASE, false, false, true));
        assertFalse(PadChords.allows(PadChords.Layer.BASE, true, true, true));
    }

    @Test
    void theLockLayerStandsDownUnderTheChargeModifier() {
        assertTrue(PadChords.allows(PadChords.Layer.LOCK, false, true, false));
        assertFalse(PadChords.allows(PadChords.Layer.LOCK, true, true, false));
    }

    @Test
    void theDescendLayerStandsDownUnderEitherOtherModifier() {
        assertTrue(PadChords.allows(PadChords.Layer.DESCEND, false, false, true));
        assertFalse(PadChords.allows(PadChords.Layer.DESCEND, true, false, true));
        assertFalse(PadChords.allows(PadChords.Layer.DESCEND, false, true, true));
    }
}
