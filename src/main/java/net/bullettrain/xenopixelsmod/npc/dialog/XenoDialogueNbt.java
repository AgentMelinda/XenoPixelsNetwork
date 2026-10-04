package net.bullettrain.xenopixelsmod.npc.dialog;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A dialogue as NBT, so one can be written per NPC instead of only per datapack.
 *
 * <p>A datapack dialogue is authored in a file and cannot be edited from inside the game: a
 * {@code SimpleJsonResourceReloadListener} reads a pack and has nothing to write back to. So an
 * NPC-owned dialogue lives on the NPC's own profile, which already persists with the entity and
 * already travels to the editor and back through the save whitelist. That is the same place the
 * attack slots and the bubble palettes live, and for the same reason.
 *
 * <p>The two coexist rather than compete: an NPC with its own dialogue uses it, and one without
 * falls back to the dialogue its role names. A pack can still ship conversations for every NPC of a
 * role, and a single NPC can still be given its own without touching the pack.
 *
 * <p>Bounds are checked on read as well as on write. NBT that reaches here has come off the network
 * through the editor's save path, so its size is decided by whoever sent it.
 */
public final class XenoDialogueNbt {

    /** Matches what the open packet will carry; a dialogue larger than this cannot be shown. */
    public static final int MAX_NODES = 128;
    public static final int MAX_OPTIONS = 16;
    public static final int MAX_NODE_ID = 128;
    public static final int MAX_TEXT = 512;
    public static final int MAX_OPTION_TEXT = 256;
    public static final int MAX_QUEST_ID = 256;
    public static final int MAX_COMMAND = 256;

    private static final String START = "Start";
    private static final String NODES = "Nodes";
    private static final String PALETTE = "Palette";
    private static final String ID = "Id";
    private static final String TEXT = "Text";
    private static final String OPTIONS = "Options";
    private static final String TYPE = "Type";
    private static final String TARGET = "Target";
    private static final String QUEST = "Quest";
    private static final String COMMAND = "Command";
    private static final String OPTION_PALETTE = "OptionPalette";

    private XenoDialogueNbt() {
    }

    /** An empty dialogue, which is what an NPC that has never been given one has. */
    public static CompoundTag empty() {
        CompoundTag tag = new CompoundTag();
        tag.putString(START, "");
        tag.put(NODES, new ListTag());
        return tag;
    }

    /**
     * Writes a dialogue, or {@link #empty()} when there is none.
     *
     * <p>A node list rather than a compound of node names: NBT compounds do not keep insertion
     * order, and node order is what an editor lists and pages through. The same reason
     * {@code XenoDialogue} stopped using {@code Map.copyOf}.
     */
    public static CompoundTag write(XenoDialogue dialogue) {
        if (dialogue == null || dialogue.nodes().isEmpty()) {
            return empty();
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(START, clamp(dialogue.start(), MAX_NODE_ID));

        ListTag nodes = new ListTag();
        int written = 0;
        for (Map.Entry<String, XenoDialogue.Node> entry : dialogue.nodes().entrySet()) {
            if (written++ >= MAX_NODES) {
                break;
            }
            CompoundTag node = new CompoundTag();
            node.putString(ID, clamp(entry.getKey(), MAX_NODE_ID));
            node.putString(TEXT, clamp(entry.getValue().text(), MAX_TEXT));
            // Written only when set, so a dialogue that uses the NPC's palette stays byte-identical
            // to what it was before the field existed.
            if (!entry.getValue().palette().isEmpty()) {
                node.putString(PALETTE, clamp(entry.getValue().palette(), MAX_NODE_ID));
            }

            ListTag options = new ListTag();
            List<XenoDialogue.Option> source = entry.getValue().options();
            for (int i = 0; i < Math.min(MAX_OPTIONS, source.size()); i++) {
                XenoDialogue.Option option = source.get(i);
                CompoundTag written0 = new CompoundTag();
                written0.putString(TEXT, clamp(option.text(), MAX_OPTION_TEXT));
                written0.putString(TYPE, (option.type() == null
                        ? XenoDialogue.OptionType.QUIT : option.type()).name());
                written0.putString(TARGET, clamp(option.target(), MAX_NODE_ID));
                written0.putString(QUEST, clamp(option.quest(), MAX_QUEST_ID));
                written0.putString(COMMAND, clamp(option.command(), MAX_COMMAND));
                if (!option.palette().isEmpty()) {
                    written0.putString(OPTION_PALETTE, clamp(option.palette(), MAX_NODE_ID));
                }
                options.add(written0);
            }
            node.put(OPTIONS, options);
            nodes.add(node);
        }
        tag.put(NODES, nodes);
        return tag;
    }

    /**
     * Reads a dialogue, or null when the tag holds none that can be shown.
     *
     * <p>Null rather than an empty dialogue: every caller treats null as "this NPC has nothing of
     * its own", which is what falls through to the role's dialogue. An empty-but-present dialogue
     * would instead shadow the role's and leave the NPC silent.
     *
     * <p>A start node naming something the tag does not contain is treated the same way. That can
     * only come from an editor that renamed a node without moving the start, and an NPC that says
     * nothing is a better outcome than one that opens an empty bubble.
     */
    public static XenoDialogue read(CompoundTag tag) {
        if (tag == null || !tag.contains(NODES, Tag.TAG_LIST)) {
            return null;
        }
        ListTag list = tag.getList(NODES, Tag.TAG_COMPOUND);
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(MAX_NODES, list.size()); i++) {
            CompoundTag node = list.getCompound(i);
            String id = clamp(node.getString(ID), MAX_NODE_ID);
            if (id.isBlank()) {
                continue;
            }
            ListTag rawOptions = node.getList(OPTIONS, Tag.TAG_COMPOUND);
            List<XenoDialogue.Option> options = new ArrayList<>();
            for (int o = 0; o < Math.min(MAX_OPTIONS, rawOptions.size()); o++) {
                CompoundTag option = rawOptions.getCompound(o);
                options.add(new XenoDialogue.Option(
                        clamp(option.getString(TEXT), MAX_OPTION_TEXT),
                        XenoDialogue.parseType(option.getString(TYPE)),
                        clamp(option.getString(TARGET), MAX_NODE_ID),
                        clamp(option.getString(QUEST), MAX_QUEST_ID),
                        clamp(option.getString(COMMAND), MAX_COMMAND), -1,
                        clamp(option.getString(OPTION_PALETTE), MAX_NODE_ID)));
            }
            nodes.put(id, new XenoDialogue.Node(clamp(node.getString(TEXT), MAX_TEXT),
                    List.copyOf(options), clamp(node.getString(PALETTE), MAX_NODE_ID)));
        }
        if (nodes.isEmpty()) {
            return null;
        }
        String start = clamp(tag.getString(START), MAX_NODE_ID);
        if (!nodes.containsKey(start)) {
            return null;
        }
        return new XenoDialogue(start, Collections.unmodifiableMap(nodes));
    }

    /** Whether a tag holds a dialogue that can actually be opened. */
    public static boolean isPresent(CompoundTag tag) {
        return read(tag) != null;
    }

    private static String clamp(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
