package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.compat.npc.brain.v2.NpcSagaCombatContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The distance rule behind "the NPC attacks the air".
 *
 * <p>Nothing on the melee path measured distance, and the two brains disagreed about where melee
 * even ended - {@code NpcCombatBrain} used 4.0/32.0 while the v2 brain used DragonMineZ's own
 * 4.5/12.0/28.0. These tests pin the shared numbers and the band each distance falls in.
 *
 * <p>Only the pure arithmetic is covered here. {@code meleeReach} and {@code withinMelee} take live
 * entities, which cannot be constructed without a running server, so those are checked in game -
 * the same split {@code NpcDisplayApplySizeTest} makes.
 */
class NpcCombatRangesTest {

    @Test
    void theConstantsAreDragonMineZsOwn() {
        // Read from the decompiled SagasCombatBrain: MELEE_RANGE 4.5, MID_RANGE 12.0,
        // OUT_RANGE 28.0, and CombatContext.APPROACH_THRESHOLD 0.05. If DMZ ever changes them this
        // test is the reminder to re-read the jar rather than guess.
        assertEquals(4.5, NpcCombatRanges.MELEE, 1.0e-9);
        assertEquals(12.0, NpcCombatRanges.MID, 1.0e-9);
        assertEquals(28.0, NpcCombatRanges.OUT, 1.0e-9);
        assertEquals(0.05, NpcCombatRanges.APPROACH_THRESHOLD, 1.0e-9);
    }

    @Test
    void bothBrainsReadTheSameNumbers() {
        // The v2 context used to declare its own copies. Sharing them is what stops the default
        // brain and the saga brain drifting apart again.
        assertEquals(NpcCombatRanges.MELEE, NpcSagaCombatContext.MELEE, 1.0e-9);
        assertEquals(NpcCombatRanges.MID, NpcSagaCombatContext.MID, 1.0e-9);
        assertEquals(NpcCombatRanges.OUT, NpcSagaCombatContext.OUT, 1.0e-9);
        assertEquals(NpcCombatRanges.APPROACH_THRESHOLD,
                NpcSagaCombatContext.APPROACH_THRESHOLD, 1.0e-9);
    }

    @Test
    void theDefaultBrainsBandsAreNowTheSharedOnes() {
        // NpcCombatBrain.MELEE_BAND was 4.0 and KI_BAND was 32.0 - neither of them DMZ's.
        assertEquals(NpcCombatRanges.MELEE, NpcCombatBrain.MELEE_BAND, 1.0e-9);
        assertEquals(NpcCombatRanges.MID, NpcCombatBrain.MID_BAND, 1.0e-9);
        assertEquals(NpcCombatRanges.OUT, NpcCombatBrain.KI_BAND, 1.0e-9);
    }

    @Test
    void eachDistanceFallsInTheBandItShould() {
        assertSame(NpcCombatRanges.Band.MELEE, NpcCombatRanges.bandOf(0.0));
        assertSame(NpcCombatRanges.Band.MELEE, NpcCombatRanges.bandOf(3.0));
        assertSame(NpcCombatRanges.Band.MID, NpcCombatRanges.bandOf(8.0));
        assertSame(NpcCombatRanges.Band.OUT, NpcCombatRanges.bandOf(20.0));
        assertSame(NpcCombatRanges.Band.BEYOND, NpcCombatRanges.bandOf(40.0));
    }

    @Test
    void theBoundariesBelongToTheNearerBand() {
        // Exactly at a boundary is still inside it, so there is no distance that belongs to no band.
        assertSame(NpcCombatRanges.Band.MELEE, NpcCombatRanges.bandOf(NpcCombatRanges.MELEE));
        assertSame(NpcCombatRanges.Band.MID, NpcCombatRanges.bandOf(NpcCombatRanges.MID));
        assertSame(NpcCombatRanges.Band.OUT, NpcCombatRanges.bandOf(NpcCombatRanges.OUT));

        // And a hair past each one has moved on.
        assertSame(NpcCombatRanges.Band.MID, NpcCombatRanges.bandOf(NpcCombatRanges.MELEE + 1.0e-6));
        assertSame(NpcCombatRanges.Band.OUT, NpcCombatRanges.bandOf(NpcCombatRanges.MID + 1.0e-6));
        assertSame(NpcCombatRanges.Band.BEYOND,
                NpcCombatRanges.bandOf(NpcCombatRanges.OUT + 1.0e-6));
    }

    @Test
    void theOldThirtyTwoBlockKiBandIsNowOutOfRange() {
        // The specific regression: at 30 blocks the default brain used to still be firing ki.
        // DMZ stops at 28, and past that an NPC closes the distance instead.
        assertSame(NpcCombatRanges.Band.BEYOND, NpcCombatRanges.bandOf(30.0));
    }

    @Test
    void everyBandIsReachableSoNoneIsDeadCode() {
        for (NpcCombatRanges.Band band : NpcCombatRanges.Band.values()) {
            boolean seen = false;
            for (double d = 0.0; d <= 60.0; d += 0.25) {
                if (NpcCombatRanges.bandOf(d) == band) {
                    seen = true;
                    break;
                }
            }
            assertEquals(true, seen, band + " is never chosen at any distance");
        }
    }

    @Test
    void meleeReachFallsBackToTheConfiguredAttackStartRadius() {
        // The arithmetic behind meleeReach, split out because it takes live entities in game.
        // The /xenoset npcAttackStartRadius default is 1.0: an NPC starts attacking only when it
        // is essentially touching its target, not from DragonMineZ's 4.5-block band.
        assertEquals(1.0, NpcCombatRanges.resolveReach(0.0, 1.0, 0.0), 1.0e-9);
        // Standard 0.6 + 0.6 hitboxes floor it at touching distance, centre to centre.
        assertEquals(1.2, NpcCombatRanges.resolveReach(0.0, 1.0, 1.2), 1.0e-9);
        // A per-NPC "Melee Range" above zero still wins over the global default.
        assertEquals(3.0, NpcCombatRanges.resolveReach(3.0, 1.0, 1.2), 1.0e-9);
        // Setting the key to 4.5 restores the old shared band for everyone.
        assertEquals(4.5, NpcCombatRanges.resolveReach(0.0, 4.5, 1.2), 1.0e-9);
        // And a huge NPC is never gated out of touching what it stands on.
        assertEquals(6.0, NpcCombatRanges.resolveReach(1.0, 1.0, 6.0), 1.0e-9);
    }
}
