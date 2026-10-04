package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import xenoapi.npcs.api.handler.IDialogHandler;
import xenoapi.npcs.api.handler.data.IDialog;
import xenoapi.npcs.api.handler.data.IDialogCategory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Every stored native conversation as XenoAPI's {@link IDialogHandler}. */
public final class XenoDialogHandler implements IDialogHandler {
    @Override
    public List<IDialogCategory> categories() {
        Set<String> groups = new LinkedHashSet<>();
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store != null) {
            for (XenoNpcWorldStore.Entry entry : store.list(XenoNpcStoreCategory.DIALOGS)) groups.add(entry.group());
        }
        List<IDialogCategory> out = new ArrayList<>();
        for (String group : groups) out.add(new XenoDialogCategory(group));
        return out;
    }

    /** The dialog carrying this CustomNPCs number, or null. */
    @Override
    public IDialog get(int id) {
        XenoScriptIds.Ref ref = XenoScriptIds.dialog(id);
        return ref == null ? null : new XenoDialogAdapter(ref.group(), ref.id());
    }

    /** A stored conversation by {@code group/id}, for Xeno-made dialogs that have no number. */
    public IDialog get(String ref) {
        if (ref == null) return null;
        int slash = ref.indexOf('/');
        if (slash <= 0 || slash == ref.length() - 1) return null;
        String group = ref.substring(0, slash).trim();
        String id = ref.substring(slash + 1).trim();
        XenoNpcWorldStore store = XenoNpcStores.get();
        return store == null || store.get(XenoNpcStoreCategory.DIALOGS, group, id) == null
                ? null : new XenoDialogAdapter(group, id);
    }
}
