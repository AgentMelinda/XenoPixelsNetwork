package net.bullettrain.xenopixelsmod.npc.bank;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes bank definitions in the world store.
 *
 * <p>{@code BANKS} is an <b>ungrouped</b> category, so every call passes {@code ""} as the group.
 * It was one of the reservations documented on 2026-09-23, whose stated unblock was "the Bank role,
 * which does not exist" — this is that reader.
 *
 * <p>Server-side only, and deliberately a near-copy of {@code TransportNetworks}: the client learns
 * which banks exist from {@code ClientNpcStoreIndex}, which is already synced on join and after
 * {@code /reload}, so the editor's picker needs no packet of its own.
 */
public final class Banks {

    /** {@code BANKS} is ungrouped; the store still wants a group argument. */
    private static final String NO_GROUP = "";

    private Banks() {
    }

    /**
     * One bank by id, or null when the store has no such entry.
     *
     * <p>Null is an ordinary outcome, not an error: an NPC can point at a bank an operator has
     * since deleted, and the caller's job is to offer nothing rather than to throw.
     */
    public static BankDefinition get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return null;
        }
        CompoundTag tag = store.get(XenoNpcStoreCategory.BANKS, NO_GROUP, id);
        return tag == null ? null : BankDefinition.load(id, tag);
    }

    /** Every bank in the store, in load order. */
    public static List<BankDefinition> all() {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return List.of();
        }
        List<BankDefinition> out = new ArrayList<>();
        for (XenoNpcWorldStore.Entry entry : store.list(XenoNpcStoreCategory.BANKS)) {
            out.add(BankDefinition.load(entry.id(), entry.tag()));
        }
        return List.copyOf(out);
    }

    /**
     * Writes one bank.
     *
     * @return null on success, or the store's reason for refusing — an id that cannot become a
     *         filename is refused rather than rewritten, so the operator's next lookup still finds
     *         what they named
     */
    public static String put(BankDefinition bank) {
        if (bank == null) {
            return "nothing to write";
        }
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return "the NPC store is not open";
        }
        return store.put(XenoNpcStoreCategory.BANKS, NO_GROUP, bank.id(), bank.save());
    }

    /**
     * Writes a bank only if that id is free.
     *
     * @return the id actually used, or null when it could not be written at all
     */
    public static String putIfAbsent(BankDefinition bank) {
        if (bank == null || get(bank.id()) != null) {
            return null;
        }
        String refusal = put(bank);
        if (refusal != null) {
            XenoPixelsMod.LOGGER.warn("Bank '{}' could not be written: {}", bank.id(), refusal);
            return null;
        }
        return bank.id();
    }
}
