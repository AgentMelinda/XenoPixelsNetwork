package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import static net.bullettrain.xenopixelsmod.combat.Bt3ComboTerminator.Kind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Bt3ComboTerminatorTest {

    @Test
    void anOrdinaryBeatIsNotATerminator() {
        assertEquals(Kind.NONE, Bt3ComboTerminator.resolve(false, false, true, true));
    }

    @Test
    void theFinisherBeatBecomesTheUltimate() {
        assertEquals(Kind.ULTIMATE, Bt3ComboTerminator.resolve(true, false, true, true));
    }

    @Test
    void aLaunchingFinisherStaysALaunch() {
        // Forward on the finisher already means "launch them". The Ultimate must not also fire, or
        // one press would spend two moves.
        assertEquals(Kind.NONE, Bt3ComboTerminator.resolve(true, true, true, true));
    }

    @Test
    void swingingAtSomeoneOutOfReachBecomesZBurst() {
        assertEquals(Kind.Z_BURST, Bt3ComboTerminator.resolve(false, false, true, false));
    }

    @Test
    void outOfRangeOutranksTheFinisher() {
        // You cannot finish someone you cannot touch; close the gap first.
        assertEquals(Kind.Z_BURST, Bt3ComboTerminator.resolve(true, false, true, false));
    }

    @Test
    void nothingFiresWithoutALock() {
        // Freelook swings still have to be able to hit empty air without spending a technique.
        assertEquals(Kind.NONE, Bt3ComboTerminator.resolve(false, false, false, false));
        assertEquals(Kind.NONE, Bt3ComboTerminator.resolve(true, false, false, false));
        assertEquals(Kind.NONE, Bt3ComboTerminator.resolve(true, false, false, true));
    }

    @Test
    void theRuleIsTotalAndDeterministic() {
        // The client predicts the beat and the server decides it, from the same inputs and with no
        // round trip between them. A case that answered null, threw, or answered differently on a
        // second call would desync the two.
        for (boolean finisher : new boolean[]{false, true}) {
            for (boolean launcher : new boolean[]{false, true}) {
                for (boolean lock : new boolean[]{false, true}) {
                    for (boolean range : new boolean[]{false, true}) {
                        Kind first = Bt3ComboTerminator.resolve(finisher, launcher, lock, range);
                        assertNotNull(first, "every input combination must resolve");
                        assertEquals(first,
                                Bt3ComboTerminator.resolve(finisher, launcher, lock, range),
                                "resolve must not depend on anything but its arguments");
                    }
                }
            }
        }
    }
}
