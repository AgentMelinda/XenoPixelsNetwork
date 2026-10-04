package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.features.progression.QuestObjective;
import net.bullettrain.xenopixelsmod.features.progression.XenoQuests;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestImportTest {
    @Test
    void mapsVerifiedSingleNpcKillQuestAndKeepsTextAndRewards() {
        CompoundTag source = new CompoundTag();
        source.putInt("Type", 2); // MyNPCs QuestType.KILL in the inspected 1.5.0 jar
        source.putString("Title", "Defeat Raditz");
        source.putString("Text", "Find and defeat him.");
        source.putString("CompleteText", "You did it.");
        source.putString("CompleterNpc", "Goku");
        source.putInt("QuestCompletion", 0); // EnumQuestCompletion.Npc
        source.putInt("RewardExp", 100);
        ListTag targets = new ListTag();
        CompoundTag target = new CompoundTag();
        target.putString("Slot", "Raditz");
        target.putInt("Value", 2);
        targets.add(target);
        source.put("QuestDialogs", targets);

        QuestImport.Imported imported = QuestImport.convert(1, "quest_raditz",
                "mynpcs_dbz", source, new NpcImportReport());

        assertEquals(QuestObjective.KILL_NPC, imported.quest().goal().type());
        assertEquals("raditz", imported.quest().goal().parameter());
        assertEquals(2, imported.quest().target());
        assertEquals(100, imported.quest().reward().experience());
        assertEquals("Goku", imported.quest().completerNpc());
        var roundTrip = XenoQuests.parse(imported.id(),
                com.google.gson.JsonParser.parseString(QuestImport.toJson(imported.quest())));
        assertEquals(QuestObjective.KILL_NPC, roundTrip.goal().type());
    }

    @Test
    void anItemQuestNeedsANamedItem() {
        CompoundTag source = new CompoundTag();
        source.putInt("Type", 0);
        NpcImportReport report = new NpcImportReport();

        assertNull(QuestImport.convert(4, "quest_items", "mynpcs_main", source, report));
        assertTrue(report.failures().stream().anyMatch(failure ->
                failure.reason().toLowerCase(java.util.Locale.ROOT).contains("item quest")));
    }

    @Test
    void importsItemRequirementAndMyNpcsLeaveItemsRule() {
        CompoundTag source = new CompoundTag();
        source.putInt("Type", 0);
        source.putBoolean("LeaveItems", false);
        CompoundTag items = new CompoundTag();
        ListTag stacks = new ListTag();
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "minecraft:diamond_helmet");
        stack.putInt("Count", 2);
        stacks.add(stack);
        items.put("NpcMiscInv", stacks);
        source.put("Items", items);

        QuestImport.Imported imported = QuestImport.convert(9, "quest_helmet", "mynpcs_main",
                source, new NpcImportReport());

        assertEquals(QuestObjective.ITEM, imported.quest().steps().get(0).goal().type());
        assertEquals("minecraft:diamond_helmet", imported.quest().steps().get(0).goal().parameter());
        assertEquals(2, imported.quest().steps().get(0).target());
        assertTrue(imported.quest().steps().get(0).takeItems());
    }

    @Test
    void aMultiTargetKillQuestKeepsEveryName() {
        CompoundTag source = new CompoundTag();
        source.putInt("Type", 2);
        ListTag targets = new ListTag();
        for (String name : new String[]{"Raditz", "Nappa"}) {
            CompoundTag target = new CompoundTag();
            target.putString("Slot", name);
            target.putInt("Value", 1);
            targets.add(target);
        }
        source.put("QuestDialogs", targets);
        NpcImportReport report = new NpcImportReport();

        QuestImport.Imported imported = QuestImport.convert(5, "quest_two", "mynpcs_main", source, report);
        assertEquals(2, imported.quest().steps().size());
        assertEquals("raditz", imported.quest().steps().get(0).goal().parameter());
        assertEquals("nappa", imported.quest().steps().get(1).goal().parameter());
    }

    @Test
    void importsMyNpcsDialogIdsAsXenoDialogueNodeObjectives() {
        CompoundTag source = new CompoundTag();
        source.putInt("Type", 1); // MyNPCs QuestType.DIALOG
        ListTag dialogs = new ListTag();
        for (int dialogId : new int[]{12, 18}) {
            CompoundTag row = new CompoundTag();
            row.putString("Slot", "dialog_slot_" + dialogId);
            row.putInt("Value", dialogId);
            dialogs.add(row);
        }
        source.put("QuestDialogs", dialogs);

        var imported = QuestImport.convert(3, "quest_dialogs", "mynpcs_main",
                source, new NpcImportReport());

        assertEquals(2, imported.quest().steps().size());
        assertEquals(QuestObjective.DIALOG, imported.quest().steps().get(0).goal().type());
        assertEquals("n12", imported.quest().steps().get(0).goal().parameter());
        assertEquals("n18", imported.quest().steps().get(1).goal().parameter());
        assertEquals(1, imported.quest().steps().get(0).target());
    }

    @Test
    void mapsQuestRepeatFactionPointsMiscInvRewardsAndRandomFlag() {
        CompoundTag source = new CompoundTag();
        source.putInt("Type", 2);
        ListTag targets = new ListTag();
        CompoundTag target = new CompoundTag();
        target.putString("Slot", "Vegeta");
        target.putInt("Value", 1);
        targets.add(target);
        source.put("QuestDialogs", targets);
        source.putInt("QuestRepeat", 3); // EnumQuestRepeat ordinal: NONE,REPEATABLE,MCDAILY,MCWEEKLY
        source.putInt("QuestCompletion", 1); // Instant
        source.putString("QuestCommand", "give @dp diamond 3");
        source.putBoolean("RandomReward", true);
        CompoundTag rewards = new CompoundTag();
        ListTag misc = new ListTag();
        for (String itemId : new String[]{"minecraft:diamond", "minecraft:emerald"}) {
            CompoundTag stack = new CompoundTag();
            stack.putString("id", itemId);
            stack.putInt("Count", 2);
            misc.add(stack);
        }
        rewards.put("NpcMiscInv", misc);
        source.put("Rewards", rewards);
        CompoundTag factionPoints = new CompoundTag();
        factionPoints.putInt("OptionFactions1", 4);
        factionPoints.putInt("OptionFaction1Points", 15);
        factionPoints.putInt("OptionFactions2", 4);
        factionPoints.putInt("OptionFaction2Points", 10);
        factionPoints.putBoolean("DecreaseFaction2Points", true);
        source.put("QuestFactionPoints", factionPoints);

        QuestImport.Imported imported = QuestImport.convert(1, "quest_parity", "mynpcs_main",
                source, new NpcImportReport());

        assertEquals(net.bullettrain.xenopixelsmod.features.progression.QuestRepeat.MCWEEKLY,
                imported.quest().repeat());
        assertEquals(net.bullettrain.xenopixelsmod.features.progression.QuestCompletionMode.INSTANT,
                imported.quest().completionMode());
        assertTrue(imported.quest().randomReward());
        assertEquals(java.util.List.of("give {player} diamond 3"),
                imported.quest().reward().commands());
        assertEquals(2, imported.quest().reward().items().size());
        assertEquals("minecraft:diamond", imported.quest().reward().items().get(0).id());
        assertEquals(2, imported.quest().reward().items().get(0).count());
        assertEquals(2, imported.quest().reward().factionPoints().size());
        assertEquals("faction_4", imported.quest().reward().factionPoints().get(0).factionId());
        assertEquals(15, imported.quest().reward().factionPoints().get(0).points());
        assertEquals(-10, imported.quest().reward().factionPoints().get(1).points());
    }

    @Test
    void rejectsAnUnknownQuestRepeatOrdinal() {
        CompoundTag source = new CompoundTag();
        source.putInt("Type", 5); // Manual
        source.putInt("QuestRepeat", 99);
        NpcImportReport report = new NpcImportReport();

        assertNull(QuestImport.convert(7, "quest_repeat", "mynpcs_main", source, report));
        assertTrue(report.failures().stream().anyMatch(failure ->
                failure.reason().contains("repeat rule")));
    }

    @Test
    void rejectsAnUnknownCompletionMode() {
        CompoundTag source = new CompoundTag();
        source.putInt("Type", 5);
        source.putInt("QuestCompletion", 4);
        NpcImportReport report = new NpcImportReport();

        assertNull(QuestImport.convert(8, "quest_completion", "mynpcs_main", source, report));
        assertTrue(report.failures().stream().anyMatch(failure ->
                failure.reason().contains("completion mode")));
    }

    @Test
    void rejectsLocationAndAreaKillWhenTheirSourceRulesCannotBePreserved() {
        CompoundTag location = new CompoundTag();
        location.putInt("Type", 3);
        NpcImportReport locationReport = new NpcImportReport();
        assertNull(QuestImport.convert(1, "quest_location", "mynpcs_main",
                location, locationReport));
        assertTrue(locationReport.failures().stream().anyMatch(failure ->
                failure.reason().contains("named-location completion state")));

        CompoundTag areaKill = new CompoundTag();
        areaKill.putInt("Type", 4);
        NpcImportReport areaReport = new NpcImportReport();
        assertNull(QuestImport.convert(2, "quest_area", "mynpcs_main",
                areaKill, areaReport));
        assertTrue(areaReport.failures().stream().anyMatch(failure ->
                failure.reason().contains("bounds are not present")));
    }
}
