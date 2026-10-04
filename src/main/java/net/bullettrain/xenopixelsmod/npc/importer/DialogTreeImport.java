package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts MyNPCs' per-node dialog files into Xeno's per-tree representation. */
public final class DialogTreeImport {
    public record Imported(String id, XenoDialogue dialogue) {}
    public record Ref(String group, String id) {}
    public record Conversion(List<Imported> dialogues, Map<Integer, Ref> sourceSlots) {}

    private DialogTreeImport() {}

    /** One category folder at a time; links outside the folder are reported and omitted. */
    public static Conversion convert(Map<Integer, CompoundTag> source,
                                     NpcImportReport report, String group) {
        return convert(source, report, group, "mynpcs");
    }

    public static Conversion convert(Map<Integer, CompoundTag> source,
                                     NpcImportReport report, String group, String sourceMod) {
        if (source == null || source.isEmpty()) return new Conversion(List.of(), Map.of());
        Map<Integer, String> nodeIds = new LinkedHashMap<>();
        source.keySet().stream().sorted().forEach(slot -> nodeIds.put(slot, "n" + slot));

        List<Imported> out = new ArrayList<>();
        Map<Integer, Ref> sourceRefs = new LinkedHashMap<>();
        // Each source node is a valid NPC assignment target. Make it a tree entry point so an NPC
        // linked directly to a branch starts at that branch, while option edges still reach the
        // same reachable descendants.
        for (int root : source.keySet().stream().sorted().toList()) {
            Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
            Set<Integer> emitted = new LinkedHashSet<>();
            collect(root, source, nodeIds, nodes, emitted, report, group, sourceMod);
            if (!nodes.isEmpty()) {
                if (nodes.size() > XenoDialogueNbt.MAX_NODES) {
                    report.failed("dialogs", group + "/" + root,
                            "reachable tree has " + nodes.size() + " nodes; Xeno supports at most "
                                    + XenoDialogueNbt.MAX_NODES);
                    continue;
                }
                String id = "dialog_" + root;
                out.add(new Imported(id, new XenoDialogue(nodeIds.get(root), nodes)));
                sourceRefs.put(root, new Ref(group, id));
            }
        }
        return new Conversion(List.copyOf(out), Map.copyOf(sourceRefs));
    }

    private static void collect(int slot, Map<Integer, CompoundTag> source,
                                Map<Integer, String> nodeIds, Map<String, XenoDialogue.Node> nodes,
                                Set<Integer> emitted, NpcImportReport report, String group,
                                String sourceMod) {
        if (emitted.contains(slot)) return;
        CompoundTag raw = source.get(slot);
        if (raw == null) return;
        emitted.add(slot);
        String sourceText = raw.getString("DialogText");
        if (sourceText.length() > XenoDialogueNbt.MAX_TEXT) {
            report.note("dialog " + group + "/" + slot
                    + " text was truncated to Xeno's " + XenoDialogueNbt.MAX_TEXT + "-character limit");
        }
        String text = text(sourceText, "", XenoDialogueNbt.MAX_TEXT);
        nodes.put(nodeIds.get(slot), new XenoDialogue.Node(text, List.of()));
        if (raw.contains("DialogCommand") && !raw.getString("DialogCommand").isBlank()) {
            report.note("dialog " + group + "/" + slot
                    + " has a source command; it was omitted for server-command safety");
        }
        if (hasActiveAvailability(raw)) {
            report.note("dialog " + group + "/" + slot
                    + " has source availability conditions; Xeno dialogue does not currently gate trees by those rules");
        }
        List<XenoDialogue.Option> options = new ArrayList<>();
        ListTag rawOptions = raw.getList("Options", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(rawOptions.size(), 16); i++) {
            CompoundTag option = rawOptions.getCompound(i).getCompound("Option");
            int type = option.getInt("OptionType");
            String sourceLabel = option.getString("Title");
            if (sourceLabel.length() > XenoDialogueNbt.MAX_OPTION_TEXT) {
                report.note("dialog " + group + "/" + slot + " option title was truncated to Xeno's "
                        + XenoDialogueNbt.MAX_OPTION_TEXT + "-character limit");
            }
            String label = text(sourceLabel, "...", XenoDialogueNbt.MAX_OPTION_TEXT);
            String command = option.getString("DialogCommand").replace("@dp", "{player}").trim();
            if (!command.isBlank()) {
                options.add(new XenoDialogue.Option(label, XenoDialogue.OptionType.COMMAND,
                        "", "", command));
                continue;
            }
            if (type == 0) {
                options.add(new XenoDialogue.Option(label, XenoDialogue.OptionType.QUIT,
                        "", "", ""));
            } else if (type == 1) {
                int target = option.getInt("Dialog");
                String targetId = nodeIds.get(target);
                if (targetId == null) {
                    report.note("dialog " + group + "/" + slot + " option '" + label
                            + "' targets missing dialog slot " + target);
                    options.add(new XenoDialogue.Option(label, XenoDialogue.OptionType.QUIT,
                            "", "", ""));
                } else {
                    options.add(new XenoDialogue.Option(label, XenoDialogue.OptionType.TEXT,
                            targetId, "", ""));
                    collect(target, source, nodeIds, nodes, emitted, report, group, sourceMod);
                }
            } else {
                // MyNPCs 1.5.0 marks option type 2 invalid in DialogOption.isValid().
                report.note("dialog " + group + "/" + slot + " skipped invalid option type " + type);
            }
        }
        if (rawOptions.size() > 16) report.note("dialog " + group + "/" + slot
                + " has options beyond Xeno's 16-option limit");
        if (raw.contains("DialogQuest") && raw.getInt("DialogQuest") >= 0) {
            int sourceQuest = raw.getInt("DialogQuest");
            String quest = report.quest(sourceMod, sourceQuest);
            if (quest == null) {
                report.note("dialog " + group + "/" + slot
                        + " references quest slot " + sourceQuest + " which was not imported");
            } else if (options.size() >= 16) {
                report.note("dialog " + group + "/" + slot
                        + " cannot show quest " + quest + ": option limit reached");
            } else {
                options.add(new XenoDialogue.Option("Accept quest", XenoDialogue.OptionType.QUEST,
                        "", quest, ""));
                report.note("dialog " + group + "/" + slot
                        + " source quest action became an explicit Accept quest choice");
            }
        }
        nodes.put(nodeIds.get(slot), new XenoDialogue.Node(text, List.copyOf(options)));
    }

    private static boolean hasActiveAvailability(CompoundTag raw) {
        for (String key : raw.getAllKeys()) {
            if (!key.startsWith("Availability")) continue;
            if (key.equals("AvailabilityMinPlayerLevel")) {
                if (raw.getInt(key) > 0) return true;
            } else if (key.matches("Availability(Quest|Faction|Dialog|Scoreboard|DayTime)[2-4]?(Id)?")) {
                if (key.endsWith("Id")) {
                    if (raw.getInt(key) >= 0) return true;
                } else if (raw.getBoolean(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String text(String value, String fallback, int max) {
        if (value == null) return fallback;
        String clean = value.trim();
        if (clean.length() > max) clean = clean.substring(0, max);
        return clean.isEmpty() ? fallback : clean;
    }
}
