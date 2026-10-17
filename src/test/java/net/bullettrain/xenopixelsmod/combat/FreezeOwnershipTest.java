package net.bullettrain.xenopixelsmod.combat;

import static org.junit.jupiter.api.Assertions.*;

import net.bullettrain.xenopixelsmod.combat.FreezeOwnership.Hold;
import net.bullettrain.xenopixelsmod.combat.FreezeOwnership.Reason;
import org.junit.jupiter.api.Test;

/**
 * The freeze lease on its own, with no Minecraft bootstrap — the same approach
 * {@code V3MotionLeaseTest} takes for the movement lease.
 *
 * <p>Each case here is a way an NPC used to end up permanently frozen: {@code NoAI} is written into
 * the entity's save data by vanilla, so any restore that did not run left the NPC immobile through
 * every subsequent reload.
 */
class FreezeOwnershipTest {

    @Test void firstHolderCapturesTheRealPreFreezeValueAndLaterHoldersDoNotResnapshot() {
        Hold hold = new Hold();
        // An awake NPC: the value that must come back at the end is false.
        assertTrue(hold.acquire(Reason.TECHNIQUE, false), "first acquire must apply the freeze");
        // A cinematic piles on while the flag is already set. Re-capturing now would record the
        // frozen state as the thing to restore, which is exactly how the flag used to get stuck.
        assertFalse(hold.acquire(Reason.CINEMATIC, true), "second acquire must not re-apply");
        assertFalse(hold.noAiBefore(), "the captured value must still be the original");
    }

    @Test void releaseWhileAnotherReasonStillHoldsRestoresNothing() {
        Hold hold = new Hold();
        hold.acquire(Reason.TECHNIQUE, false);
        hold.acquire(Reason.CINEMATIC, false);
        assertNull(hold.release(Reason.TECHNIQUE), "a remaining holder must keep the freeze");
        assertTrue(hold.held());
        assertTrue(hold.heldBy(Reason.CINEMATIC));
        assertFalse(hold.heldBy(Reason.TECHNIQUE));
    }

    @Test void lastHolderOutRestoresTheCapturedValueExactlyOnce() {
        Hold hold = new Hold();
        hold.acquire(Reason.TECHNIQUE, false);
        hold.acquire(Reason.CINEMATIC, false);
        assertNull(hold.release(Reason.TECHNIQUE));
        assertEquals(Boolean.FALSE, hold.release(Reason.CINEMATIC));
        assertFalse(hold.held());
        // Idempotent: a teardown path that runs twice must not restore a second, bogus value.
        assertNull(hold.release(Reason.CINEMATIC));
    }

    @Test void releasingAReasonThatWasNeverHeldIsANoOp() {
        Hold hold = new Hold();
        assertNull(hold.release(Reason.FINISHER));
        hold.acquire(Reason.TECHNIQUE, false);
        assertNull(hold.release(Reason.FINISHER), "an unrelated release must not drop the freeze");
        assertTrue(hold.held());
    }

    /**
     * The reported symptom: a rush or ultimate finishes, and the NPC it held stays frozen. The restore
     * must not depend on any other bookkeeping still matching — it used to be gated on a two-argument
     * map remove that silently failed once the victim entry had been taken over or dropped.
     */
    @Test void finisherAndTechniqueEachRestoreIndependentlyOfAnyExternalMapping() {
        Hold finisherVictim = new Hold();
        finisherVictim.acquire(Reason.FINISHER, false);
        assertEquals(Boolean.FALSE, finisherVictim.release(Reason.FINISHER));

        Hold rushVictim = new Hold();
        rushVictim.acquire(Reason.TECHNIQUE, false);
        assertEquals(Boolean.FALSE, rushVictim.release(Reason.TECHNIQUE));
        assertFalse(rushVictim.held());
    }

    @Test void anAlreadyFrozenNpcGoesBackToFrozenNotAwake() {
        Hold hold = new Hold();
        // An editor-placed or operator-parked NPC is NoAI before combat touches it. Waking it would
        // be a different bug, so the captured value has to be true and come back true.
        assertTrue(hold.acquire(Reason.CINEMATIC, true));
        assertTrue(hold.noAiBefore());
        assertEquals(Boolean.TRUE, hold.release(Reason.CINEMATIC));
    }

    @Test void releaseAllRestoresRegardlessOfHowManyReasonsWereHeld() {
        Hold hold = new Hold();
        hold.acquire(Reason.TECHNIQUE, false);
        hold.acquire(Reason.CINEMATIC, false);
        hold.acquire(Reason.FINISHER, false);
        assertEquals(Boolean.FALSE, hold.releaseAll());
        assertFalse(hold.held());
        assertEquals(0, hold.reasons().size());
        assertNull(hold.releaseAll(), "a second teardown must not invent a restore");
    }

    /**
     * {@code XenoNpcEntity#aiStep} borrows and returns the flag every tick. Holding is set membership
     * rather than a refcount precisely so this cannot drift: a missed release must not leave a count
     * that no later release can reach zero.
     */
    @Test void perTickRecoveryBorrowStaysBalancedAcrossManyTicks() {
        Hold hold = new Hold();
        for (int tick = 0; tick < 1000; tick++) {
            hold.acquire(Reason.RECOVERY, false);
            assertEquals(Boolean.FALSE, hold.release(Reason.RECOVERY));
        }
        assertFalse(hold.held());

        // A missed release, then the next tick's release: still returns to the captured value.
        hold.acquire(Reason.RECOVERY, false);
        hold.acquire(Reason.RECOVERY, false);
        assertEquals(Boolean.FALSE, hold.release(Reason.RECOVERY));
        assertFalse(hold.held());
    }

    @Test void aRecoveryBorrowCannotWakeAnNpcAnotherSystemIsHolding() {
        Hold hold = new Hold();
        hold.acquire(Reason.TECHNIQUE, false);
        // aiStep borrows on top of a live technique hold, then gives the borrow back.
        assertFalse(hold.acquire(Reason.RECOVERY, true));
        assertNull(hold.release(Reason.RECOVERY), "the technique still holds, so nothing restores");
        assertTrue(hold.heldBy(Reason.TECHNIQUE));
        assertEquals(Boolean.FALSE, hold.release(Reason.TECHNIQUE));
    }

    @Test void reasonsAreReportedForDiagnostics() {
        Hold hold = new Hold();
        assertEquals(0, hold.reasons().size());
        hold.acquire(Reason.TECHNIQUE, false);
        hold.acquire(Reason.CINEMATIC, false);
        assertEquals(2, hold.reasons().size());
        assertTrue(hold.reasons().contains(Reason.TECHNIQUE));
        assertTrue(hold.reasons().contains(Reason.CINEMATIC));
    }

    @Test void nullReasonIsRefusedRatherThanHalfApplied() {
        Hold hold = new Hold();
        assertFalse(hold.acquire(null, false));
        assertFalse(hold.held());
        assertNull(hold.release(null));
        assertFalse(hold.heldBy(null));
    }
}
