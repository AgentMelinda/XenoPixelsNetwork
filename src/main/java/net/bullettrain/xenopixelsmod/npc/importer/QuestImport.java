package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.features.progression.QuestCompletionMode;
import net.bullettrain.xenopixelsmod.features.progression.QuestObjective;
import net.bullettrain.xenopixelsmod.features.progression.QuestReward;
import net.bullettrain.xenopixelsmod.features.progression.QuestRepeat;
import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Map;

/** Bounded conversion of the verified MyNPCs 1.5.0 quest fields Xeno can execute faithfully. */
public final class QuestImport {
    public record Imported(String id, ParallelQuests.QuestDef quest) {}

    private QuestImport() {}

    public static String toJson(ParallelQuests.QuestDef quest) {
        JsonObject root = new JsonObject();
        root.addProperty("title", quest.title());
        root.addProperty("description", quest.desc());
        root.addProperty("target", quest.target());
        root.addProperty("objective", quest.goal().type().name().toLowerCase(java.util.Locale.ROOT));
        root.addProperty("parameter", quest.goal().parameter());
        root.addProperty("category", quest.category());
        root.addProperty("log_text", quest.logText());
        root.addProperty("complete_text", quest.completeText());
        root.addProperty("completion", quest.completionMode().name());
        root.addProperty("completer_npc", quest.completerNpc());
        root.addProperty("repeat", quest.repeat().name());
        com.google.gson.JsonObject reward = new com.google.gson.JsonObject();
        reward.addProperty("skill_points", quest.reward().skillPoints());
        reward.addProperty("experience", quest.reward().experience());
        com.google.gson.JsonArray items = new com.google.gson.JsonArray();
        for (QuestReward.ItemGrant item : quest.reward().items()) {
            com.google.gson.JsonObject one = new com.google.gson.JsonObject();
            one.addProperty("id", item.id());
            one.addProperty("count", item.count());
            items.add(one);
        }
        reward.add("items", items);
        com.google.gson.JsonArray commands = new com.google.gson.JsonArray();
        for (String command : quest.reward().commands()) commands.add(command);
        reward.add("commands", commands);
        com.google.gson.JsonArray factions = new com.google.gson.JsonArray();
        for (QuestReward.FactionGrant grant : quest.reward().factionPoints()) {
            com.google.gson.JsonObject one = new com.google.gson.JsonObject();
            one.addProperty("faction", grant.factionId());
            one.addProperty("points", grant.points());
            factions.add(one);
        }
        reward.add("faction_points", factions);
        root.add("reward", reward);
        root.addProperty("random_reward", quest.randomReward());
        if (quest.targetHunts()) {
            root.addProperty("target_hunts", true);
        }
        root.addProperty("next_quest", quest.nextQuest());
        com.google.gson.JsonObject mail = new com.google.gson.JsonObject();
        mail.addProperty("sender", quest.mail().sender());
        mail.addProperty("subject", quest.mail().subject());
        mail.addProperty("body", quest.mail().body());
        root.add("mail", mail);
        root.add("availability", quest.availability().toJson());
        com.google.gson.JsonArray objectives = new com.google.gson.JsonArray();
        for (net.bullettrain.xenopixelsmod.features.progression.QuestStep step : quest.steps()) {
            com.google.gson.JsonObject one = new com.google.gson.JsonObject();
            one.addProperty("objective", step.goal().type().name().toLowerCase(java.util.Locale.ROOT));
            one.addProperty("parameter", step.goal().parameter());
            one.addProperty("target", step.target());
            one.addProperty("take_items", step.takeItems());
            one.addProperty("ignore_damage", step.ignoreDamage());
            one.addProperty("ignore_nbt", step.ignoreNbt());
            one.addProperty("x", step.x());
            one.addProperty("y", step.y());
            one.addProperty("z", step.z());
            one.addProperty("dimension", step.dimension());
            one.addProperty("radius", step.radius());
            objectives.add(one);
        }
        root.add("objectives", objectives);
        return root.toString();
    }

    public static Imported convert(int slot, String id, String group, CompoundTag source,
                                   NpcImportReport report) {
        String title = source.getString("Title");
        if (title.isBlank()) title = "Quest " + slot;
        String description = source.getString("Text");
        int type = source.getInt("Type");
        java.util.List<net.bullettrain.xenopixelsmod.features.progression.QuestStep> steps =
                stepsFrom(type, source, group, slot, report);
        if (steps == null) return null;
        if (steps.size() > net.bullettrain.xenopixelsmod.features.progression.ActiveQuest.MAX_STEPS) {
            report.failed("quests", group + "/" + slot,
                    "quest has more than "
                            + net.bullettrain.xenopixelsmod.features.progression.ActiveQuest.MAX_STEPS
                            + " objective steps");
            return null;
        }
        QuestObjective objective = steps.get(0).goal().type();
        String parameter = steps.get(0).goal().parameter();
        int target = steps.get(0).target();

        int completion = source.getInt("QuestCompletion");
        if (completion < 0 || completion > 1) {
            report.failed("quests", group + "/" + slot,
                    "unknown MyNPCs completion mode " + completion);
            return null;
        }
        QuestCompletionMode mode = completion == 0
                ? QuestCompletionMode.NPC : QuestCompletionMode.INSTANT;
        String completer = source.getString("CompleterNpc");
        if (mode == QuestCompletionMode.NPC && completer.isBlank()) {
            report.note("quest " + group + "/" + slot
                    + " requested NPC hand-in without a completer; Xeno will complete it instantly");
            mode = QuestCompletionMode.INSTANT;
        }
        int exp = Math.max(0, Math.min(32767, source.getInt("RewardExp")));
        QuestRepeat repeat;
        try {
            repeat = QuestRepeat.fromMyNpcs(source.getInt("QuestRepeat"));
        } catch (IllegalArgumentException exception) {
            report.failed("quests", group + "/" + slot, exception.getMessage());
            return null;
        }
        String command = source.getString("QuestCommand").replace("@dp", "{player}").trim();
        java.util.List<String> commands = command.isBlank() ? List.of() : List.of(command);
        CompoundTag rewards = source.getCompound("Rewards");
        ListTag itemRewards = rewards.getList("NpcMiscInv", Tag.TAG_COMPOUND);
        java.util.List<QuestReward.ItemGrant> items = new java.util.ArrayList<>();
        for (int i = 0; i < itemRewards.size(); i++) {
            CompoundTag stack = itemRewards.getCompound(i);
            String itemId = stack.getString("id");
            if (itemId.isBlank()) itemId = stack.getString("Item");
            if (!itemId.isBlank()) {
                items.add(new QuestReward.ItemGrant(itemId, Math.max(1, stack.getInt("Count"))));
            }
        }
        CompoundTag mailTag = source.getCompound("QuestMail");
        net.bullettrain.xenopixelsmod.features.progression.QuestMail mail =
                new net.bullettrain.xenopixelsmod.features.progression.QuestMail(
                        mailTag.getString("Sender"), mailTag.getString("Subject"),
                        mailTag.getString("Text"), List.of());
        String next = source.getInt("NextQuestId") >= 0
                ? group + "_" + source.getInt("NextQuestId") : "";
        CompoundTag factionPoints = source.getCompound("QuestFactionPoints");
        java.util.List<QuestReward.FactionGrant> factions = new java.util.ArrayList<>();
        addFaction(factions, factionPoints, 1);
        addFaction(factions, factionPoints, 2);
        QuestReward reward = new QuestReward(0, exp, items, commands, factions);
        ParallelQuests.QuestDef quest = new ParallelQuests.QuestDef(id, title, description,
                Math.max(1, Math.min(32767, target)), new QuestObjective.Goal(objective, parameter),
                reward, group, description, source.getString("CompleteText"), mode, completer,
                repeat, steps, next, source.getBoolean("RandomReward"), mail,
                net.bullettrain.xenopixelsmod.features.progression.QuestAvailability.NONE);
        return new Imported(id, quest);
    }

    private static void addFaction(java.util.List<QuestReward.FactionGrant> factions,
                                   CompoundTag tag, int slot) {
        int points = tag.getInt("OptionFaction" + slot + "Points");
        if (tag.getBoolean("DecreaseFaction" + slot + "Points")) points = -Math.abs(points);
        String faction = tag.getString("Faction" + slot);
        if (faction.isBlank() && tag.getInt("OptionFactions" + slot) >= 0 && points != 0) {
            faction = "faction_" + tag.getInt("OptionFactions" + slot);
        }
        if (!faction.isBlank() && points != 0) {
            factions.add(new QuestReward.FactionGrant(faction, points));
        }
    }

    /** MyNPCs 1.5.0 QuestType ordinals: 0 item, 1 dialog, 2 kill, 3 location, 4 area kill, 5 manual. */
    private static java.util.List<net.bullettrain.xenopixelsmod.features.progression.QuestStep> stepsFrom(
            int type, CompoundTag source, String group, int slot, NpcImportReport report) {
        ListTag rows = source.getList("QuestDialogs", Tag.TAG_COMPOUND);
        java.util.List<net.bullettrain.xenopixelsmod.features.progression.QuestStep> steps =
                new java.util.ArrayList<>();
        if (type == 0) {
            boolean ignoreDamage = source.getBoolean("IgnoreDamage");
            boolean ignoreNbt = source.getBoolean("IgnoreNBT");
            boolean takeItems = !source.getBoolean("LeaveItems");
            CompoundTag questData = source.getCompound("QuestData");
            CompoundTag itemData = source.contains("Items", Tag.TAG_COMPOUND)
                    ? source.getCompound("Items") : questData.getCompound("Items");
            ListTag itemRows = itemData.getList("NpcMiscInv", Tag.TAG_COMPOUND);
            java.util.Map<String, Integer> itemCounts = new java.util.LinkedHashMap<>();
            for (int i = 0; i < itemRows.size(); i++) {
                CompoundTag item = itemRows.getCompound(i);
                String itemId = item.getString("id");
                if (itemId.isBlank()) itemId = item.getString("Item");
                if (itemId.isBlank()) {
                    report.failed("quests", group + "/" + slot,
                            "item quest contains a stack without an item id");
                    return null;
                }
                if (!ignoreNbt && (!item.getCompound("tag").isEmpty()
                        || !item.getCompound("components").isEmpty())) {
                    report.failed("quests", group + "/" + slot,
                            "item quest requires stack data that Xeno cannot represent yet");
                    return null;
                }
                if (!ignoreDamage && item.getInt("Damage") > 0) {
                    report.failed("quests", group + "/" + slot,
                            "item quest requires a specific damage value that Xeno cannot represent yet");
                    return null;
                }
                int count = Math.max(1, item.getInt("Count"));
                itemCounts.merge(itemId, count, (left, right) ->
                        Math.min(32767, left + right));
            }
            for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
                steps.add(new net.bullettrain.xenopixelsmod.features.progression.QuestStep(
                        new QuestObjective.Goal(QuestObjective.ITEM, entry.getKey()), entry.getValue(),
                        takeItems, ignoreDamage, ignoreNbt, 0, 0, 0, "", 0));
            }
            if (steps.isEmpty()) {
                String itemId = source.getString("Item");
                if (itemId.isBlank()) itemId = source.getString("Target");
                if (!itemId.isBlank()) {
                    steps.add(new net.bullettrain.xenopixelsmod.features.progression.QuestStep(
                            new QuestObjective.Goal(QuestObjective.ITEM, itemId),
                            Math.max(1, source.getInt("TargetCount")), takeItems,
                            ignoreDamage, ignoreNbt, 0, 0, 0, "", 0));
                }
            }
            if (steps.isEmpty()) {
                report.failed("quests", group + "/" + slot,
                        "item quest has no supported item targets");
                return null;
            }
            return steps;
        }
        if (type == 2 || type == 4) {
            if (type == 4) {
                report.failed("quests", group + "/" + slot,
                        "area-kill quest bounds are not present in the verified MyNPCs 1.5.0 quest data");
                return null;
            }
            if (rows.isEmpty()) {
                report.failed("quests", group + "/" + slot, "kill quest has no targets");
                return null;
            }
            for (int i = 0; i < rows.size(); i++) {
                CompoundTag row = rows.getCompound(i);
                String name = row.getString("Slot").trim();
                int count = Math.max(1, row.getInt("Value"));
                if (name.isBlank()) {
                    report.failed("quests", group + "/" + slot, "kill quest target name is missing");
                    return null;
                }
                steps.add(new net.bullettrain.xenopixelsmod.features.progression.QuestStep(
                        new QuestObjective.Goal(QuestObjective.KILL_NPC, name), count,
                        false, false, false, 0, 0, 0, "", 0));
            }
            return steps;
        }
        if (type == 1) {
            if (rows.isEmpty()) {
                report.failed("quests", group + "/" + slot,
                        "dialog quest has no dialog IDs in QuestDialogs");
                return null;
            }
            for (int i = 0; i < rows.size(); i++) {
                int dialogId = rows.getCompound(i).getInt("Value");
                if (dialogId < 0) {
                    report.failed("quests", group + "/" + slot,
                            "dialog quest contains an invalid dialog ID");
                    return null;
                }
                steps.add(new net.bullettrain.xenopixelsmod.features.progression.QuestStep(
                        new QuestObjective.Goal(QuestObjective.DIALOG, "n" + dialogId),
                        1, false, false, false, 0, 0, 0, "", 0));
            }
            return steps;
        }
        if (type == 3) {
            report.failed("quests", group + "/" + slot,
                    "MyNPCs location quests use named-location completion state; Xeno currently uses coordinate locations");
            return null;
        }
        QuestObjective objective = switch (type) {
            case 5 -> QuestObjective.MANUAL;
            default -> null;
        };
        if (objective == null) {
            report.failed("quests", group + "/" + slot,
                    "MyNPCs quest type " + type + " has no equivalent Xeno objective yet");
            return null;
        }
        String parameter = source.getString("Item");
        if (parameter.isBlank()) parameter = source.getString("Target");
        int count = Math.max(1, source.getInt("TargetCount"));
        if (objective == QuestObjective.LOCATION && parameter.isBlank()) {
            parameter = source.getInt("X") + "," + source.getInt("Y") + "," + source.getInt("Z");
        }
        if ((objective == QuestObjective.ITEM || objective == QuestObjective.DIALOG
                || objective == QuestObjective.LOCATION) && parameter.isBlank()) {
            report.failed("quests", group + "/" + slot, objective.name() + " needs a target name");
            return null;
        }
        steps.add(new net.bullettrain.xenopixelsmod.features.progression.QuestStep(
                new QuestObjective.Goal(objective, parameter), count,
                source.getBoolean("TakeItems"), source.getBoolean("IgnoreDamage"),
                source.getBoolean("IgnoreNBT"),
                source.getInt("X"), source.getInt("Y"), source.getInt("Z"), "", 0));
        return steps;
    }
}
