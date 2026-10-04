package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.item.custom.XenoNpcPayload;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.bullettrain.xenopixelsmod.npc.importer.SlotIndex;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcClones;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.IWorld;
import xenoapi.npcs.api.entity.IEntity;
import xenoapi.npcs.api.handler.ICloneHandler;

/**
 * The world store's clone library (tabs 1-9) as XenoAPI's {@link ICloneHandler}. A clone is placed
 * by the same restore path {@code /xenoclone place} and the Cloner item use. Names are the store's
 * ids; a CustomNPCs name the store cannot use as a filename ("Bob the Guard") is folded onto the id
 * alphabet ("bob_the_guard"), the same folding every import applies.
 */
public final class XenoCloneHandler implements ICloneHandler {

    static int tab(String method, int tab) {
        if (tab < XenoNpcClones.MIN_TAB || tab > XenoNpcClones.MAX_TAB) {
            throw new IllegalArgumentException(method + ": tab must be " + XenoNpcClones.MIN_TAB + "-" + XenoNpcClones.MAX_TAB);
        }
        return tab;
    }

    /** The store id for a clone name: itself when legal, otherwise its folded form. */
    static String id(String method, String name) {
        String value = XenoApiAdapters.boundedText(method, name, XenoNpcStorePaths.MAX_ID);
        if (value.isEmpty()) throw new IllegalArgumentException(method + ": name cannot be empty");
        return XenoNpcStorePaths.isValidId(value) ? value : new SlotIndex().assign(0, value);
    }

    /** A clone as a new NPC in {@code level} at the position, not yet added to the world. */
    static XenoNpcEntity create(ServerLevel level, int tab, String name, double x, double y, double z) {
        CompoundTag payload = XenoNpcClones.load(tab, id("clone", name));
        if (payload == null) return null;
        XenoNpcEntity npc = ModEntities.xenoNpcType(XenoNpcRole.byId(payload.getString("Role"))).create(level);
        if (npc == null) throw new CustomNPCsException("Clone %s names a role that is gone", name);
        npc.moveTo(x, y, z, 0.0f, 0.0f);
        // keepOwner false: placing from the library makes a new NPC, exactly as /xenoclone place does.
        XenoNpcPayload.apply(npc, payload, null, x, y, z, false);
        return npc;
    }

    @Override
    public IEntity spawn(double x, double y, double z, int tab, String name, IWorld world) {
        XenoApiAdapters.requireFinite("ICloneHandler.spawn", x, y, z);
        ServerLevel level = XenoApiAdapters.unwrap(world);
        XenoApiAdapters.requireServerThread(level);
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            throw new IllegalArgumentException("ICloneHandler.spawn: position is not loaded or is outside the build height");
        }
        XenoNpcEntity npc = create(level, tab("ICloneHandler.spawn", tab), name, x, y, z);
        if (npc == null) return null;
        if (!level.addFreshEntity(npc)) throw new CustomNPCsException("There is no room for clone %s there", name);
        return XenoApiAdapters.wrap(npc);
    }

    /** The clone as an entity that is not in the world; {@code spawn()} on it adds it. */
    @Override
    public IEntity get(int tab, String name, IWorld world) {
        ServerLevel level = XenoApiAdapters.unwrap(world);
        XenoApiAdapters.requireServerThread(level);
        var spawn = level.getSharedSpawnPos();
        XenoNpcEntity npc = create(level, tab("ICloneHandler.get", tab), name, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        return npc == null ? null : XenoApiAdapters.wrap(npc);
    }

    /** Saves a native NPC to the library; other entities have no clone format natively. */
    @Override
    public void set(int tab, String name, IEntity entity) {
        if (!(XenoApiAdapters.unwrap(entity) instanceof XenoNpcEntity npc)) {
            throw new IllegalArgumentException("ICloneHandler.set: only native Xeno NPCs can be stored as clones");
        }
        XenoApiAdapters.requireServerThread(npc.level());
        String refusal = XenoNpcClones.save(npc, tab("ICloneHandler.set", tab), id("ICloneHandler.set", name));
        if (refusal != null) throw new CustomNPCsException("ICloneHandler.set: %s", refusal);
    }

    @Override
    public void remove(int tab, String name) {
        XenoApiAdapters.requireServerThreadNow();
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) return;
        String id = id("ICloneHandler.remove", name);
        String group = XenoNpcClones.group(tab("ICloneHandler.remove", tab));
        if (store.get(XenoNpcStoreCategory.CLONES, group, id) == null) return;
        String refusal = store.remove(XenoNpcStoreCategory.CLONES, group, id);
        if (refusal != null) throw new CustomNPCsException("ICloneHandler.remove: %s", refusal);
    }
}
