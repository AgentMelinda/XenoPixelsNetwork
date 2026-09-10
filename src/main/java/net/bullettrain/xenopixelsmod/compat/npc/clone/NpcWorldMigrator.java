package net.bullettrain.xenopixelsmod.compat.npc.clone;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.clone.NpcMigrationPlan.Action;
import net.bullettrain.xenopixelsmod.compat.npc.clone.NpcMigrationPlan.Step;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Copies a world's CustomNPCs data across to My NPCs.
 *
 * <p>Clones, quests, dialogs, and scripts are converted conservatively. Other data is copied.
 *
 * <p>Two rules make this safe to run unattended on somebody's server:
 * <ul>
 *   <li><b>Never overwrite.</b> A file My NPCs already has is left exactly as it is.</li>
 *   <li><b>Never modify the source.</b> The CustomNPCs folder is only ever read, so putting the old
 *       mod back is all it takes to go back.</li>
 * </ul>
 *
 * <p>The decision half lives in {@link NpcMigrationPlan}, which has no filesystem access and is
 * tested; this is the thin IO around it.
 */
public final class NpcWorldMigrator {

    private static final String CONTROLLER = "espi.mynpcs.controllers.ServerCloneController";

    private NpcWorldMigrator() {
    }

    /** What a migration run did, for the log and for the command. */
    public record Result(int copied, int converted, int skipped, int failedFiles,
                         String where, String failure) {
        public boolean failed() {
            return failure != null;
        }

        public boolean changedAnything() {
            return copied > 0 || converted > 0;
        }

        public String summary() {
            return failed() ? failure
                    : "copied " + copied + ", converted " + converted + ", skipped " + skipped
                            + ", failed " + failedFiles
                            + " (" + where + ")";
        }
    }

    /** Runs the migration for the currently loaded world. */
    public static Result run() {
        File myNpcs;
        try {
            Class<?> type = Class.forName(CONTROLLER);
            Object controller = type.getField("Instance").get(null);
            if (controller == null) {
                return failure("My NPCs' clone controller has not started yet");
            }
            // <world>/mynpcs/clones -> <world>/mynpcs
            myNpcs = ((File) type.getMethod("getDir").invoke(controller)).getParentFile();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return failure("My NPCs is not installed, or its clone controller has changed: " + e);
        }
        if (myNpcs == null) {
            return failure("Could not locate the My NPCs world folder");
        }
        File customNpcs = new File(myNpcs.getParentFile(), "customnpcs");
        if (!customNpcs.isDirectory()) {
            return new Result(0, 0, 0, 0, customNpcs.getAbsolutePath(), null);
        }

        List<String> source = relativeFiles(customNpcs.toPath());
        List<String> destination = relativeFiles(myNpcs.toPath());
        List<Step> steps = NpcMigrationPlan.plan(source, destination);

        int copied = 0;
        int converted = 0;
        int failedFiles = 0;
        int skipped = NpcMigrationPlan.count(steps, Action.SKIP);
        for (Step step : steps) {
            Path from = customNpcs.toPath().resolve(step.path());
            Path to = myNpcs.toPath().resolve(step.path());
            switch (step.action()) {
                case COPY -> {
                    if (copyFile(from, to)) copied++;
                }
                case CONVERT -> {
                    if (convertFile(step.path(), from, to)) converted++;
                    else failedFiles++;
                }
                case SKIP -> { }
            }
        }
        return new Result(copied, converted, skipped, failedFiles,
                customNpcs.getAbsolutePath(), null);
    }

    /** Every file under a folder, as paths relative to it with '/' separators. */
    private static List<String> relativeFiles(Path root) {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .forEach(path -> out.add(root.relativize(path).toString().replace('\\', '/')));
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Could not list {}: {}", root, e.toString());
        }
        return out;
    }

    private static boolean copyFile(Path from, Path to) {
        try {
            Files.createDirectories(to.getParent());
            // COPY_ATTRIBUTES only; no REPLACE_EXISTING, so a race cannot clobber My NPCs' own file.
            Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
            return true;
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Could not copy {}: {}", from.getFileName(), e.toString());
            return false;
        }
    }

    /**
     * Reads a clone, converts it, and writes the result.
     *
     * @return false when it could not be parsed, in which case the caller copies it verbatim rather
     *         than losing it — a file this does not understand is still the player's data
     */
    private static boolean convertFile(String relativePath, Path from, Path to) {
        try {
            String source = Files.readString(from, StandardCharsets.UTF_8);
            String converted;
            if (relativePath.startsWith("clones/")) {
                CompoundTag clone = TagParser.parseTag(source);
                if (!NpcCloneConverter.isCustomNpcsClone(clone)) return false;
                List<String> dropped = NpcCloneConverter.droppedKeys(clone);
                if (!dropped.isEmpty()) {
                    XenoPixelsMod.LOGGER.info("Clone {} converted; dropped fields My NPCs has no home for: {}",
                            from.getFileName(), String.join(", ", dropped));
                }
                converted = NbtUtils.structureToSnbt(NpcCloneConverter.convert(clone));
            } else if (NpcWorldDataConverter.isStructured(relativePath)) {
                CompoundTag data = TagParser.parseTag(source);
                converted = NbtUtils.structureToSnbt(NpcWorldDataConverter.convertStructured(data));
            } else if (NpcWorldDataConverter.isScript(relativePath)) {
                converted = NpcWorldDataConverter.convertScript(source);
            } else {
                return false;
            }
            writeStringAtomic(to, converted);
            return true;
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Could not convert {}; destination left absent: {}",
                    from.getFileName(), e.toString());
            return false;
        }
    }

    private static void writeStringAtomic(Path to, String value) throws IOException {
        Files.createDirectories(to.getParent());
        Path temporary = Files.createTempFile(to.getParent(), to.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, value, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, to, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, to);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Result failure(String message) {
        return new Result(0, 0, 0, 0, "", message);
    }
}
