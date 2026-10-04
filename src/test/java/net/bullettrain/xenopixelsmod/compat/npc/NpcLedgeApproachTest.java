package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 2026-09-30 owner: "he still punches the air" with the NPC on a ledge above the player, and "the
 * npc check if it can continue foward or foward and deacend down needs to let it be on the player".
 */
class NpcLedgeApproachTest {

    @Test
    void inReachItDoesNothingNewTheSwingHandlesIt() {
        assertEquals(NpcLedgeApproach.Move.NONE, NpcLedgeApproach.decide(true, true, -3.0, 1.0, true));
    }

    @Test
    void aPathToThePlayerIsWalked() {
        assertEquals(NpcLedgeApproach.Move.NAVIGATE, NpcLedgeApproach.decide(false, true, -3.0, 1.0, false));
        assertEquals(NpcLedgeApproach.Move.NAVIGATE, NpcLedgeApproach.decide(false, true, 0.0, 3.0, false));
    }

    @Test
    void withNoPathAndThePlayerBelowPastAnEdgeItStepsOff() {
        assertEquals(NpcLedgeApproach.Move.STEP_OFF, NpcLedgeApproach.decide(false, false, -3.0, 1.0, true));
        assertEquals(NpcLedgeApproach.Move.STEP_OFF, NpcLedgeApproach.decide(false, false, -6.0, 0.0, true),
                "straight below counts too: it steps forward off the edge");
    }

    @Test
    void itNeverStepsOffTowardsAPlayerAboveOrLevelOrWithSolidGroundAhead() {
        assertEquals(NpcLedgeApproach.Move.NONE, NpcLedgeApproach.decide(false, false, 3.0, 1.0, true),
                "a player above is for flight or ki, not a jump off something");
        assertEquals(NpcLedgeApproach.Move.NONE, NpcLedgeApproach.decide(false, false, -0.2, 1.0, true));
        assertEquals(NpcLedgeApproach.Move.NONE, NpcLedgeApproach.decide(false, false, -3.0, 1.0, false),
                "no drop ahead: walking forward would not bring it down");
    }

    /**
     * 2026-09-30 owner: standing on the same spot "its flickinring when moving from left to right a
     * bit". Straight above or below, the path is a single node: walking it only re-centres the NPC on
     * its block, and a fresh path every tick sent it back and forth. Walking cannot close a height gap.
     */
    @Test
    void inTheSameColumnItDoesNotWalkAPathBackAndForth() {
        assertEquals(NpcLedgeApproach.Move.NONE, NpcLedgeApproach.decide(false, true, -3.0, 0.4, false));
        assertEquals(NpcLedgeApproach.Move.NONE, NpcLedgeApproach.decide(false, true, 3.0, 0.0, false));
        assertEquals(NpcLedgeApproach.Move.STEP_OFF, NpcLedgeApproach.decide(false, true, -3.0, 0.4, true),
                "on a floor over the player it still steps off the edge");
    }

    @Test
    void itDoesNotLeapOffAtAPlayerFarAway() {
        assertEquals(NpcLedgeApproach.Move.NONE,
                NpcLedgeApproach.decide(false, false, -3.0, NpcLedgeApproach.MAX_STEP_HORIZONTAL + 1.0, true));
    }

    @Test
    void closeHorizontalTargetsOnTheSameFloorStillHaveAWalkablePath() {
        assertEquals(NpcLedgeApproach.Move.NAVIGATE,
                NpcLedgeApproach.decide(false, true, 0.0, 0.4, false));
        assertEquals(NpcLedgeApproach.Move.NAVIGATE,
                NpcLedgeApproach.decide(false, true, -3.0, 0.6, false));
    }

    @Test
    void tinyHorizontalOffsetsDoNotFlipFacingInThePlayersColumn() {
        assertEquals(37.0f, NpcBrainKiRotation.targetYaw(0.0, 0.0, 37.0f));
        assertEquals(37.0f, NpcBrainKiRotation.targetYaw(0.01, -0.01, 37.0f));
        assertEquals(37.0f, NpcBrainKiRotation.targetYaw(-0.01, 0.01, 37.0f));
        assertEquals(NpcBrainKiRotation.targetYaw(1.0, 0.0),
                NpcBrainKiRotation.targetYaw(1.0, 0.0, 37.0f));
    }
}
