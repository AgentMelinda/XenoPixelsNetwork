package net.bullettrain.xenopixelsmod.npc.movement;

import net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules that decide which system may move an NPC.
 *
 * <p>These are the whole point of the arbiter, so they are tested directly rather than inferred
 * from a running world. The bug being fixed - several systems steering one NPC at once - is not
 * visible in a screenshot and not reproducible on demand; what is checkable is that a lower
 * priority cannot take an NPC away from a higher one, and that a mover which is getting nowhere is
 * eventually told to stop trying.
 */
class NpcMovementLedgerTest {

    /** Any tick will do; the rules care about differences, not absolute time. */
    private static final long T = 1_000L;

    // ------------------------------------------------------------ priority

    @Test
    void anUnclaimedNpcGrantsWhoeverAsks() {
        assertTrue(new NpcMovementLedger().claim(Claim.STROLL, T));
    }

    @Test
    void aLowerPriorityCannotInterruptALiveHigherOne() {
        // The one that matters most: the leash dragging an NPC home out of the middle of a fight
        // is exactly what made combat look broken.
        NpcMovementLedger ledger = new NpcMovementLedger();
        assertTrue(ledger.claim(Claim.COMBAT, T));
        assertFalse(ledger.claim(Claim.LEASH, T));
        assertFalse(ledger.claim(Claim.PATROL, T));
        assertEquals(Claim.COMBAT, ledger.current(T));
    }

    @Test
    void aHigherPriorityTakesOverImmediately() {
        NpcMovementLedger ledger = new NpcMovementLedger();
        assertTrue(ledger.claim(Claim.LEASH, T));
        assertTrue(ledger.claim(Claim.COMBAT, T));
        assertEquals(Claim.COMBAT, ledger.current(T));
    }

    @Test
    void aSceneOutranksEverything() {
        for (Claim other : Claim.values()) {
            NpcMovementLedger ledger = new NpcMovementLedger();
            assertTrue(ledger.claim(other, T));
            assertTrue(ledger.claim(Claim.SCENE, T), "scene lost to " + other);
        }
    }

    @Test
    void theHolderKeepsAskingAndKeepsIt() {
        NpcMovementLedger ledger = new NpcMovementLedger();
        assertTrue(ledger.claim(Claim.PATROL, T));
        assertTrue(ledger.claim(Claim.PATROL, T + 50));
        assertEquals(Claim.PATROL, ledger.current(T + 50));
    }

    @Test
    void theOrderOfTheEnumIsThePriority() {
        // Reordering these constants changes which system wins a fight over an NPC, so the order is
        // behaviour rather than presentation.
        assertTrue(Claim.SCENE.ordinal() < Claim.COMBAT.ordinal());
        assertTrue(Claim.COMBAT.ordinal() < Claim.FOLLOW.ordinal());
        assertTrue(Claim.FOLLOW.ordinal() < Claim.PATROL.ordinal());
        assertTrue(Claim.COMBAT.ordinal() < Claim.PATROL.ordinal());
        assertTrue(Claim.PATROL.ordinal() < Claim.LEASH.ordinal());
        assertTrue(Claim.LEASH.ordinal() < Claim.STROLL.ordinal());
    }

    // ------------------------------------------------------------ release and expiry

    @Test
    void releasingFreesTheNpc() {
        NpcMovementLedger ledger = new NpcMovementLedger();
        ledger.claim(Claim.COMBAT, T);
        ledger.release(Claim.COMBAT);
        assertNull(ledger.current(T));
        assertTrue(ledger.claim(Claim.LEASH, T));
    }

    @Test
    void aReleaseFromSomebodyWhoDoesNotHoldItIsIgnored() {
        // A system that finishes long after being outranked must not free the claim out from under
        // whoever took it - that would hand the NPC back to the loser of the argument.
        NpcMovementLedger ledger = new NpcMovementLedger();
        ledger.claim(Claim.LEASH, T);
        ledger.claim(Claim.COMBAT, T);
        ledger.release(Claim.LEASH);
        assertEquals(Claim.COMBAT, ledger.current(T));
    }

    @Test
    void anAbandonedClaimExpiresRatherThanHoldingTheNpcForever() {
        // A scene deleted mid-playback, or a brain that stopped running, would otherwise leave an
        // NPC nothing could ever steer again.
        NpcMovementLedger ledger = new NpcMovementLedger();
        ledger.claim(Claim.SCENE, T);
        long after = T + NpcMovementLedger.CLAIM_TIMEOUT_TICKS;
        assertNull(ledger.current(after));
        assertTrue(ledger.claim(Claim.STROLL, after));
    }

    @Test
    void aClaimIsStillLiveOneTickBeforeItExpires() {
        NpcMovementLedger ledger = new NpcMovementLedger();
        ledger.claim(Claim.SCENE, T);
        long justBefore = T + NpcMovementLedger.CLAIM_TIMEOUT_TICKS - 1;
        assertEquals(Claim.SCENE, ledger.current(justBefore));
        assertFalse(ledger.claim(Claim.LEASH, justBefore));
    }

    // ------------------------------------------------------------ getting nowhere

    @Test
    void anNpcThatIsMovingKeepsItsPermissionToTry() {
        NpcMovementLedger ledger = new NpcMovementLedger();
        assertTrue(ledger.progressing(0, 64, 0));
        for (int step = 1; step < 10; step++) {
            assertTrue(ledger.progressing(step, 64, 0), "step " + step);
        }
        assertFalse(ledger.stuck());
    }

    @Test
    void anNpcPinnedInPlaceIsEventuallyToldToStopTrying() {
        // The wedged case: moveTo(home) was re-issued every twenty ticks forever and the NPC
        // lurched at the obstacle each time.
        NpcMovementLedger ledger = new NpcMovementLedger();
        assertTrue(ledger.progressing(10, 64, 10));
        for (int strike = 1; strike < NpcMovementLedger.STUCK_STRIKES; strike++) {
            assertTrue(ledger.progressing(10, 64, 10), "strike " + strike);
        }
        assertFalse(ledger.progressing(10, 64, 10));
        assertTrue(ledger.stuck());
    }

    @Test
    void shufflingBackAndForthAgainstAWallIsNotProgress() {
        // Measuring each check against the previous one would let a few centimetres of jitter read
        // as progress forever, which is the exact motion this exists to catch. The remembered
        // position stays put until a real move happens.
        NpcMovementLedger ledger = new NpcMovementLedger();
        ledger.progressing(10.0, 64, 10.0);
        ledger.progressing(10.2, 64, 10.0);
        ledger.progressing(10.0, 64, 10.0);
        assertFalse(ledger.progressing(10.2, 64, 10.0));
        assertTrue(ledger.stuck());
    }

    @Test
    void oneRealStepClearsTheStrikes() {
        NpcMovementLedger ledger = new NpcMovementLedger();
        ledger.progressing(0, 64, 0);
        ledger.progressing(0, 64, 0);
        assertTrue(ledger.progressing(5, 64, 0));
        assertFalse(ledger.stuck());
    }

    @Test
    void aNewDestinationForgetsThatItWasStuck() {
        // Without this an NPC wedged once would refuse to walk for the rest of its life.
        NpcMovementLedger ledger = new NpcMovementLedger();
        for (int i = 0; i <= NpcMovementLedger.STUCK_STRIKES; i++) {
            ledger.progressing(1, 64, 1);
        }
        assertTrue(ledger.stuck());
        ledger.clearProgress();
        assertFalse(ledger.stuck());
        assertTrue(ledger.progressing(1, 64, 1));
    }

    @Test
    void handingTheNpcToAnotherSystemForgetsTheOldProgress() {
        // The old record was measuring movement toward somewhere else entirely, so carrying it over
        // would declare the new mover stuck before it had tried anything.
        NpcMovementLedger ledger = new NpcMovementLedger();
        ledger.claim(Claim.LEASH, T);
        for (int i = 0; i <= NpcMovementLedger.STUCK_STRIKES; i++) {
            ledger.progressing(1, 64, 1);
        }
        assertTrue(ledger.stuck());
        ledger.claim(Claim.COMBAT, T);
        assertFalse(ledger.stuck());
    }

    @Test
    void anNpcThatHasNeverBeenCheckedIsNotStuck() {
        assertFalse(new NpcMovementLedger().stuck());
    }
}
