package net.bullettrain.xenopixelsmod.npc.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Their integer slots, and the string ids we give them.
 *
 * <p>My NPCs keys factions, dialogs, quests and transport entries by an int slot; ours are strings
 * that become filenames. Every import therefore needs this map, and an NPC's {@code FactionID} and
 * every {@code AttackFactions} entry is resolved through it.
 *
 * <p>Ids are derived rather than rejected. {@code XenoNpcStorePaths} refuses a bad id rather than
 * rewriting it, which is right for content an operator typed — they can go and fix it. An import
 * has nobody to ask, so it must produce something legal from whatever the file holds, and say what
 * it did.
 */
public final class SlotIndex {

    /** Matches {@code XenoNpcStorePaths}: ids are a filesystem boundary. */
    private static final int MAX_ID_LENGTH = 64;

    /** Cannot be created on Windows at all, whatever the extension. */
    private static final Set<String> RESERVED = Set.of(
            "con", "nul", "prn", "aux",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private final Map<Integer, String> bySlot = new LinkedHashMap<>();
    private final Set<String> taken = new java.util.LinkedHashSet<>();
    private final List<String> renames = new ArrayList<>();

    /**
     * Gives one slot an id derived from its name, and remembers it.
     *
     * @return the id, which is unique within this index
     */
    public String assign(int slot, String name) {
        String base = sanitise(name);
        if (base.isEmpty()) {
            // Stable and traceable rather than random: an operator can find it by slot.
            base = "faction_" + slot;
        }
        if (RESERVED.contains(base)) {
            // con.json cannot exist on Windows, so an import that produced one would fail there
            // and succeed on Linux - a world that depends on the operator's filesystem.
            base = base + "_";
        }
        String id = base;
        int suffix = 2;
        while (taken.contains(id)) {
            String tail = "_" + suffix++;
            int room = Math.max(1, MAX_ID_LENGTH - tail.length());
            id = (base.length() > room ? base.substring(0, room) : base) + tail;
        }
        taken.add(id);
        bySlot.put(slot, id);
        // Reported only when it is worth acting on. "Friendly" becoming "friendly" is case
        // folding, which every id undergoes and which nobody needs to be told about; the first
        // real import buried five genuinely useful lines under five of those. A rename that
        // dropped characters, or took a collision suffix, is a different matter.
        if (name != null && !id.equalsIgnoreCase(name)) {
            renames.add("slot " + slot + ": \"" + name + "\" -> " + id);
        }
        return id;
    }

    /** The id for one of their slots, or null when nothing claimed it. */
    public String idFor(int slot) {
        return bySlot.get(slot);
    }

    /** Every id this index handed out, in slot order. */
    public Map<Integer, String> all() {
        return Map.copyOf(bySlot);
    }

    /** Every name that did not survive as itself, with what it became. */
    public List<String> renames() {
        return List.copyOf(renames);
    }

    /**
     * Folds a free-text name onto the id alphabet.
     *
     * <p>Lower case is not cosmetic: NTFS treats {@code Guards} and {@code guards} as one file and
     * ext4 as two, so accepting both would make a world behave differently per filesystem. Folding
     * them together turns that into a visible collision this class then resolves.
     */
    static String sanitise(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        boolean lastWasSeparator = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean legal = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.';
            if (legal) {
                out.append(c);
                lastWasSeparator = c == '_' || c == '-' || c == '.';
                continue;
            }
            // Whitespace is a word break and becomes one separator however long the run is.
            // Every other illegal character - an apostrophe, an exclamation mark - is simply
            // dropped, so "The King's  Men!" reads as "the_kings_men" and not "the_king_s__men_".
            if (Character.isWhitespace(c)) {
                if (!lastWasSeparator && out.length() > 0) {
                    out.append('_');
                    lastWasSeparator = true;
                }
            }
        }
        String trimmed = trimSeparators(out.toString());
        return trimmed.length() > MAX_ID_LENGTH
                ? trimSeparators(trimmed.substring(0, MAX_ID_LENGTH))
                : trimmed;
    }

    /** The store rejects a leading dot or dash and a trailing dot, so none may be produced. */
    private static String trimSeparators(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSeparator(value.charAt(start))) {
            start++;
        }
        while (end > start && isSeparator(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isSeparator(char c) {
        return c == '_' || c == '-' || c == '.';
    }
}
