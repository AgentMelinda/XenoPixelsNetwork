package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueNbt;
import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.bullettrain.xenopixelsmod.npc.store.XenoFactionNbt;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads source NPC content files and writes Xeno world-store entries without loading source mods. */
public final class NpcImportService {
    private static final long MAX_BLOB_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_TEXT_BYTES = 2L * 1024L * 1024L;

    private NpcImportService() {}

    public static NpcImportReport importFactions(Path worldPath, Path gameDir,
                                                 XenoNpcWorldStore store, boolean dryRun) {
        return importContent(worldPath, gameDir, store, dryRun, false);
    }

    /** Imports all supported shared content from both source mod roots. */
    public static NpcImportReport importAll(Path worldPath, Path gameDir,
                                            XenoNpcWorldStore store, boolean dryRun) {
        return importContent(worldPath, gameDir, store, dryRun, true);
    }

    private static NpcImportReport importContent(Path worldPath, Path gameDir,
                                                 XenoNpcWorldStore store, boolean dryRun,
                                                 boolean libraries) {
        NpcImportReport report = new NpcImportReport();
        List<ForeignNpcRoots.Root> roots = ForeignNpcRoots.discover(worldPath, gameDir);
        if (roots.isEmpty()) {
            report.note("no My NPCs or CustomNPCs content found under " + worldPath + " or " + gameDir);
            return report;
        }

        for (ForeignNpcRoots.Root root : roots) {
            if (root.mod().endsWith("-clones")) continue;
            Path factions = root.path().resolve("factions.dat");
            if (Files.isRegularFile(factions)) {
                try {
                    importFactionBlob(root, factions, store, dryRun, report);
                } catch (IOException | RuntimeException e) {
                    report.failed("factions", root.mod(), message(e));
                    XenoPixelsMod.LOGGER.warn("NPC import: {} failed: {}", factions, e.toString());
                }
            }
            if (libraries) {
                importGrouped(root, "quests", store, dryRun, report);
                importGrouped(root, "dialogs", store, dryRun, report);
            }
        }

        if (report.imported() == 0 && report.failures().isEmpty()) {
            report.note("no supported factions, quests, or dialogs were found in the source folders");
        }
        return report;
    }

    private static void importFactionBlob(ForeignNpcRoots.Root root, Path blob,
                                          XenoNpcWorldStore store, boolean dryRun,
                                          NpcImportReport report) throws IOException {
        long size = Files.size(blob);
        if (size > MAX_BLOB_BYTES) throw new IOException("file exceeds 8 MiB limit: " + size);
        CompoundTag tag = NbtIo.readCompressed(blob, NbtAccounter.create(MAX_BLOB_BYTES));
        Map<String, Integer> slotById = new java.util.HashMap<>();
        for (Map.Entry<Integer, String> mapping : FactionsBlobImport.slotIds(tag).entrySet()) {
            report.mapFaction(root.mod(), mapping.getKey(), mapping.getValue());
            slotById.put(mapping.getValue(), mapping.getKey());
        }
        for (XenoFaction faction : FactionsBlobImport.fanOut(tag, report)) {
            if (store.get(XenoNpcStoreCategory.FACTIONS, "", faction.id()) != null) {
                report.failed("factions", faction.id(), "Xeno entry already exists; left unchanged");
                continue;
            }
            if (dryRun) {
                report.imported(root.mod() + " factions (dry run)", faction.id());
            } else {
                CompoundTag written = XenoFactionNbt.write(faction);
                // The CustomNPCs number, so scripts that address this faction by it still find it.
                Integer slot = slotById.get(faction.id());
                if (slot != null) {
                    written.putString("SourceMod", root.mod());
                    written.putInt("SourceSlot", slot);
                }
                storeOne(store, XenoNpcStoreCategory.FACTIONS, "", faction.id(),
                        written, "factions", report);
            }
        }
    }

    private static void importGrouped(ForeignNpcRoots.Root root, String category,
                                      XenoNpcWorldStore store, boolean dryRun,
                                      NpcImportReport report) {
        Path directory = root.path().resolve(category);
        if (!Files.isDirectory(directory)) return;
        List<Path> files;
        try (var walk = Files.walk(directory, 2)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString)).toList();
        } catch (IOException e) {
            report.failed(category, root.mod(), "could not list source folder: " + e.getMessage());
            return;
        }

        Map<String, Map<Integer, CompoundTag>> groups = new LinkedHashMap<>();
        for (Path file : files) {
            try {
                long size = Files.size(file);
                if (size > MAX_TEXT_BYTES) throw new IOException("file exceeds 2 MiB limit");
                Path relative = directory.relativize(file);
                String sourceGroup = relative.getParent() == null ? "ungrouped"
                        : relative.getParent().toString().replace('\\', '/');
                String group = safeId(root.mod() + "_" + sourceGroup);
                String filename = file.getFileName().toString();
                int slot = Integer.parseInt(filename.substring(0, filename.lastIndexOf('.')));
                CompoundTag tag = ForeignSnbt.parse(Files.readString(file, StandardCharsets.UTF_8));
                if (category.equals("dialogs") && tag.contains("DialogId")
                        && tag.getInt("DialogId") != slot) {
                    report.failed(category, file.toString(), "DialogId " + tag.getInt("DialogId")
                            + " does not match filename slot " + slot);
                    continue;
                }
                if (root.mod().equals("customnpcs")) {
                    tag = net.bullettrain.xenopixelsmod.compat.npc.clone.NpcWorldDataConverter
                            .convertStructured(tag);
                }
                CompoundTag duplicate = groups.computeIfAbsent(group, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(slot, tag);
                if (duplicate != null) report.failed(category, file.toString(),
                        "duplicate numeric slot " + slot);
            } catch (IOException | RuntimeException e) {
                report.failed(category, file.toString(), message(e));
            }
        }

        for (Map.Entry<String, Map<Integer, CompoundTag>> group : groups.entrySet()) {
            if (category.equals("dialogs")) {
                DialogTreeImport.Conversion conversion = DialogTreeImport.convert(
                        group.getValue(), report, group.getKey(), root.mod());
                for (Map.Entry<Integer, DialogTreeImport.Ref> mapping : conversion.sourceSlots().entrySet()) {
                    report.mapDialog(root.mod(), mapping.getKey(), mapping.getValue());
                }
                Map<String, Integer> slotById = new java.util.HashMap<>();
                for (Map.Entry<Integer, DialogTreeImport.Ref> mapping : conversion.sourceSlots().entrySet()) {
                    slotById.put(mapping.getValue().id(), mapping.getKey());
                }
                for (DialogTreeImport.Imported dialog : conversion.dialogues()) {
                    CompoundTag tag = XenoDialogueNbt.write(dialog.dialogue());
                    tag.putString("Name", dialog.id());
                    Integer slot = slotById.get(dialog.id());
                    if (slot != null) {
                        tag.putString("SourceMod", root.mod());
                        tag.putInt("SourceSlot", slot);
                    }
                    storeGrouped(store, XenoNpcStoreCategory.DIALOGS, group.getKey(), dialog.id(),
                            tag, category, dryRun, report);
                }
                continue;
            }
            for (Map.Entry<Integer, CompoundTag> questEntry : group.getValue().entrySet()) {
                int slot = questEntry.getKey();
                String id = safeId(group.getKey() + "_q" + slot);
                QuestImport.Imported quest = QuestImport.convert(slot, id, group.getKey(),
                        questEntry.getValue(), report);
                if (quest == null) continue;
                report.mapQuest(root.mod(), slot, id);
                CompoundTag tag = new CompoundTag();
                tag.putString("Name", quest.quest().title());
                tag.putString("DefinitionJson", QuestImport.toJson(quest.quest()));
                tag.putString("SourceMod", root.mod());
                tag.putInt("SourceSlot", slot);
                storeGrouped(store, XenoNpcStoreCategory.QUESTS, group.getKey(), id, tag,
                        category, dryRun, report);
            }
        }
    }

    private static void storeGrouped(XenoNpcWorldStore store, XenoNpcStoreCategory category,
                                     String group, String id, CompoundTag tag, String label,
                                     boolean dryRun, NpcImportReport report) {
        if (store.get(category, group, id) != null) {
            report.failed(label, group + "/" + id, "Xeno entry already exists; left unchanged");
            return;
        }
        if (dryRun) report.imported(label + " (dry run)", group + "/" + id);
        else storeOne(store, category, group, id, tag, label, report);
    }

    private static void storeOne(XenoNpcWorldStore store, XenoNpcStoreCategory category,
                                 String group, String id, CompoundTag tag, String label,
                                 NpcImportReport report) {
        String refusal = store.put(category, group, id, tag);
        if (refusal == null) report.imported(label, group.isBlank() ? id : group + "/" + id);
        else report.failed(label, group.isBlank() ? id : group + "/" + id, refusal);
    }

    private static String safeId(String raw) {
        return new SlotIndex().assign(0, raw);
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }
}
