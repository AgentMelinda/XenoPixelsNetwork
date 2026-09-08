package net.bullettrain.xenopixelsmod.client.pad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PadChordsTest {

    @Test
    void noModifierIsTheBaseLayer() {
        assertEquals(PadChords.Layer.BASE, PadChords.active(false, false));
    }

    @Test
    void eachModifierSelectsItsOwnLayer() {
        assertEquals(PadChords.Layer.CHARGE, PadChords.active(true, false));
        assertEquals(PadChords.Layer.LOCK, PadChords.active(false, true));
    }

    @Test
    void chargeWinsWhenBothAreHeld() {
        // The trigger is held for seconds at a time while powering up, so it is the modifier most
        // likely to still be down by accident. Resolving in its favour makes the ambiguous case
        // predictable instead of depending on which was pressed first.
        assertEquals(PadChords.Layer.CHARGE, PadChords.active(true, true));
    }

    @Test
    void exactlyOneLayerCanEverAct() {
        // This is the property the whole chord scheme rests on: three bindings share one face
        // button, and if two could ever pass their gate at once that button would fire two moves.
        for (boolean charge : new boolean[]{false, true}) {
            for (boolean lock : new boolean[]{false, true}) {
                int allowed = 0;
                for (PadChords.Layer layer : PadChords.Layer.values()) {
                    if (PadChords.allows(layer, charge, lock)) allowed++;
                }
                assertEquals(1, allowed,
                        "charge=" + charge + " lock=" + lock + " must allow exactly one layer");
            }
        }
    }

    @Test
    void aHeldModifierWithholdsThePlainMove() {
        // Holding the trigger has to take the base move away, not merely add the charged one --
        // otherwise one press would both melee and launch the Ultimate.
        assertTrue(PadChords.allows(PadChords.Layer.BASE, false, false));
        assertFalse(PadChords.allows(PadChords.Layer.BASE, true, false));
        assertFalse(PadChords.allows(PadChords.Layer.BASE, false, true));
        assertFalse(PadChords.allows(PadChords.Layer.BASE, true, true));
    }

    @Test
    void theLockLayerStandsDownUnderTheChargeModifier() {
        assertTrue(PadChords.allows(PadChords.Layer.LOCK, false, true));
        assertFalse(PadChords.allows(PadChords.Layer.LOCK, true, true));
    }
}
