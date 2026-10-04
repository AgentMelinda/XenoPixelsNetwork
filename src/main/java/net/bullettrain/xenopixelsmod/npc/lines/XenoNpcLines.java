package net.bullettrain.xenopixelsmod.npc.lines;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The things an NPC can say, grouped by what prompted it.
 *
 * <p>Categories follow {@code doco.md} section 3, which lists attack / interact / kill / killed /
 * random / world lines. NPC-to-NPC conversation is a job in the reference rather than a line
 * category, so it is not here.
 *
 * <pre>
 * {
 *   "interact": ["Hey.", "Still here?", "Busy."],
 *   "attack":   ["Come on then!"],
 *   "random":   ["..."]
 * }
 * </pre>
 *
 * <p>A category that is absent simply has no lines, which is the normal case - most NPCs only ever
 * define {@code interact}.
 */
public record XenoNpcLines(Map<Category, List<String>> byCategory) {

    /** What prompted the line. */
    public enum Category {
        /** A player right-clicked the NPC. Cycles, the way CustomNPCs does. */
        INTERACT,
        /** The NPC started attacking. */
        ATTACK,
        /** The NPC killed something. */
        KILL,
        /** The NPC was killed. */
        KILLED,
        /** Said occasionally while idle. */
        RANDOM,
        /** Said to nobody in particular, for ambient world flavour. */
        WORLD,
        /**
         * Said to another NPC standing nearby (native Xeno, MyNPCs or CustomNPCs); {@code {npc}} is
         * that NPC's name. A native Xeno NPC addressed this way answers with its own NPC line.
         */
        NPC;

        static Category parse(String raw) {
            if (raw == null) {
                return null;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** An NPC with nothing to say, so callers never have to null-check. */
    public static final XenoNpcLines EMPTY = new XenoNpcLines(Map.of());

    public List<String> lines(Category category) {
        return byCategory.getOrDefault(category, List.of());
    }

    public boolean has(Category category) {
        return !lines(category).isEmpty();
    }

    /**
     * The line at {@code index}, wrapping around.
     *
     * <p>Wrapping rather than clamping is what makes repeated clicks cycle through everything and
     * start again, instead of sticking on the last line. The caller owns the index so the sequence
     * can live server-side and be the same for every player watching.
     */
    public String at(Category category, int index) {
        List<String> lines = lines(category);
        if (lines.isEmpty()) {
            return "";
        }
        return lines.get(Math.floorMod(index, lines.size()));
    }

    public static XenoNpcLines fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("lines must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();

        Map<Category, List<String>> parsed = new EnumMap<>(Category.class);
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            Category category = Category.parse(entry.getKey());
            if (category == null) {
                // A misspelled category is a pack bug worth naming, not something to absorb: the
                // NPC would otherwise just never say those lines with no hint why.
                throw new IllegalArgumentException("unknown line category '" + entry.getKey() + "'");
            }
            parsed.put(category, stringList(entry.getValue(), entry.getKey()));
        }
        return new XenoNpcLines(Map.copyOf(parsed));
    }

    private static List<String> stringList(JsonElement element, String category) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("category '" + category + "' must be an array");
        }
        JsonArray array = element.getAsJsonArray();
        List<String> out = new ArrayList<>(array.size());
        for (JsonElement raw : array) {
            String line = raw.getAsString();
            if (line != null && !line.isBlank()) {
                out.add(line);
            }
        }
        return List.copyOf(out);
    }
}
