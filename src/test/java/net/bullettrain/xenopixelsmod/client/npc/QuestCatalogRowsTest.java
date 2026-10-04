package net.bullettrain.xenopixelsmod.client.npc;

import net.bullettrain.xenopixelsmod.client.npc.editor.EditorLayout;
import net.bullettrain.xenopixelsmod.client.npc.editor.EditorRow;
import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.bullettrain.xenopixelsmod.features.progression.QuestObjective;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestCatalogRowsTest {

    @Test
    void longQuestIdsStayOnTheirOwnLine() {
        Map<String, ParallelQuests.QuestDef> quests = new LinkedHashMap<>();
        quests.put("mynpcs_the_saiyan_defeat_vegeta", new ParallelQuests.QuestDef(
                "mynpcs_the_saiyan_defeat_vegeta", "Defeat Vegeta (ss)", "", 1,
                new QuestObjective.Goal(QuestObjective.KILL_NPC, "Vegeta"), null));
        quests.put("mynpcs_the_saiyan_beating_vegeta", new ParallelQuests.QuestDef(
                "mynpcs_the_saiyan_beating_vegeta", "Beating Vegeta", "", 1,
                new QuestObjective.Goal(QuestObjective.KILL_NPC, "Vegeta"), null));

        List<EditorRow> rows = XenoNpcEditorScreen.questCatalogRows(quests);
        for (EditorRow row : rows) {
            assertTrue(row.fullWidth(), "quest catalog rows share a line when they are half width");
            if (row instanceof EditorRow.Text text) {
                assertTrue(text.label().isBlank());
            }
        }

        List<EditorLayout.Placed> placed = new EditorLayout(18, 54, 382, 228, 24, 6)
                .place(rows).get(0).rows();
        for (int i = 1; i < placed.size(); i++) {
            assertTrue(placed.get(i).y() > placed.get(i - 1).y());
            assertEquals(382, placed.get(i).columnWidth());
        }
    }
}
