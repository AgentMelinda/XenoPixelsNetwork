package net.bullettrain.xenopixelsmod.features.progression;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-09-30 owner: "Kill Target Hunts Plater makes the quest giver npc attack me". Hunters are
 * picked by visible name, and NPCs commonly share one ("Humanoid"), so the quest giver matched its
 * own kill target and was turned on the player it had just given the quest to.
 */
class QuestHuntGiverTest {
    private static final UUID GIVER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void theQuestGiverNeverHuntsItsOwnClaimant() {
        assertFalse(QuestHunts.isHunter(GIVER, true, Set.of(GIVER)),
                "sharing the kill target's name does not make the giver the target");
    }

    @Test
    void anotherNpcWithTheTargetNameStillHunts() {
        assertTrue(QuestHunts.isHunter(TARGET, true, Set.of(GIVER)));
        assertFalse(QuestHunts.isHunter(TARGET, false, Set.of(GIVER)));
        assertTrue(QuestHunts.isHunter(TARGET, true, Set.of()), "a quest with no recorded giver");
    }
}
