package net.bullettrain.xenopixelsmod.client.anim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes an edited clip back into the mod's own shipped animation file - development runs only.
 *
 * <p>Editing a shipped BT3 clip normally ends with a copy in the config directory bound over the
 * intent it came from, which needs no restart and touches nothing tracked. That is the right answer
 * for a player. For the person building the mod it is not: the edit belongs in
 * {@code src/main/resources/.../bt3_combat.animation.json}, in the repository.
 *
 * <p>So when that file is reachable from the run directory - which it is under {@code runClient} and
 * is not in a shipped game - the studio offers to merge the edit into it. This writes tracked
 * repository source from inside a running game, so it follows the same discipline
 * {@code dmz/form/DmzFormEditorService} uses for DragonMineZ configs: remember, back up, write a
 * temporary file, move it into place atomically.
 */
public final class XenoClipSourceExport {

    private static final String RELATIVE_SOURCE =
            "../src/main/resources/assets/xenopixelsmod/animations/entity/bt3_combat.animation.json";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private XenoClipSourceExport() {}

    /** The repository copy of the shipped animation file, or null when this is not a dev run. */
    public static Path sourceFile() {
        try {
            Path candidate = FMLPaths.GAMEDIR.get().resolve(RELATIVE_SOURCE).normalize();
            return Files.isRegularFile(candidate) ? candidate : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean available() {
        return sourceFile() != null;
    }

    /** Where backups of the source file are kept; inside the config tree, never in the repository. */
    public static Path backupDir() {
        return XenoAnimClip.dir().resolve(".source-backups");
    }

    /**
     * Merges {@code clip} into the shipped file under {@code animationName}.
     *
     * @param animationName the full GeckoLib name to write under, e.g. {@code combat.xeno_jab_right}
     * @return the backup that was taken first
     * @throws IOException when this is not a development run, or the write fails
     */
    public static Path export(XenoAnimClip clip, String animationName) throws IOException {
        Path source = sourceFile();
        if (source == null) {
            throw new IOException("Not a development run: no src/main/resources beside the game directory");
        }
        if (clip == null) throw new IOException("Nothing to export");
        String name = animationName == null || animationName.isBlank()
                ? clip.animationName() : animationName.trim();

        String original = Files.readString(source);
        JsonObject root = XenoAnimClip.GSON.fromJson(original, JsonObject.class);
        if (root == null || !root.has("animations")) {
            throw new IOException("Shipped animation file has no animations object");
        }

        Path backup = backup(source, original);

        // Take the single animation out of the clip's own export, so the exported shape is exactly
        // what a standalone clip file would have held.
        JsonObject exported = XenoAnimClip.GSON
                .fromJson(clip.toGeckoJson(), JsonObject.class)
                .getAsJsonObject("animations");
        JsonElement body = exported.entrySet().iterator().next().getValue();
        root.getAsJsonObject("animations").add(name, body);

        Path temp = source.resolveSibling(source.getFileName() + ".xeno-tmp");
        Files.writeString(temp, XenoAnimClip.GSON.toJson(root));
        try {
            Files.move(temp, source, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            // Some filesystems cannot move atomically; a plain replace still beats writing in place.
            Files.move(temp, source, StandardCopyOption.REPLACE_EXISTING);
        }
        XenoPixelsMod.LOGGER.info("Exported {} into {} (backup at {})", name, source, backup);
        return backup;
    }

    private static Path backup(Path source, String contents) throws IOException {
        Path dir = backupDir();
        Files.createDirectories(dir);
        Path backup = dir.resolve(LocalDateTime.now().format(STAMP) + "-" + source.getFileName());
        Files.writeString(backup, contents);
        return backup;
    }
}
