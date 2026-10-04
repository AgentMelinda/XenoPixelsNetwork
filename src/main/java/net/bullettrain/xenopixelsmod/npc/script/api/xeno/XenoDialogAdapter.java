package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueNbt;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.nbt.CompoundTag;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.handler.data.IAvailability;
import xenoapi.npcs.api.handler.data.IDialog;
import xenoapi.npcs.api.handler.data.IDialogCategory;
import xenoapi.npcs.api.handler.data.IDialogOption;
import xenoapi.npcs.api.handler.data.IQuest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One node of a stored native conversation as XenoAPI's {@link IDialog}.
 *
 * <p>A CustomNPCs dialog is one node; a native conversation is a tree of nodes in one store entry.
 * An imported dialog {@code N} is the tree {@code dialog_N}, opened at its start node. An option
 * leads to another node of the same tree, and {@link IDialogOption#getDialog()} returns that node.
 * Setters change this view; {@link #save()} writes the whole tree back to the store.
 */
public final class XenoDialogAdapter implements IDialog {
    final String group;
    final String id;
    /** The node this view reads and edits; null is the tree's start node. */
    private final String nodeId;
    private XenoDialogue pending;

    XenoDialogAdapter(String group, String id, String nodeId, XenoDialogue draft) {
        this.group = group;
        this.id = id;
        this.nodeId = nodeId;
        this.pending = draft;
    }

    XenoDialogAdapter(String group, String id) {
        this(group, id, null, null);
    }

    /** The store reference, {@code group/id}: what NPC slots and viewed-dialogue records hold. */
    public String ref() { return group + "/" + id; }

    private CompoundTag stored() {
        XenoNpcWorldStore store = XenoNpcStores.get();
        return store == null ? null : store.get(XenoNpcStoreCategory.DIALOGS, group, id);
    }

    XenoDialogue tree() {
        if (pending != null) return pending;
        XenoDialogue live = XenoDialogueNbt.read(stored());
        if (live == null) throw new CustomNPCsException("Dialog %s no longer exists", ref());
        return live;
    }

    String node() {
        return nodeId == null ? tree().start() : nodeId;
    }

    XenoDialogue.Node current() {
        XenoDialogue.Node node = tree().nodes().get(node());
        if (node == null) throw new CustomNPCsException("Dialog %s has no node %s", ref(), node());
        return node;
    }

    /** The same tree opened at another node; edits to one are not seen by the other until saved. */
    XenoDialogAdapter at(String otherNode) {
        return new XenoDialogAdapter(group, id, otherNode.equals(tree().start()) ? null : otherNode, pending);
    }

    void replace(XenoDialogue.Node node) {
        XenoDialogue tree = tree();
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>(tree.nodes());
        nodes.put(node(), node);
        pending = new XenoDialogue(tree.start(), nodes);
    }

    void replaceOptions(List<XenoDialogue.Option> options) {
        XenoDialogue.Node node = current();
        if (options.size() > XenoDialogueNbt.MAX_OPTIONS) {
            throw new IllegalArgumentException("A dialog node holds at most " + XenoDialogueNbt.MAX_OPTIONS + " options");
        }
        replace(new XenoDialogue.Node(node.text(), List.copyOf(options), node.palette()));
    }

    /** The CustomNPCs number: the tree's for its start node, a node's own for an imported node. */
    @Override
    public int getId() {
        if (nodeId == null) return XenoScriptIds.slotOf(XenoNpcStoreCategory.DIALOGS, group, id);
        boolean imported = XenoScriptIds.slotOf(XenoNpcStoreCategory.DIALOGS, group, id) != XenoScriptIds.NONE;
        if (imported && nodeId.matches("n\\d{1,9}")) return Integer.parseInt(nodeId.substring(1));
        return XenoScriptIds.NONE;
    }

    @Override
    public String getName() {
        CompoundTag tag = stored();
        return tag != null && tag.contains("Name") ? tag.getString("Name") : id;
    }

    private String pendingName;

    @Override
    public void setName(String name) {
        pendingName = XenoApiAdapters.boundedText("IDialog.setName", name, 64);
    }

    @Override public String getText() { return current().text(); }

    @Override
    public void setText(String text) {
        XenoDialogue.Node node = current();
        replace(new XenoDialogue.Node(XenoApiAdapters.boundedText("IDialog.setText", text, XenoDialogueNbt.MAX_TEXT),
                node.options(), node.palette()));
    }

    /** The quest this node offers: its first quest option. */
    @Override
    public IQuest getQuest() {
        for (XenoDialogue.Option option : current().options()) {
            if (option.type() == XenoDialogue.OptionType.QUEST && ParallelQuests.definition(option.quest()) != null) {
                return new XenoQuestAdapter(option.quest());
            }
        }
        return null;
    }

    /**
     * Offers {@code quest} from this node. Native dialogs hand quests out through an option, so this
     * sets the first quest option (adding an "Accept" one when there is none); null removes them.
     */
    @Override
    public void setQuest(IQuest quest) {
        List<XenoDialogue.Option> options = new ArrayList<>();
        boolean placed = false;
        String questId = quest == null ? null : XenoQuestAdapter.nativeId(quest);
        for (XenoDialogue.Option option : current().options()) {
            if (option.type() != XenoDialogue.OptionType.QUEST) {
                options.add(option);
            } else if (questId != null && !placed) {
                options.add(new XenoDialogue.Option(option.text(), option.type(), option.target(), questId,
                        option.command(), option.sourceIndex(), option.palette()));
                placed = true;
            }
        }
        if (questId != null && !placed) {
            options.add(new XenoDialogue.Option("Accept", XenoDialogue.OptionType.QUEST, "", questId, ""));
        }
        replaceOptions(options);
    }

    /** The commands of this node's command options, which run when the player picks one. */
    @Override
    public String[] getCommands() {
        List<String> out = new ArrayList<>();
        for (XenoDialogue.Option option : current().options()) {
            if (option.type() == XenoDialogue.OptionType.COMMAND && !option.command().isBlank()) out.add(option.command());
        }
        return out.toArray(String[]::new);
    }

    /**
     * Native dialogs never run a command merely because they were shown, only from an option the
     * player picks, and only when the server enables dialogue commands. So this replaces this
     * node's command options with one "Continue" option per command.
     */
    @Override
    public void setCommands(String... commands) {
        List<XenoDialogue.Option> options = new ArrayList<>();
        for (XenoDialogue.Option option : current().options()) {
            if (option.type() != XenoDialogue.OptionType.COMMAND) options.add(option);
        }
        if (commands != null) {
            for (String command : commands) {
                String value = XenoApiAdapters.boundedText("IDialog.setCommands", command, XenoDialogueNbt.MAX_COMMAND);
                if (!value.isEmpty()) {
                    options.add(new XenoDialogue.Option("Continue", XenoDialogue.OptionType.COMMAND, "", "", value));
                }
            }
        }
        replaceOptions(options);
    }

    @Override
    public List<IDialogOption> getOptions() {
        List<IDialogOption> out = new ArrayList<>();
        for (int i = 0; i < current().options().size(); i++) out.add(new XenoDialogOption(this, i));
        return out;
    }

    /** The option at that position, or null. */
    @Override
    public IDialogOption getOption(int slot) {
        return slot >= 0 && slot < current().options().size() ? new XenoDialogOption(this, slot) : null;
    }

    @Override public IAvailability getAvailability() { return XenoDialogAvailability.INSTANCE; }
    @Override public IDialogCategory getCategory() { return new XenoDialogCategory(group); }

    @Override
    public void save() {
        XenoApiAdapters.requireServerThreadNow();
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) throw new IllegalStateException("IDialog.save needs a loaded world");
        CompoundTag previous = store.get(XenoNpcStoreCategory.DIALOGS, group, id);
        CompoundTag tag = XenoDialogueNbt.write(tree());
        if (previous != null) {
            for (String key : previous.getAllKeys()) {
                if (!tag.contains(key)) tag.put(key, previous.get(key).copy());
            }
        }
        if (pendingName != null) tag.putString("Name", pendingName);
        String refusal = store.put(XenoNpcStoreCategory.DIALOGS, group, id, tag);
        if (refusal != null) throw new CustomNPCsException("IDialog.save: %s", refusal);
        pending = null;
        pendingName = null;
        if (net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() != null) {
            net.bullettrain.xenopixelsmod.network.ModNetwork.sendToAll(
                    net.bullettrain.xenopixelsmod.network.packet.SyncNpcStoreIndexPacket.current());
        }
    }

    static XenoDialogAdapter nativeDialog(IDialog dialog) {
        if (dialog instanceof XenoDialogAdapter adapter) return adapter;
        throw new IllegalArgumentException("Foreign XenoAPI dialog: " + (dialog == null ? "null" : dialog.getClass().getName()));
    }

    /** The tree as a conversation to show, opened at this node. */
    XenoDialogue asShown() {
        XenoDialogue tree = tree();
        return nodeId == null ? tree : new XenoDialogue(nodeId, tree.nodes());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof XenoDialogAdapter d && d.group.equals(group) && d.id.equals(id) && d.node().equals(node());
    }

    @Override public int hashCode() { return ref().hashCode(); }
    @Override public String toString() { return ref() + "#" + node(); }
}
