package net.bullettrain.xenopixelsmod.compat.npc.clone;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Converts CustomNPCs clone files into real My NPCs clones, on disk.
 *
 * <p>The companion to {@code ServerCloneImportMixin}, which converts on read and leaves the files
 * alone. This is the permanent version: it writes through My NPCs' own {@code saveClone}, so the mod
 * owns the write, its cache stays correct, and the result is byte-for-byte a clone the fork saved.
 *
 * <p>Reflective throughout. My NPCs updates often, and an import that stops working is a far better
 * failure than a mod that will not load; every lookup here reports what it could not find instead of
 * throwing into the command dispatcher.
 */
public final class NpcCloneImport {

    private static final String CONTROLLER = "espi.mynpcs.controllers.ServerCloneController";

    private NpcCloneImport() {
    }

    /** What an import run did, for reporting back to whoever asked for it. */
    public record Result(int converted, int skipped, List<String> names, String failure) {
        public boolean failed() {
            return failure != null;
        }
    }

    /**
     * Converts every CustomNPCs clone found beside My NPCs' own clone folder.
     *
     * @param tab the clone tab to import, or -1 for every tab found
     */
    public static Result run(int tab) {
        Object controller;
        File myNpcsClones;
        try {
            Class<?> type = Class.forName(CONTROLLER);
            controller = type.getField("Instance").get(null);
            if (controller == null) {
                return failure("My NPCs' clone controller has not started yet");
            }
            myNpcsClones = (File) type.getMethod("getDir").invoke(controller);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return failure("My NPCs is not installed, or its clone controller has changed: " + e);
        }

        File customNpcsClones = siblingCustomNpcsClones(myNpcsClones);
        if (customNpcsClones == null || !customNpcsClones.isDirectory()) {
            return failure("No CustomNPCs clone folder beside " + myNpcsClones.getAbsolutePath());
        }

        List<String> imported = new ArrayList<>();
        int skipped = 0;
        File[] tabs = customNpcsClones.listFiles(File::isDirectory);
        if (tabs == null) {
            return failure("Could not list " + customNpcsClones.getAbsolutePath());
        }
        for (File tabDir : tabs) {
            int tabId = parseTab(tabDir.getName());
            if (tabId < 0 || (tab >= 0 && tabId != tab)) {
                continue;
            }
            File[] files = tabDir.listFiles(f -> f.getName().toLowerCase(Locale.ROOT).endsWith(".json"));
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String name = file.getName().substring(0, file.getName().length() - ".json".length());
                CompoundTag clone = read(file);
                if (clone == null || !NpcCloneConverter.isCustomNpcsClone(clone)) {
                    skipped++;
                    continue;
                }
                List<String> dropped = NpcCloneConverter.droppedKeys(clone);
                if (!dropped.isEmpty()) {
                    // Said once, out loud: losing a companion NPC's owner should not be a surprise
                    // discovered later.
                    XenoPixelsMod.LOGGER.info("Clone {} converted; dropped fields My NPCs has no "
                            + "home for: {}", name, String.join(", ", dropped));
                }
                if (save(controller, tabId, name, NpcCloneConverter.convert(clone))) {
                    imported.add(name);
                } else {
                    skipped++;
                }
            }
        }
        return new Result(imported.size(), skipped, imported, null);
    }

    /**
     * CustomNPCs' clone folder, alongside My NPCs' own.
     *
     * <p>My NPCs keeps clones at {@code <save>/<mod>/clones}, so the other mod's is the same path
     * with its own folder name — derived rather than hard-coded so this still works whatever the
     * save is called or wherever the instance lives.
     */
    private static File siblingCustomNpcsClones(File myNpcsClones) {
        File modDir = myNpcsClones.getParentFile();
        if (modDir == null) {
            return null;
        }
        File saveDir = modDir.getParentFile();
        return saveDir == null ? null : new File(new File(saveDir, "customnpcs"), "clones");
    }

    private static int parseTab(String name) {
        try {
            return Integer.parseInt(name.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static CompoundTag read(File file) {
        try {
            return TagParser.parseTag(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Could not read clone {}: {}", file.getName(), e.toString());
            return null;
        }
    }

    private static boolean save(Object controller, int tab, String name, CompoundTag clone) {
        try {
            controller.getClass()
                    .getMethod("saveClone", int.class, String.class, CompoundTag.class)
                    .invoke(controller, tab, name, clone);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            XenoPixelsMod.LOGGER.warn("Could not save converted clone {}: {}", name, e.toString());
            return false;
        }
    }

    private static Result failure(String message) {
        return new Result(0, 0, List.of(), message);
    }
}
