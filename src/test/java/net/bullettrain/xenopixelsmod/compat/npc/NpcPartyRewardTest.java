package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcPartyRewardTest {
    @Test
    void shareOffPaysOnlyTheCompleter() {
        // Null trigger is empty; share-off with a non-null trigger is a singleton (no server).
        assertTrue(NpcPartyReward.select(null, true, java.util.List.of()).isEmpty());
    }

    @Test
    void dialogTokenCommandsArePartyRewards() {
        assertTrue(NpcPartyReward.isDialogReward("xenopoints add 5000 @dp"));
        assertTrue(NpcPartyReward.isDialogReward("give {RefPlayer} diamond"));
        assertTrue(NpcPartyReward.isDialogReward("dmzpoints add 1 @p2"));
        assertFalse(NpcPartyReward.isDialogReward("say hello"));
        assertFalse(NpcPartyReward.isDialogReward(null));
    }
}
