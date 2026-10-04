package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.client.npc.quest.QuestLogLayout;
import net.bullettrain.xenopixelsmod.npc.importer.NpcImportReport;
import net.bullettrain.xenopixelsmod.npc.importer.QuestImport;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestFeatureTest {

    @Test
    void aWideLabelIsShortenedBeforeDraw() {
        String clipped = QuestLogLayout.clip("mynpcs_the_saiyan_defeat_nappa", 40, text -> text.length() * 6);
        assertTrue(clipped.endsWith("..."));
        assertTrue(clipped.length() < "mynpcs_the_saiyan_defeat_nappa".length());
    }

    @Test
    void importKeepsTheRewardCommandAndBothKillTargets() {
        CompoundTag source = new CompoundTag();
        source.putInt("Type", 2);
        source.putString("Title", "Defeat them");
        source.putString("QuestCommand", "say @dp won");
        source.putString("CompleteText", "You did it.");
        ListTag targets = new ListTag();
        for (String name : new String[]{"Raditz", "Nappa"}) {
            CompoundTag target = new CompoundTag();
            target.putString("Slot", name);
            target.putInt("Value", 1);
            targets.add(target);
        }
        source.put("QuestDialogs", targets);

        QuestImport.Imported imported = QuestImport.convert(1, "quest_two", "mynpcs", source,
                new NpcImportReport());
        assertEquals(2, imported.quest().steps().size());
        assertEquals("say {player} won", imported.quest().reward().commands().get(0));
        var parsed = XenoQuests.parse(imported.id(),
                JsonParser.parseString(QuestImport.toJson(imported.quest())));
        assertEquals(2, parsed.steps().size());
        assertEquals("say {player} won", parsed.reward().commands().get(0));
    }

    @Test
    void anUnknownObjectiveIsRejected() {
        boolean thrown = false;
        try {
            XenoQuests.parse("bad", JsonParser.parseString("{\"objective\":\"fly\"}"));
        } catch (IllegalArgumentException exception) {
            thrown = exception.getMessage().contains("unknown objective");
        }
        assertTrue(thrown);
    }

    @Test
    void questCompletionCardStyleAndRepeatRuleLoadFromDefinition() {
        ParallelQuests.QuestDef quest = XenoQuests.parse("styled_quest", JsonParser.parseString("""
                {"title":"A long quest title that must remain intact", "objective":"kill_mobs",
                 "target":1, "repeat":"RLWEEKLY", "completion_palette":"gold",
                 "completion_frame":"banner", "complete_text":"You made it."}
                """));
        assertEquals("RLWEEKLY", quest.repeat().name());
        assertEquals("gold", quest.completionPalette());
        assertEquals("banner", quest.completionFrame());
        assertEquals("You made it.", quest.completeText());
        assertEquals("A long quest title that must remain intact", quest.title());
    }

    @Test
    void aDialogCheckOtherThanAlwaysFailsClosed() {
        QuestAvailability gate = QuestAvailability.fromJson(JsonParser.parseString(
                "{\"dialogs\":[{\"id\":\"hello\",\"state\":\"after\"}]}"));
        assertFalse(QuestRuntime.available(null, null, gate));
    }

    @Test
    void manualProgressFillsOnlyAManualStep() {
        ActiveQuest quest = new ActiveQuest("manual_one", 2);
        ParallelQuests.QUESTS.put("manual_one", new ParallelQuests.QuestDef(
                "manual_one", "Manual", "", 2,
                new QuestObjective.Goal(QuestObjective.MANUAL, ""), null));
        try {
            assertTrue(QuestRuntime.onManual(quest, 1));
            assertEquals(1, quest.stepProgress(0));
            assertTrue(QuestRuntime.onManual(quest, 1));
            assertEquals(2, quest.stepProgress(0));
        } finally {
            ParallelQuests.QUESTS.remove("manual_one");
        }
    }
}
