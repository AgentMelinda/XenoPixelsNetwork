package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueNbt;
import xenoapi.npcs.api.constants.OptionType;
import xenoapi.npcs.api.handler.data.IDialog;
import xenoapi.npcs.api.handler.data.IDialogOption;

import java.util.ArrayList;
import java.util.List;

/**
 * One option of a native dialog node. The slot is its position in the node. A native option has
 * one label, so {@code getName} and {@code getText} read the same text.
 */
final class XenoDialogOption implements IDialogOption {
    /** CustomNPCs' command-block option type, which its OptionType constants leave out. */
    static final int COMMAND_OPTION = 4;

    private final XenoDialogAdapter dialog;
    private final int slot;

    XenoDialogOption(XenoDialogAdapter dialog, int slot) {
        this.dialog = dialog;
        this.slot = slot;
    }

    private XenoDialogue.Option option() {
        List<XenoDialogue.Option> options = dialog.current().options();
        if (slot >= options.size()) throw new IllegalStateException("Dialog option " + slot + " no longer exists");
        return options.get(slot);
    }

    private void set(XenoDialogue.Option replacement) {
        List<XenoDialogue.Option> options = new ArrayList<>(dialog.current().options());
        options.set(slot, replacement);
        dialog.replaceOptions(options);
    }

    private static XenoDialogue.Option with(XenoDialogue.Option o, String text, XenoDialogue.OptionType type,
                                             String target, String command) {
        return new XenoDialogue.Option(text, type, target, o.quest(), command, o.sourceIndex(), o.palette());
    }

    @Override public int getSlot() { return slot; }
    @Override public String getName() { return option().text(); }

    @Override
    public IDialogOption setName(String name) {
        XenoDialogue.Option o = option();
        set(with(o, XenoApiAdapters.boundedText("IDialogOption.setName", name, XenoDialogueNbt.MAX_OPTION_TEXT),
                o.type(), o.target(), o.command()));
        return this;
    }

    @Override public String getText() { return getName(); }
    @Override public IDialogOption setText(String text) { return setName(text); }

    /** QUIT 0, TEXT and QUEST 1 (they lead on), ROLE and TRANSPORT 3, COMMAND 4. */
    @Override
    public int getType() {
        return switch (option().type()) {
            case QUIT -> OptionType.QUIT_OPTION;
            case TEXT, QUEST -> OptionType.DIALOG_OPTION;
            case ROLE, TRANSPORT -> OptionType.ROLE_OPTION;
            case COMMAND -> COMMAND_OPTION;
        };
    }

    @Override
    public IDialogOption setType(int type) {
        XenoDialogue.OptionType next = switch (type) {
            case OptionType.QUIT_OPTION -> XenoDialogue.OptionType.QUIT;
            case OptionType.DIALOG_OPTION -> XenoDialogue.OptionType.TEXT;
            case OptionType.ROLE_OPTION -> XenoDialogue.OptionType.ROLE;
            case COMMAND_OPTION -> XenoDialogue.OptionType.COMMAND;
            case OptionType.DISABLED -> null;
            default -> throw new IllegalArgumentException("IDialogOption.setType: type must be 0-4");
        };
        if (next == null) {
            // Disabled: the option is taken out of the node, since a native node has no greyed-out row.
            List<XenoDialogue.Option> options = new ArrayList<>(dialog.current().options());
            options.remove(slot);
            dialog.replaceOptions(options);
            return this;
        }
        XenoDialogue.Option o = option();
        set(with(o, o.text(), next, o.target(), o.command()));
        return this;
    }

    @Override
    public String[] getCommands() {
        XenoDialogue.Option o = option();
        return o.command().isBlank() ? new String[0] : new String[] {o.command()};
    }

    /** One command per native option; setting one makes this a command option. */
    @Override
    public void setCommands(String... commands) {
        if (commands != null && commands.length > 1) {
            throw new IllegalArgumentException("IDialogOption.setCommands: a native option runs one command");
        }
        String command = commands == null || commands.length == 0 ? ""
                : XenoApiAdapters.boundedText("IDialogOption.setCommands", commands[0], XenoDialogueNbt.MAX_COMMAND);
        XenoDialogue.Option o = option();
        set(with(o, o.text(), command.isEmpty() ? o.type() : XenoDialogue.OptionType.COMMAND, o.target(), command));
    }

    /** The node this option leads to, or null when it leads nowhere. */
    @Override
    public IDialog getDialog() {
        XenoDialogue.Option o = option();
        if (o.target() == null || o.target().isBlank() || !dialog.tree().nodes().containsKey(o.target())) return null;
        return dialog.at(o.target());
    }

    /**
     * Makes this option lead to {@code target}, which must be a node of the same conversation: an
     * option cannot jump into another tree. An imported dialog N is found as node {@code nN}.
     */
    @Override
    public IDialogOption setDialog(IDialog target) {
        XenoDialogue.Option o = option();
        if (target == null) {
            set(with(o, o.text(), o.type(), "", o.command()));
            return this;
        }
        XenoDialogAdapter other = XenoDialogAdapter.nativeDialog(target);
        String node;
        if (other.group.equals(dialog.group) && other.id.equals(dialog.id)) {
            node = other.node();
        } else {
            int number = other.getId();
            node = number == XenoScriptIds.NONE ? null : "n" + number;
            if (node == null || !dialog.tree().nodes().containsKey(node)) {
                throw new IllegalArgumentException("IDialogOption.setDialog: the dialog must be a node of the same conversation");
            }
        }
        set(with(o, o.text(), XenoDialogue.OptionType.TEXT, node, o.command()));
        return this;
    }
}
