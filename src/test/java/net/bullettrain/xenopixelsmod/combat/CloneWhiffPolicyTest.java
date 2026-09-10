package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.combat.clone.CloneCombatPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that stops a clone punching air forever.
 *
 * <p>{@code decide} works entirely from positions, so it cannot tell a swing that landed from one
 * that hit nothing. This is the counter that can.
 */
class CloneWhiffPolicyTest {

    @Test
    void aClonePersistsThroughTheOddMissedSwing() {
        // A single failed hit is normal -- the target blocked, or was briefly invulnerable after
        // being hit. Giving up on one would make clones quit constantly.
        assertFalse(CloneCombatPolicy.shouldAbandon(0));
        assertFalse(CloneCombatPolicy.shouldAbandon(1));
    }

    @Test
    void aRunOfSwingsThatLandNothingGivesUp() {
        assertTrue(CloneCombatPolicy.shouldAbandon(CloneCombatPolicy.MAX_WHIFFS));
        assertTrue(CloneCombatPolicy.shouldAbandon(CloneCombatPolicy.MAX_WHIFFS + 1));
        assertTrue(CloneCombatPolicy.shouldAbandon(50));
    }

    @Test
    void theThresholdIsSmallEnoughToBeNoticedQuickly() {
        // The whole point is that a player never watches a clone punch nothing for long. At one
        // swing per 20 ticks this bounds it to a few seconds.
        assertTrue(CloneCombatPolicy.MAX_WHIFFS > 0 && CloneCombatPolicy.MAX_WHIFFS <= 5,
                "MAX_WHIFFS should stay small; was " + CloneCombatPolicy.MAX_WHIFFS);
    }

    @Test
    void aNegativeCountNeverAbandons() {
        // Defensive: a reset that underflows must not read as "give up".
        assertFalse(CloneCombatPolicy.shouldAbandon(-1));
    }
}
