package net.bullettrain.xenopixelsmod.npc.store;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reading and writing one SNBT file.
 *
 * <p>SNBT in a {@code .json} file is what My NPCs and CustomNPCs both use, and what makes a store
 * file something an operator can open and read. We write it with {@link NbtUtils#structureToSnbt}
 * and read it with {@link TagParser}, the same pair {@code NpcWorldMigrator} already uses to convert
 * between those two mods - so the format is not a guess.
 *
 * <p>Deliberately <em>not</em> their dialect. My NPCs writes through its own {@code NBTJsonUtil},
 * which escapes quotes but leaves literal newlines inside quoted strings; real dialogue files in
 * this repo's test worlds contain them. That is neither valid JSON nor reliably valid SNBT, and
 * inheriting the quirk would mean inheriting a parser hazard for no benefit. Reading <em>their</em>
 * files is the importer's problem, and it can deal with it there.
 */
public final class SnbtFiles {

    /**
     * The largest file that will be read.
     *
     * <p>Checked with {@link Files#size} <em>before</em> the file is opened, so a hand-dropped
     * multi-gigabyte file is never pulled into a String to find out how big it was.
     */
    public static final long MAX_BYTES = 256L * 1024L;

    /**
     * How deeply a file may nest.
     *
     * <p>Scanned on the raw text before {@link TagParser} sees it. Whether {@code TagParser} has a
     * recursion limit of its own is not something to depend on: a counting pass over the characters
     * costs nothing and makes the guarantee true either way.
     */
    public static final int MAX_DEPTH = 32;

    private SnbtFiles() {
    }

    /** What went wrong, for a caller that wants to report it rather than throw. */
    public static final class StoreIoException extends IOException {
        public StoreIoException(String message) {
            super(message);
        }

        public StoreIoException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Reads one file, refusing anything outside its bounds.
     *
     * @throws StoreIoException when the file is too large, too deep, or not parseable
     */
    public static CompoundTag read(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_BYTES) {
            throw new StoreIoException(
                    "file is " + size + " bytes, over the " + MAX_BYTES + " byte limit");
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        int depth = depthOf(text);
        if (depth > MAX_DEPTH) {
            throw new StoreIoException("nesting is " + depth + " deep, over the limit of "
                    + MAX_DEPTH);
        }
        try {
            return TagParser.parseTag(text);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException e) {
            // Checked, because TagParser reports a syntax error the way a command would. Either way
            // a file we cannot read is reported and skipped, never guessed at.
            throw new StoreIoException("not readable as SNBT: " + e.getMessage(), e);
        }
    }

    /**
     * Writes one file, atomically.
     *
     * <p>Temp file then move, so a crash mid-write leaves the previous version intact rather than a
     * half-written one. Lifted from {@code NpcWorldMigrator.writeStringAtomic}, including its
     * fallback for filesystems that cannot move atomically - that discipline exists precisely
     * because an operator's data is on the line.
     */
    public static void write(Path file, CompoundTag tag) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(),
                ".tmp");
        try {
            Files.writeString(temporary, NbtUtils.structureToSnbt(tag), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            // On success the move consumed it; on failure this is what stops .tmp files piling up
            // next to the real ones.
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * How deeply braces and brackets nest, ignoring anything inside a quoted string.
     *
     * <p>Quote-aware because a dialogue line may perfectly well contain a {@code {} - {player} is
     * the placeholder every shipped dialogue uses - and counting those would refuse ordinary files.
     */
    static int depthOf(String text) {
        int depth = 0;
        int deepest = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{', '[' -> deepest = Math.max(deepest, ++depth);
                case '}', ']' -> depth--;
                default -> { }
            }
        }
        return deepest;
    }
}
