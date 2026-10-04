package net.bullettrain.xenopixelsmod.npc.scene;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/** World-store access for shared scene definitions. Server thread only. */
public final class XenoNpcScenes {
    private XenoNpcScenes() {}

    public static XenoNpcScene get(String id) {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null || id == null || id.isBlank()) {
            return null;
        }
        CompoundTag tag = store.get(XenoNpcStoreCategory.SCENES, "", id);
        return tag == null ? null : XenoNpcScene.load(id, tag);
    }

    public static List<XenoNpcScene> all() {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return List.of();
        }
        List<XenoNpcScene> result = new ArrayList<>();
        for (XenoNpcWorldStore.Entry entry : store.list(XenoNpcStoreCategory.SCENES)) {
            result.add(XenoNpcScene.load(entry.id(), entry.tag()));
        }
        return List.copyOf(result);
    }

    public static String put(XenoNpcScene scene) {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return "the NPC store is not open";
        }
        return store.put(XenoNpcStoreCategory.SCENES, "", scene.id(), scene.save());
    }

    public static String remove(String id) {
        XenoNpcWorldStore store = XenoNpcStores.get();
        return store == null ? "the NPC store is not open"
                : store.remove(XenoNpcStoreCategory.SCENES, "", id);
    }
}
