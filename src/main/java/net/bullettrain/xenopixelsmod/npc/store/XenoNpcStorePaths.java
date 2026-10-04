package net.bullettrain.xenopixelsmod.npc.store;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Turns an id into a file path, and refuses the ones that must not become one.
 *
 * <p>Ids arrive from an operator's editor over the network and end up as filenames, so this is a
 * security boundary rather than tidiness. It has no filesystem access at all, which is what makes
 * it testable on its own - the same split {@code NpcMigrationPlan} and {@code NpcWorldMigrator}
 * already use, where the decisions are pure and the IO is thin.
 *
 * <p><b>Rejects, never rewrites.</b> Silently sanitising {@code My Faction} into {@code my_faction}
 * means the operator's next lookup misses and the entry appears to have vanished. A refusal with a
 * reason is something they can act on.
 */
public final class XenoNpcStorePaths {

    /** Long enough for a readable name, short enough for every filesystem. */
    public static final int MAX_ID = 64;

    /** Group names are folder names under the category, and bound by the same rules. */
    public static final int MAX_GROUP = 64;

    public static final String EXTENSION = ".json";

    /**
     * Names Windows refuses to create a file called, with or without an extension.
     *
     * <p>A server running on Windows would otherwise fail to save a faction called {@code con} with
     * an IO error that says nothing about why.
     */
    private static final Set<String> RESERVED = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private XenoNpcStorePaths() {
    }

    /** Why an id was refused, or null when it is fine. */
    public static String reject(String id) {
        return rejectSegment(id, MAX_ID, "id");
    }

    /** Why a group name was refused, or null when it is fine. */
    public static String rejectGroup(String group) {
        return rejectSegment(group, MAX_GROUP, "group");
    }

    private static String rejectSegment(String raw, int max, String what) {
        if (raw == null || raw.isEmpty()) {
            return "the " + what + " is empty";
        }
        if (raw.length() > max) {
            return "the " + what + " is longer than " + max + " characters";
        }
        if (!raw.equals(raw.toLowerCase(Locale.ROOT))) {
            // Lower-cased throughout, because NTFS treats Guards and guards as one file and ext4
            // treats them as two. Accepting both spellings means a world that behaves differently
            // depending on the operator's filesystem.
            return "the " + what + " must be lower case";
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.';
            if (!allowed) {
                return "the " + what + " may only contain a-z, 0-9, '_', '-' and '.'";
            }
        }
        char first = raw.charAt(0);
        if (first == '.' || first == '-') {
            return "the " + what + " may not start with '" + first + "'";
        }
        if (raw.charAt(raw.length() - 1) == '.') {
            // Windows silently strips it, so "x." and "x" would be the same file on one OS and two
            // on another.
            return "the " + what + " may not end with '.'";
        }
        if (raw.equals(".") || raw.equals("..")) {
            return "the " + what + " may not be '.' or '..'";
        }
        String withoutExtension = raw.contains(".") ? raw.substring(0, raw.indexOf('.')) : raw;
        if (RESERVED.contains(withoutExtension)) {
            return "'" + withoutExtension + "' is a reserved device name on Windows";
        }
        return null;
    }

    public static boolean isValidId(String id) {
        return reject(id) == null;
    }

    public static boolean isValidGroup(String group) {
        return rejectGroup(group) == null;
    }

    /**
     * The file for one entry, or null when anything about it is refused.
     *
     * <p>The character rules above already make {@code /}, {@code \}, {@code :} and {@code ..}
     * impossible, so the containment check at the end is redundant today. It is here because the
     * character rules are exactly what a later refactor loosens, and a traversal that only a
     * charset check was stopping is a bad way to find that out.
     */
    public static Path resolve(Path root, XenoNpcStoreCategory category, String group, String id) {
        if (root == null || category == null || !isValidId(id)) {
            return null;
        }
        Path directory = root.resolve(category.folder());
        if (category.grouped()) {
            if (!isValidGroup(group)) {
                return null;
            }
            directory = directory.resolve(group);
        } else if (group != null && !group.isEmpty()) {
            // An ungrouped category given a group is a caller bug, not a path to invent.
            return null;
        }
        Path resolved = directory.resolve(id + EXTENSION).normalize();
        return resolved.startsWith(root.normalize()) ? resolved : null;
    }

    /** The id a filename carries, or null when the name is not one we would have written. */
    public static String idFromFileName(String fileName) {
        if (fileName == null || !fileName.endsWith(EXTENSION)) {
            return null;
        }
        String id = fileName.substring(0, fileName.length() - EXTENSION.length());
        return isValidId(id) ? id : null;
    }
}
