package net.bullettrain.xenopixelsmod.client.npc.editor;

import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueNbt;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A dialogue being edited: mutable, ordered, and never half-written.
 *
 * <p>{@link XenoDialogue} is immutable and refuses to exist in an invalid state - it will not parse
 * without a start node that resolves. That is right for something loaded from a pack and wrong for
 * something being typed: an editor has to hold a dialogue mid-edit, with a node just added and not
 * yet named, or a start pointing at a node about to be renamed. So the editor edits this and
 * converts at the boundaries.
 *
 * <p>{@link #problems()} is the other half of that trade. Because a draft tolerates a broken state,
 * something has to say so out loud, and the editor shows the list. Silently refusing to save would
 * be the worst of both.
 */
public final class DialogueDraft {

    /** One option under a node. Mutable, unlike the record it converts to. */
    public static final class Option {
        public String text = "...";
        public XenoDialogue.OptionType type = XenoDialogue.OptionType.TEXT;
        public String target = "";
        public String quest = "";
        public String command = "";
        public String palette = "";
    }

    /** One node: a line the NPC says, and what the player can answer. */
    public static final class Node {
        public String id;
        public String text = "";
        public String palette = "";
        public final List<Option> options = new ArrayList<>();

        Node(String id) {
            this.id = id;
        }
    }

    /** Beyond this the pager stops being usable and the payload stops being sane. */
    public static final int MAX_NODES = XenoDialogueNbt.MAX_NODES;
    public static final int MAX_OPTIONS = XenoDialogueNbt.MAX_OPTIONS;

    private final List<Node> nodes = new ArrayList<>();
    private String start = "";

    private DialogueDraft() {
    }

    /** Reads a draft out of a profile's stored dialogue tag. An absent one starts empty. */
    public static DialogueDraft of(CompoundTag tag) {
        DialogueDraft draft = new DialogueDraft();
        XenoDialogue parsed = XenoDialogueNbt.read(tag);
        if (parsed == null) {
            return draft;
        }
        draft.start = parsed.start();
        for (Map.Entry<String, XenoDialogue.Node> entry : parsed.nodes().entrySet()) {
            Node node = new Node(entry.getKey());
            node.text = entry.getValue().text();
            node.palette = entry.getValue().palette();
            for (XenoDialogue.Option option : entry.getValue().options()) {
                Option copy = new Option();
                copy.text = option.text();
                copy.type = option.type() == null ? XenoDialogue.OptionType.QUIT : option.type();
                copy.target = option.target();
                copy.quest = option.quest();
                copy.command = option.command();
                copy.palette = option.palette();
                node.options.add(copy);
            }
            draft.nodes.add(node);
        }
        return draft;
    }

    public List<Node> nodes() {
        return nodes;
    }

    /** The node at this index, or null - the editor holds an index across a page rebuild. */
    public Node node(int index) {
        return index < 0 || index >= nodes.size() ? null : nodes.get(index);
    }

    public String start() {
        return start;
    }

    public void setStart(String id) {
        start = id == null ? "" : id.trim();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /** Every node id, for the cycler an option's target uses. */
    public List<String> nodeIds() {
        List<String> ids = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            ids.add(node.id);
        }
        return ids;
    }

    /**
     * Adds a node and returns its index, or -1 at the cap.
     *
     * <p>The first node added becomes the start. An editor where a new dialogue is immediately
     * unopenable until you find the start cycler would be the kind of trap this whole screen is
     * written to avoid.
     */
    public int addNode() {
        if (nodes.size() >= MAX_NODES) {
            return -1;
        }
        Node node = new Node(freeId());
        nodes.add(node);
        if (start.isBlank()) {
            start = node.id;
        }
        return nodes.size() - 1;
    }

    /** Removes a node. When it was the start, the first remaining node takes over. */
    public void removeNode(int index) {
        if (index < 0 || index >= nodes.size()) {
            return;
        }
        String removed = nodes.remove(index).id;
        if (removed.equals(start)) {
            start = nodes.isEmpty() ? "" : nodes.get(0).id;
        }
    }

    /**
     * Renames a node, moving the start and every option that pointed at it.
     *
     * <p>Renaming without this would silently break every link into the node, which is the failure
     * a draft is least able to warn about usefully: the dialogue stays valid-looking and simply
     * dead-ends.
     */
    public void renameNode(int index, String rawId) {
        Node node = node(index);
        if (node == null) {
            return;
        }
        String id = sanitiseId(rawId);
        if (id.isBlank() || id.equals(node.id)) {
            return;
        }
        String was = node.id;
        node.id = id;
        if (start.equals(was)) {
            start = id;
        }
        for (Node other : nodes) {
            for (Option option : other.options) {
                if (option.target.equals(was)) {
                    option.target = id;
                }
            }
        }
    }

    /** Adds an option to a node and returns its index, or -1 at the cap. */
    public int addOption(int nodeIndex) {
        Node node = node(nodeIndex);
        if (node == null || node.options.size() >= MAX_OPTIONS) {
            return -1;
        }
        node.options.add(new Option());
        return node.options.size() - 1;
    }

    public void removeOption(int nodeIndex, int optionIndex) {
        Node node = node(nodeIndex);
        if (node != null && optionIndex >= 0 && optionIndex < node.options.size()) {
            node.options.remove(optionIndex);
        }
    }

    /** Writes the draft back into the tag shape the profile stores. */
    public CompoundTag toTag() {
        if (nodes.isEmpty()) {
            return XenoDialogueNbt.empty();
        }
        Map<String, XenoDialogue.Node> built = new LinkedHashMap<>();
        for (Node node : nodes) {
            if (node.id.isBlank()) {
                continue;
            }
            List<XenoDialogue.Option> options = new ArrayList<>(node.options.size());
            for (Option option : node.options) {
                options.add(new XenoDialogue.Option(option.text, option.type, option.target,
                        option.quest, option.command, -1, option.palette));
            }
            built.put(node.id, new XenoDialogue.Node(node.text, List.copyOf(options),
                    node.palette));
        }
        // The record's canonical constructor validates nothing - only fromJson does - so a draft
        // whose start does not resolve yet still converts. That is deliberate: an unfinished edit
        // is kept rather than thrown away on close, and XenoDialogueNbt.read answers null for it,
        // so the NPC falls back to its role's dialogue instead of going silent.
        return XenoDialogueNbt.write(new XenoDialogue(start, built));
    }

    /**
     * Everything wrong with the draft right now, in the order an author would fix it.
     *
     * <p>Empty means it will open. Each entry is phrased as what is missing rather than as an error
     * code, because it is shown on the screen next to the thing to fix.
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (nodes.isEmpty()) {
            return problems;
        }

        Set<String> ids = new LinkedHashSet<>();
        Set<String> duplicated = new LinkedHashSet<>();
        for (Node node : nodes) {
            if (node.id.isBlank()) {
                problems.add("A node has no name.");
            } else if (!ids.add(node.id)) {
                duplicated.add(node.id);
            }
        }
        for (String id : duplicated) {
            problems.add("Two nodes are named " + id + ".");
        }
        if (start.isBlank()) {
            problems.add("No start node is set.");
        } else if (!ids.contains(start)) {
            problems.add("Start node " + start + " does not exist.");
        }

        for (Node node : nodes) {
            for (int i = 0; i < node.options.size(); i++) {
                Option option = node.options.get(i);
                String where = node.id + " option " + (i + 1);
                switch (option.type) {
                    case TEXT -> {
                        if (option.target.isBlank()) {
                            problems.add(where + " goes nowhere.");
                        } else if (!ids.contains(option.target)) {
                            problems.add(where + " points at missing node "
                                    + option.target + ".");
                        }
                    }
                    case QUEST -> {
                        if (option.quest.isBlank()) {
                            problems.add(where + " names no quest.");
                        }
                    }
                    case COMMAND -> {
                        if (option.command.isBlank()) {
                            problems.add(where + " runs no command.");
                        }
                    }
                    default -> {
                        // QUIT needs nothing. ROLE is inert until economy roles exist, and the
                        // option screen says so rather than this list repeating it per option.
                    }
                }
            }
        }
        return problems;
    }

    /** Ids are keys, so they are lowercased and stripped of what would not survive a round trip. */
    static String sanitiseId(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (char c : raw.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-') {
                out.append(c);
            } else if (c == ' ') {
                out.append('_');
            }
            if (out.length() >= 32) {
                break;
            }
        }
        return out.toString();
    }

    /**
     * A default name for a new node, numbered by the position it will occupy.
     *
     * <p>Numbered from the list position rather than from 1, so the name matches the row number the
     * list shows beside it - {@code node_2} is the second line. Starting the search at 1 instead
     * named the second line {@code node_1}, which reads as a mistake even though nothing clashed.
     */
    private String freeId() {
        Set<String> taken = new LinkedHashSet<>(nodeIds());
        if (taken.isEmpty()) {
            return "root";
        }
        for (int offset = 0; offset <= MAX_NODES; offset++) {
            String candidate = "node_" + (nodes.size() + 1 + offset);
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return "node";
    }
}
