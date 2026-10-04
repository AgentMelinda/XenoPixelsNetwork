package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.bullettrain.xenopixelsmod.npc.importer.SlotIndex;
import net.bullettrain.xenopixelsmod.npc.store.XenoFactionNbt;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcDataSource;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.handler.IFactionHandler;
import xenoapi.npcs.api.handler.data.IFaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Every native faction, store and datapack together, as XenoAPI's {@link IFactionHandler}.
 * Numbers are the ones imported content carries; see {@link XenoScriptIds}.
 */
public final class XenoFactionHandler implements IFactionHandler {
    static final int MAX_NAME = 64;

    @Override
    public List<IFaction> list() {
        List<IFaction> out = new ArrayList<>();
        for (XenoFaction faction : XenoNpcDataSource.factions()) out.add(new XenoFactionAdapter(faction.id()));
        return out;
    }

    @Override
    public IFaction get(int id) {
        String faction = XenoScriptIds.factionId(id);
        return faction == null || XenoNpcDataSource.faction(faction) == null ? null : new XenoFactionAdapter(faction);
    }

    /** The faction a native id names, for scripts that hold a Xeno-made faction by name. */
    public IFaction get(String id) {
        XenoFaction faction = id == null ? null : XenoNpcDataSource.faction(id);
        return faction == null ? null : new XenoFactionAdapter(faction.id());
    }

    /**
     * Removes a stored faction and returns it as it was. A faction that only a datapack defines
     * cannot be deleted from a script; deleting a stored copy brings the pack's one back.
     */
    @Override
    public IFaction delete(int id) {
        XenoApiAdapters.requireServerThreadNow();
        String faction = XenoScriptIds.factionId(id);
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (faction == null || store == null) return null;
        XenoFaction removed = XenoNpcDataSource.faction(faction);
        String refusal = store.remove(XenoNpcStoreCategory.FACTIONS, "", faction);
        if (refusal != null) throw new CustomNPCsException("IFactionHandler.delete: %s", refusal);
        XenoFactionAdapter.broadcast();
        return removed == null ? null : new XenoFactionAdapter(faction, removed);
    }

    /**
     * A new stored faction. Its id is the name folded onto the store's id alphabet, with a suffix
     * when taken. It has no CustomNPCs number, so {@code getId()} answers -1.
     */
    @Override
    public IFaction create(String name, int color) {
        String display = XenoApiAdapters.boundedText("IFactionHandler.create", name, MAX_NAME);
        if (display.isEmpty()) throw new IllegalArgumentException("IFactionHandler.create: name cannot be empty");
        XenoApiAdapters.requireServerThreadNow();
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) throw new IllegalStateException("IFactionHandler.create needs a loaded world");
        String base = new SlotIndex().assign(0, display);
        String id = base;
        for (int suffix = 2; store.get(XenoNpcStoreCategory.FACTIONS, "", id) != null
                || XenoNpcDataSource.faction(id) != null; suffix++) {
            id = base + "_" + suffix;
        }
        XenoFaction faction = new XenoFaction(id, display, color, List.of(), 0, true);
        String refusal = store.put(XenoNpcStoreCategory.FACTIONS, "", id, XenoFactionNbt.write(faction));
        if (refusal != null) throw new CustomNPCsException("IFactionHandler.create: %s", refusal);
        XenoFactionAdapter.broadcast();
        return new XenoFactionAdapter(id);
    }
}
