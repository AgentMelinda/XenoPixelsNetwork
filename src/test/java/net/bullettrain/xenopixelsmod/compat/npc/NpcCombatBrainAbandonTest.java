package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.combat.clone.CloneCombatPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The combat brain's own version of the rule that stops an NPC punching air.
 *
 * <p>The brain's melee fallback used to throw away the result of {@code NpcMeleeDamage.hit}, so an
 * NPC swinging at something it could never damage kept swinging once per decision indefinitely. The
 * multi-form clone AI has had a whiff rule since it shipped; the brain now reuses the same one, and
 * these tests pin the two pieces of that which are checkable without a running server: the shared
 * threshold, and the window an NPC sits out after giving up.
 */
class NpcCombatBrainAbandonTest {

    /**
     * Both AI modes give up after the same run of swings that hit nothing.
     *
     * <p>Sharing {@link CloneCombatPolicy} rather than restating the numbers is the point: a player
     * switching {@code /xenomultiform ai} between {@code clone} and {@code brain} should not find
     * that one of them abandons a hopeless fight and the other does not.
     */
    @Test
    void theBrainGivesUpOnTheSameStreakTheCloneAiDoes() {
        int streak = 0;
        for (int swing = 0; swing < CloneCombatPolicy.MAX_WHIFFS; swing++) {
            assertFalse(CloneCombatPolicy.shouldAbandon(streak),
                    "gave up after only " + swing + " swings that hit nothing");
            streak = CloneCombatPolicy.noteSwing(streak, false);
        }
        assertTrue(CloneCombatPolicy.shouldAbandon(streak));
    }

    @Test
    void oneLandedHitClearsTheStreak() {
        int streak = CloneCombatPolicy.noteSwing(CloneCombatPolicy.noteSwing(0, false), false);
        assertTrue(streak > 0);
        assertFalse(CloneCombatPolicy.shouldAbandon(CloneCombatPolicy.noteSwing(streak, true)),
                "a connected hit has to reset the streak, or a long fight eventually abandons itself");
    }

    @Test
    void anNpcSitsOutTheWholeWindowAndThenFightsAgain() {
        int until = 1_000;
        int window = 60;
        assertTrue(NpcCombatBrain.withinAbandonWindow(until - window, until, window),
                "the window should start the moment it is set");
        assertTrue(NpcCombatBrain.withinAbandonWindow(until - 1, until, window));
        assertFalse(NpcCombatBrain.withinAbandonWindow(until, until, window),
                "the NPC must be able to fight again the tick the window ends");
        assertFalse(NpcCombatBrain.withinAbandonWindow(until + 500, until, window));
    }

    /**
     * A deadline left over from before a server restart does not mute an NPC.
     *
     * <p>Server tick counts restart at zero, so a deadline written at tick 400,000 would otherwise
     * keep the NPC out of combat for the hours it takes the counter to get back there.
     */
    @Test
    void aDeadlineFromBeforeARestartIsTreatedAsStale() {
        assertFalse(NpcCombatBrain.withinAbandonWindow(5, 400_000, 60),
                "a fresh server tick must not be read as sitting inside an old abandon window");
        assertTrue(NpcCombatBrain.withinAbandonWindow(0, 60, 60),
                "a genuine window set at tick 0 still has to hold");
    }

    @Test
    void aZeroWindowStillExpiresRatherThanDividingTheWorldByZero() {
        assertFalse(NpcCombatBrain.withinAbandonWindow(10, 10, 0));
        assertTrue(NpcCombatBrain.withinAbandonWindow(9, 10, 0));
    }
}
