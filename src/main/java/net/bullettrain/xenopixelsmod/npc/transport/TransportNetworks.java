package net.bullettrain.xenopixelsmod.npc.transport;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes transport networks in the world store.
 *
 * <p>{@code TRANSPORT} is an <b>ungrouped</b> category, so every call passes {@code ""} as the
 * group. It existed with no reader until now; this is the reader.
 *
 * <p>Server-side only. The client learns which networks exist from {@code ClientNpcStoreIndex},
 * which is already synced on join and after {@code /reload} — the picker needed no packet of its
 * own.
 */
public final class TransportNetworks {

    /** {@code TRANSPORT} is ungrouped; the store still wants a group argument. */
    private static final String NO_GROUP = "";

    private TransportNetworks() {
    }

    /**
     * One network by id, or null when the store has no such entry.
     *
     * <p>Null is an ordinary outcome, not an error: an NPC can point at a network an operator has
     * since deleted, and the caller's job is to offer nothing rather than to throw.
     */
    public static TransportNetwork get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return null;
        }
        CompoundTag tag = store.get(XenoNpcStoreCategory.TRANSPORT, NO_GROUP, id);
        return tag == null ? null : TransportNetwork.load(id, tag);
    }

    /** Every network in the store, in load order. */
    public static List<TransportNetwork> all() {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return List.of();
        }
        List<TransportNetwork> out = new ArrayList<>();
        for (XenoNpcWorldStore.Entry entry : store.list(XenoNpcStoreCategory.TRANSPORT)) {
            out.add(TransportNetwork.load(entry.id(), entry.tag()));
        }
        return List.copyOf(out);
    }

    /**
     * Writes one network.
     *
     * @return null on success, or the store's reason for refusing — an id that cannot become a
     *         filename is refused rather than rewritten, so the operator's next lookup still finds
     *         what they named
     */
    public static String put(TransportNetwork network) {
        if (network == null) {
            return "nothing to write";
        }
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return "the NPC store is not open";
        }
        return store.put(XenoNpcStoreCategory.TRANSPORT, NO_GROUP, network.id(), network.save());
    }

    /**
     * Writes a network only if that id is free.
     *
     * <p>Used by the migration, which must never overwrite a network an operator built by hand
     * that happens to collide with an NPC's name.
     *
     * @return the id actually used, or null when it could not be written at all
     */
    public static String putIfAbsent(TransportNetwork network) {
        if (network == null || get(network.id()) != null) {
            return null;
        }
        String refusal = put(network);
        if (refusal != null) {
            XenoPixelsMod.LOGGER.warn("Transport network '{}' could not be written: {}",
                    network.id(), refusal);
            return null;
        }
        return network.id();
    }
}
