package net.bullettrain.xenopixelsmod.npc.spawn;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * World-store access for the natural spawn rules. Server thread only.
 *
 * <p><b>Retires the reservation.</b> {@link XenoNpcStoreCategory#SPAWNS} shipped with "Reserved, no
 * reader. Unblocked by a spawner that reads them" — {@link NpcNaturalSpawnService} is that spawner.
 *
 * <p>Reads the store live on every call rather than caching, exactly as {@code XenoNpcScenes} does.
 * A cache here would need an invalidation rule at every one of the store's write paths (editor,
 * command, importer, hand edit) and the first missed one is a rule that stops working with no
 * explanation. The store keeps its entries in memory once loaded, so the live read is not a disk
 * read.
 */
public final class NpcNaturalSpawns {

    private NpcNaturalSpawns() {
    }

    /** One rule by id, or null when nothing is stored under it. */
    @Nullable
    public static NpcNaturalSpawn get(String id) {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null || id == null || id.isBlank()) {
            return null;
        }
        net.minecraft.nbt.CompoundTag tag = store.get(XenoNpcStoreCategory.SPAWNS, "", id);
        return tag == null ? null : NpcNaturalSpawn.load(id, tag);
    }

    /** Every stored rule, in the store's order. */
    public static List<NpcNaturalSpawn> all() {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return List.of();
        }
        List<NpcNaturalSpawn> result = new ArrayList<>();
        for (XenoNpcWorldStore.Entry entry : store.list(XenoNpcStoreCategory.SPAWNS)) {
            result.add(NpcNaturalSpawn.load(entry.id(), entry.tag()));
        }
        return List.copyOf(result);
    }

    /** The rules that could place something in this biome at this time of day. */
    public static List<NpcNaturalSpawn> eligibleAt(String biomeId, boolean day) {
        return NpcNaturalSpawn.eligible(all(), biomeId, day);
    }

    /**
     * Stores a rule.
     *
     * @return null on success, or why it was refused
     */
    public static String put(NpcNaturalSpawn rule) {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return "the world store is not loaded";
        }
        return store.put(XenoNpcStoreCategory.SPAWNS, "", rule.id(), rule.save());
    }

    /** Deletes a rule. */
    public static String remove(String id) {
        XenoNpcWorldStore store = XenoNpcStores.get();
        return store == null ? "the world store is not loaded"
                : store.remove(XenoNpcStoreCategory.SPAWNS, "", id);
    }
}
