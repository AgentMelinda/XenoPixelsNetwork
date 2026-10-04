package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.handler.data.IDialog;
import xenoapi.npcs.api.handler.data.IDialogCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A dialog store group (its folder) as a CustomNPCs dialog category. */
final class XenoDialogCategory implements IDialogCategory {
    private final String group;

    XenoDialogCategory(String group) {
        this.group = group;
    }

    @Override
    public List<IDialog> dialogs() {
        List<IDialog> out = new ArrayList<>();
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) return out;
        for (XenoNpcWorldStore.Entry entry : store.list(XenoNpcStoreCategory.DIALOGS)) {
            if (entry.group().equals(group)) out.add(new XenoDialogAdapter(entry.group(), entry.id()));
        }
        return out;
    }

    @Override public String getName() { return group; }

    /**
     * Refused: NPC dialog slots hold {@code group/id}, so moving a category's files would silently
     * disconnect every NPC that uses one of its dialogs.
     */
    @Override
    public void setName(String name) {
        throw new CustomNPCsException("IDialogCategory.setName: renaming a dialog category would disconnect the NPCs linked to its dialogs");
    }

    /** A new, unsaved one-node conversation in this category; call {@code save()} to keep it. */
    @Override
    public IDialog create() {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) throw new IllegalStateException("IDialogCategory.create needs a loaded world");
        String id = "dialog_new";
        for (int n = 2; store.get(XenoNpcStoreCategory.DIALOGS, group, id) != null; n++) id = "dialog_new_" + n;
        XenoDialogue draft = new XenoDialogue("root", Map.of("root", new XenoDialogue.Node("", List.of())));
        return new XenoDialogAdapter(group, id, null, draft);
    }
}
