package net.bullettrain.xenopixelsmod.npc.dialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A branching conversation attached to an NPC, loaded from a datapack.
 *
 * <p>A dialogue is a map of nodes; {@code start} names the one interaction opens with. Each node has
 * text and a list of options, and each option says what choosing it does. Keeping it a flat map
 * rather than a nested tree means a node can be reached from several places without duplicating it,
 * which is how "back to the top" works.
 *
 * <pre>
 * {
 *   "start": "root",
 *   "nodes": {
 *     "root": {
 *       "text": "So you want to train, {player}?",
 *       "options": [
 *         { "text": "Tell me more", "type": "text",    "target": "more" },
 *         { "text": "I will do it", "type": "quest",   "quest": "xenopixelsmod:kill_mobs" },
 *         { "text": "Not now",      "type": "quit" }
 *       ]
 *     }
 *   }
 * }
 * </pre>
 */
public record XenoDialogue(String start, Map<String, Node> nodes) {

    /** What choosing an option does. Unknown values parse as {@link #QUIT} rather than failing. */
    public enum OptionType {
        /** Move to another node. */
        TEXT,
        /** Offer the named quest; NPC hand-in is checked separately on interaction. */
        QUEST,
        /** Run a server command. Gated by config; see {@code XenoDialogueRuntime}. */
        COMMAND,
        /** Close the conversation. */
        QUIT,
        /**
         * Open a job or role screen.
         *
         * <p>Recognised so a datapack written against the reference does not fail to load, but not
         * implemented: there are no economy roles yet. An option of this type is shown disabled
         * with a reason rather than silently doing nothing.
         */
        ROLE,
        /**
         * Travel to the destination named by the option's {@code target}.
         *
         * <p>Only ever produced by the server when a transporter NPC is opened; a dialogue file
         * cannot name one, because the destination has to exist on that NPC's own list and a
         * datapack has no way to guarantee that.
         */
        TRANSPORT
    }

    public record Option(String text, OptionType type, String target, String quest,
                         String command, int sourceIndex, String palette) {
        public Option {
            palette = palette == null ? "" : palette.trim();
        }

        public Option(String text, OptionType type, String target, String quest,
                      String command, int sourceIndex) {
            this(text, type, target, quest, command, sourceIndex, "");
        }

        public Option(String text, OptionType type, String target, String quest, String command) {
            this(text, type, target, quest, command, -1, "");
        }
    }

    /**
     * One line of a conversation.
     *
     * <p>{@code palette} is optional and blank by default, meaning "use the NPC's own
     * {@code bubblePalette}". Every dialogue written before the field existed therefore renders
     * unchanged, and a conversation can shift colour between nodes when an author wants it to.
     */
    public record Node(String text, List<Option> options, String palette) {
        public Node {
            palette = palette == null ? "" : palette.trim();
        }

        /** A node with no palette of its own, which is what every older dialogue is. */
        public Node(String text, List<Option> options) {
            this(text, options, "");
        }
    }

    /** The node interaction opens on, or null when the dialogue names none that exists. */
    public Node startNode() {
        return nodes.get(start);
    }

    public static XenoDialogue fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("dialogue must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        String start = root.has("start") ? root.get("start").getAsString() : "root";

        Map<String, Node> nodes = new LinkedHashMap<>();
        if (root.has("nodes") && root.get("nodes").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("nodes").entrySet()) {
                nodes.put(entry.getKey(), nodeFromJson(entry.getValue()));
            }
        }
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("dialogue has no nodes");
        }
        if (!nodes.containsKey(start)) {
            throw new IllegalArgumentException("dialogue start node '" + start + "' does not exist");
        }
        // Not Map.copyOf: that is unordered, and node order is what the editor and the wire
        // encoding both walk. Keeping pack order makes both deterministic.
        return new XenoDialogue(start, java.util.Collections.unmodifiableMap(nodes));
    }

    private static Node nodeFromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("dialogue node must be a JSON object");
        }
        JsonObject obj = element.getAsJsonObject();
        String text = obj.has("text") ? obj.get("text").getAsString() : "";

        List<Option> options = new ArrayList<>();
        if (obj.has("options") && obj.get("options").isJsonArray()) {
            JsonArray array = obj.getAsJsonArray("options");
            for (JsonElement raw : array) {
                options.add(optionFromJson(raw));
            }
        }
        String palette = obj.has("palette") ? obj.get("palette").getAsString() : "";
        return new Node(text, List.copyOf(options), palette);
    }

    private static Option optionFromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("dialogue option must be a JSON object");
        }
        JsonObject obj = element.getAsJsonObject();
        return new Option(
                obj.has("text") ? obj.get("text").getAsString() : "...",
                parseType(obj.has("type") ? obj.get("type").getAsString() : null),
                obj.has("target") ? obj.get("target").getAsString() : "",
                obj.has("quest") ? obj.get("quest").getAsString() : "",
                obj.has("command") ? obj.get("command").getAsString() : "",
                -1, obj.has("palette") ? obj.get("palette").getAsString() : "");
    }

    /** An unrecognised type closes the conversation rather than breaking the whole dialogue. */
    static OptionType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionType.TEXT;
        }
        try {
            return OptionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OptionType.QUIT;
        }
    }
}
