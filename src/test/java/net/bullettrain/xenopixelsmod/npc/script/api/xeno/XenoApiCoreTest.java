package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.bullettrain.xenopixelsmod.features.progression.QuestAvailability;
import net.bullettrain.xenopixelsmod.features.progression.QuestObjective;
import net.bullettrain.xenopixelsmod.features.progression.QuestReward;
import net.bullettrain.xenopixelsmod.features.progression.QuestRuntime;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Scoreboard;
import org.junit.jupiter.api.Test;
import xenoapi.npcs.api.IScoreboard;
import xenoapi.npcs.api.constants.OptionType;
import xenoapi.npcs.api.constants.PotionEffectType;
import xenoapi.npcs.api.constants.QuestType;
import xenoapi.npcs.api.handler.data.IAvailability;
import xenoapi.npcs.api.handler.data.IDialogOption;
import xenoapi.npcs.api.handler.data.IQuest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The core CustomNPCs calls that now have native backing, exercised without a world. */
class XenoApiCoreTest {
    private final NativeNpcApi offline = new NativeNpcApi(() -> null);

    // ------------------------------------------------------------------ numbers

    @Test
    void importedEntriesCarryTheirCustomNpcsNumber() {
        CompoundTag tagged = new CompoundTag();
        tagged.putString("SourceMod", "customnpcs");
        tagged.putInt("SourceSlot", 7);
        assertEquals(7, XenoScriptIds.slotOf(XenoNpcStoreCategory.FACTIONS, "", "guards", tagged));

        CompoundTag foreignOther = new CompoundTag();
        foreignOther.putString("SourceMod", "somethingelse");
        foreignOther.putInt("SourceSlot", 7);
        assertEquals(XenoScriptIds.NONE, XenoScriptIds.slotOf(XenoNpcStoreCategory.FACTIONS, "", "guards", foreignOther));

        assertEquals(12, XenoScriptIds.slotOf(XenoNpcStoreCategory.QUESTS, "mynpcs_main", "mynpcs_main_q12", new CompoundTag()));
        assertEquals(3, XenoScriptIds.slotOf(XenoNpcStoreCategory.DIALOGS, "mynpcs_intro", "dialog_3", new CompoundTag()));
    }

    @Test
    void xenoMadeContentHasNoNumber() {
        assertEquals(XenoScriptIds.NONE, XenoScriptIds.slotOf(XenoNpcStoreCategory.FACTIONS, "", "guards", new CompoundTag()));
        assertEquals(XenoScriptIds.NONE, XenoScriptIds.slotOf(XenoNpcStoreCategory.DIALOGS, "npc", "dialog_3", new CompoundTag()));
        assertEquals(XenoScriptIds.NONE, XenoScriptIds.slotOf(XenoNpcStoreCategory.QUESTS, "main", "kill_mobs", new CompoundTag()));
    }

    @Test
    void anEditorSaveKeepsTheImportedNumber() {
        CompoundTag previous = new CompoundTag();
        previous.putString("SourceMod", "mynpcs");
        previous.putInt("SourceSlot", 4);
        CompoundTag replacement = new CompoundTag();
        replacement.putString("Name", "Guards");
        XenoScriptIds.carrySourceSlot(previous, replacement);
        assertEquals(4, replacement.getInt("SourceSlot"));
        assertEquals("mynpcs", replacement.getString("SourceMod"));
        XenoScriptIds.carrySourceSlot(null, new CompoundTag());
    }

    @Test
    void numbersResolveToNothingWithoutAWorld() {
        assertNull(XenoScriptIds.factionId(1));
        assertNull(offline.getFactions().get(1));
        assertNull(offline.getDialogs().get(1));
        assertNull(offline.getQuests().get(1));
    }

    // ------------------------------------------------------------------ dialogs

    private static XenoDialogAdapter draftDialog() {
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        nodes.put("n1", new XenoDialogue.Node("Hello", List.of(
                new XenoDialogue.Option("Tell me more", XenoDialogue.OptionType.TEXT, "n2", "", ""),
                new XenoDialogue.Option("Bye", XenoDialogue.OptionType.QUIT, "", "", ""))));
        nodes.put("n2", new XenoDialogue.Node("More", List.of()));
        return new XenoDialogAdapter("mynpcs_intro", "dialog_1", null, new XenoDialogue("n1", nodes));
    }

    @Test
    void aDialogReadsAndEditsItsNodeAndOptions() {
        XenoDialogAdapter dialog = draftDialog();
        assertEquals("Hello", dialog.getText());
        assertEquals(2, dialog.getOptions().size());
        IDialogOption first = dialog.getOption(0);
        assertEquals(OptionType.DIALOG_OPTION, first.getType());
        assertEquals(OptionType.QUIT_OPTION, dialog.getOption(1).getType());
        assertEquals("More", first.getDialog().getText());
        assertNull(dialog.getOption(5));

        dialog.setText("Hi there");
        first.setName("Go on");
        assertEquals("Hi there", dialog.getText());
        assertEquals("Go on", dialog.getOption(0).getText());
    }

    @Test
    void dialogCommandsBecomeCommandOptions() {
        XenoDialogAdapter dialog = draftDialog();
        dialog.setCommands("say one", "say two");
        assertArrayEquals(new String[] {"say one", "say two"}, dialog.getCommands());
        assertEquals(4, dialog.getOptions().size());
        assertEquals(XenoDialogOption.COMMAND_OPTION, dialog.getOption(2).getType());
        assertThrows(IllegalArgumentException.class, () -> dialog.getOption(0).setCommands("a", "b"));
        dialog.getOption(1).setType(OptionType.DISABLED);
        assertEquals(3, dialog.getOptions().size(), "a disabled option is taken out of the node");
    }

    @Test
    void anOptionLeadsOnlyWithinItsConversation() {
        XenoDialogAdapter dialog = draftDialog();
        XenoDialogAdapter other = new XenoDialogAdapter("npc", "elsewhere", null,
                new XenoDialogue("root", Map.of("root", new XenoDialogue.Node("x", List.of()))));
        assertThrows(IllegalArgumentException.class, () -> dialog.getOption(1).setDialog(other));
        dialog.getOption(1).setDialog(dialog.getOption(0).getDialog());
        assertEquals("More", dialog.getOption(1).getDialog().getText());
    }

    @Test
    void aDialogOffersAQuestThroughAnOption() {
        XenoDialogAdapter dialog = draftDialog();
        IQuest quest = new XenoQuestAdapter("kill_mobs");
        dialog.setQuest(quest);
        assertEquals(XenoDialogue.OptionType.QUEST, dialog.current().options().get(2).type());
        assertEquals("kill_mobs", dialog.current().options().get(2).quest());
        dialog.setQuest(null);
        assertEquals(2, dialog.getOptions().size());
    }

    // ------------------------------------------------------------------ availability

    /**
     * A script's conditions on a dialog node, stored on that node.
     *
     * <p>Offline, no CustomNPCs number resolves to anything, which is what makes the refusal testable:
     * asking for quest 12 has to say so rather than write the string "12" into a native id field.
     */
    @Test
    void aDialogAvailabilityWritesItsOwnSlots() {
        XenoDialogAdapter dialog = draftDialog();
        IAvailability availability = dialog.getAvailability();

        assertEquals(0, availability.getDaytime());
        assertEquals(0, availability.getMinPlayerLevel());
        availability.setDaytime(1);
        assertEquals(1, availability.getDaytime());
        assertEquals("night", dialog.current().availability().daytime());
        availability.setMinPlayerLevel(7);
        assertEquals(7, availability.getMinPlayerLevel());
        assertThrows(IllegalArgumentException.class, () -> availability.setDaytime(3));
        assertThrows(IllegalArgumentException.class, () -> availability.setMinPlayerLevel(-1));

        availability.setScoreboard(1, "kills", 2, 5);
        var scores = dialog.current().availability().scores();
        assertEquals(2, scores.size(), "slot 1 leaves slot 0 as an empty slot rather than sliding down");
        assertEquals("", scores.get(0).objective());
        assertEquals(QuestAvailability.Compare.BIGGER, scores.get(1).compare());
        assertEquals(5, scores.get(1).value());
        assertThrows(IllegalArgumentException.class, () -> availability.setScoreboard(2, "kills", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> availability.setScoreboard(0, " ", 0, 1));

        assertEquals(-1, availability.getQuest(0));
        assertEquals(-1, availability.getDialog(3));
        assertThrows(xenoapi.npcs.api.CustomNPCsException.class, () -> availability.setQuest(0, 12, 5));
        assertThrows(xenoapi.npcs.api.CustomNPCsException.class, () -> availability.setFaction(0, 3, 1, 0));
        assertThrows(xenoapi.npcs.api.CustomNPCsException.class, () -> availability.setDialog(0, 4, 1));

        // "Always" names nothing, so it is the one state that stores without a world to look up.
        availability.setDialog(2, 7, 0);
        var dialogs = dialog.current().availability().dialogs();
        assertEquals(3, dialogs.size());
        assertEquals("", dialogs.get(2).dialogId());
        assertEquals(QuestAvailability.DialogState.ALWAYS, dialogs.get(2).state());
        assertEquals(-1, availability.getDialog(2));
        assertThrows(IllegalArgumentException.class, () -> availability.setQuest(0, 12, 6));
    }

    /**
     * A dialog gate with no reference stays unsatisfiable, while an emptied slot is no condition.
     *
     * <p>The faction and score halves of that rule cannot be asserted here: {@code QuestRuntime}
     * reaches them only after the player check, so a unit test has no scoreboard or faction standing to
     * look at. What is provable offline is that the fail-closed sentinel the converter writes still
     * shuts the door, and that the blanks the script API stores do not.
     */
    @Test
    void anEmptyGateDoesNotGateWhileAnUnresolvedOneDoes() {
        var blank = new QuestAvailability(List.of(), List.of(), "always",
                List.of(new QuestAvailability.FactionGate("", QuestAvailability.Stance.HOSTILE, true)),
                List.of(new QuestAvailability.ScoreGate("", QuestAvailability.Compare.BIGGER, 9)), 0);
        assertTrue(QuestRuntime.available(null, null, blank), "a slot the script cleared is no test");

        assertFalse(QuestRuntime.available(null, null, new QuestAvailability(List.of(),
                List.of(new QuestAvailability.DialogGate("<unresolved>", QuestAvailability.DialogState.AFTER)),
                "always", List.of(), List.of(), 0)),
                "an availability the converter could not read stays gated shut");
    }

    // ------------------------------------------------------------------ quests

    private static XenoQuestAdapter draftQuest() {
        ParallelQuests.QuestDef def = new ParallelQuests.QuestDef("test_q1", "Test", "", 3,
                new QuestObjective.Goal(QuestObjective.KILL_TYPE, "minecraft:zombie"), QuestReward.DEFAULT,
                "main", "", "", net.bullettrain.xenopixelsmod.features.progression.QuestCompletionMode.INSTANT, "");
        return new XenoQuestAdapter("test_q1", def, "main");
    }

    @Test
    void aQuestEditsItsDefinition() {
        XenoQuestAdapter quest = draftQuest();
        assertEquals(QuestType.KILL, quest.getType());
        quest.setName("Renamed");
        quest.setLogText("Kill three");
        quest.setNpcName("Elder");
        assertEquals("Renamed", quest.getName());
        assertEquals("Kill three", quest.getLogText());
        assertEquals("Elder", quest.getNpcName());
        assertEquals(net.bullettrain.xenopixelsmod.features.progression.QuestCompletionMode.NPC, quest.def().completionMode());
        quest.setType(QuestType.MANUAL);
        assertEquals(QuestType.MANUAL, quest.getType());
        assertThrows(IllegalArgumentException.class, () -> quest.setType(9));
    }

    @Test
    void questRewardsAreItemsAndCounts() {
        XenoQuestAdapter quest = draftQuest();
        quest.setRewards(new xenoapi.npcs.api.item.IItemStack[] {XenoApiAdapters.wrap(new ItemStack(Items.DIAMOND, 3))});
        assertEquals(1, quest.getRewards().length);
        assertEquals(3, quest.getRewards()[0].getStackSize());
        quest.setCommands("say done");
        assertArrayEquals(new String[] {"say done"}, quest.getCommands());
        assertThrows(IllegalArgumentException.class, () -> quest.setCommands(new String[17]));
    }

    // ------------------------------------------------------------------ mail / effects / scoreboard

    @Test
    void mailHoldsFourItemSlots() {
        var mail = offline.createMail("Elder", "A gift");
        mail.setText("Take this");
        mail.setItem(0, XenoApiAdapters.wrap(new ItemStack(Items.APPLE)));
        assertEquals("Elder", mail.getSender());
        assertEquals(1, mail.getItem(0).getStackSize());
        assertThrows(IllegalArgumentException.class, () -> mail.setItem(4, null));
        mail.setQuest(99);
        assertNull(mail.getQuest(), "an unknown quest number clears the quest");
    }

    @Test
    void potionEffectNumbersRoundTripThroughRegistryIds() {
        String poison = XenoNpcViews.effectId(PotionEffectType.POISON);
        assertEquals("minecraft:poison", poison);
        assertEquals(PotionEffectType.POISON, XenoNpcViews.effectType(poison));
        assertEquals("", XenoNpcViews.effectId(PotionEffectType.NONE));
        assertEquals(PotionEffectType.NONE, XenoNpcViews.effectType("minecraft:nothing"));
        assertThrows(IllegalArgumentException.class, () -> XenoNpcViews.effectId(500));
    }

    @Test
    void theScoreboardEditsObjectivesScoresAndTeams() {
        IScoreboard board = new XenoScoreboardAdapter(new Scoreboard());
        board.addObjective("kills", "dummy");
        assertTrue(board.hasObjective("kills"));
        board.setPlayerScore("Steve", "kills", 5);
        assertEquals(5, board.getPlayerScore("Steve", "kills"));
        assertTrue(board.hasPlayerObjective("Steve", "kills"));
        assertEquals(1, board.getObjective("kills").getScores().length);
        board.deletePlayerScore("Steve", "kills");
        assertFalse(board.hasPlayerObjective("Steve", "kills"));
        assertThrows(xenoapi.npcs.api.CustomNPCsException.class, () -> board.addObjective("kills", "dummy"));
        assertThrows(xenoapi.npcs.api.CustomNPCsException.class, () -> board.addObjective("x", "no_such_criteria"));

        var team = board.addTeam("red");
        team.addPlayer("Alex");
        team.setColor("red");
        assertTrue(team.hasPlayer("Alex"));
        assertEquals("red", board.getPlayerTeam("Alex").getName());
        assertEquals("red", team.getColor());
        board.removePlayerTeam("Alex");
        assertFalse(team.hasPlayer("Alex"));
        board.removeTeam("red");
        assertFalse(board.hasTeam("red"));
    }
}
