package net.bullettrain.xenopixelsmod.features.progression;

import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.npc.importer.QuestImport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** todolist #14: a KILL_NPC quest may make its target hunt the claimant. */
class QuestTargetHuntsTest {

    private static ParallelQuests.QuestDef parse(String json) {
        return XenoQuests.parse("hunt", JsonParser.parseString(json));
    }

    @Test
    void theOptionNamesTheKillNpcTarget() {
        ParallelQuests.QuestDef def = parse("""
                { "title": "Defeat Vegeta", "objective": "kill_npc", "parameter": "Vegeta",
                  "target": 1, "target_hunts": true }
                """);
        assertTrue(def.targetHunts());
        // Goal parameters are stored lower-case; QuestNpcTarget.matches compares ignoring case.
        assertEquals(List.of("vegeta"), def.huntedNpcNames());
    }

    @Test
    void offByDefaultAndNeverForOtherObjectives() {
        assertTrue(parse("""
                { "objective": "kill_npc", "parameter": "Vegeta", "target": 1 }
                """).huntedNpcNames().isEmpty(), "absent means off: old quests keep behaving");
        assertTrue(parse("""
                { "objective": "kill_mobs", "target": 3, "target_hunts": true }
                """).huntedNpcNames().isEmpty(), "only a named NPC can hunt");
    }

    @Test
    void theStoreRoundTripKeepsTheOption() {
        ParallelQuests.QuestDef def = parse("""
                { "objective": "kill_npc", "parameter": "Vegeta", "target": 1, "target_hunts": true }
                """);
        ParallelQuests.QuestDef reread = parse(QuestImport.toJson(def));
        assertTrue(reread.targetHunts());
        assertFalse(parse(QuestImport.toJson(parse("""
                { "objective": "kill_npc", "parameter": "Vegeta", "target": 1 }
                """))).targetHunts());
    }
}
