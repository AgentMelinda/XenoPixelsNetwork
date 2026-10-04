package net.bullettrain.xenopixelsmod.npc.importer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

/**
 * Reads My NPCs / CustomNPCs content files.
 *
 * <p>Their {@code .json} is not JSON and is not quite SNBT. Real content files in a live save
 * contain <b>raw, unescaped newlines inside quoted strings</b> — their writer escapes quotes but
 * not line breaks.
 *
 * <p>{@code docs/xeno-npc-schema.md} warned that {@link TagParser} is "not obliged to accept"
 * that. Tested against NeoForge 21.1.248, it does: the string reader treats a newline as an
 * ordinary character and hands it back intact.
 *
 * <p>An earlier version of this class escaped those newlines before parsing, and <em>that</em> is
 * what failed — Mojang's parser accepts only a backslash and a quote as escape sequences and
 * rejects an escaped-n as invalid. The escaping pass was the bug, not the newlines. It is recorded
 * here because the warning that prompted it was reasonable and wrong, and the next reader deserves
 * to know it was actually measured.
 *
 * <p>{@code rawNewlinesSurviveTheParser} pins the behaviour, so a future Minecraft version that
 * does tighten this is caught by a test rather than by somebody's migration.
 *
 * <p>Deliberately names no class from either mod. An operator migrating away from My NPCs will not
 * keep it installed in order to be migrated away from, so anything that needed it on the classpath
 * would be unable to run at the moment it is wanted.
 */
public final class ForeignSnbt {

    /** One file failed. Carries enough to name it without taking the whole import down. */
    public static final class ParseFailure extends RuntimeException {
        public ParseFailure(String message, Throwable cause) {
            super(message, cause);
        }

        public ParseFailure(String message) {
            super(message);
        }
    }

    private ForeignSnbt() {
    }

    /**
     * Parses one of their files.
     *
     * @throws ParseFailure when the text is not a tag at all; callers report it per file so one bad
     *                      entry costs one entry rather than the whole import
     */
    public static CompoundTag parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ParseFailure("empty file");
        }
        try {
            return TagParser.parseTag(raw);
        } catch (Exception e) {
            throw new ParseFailure("not readable as a tag: " + e.getMessage(), e);
        }
    }
}
