package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.features.progression.*;
import net.minecraft.nbt.*;
import java.util.HashSet;
import java.util.function.Function;

/** UUID migration merges only missing state. Existing Xeno progress always wins conflicts. */
public final class PlayerProgressImport {
    private PlayerProgressImport() {}

    public static CompoundTag merge(CompoundTag source, CompoundTag existing, String mod,
                                    NpcImportReport report, Function<String, ParallelQuests.QuestDef> definitions) {
        CompoundTag result = existing.copy();
        QuestBook before = new QuestBook();
        before.loadFrom(existing);
        QuestBook book = new QuestBook();
        book.loadFrom(existing);
        CompoundTag questData = source.getCompound("QuestData");
        ListTag completed = questData.getList("CompletedQuests", Tag.TAG_COMPOUND);
        for (int i = 0; i < completed.size(); i++) {
            CompoundTag row = completed.getCompound(i);
            String id = report.quest(mod, row.getInt("Quest"));
            if (id == null) { report.note("player progress unresolved completed quest slot " + row.getInt("Quest")); continue; }
            if (before.hasCompleted(id) || before.isActive(id)) {
                report.note("player progress conflict: kept Xeno quest " + id); continue;
            }
            if (book.completed().size() >= QuestBook.MAX_COMPLETED) { report.note("completed quest history limit reached; retained source backup"); continue; }
            // Source Date is world ticks, not an epoch timestamp. Preserve it without inventing a wall clock.
            book.complete(id, row.getLong("Date"), 0L);
        }
        ListTag active = questData.getList("ActiveQuests", Tag.TAG_COMPOUND);
        for (int i = 0; i < active.size(); i++) {
            CompoundTag row = active.getCompound(i);
            String id = report.quest(mod, row.getInt("Quest"));
            var def = id == null ? null : definitions.apply(id);
            if (def == null) { report.note("player progress unresolved active quest slot " + row.getInt("Quest")); continue; }
            if (before.hasCompleted(id) || before.isActive(id)) { report.note("player progress conflict: kept Xeno quest " + id); continue; }
            String refusal = book.start(id, def.target());
            if (refusal != null) { report.note("player progress " + refusal); continue; }
            ActiveQuest quest = book.active(id);
            ListTag killed = row.getCompound("ExtraData").getList("Killed", Tag.TAG_COMPOUND);
            for (int step = 0; step < def.steps().size(); step++) {
                QuestStep target = def.steps().get(step);
                if (target.goal().type() != QuestObjective.KILL_NPC) continue;
                for (int k = 0; k < killed.size(); k++) {
                    CompoundTag count = killed.getCompound(k);
                    // Goal.parameter() is normalized to lower case by the record, while the source
                    // slot holds the author's display name ("Saibamen A"). Comparing the raw strings
                    // matched nothing, so resumed kill counters silently came across as zero.
                    if (target.goal().parameter().equals(normalized(count.getString("Slot"))))
                        quest.setStepProgress(step, count.getInt("Value"), target.target());
                }
            }
            if (row.getBoolean("QuestCompleted")) quest.markReady();
            for (String key : row.getCompound("ExtraData").getAllKeys())
                if (!key.equals("Killed")) report.note("player active quest " + id + " unmapped ExtraData." + key);
        }
        book.saveTo(result);

        ListTag viewed = existing.getList("ViewedDialogues", Tag.TAG_STRING).copy();
        var seen = new HashSet<String>();
        for (int i = 0; i < viewed.size(); i++) seen.add(viewed.getString(i));
        ListTag dialogs = source.getList("DialogData", Tag.TAG_COMPOUND);
        for (int i = 0; i < dialogs.size(); i++) {
            int slot = dialogs.getCompound(i).getInt("Dialog");
            var ref = report.dialog(mod, slot);
            if (ref == null) { report.note("player progress unresolved dialog slot " + slot); continue; }
            String id = ref.group() + "/" + ref.id();
            if (seen.add(id) && viewed.size() < 4096) viewed.add(StringTag.valueOf(id));
        }
        result.put("ViewedDialogues", viewed);
        CompoundTag standings = existing.getCompound("FactionStanding").copy();
        ListTag factions = source.getList("FactionData", Tag.TAG_COMPOUND);
        for (int i = 0; i < factions.size(); i++) {
            CompoundTag row = factions.getCompound(i);
            String id = report.faction(mod, row.getInt("Faction"));
            if (id == null) { report.note("player progress unresolved faction slot " + row.getInt("Faction")); continue; }
            int points = Math.max(-1000, Math.min(1000, row.getInt("Points")));
            if (standings.contains(id)) {
                if (standings.getInt(id) != points) report.note("player progress conflict: kept Xeno faction " + id);
            } else standings.putInt(id, points);
            if (points != row.getInt("Points")) report.note("player faction " + id + " clamped " + row.getInt("Points") + " to " + points);
        }
        result.put("FactionStanding", standings);
        CompoundTag scripts = existing.getCompound("XenoScriptData").copy();
        CompoundTag sourceScripts = source.getCompound("ScriptStoreddata");
        for (String key : sourceScripts.getAllKeys()) {
            Tag value = sourceScripts.get(key);
            if (scripts.contains(key)) {
                if (!scripts.get(key).equals(value)) report.note("player progress conflict: kept Xeno script key " + key);
            } else if (key.length() <= 64 && scripts.size() < 64 && (value instanceof StringTag || value instanceof DoubleTag)) {
                scripts.put(key, value.copy());
            } else report.note("player script key outside native bounds/type: " + key);
        }
        result.put("XenoScriptData", scripts);
        for (String key : source.getAllKeys()) {
            if (!java.util.Set.of("QuestData", "DialogData", "FactionData", "ScriptStoreddata", "UUID", "PlayerName").contains(key))
                report.noteGrouped("player field retained only in source backup: " + key, source.getString("UUID"));
        }
        return result;
    }

    /** Mirrors the normalization {@code QuestObjective.Goal} applies to its parameter. */
    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
