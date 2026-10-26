package net.bullettrain.xenopixelsmod.space.hakai;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chart bodies erased by planet Hakai (design-doc 16b: "a Universe Registry state change, not a chunk-deletion event").
 * The chart is rebuilt without these ids; nothing in any dimension is deleted, so restoring is just removing the entry.
 */
public final class ErasedBodiesSavedData extends SavedData {
    private static final String FILE_NAME = "xenopixels_erased_bodies";
    public static final int MAX = 1024;

    public record Entry(long erasedAtMillis, String by, double x, double z) {
        public boolean hasPosition() { return coordinate(x) && coordinate(z); }
    }

    private final Map<String, Entry> erased = new LinkedHashMap<>();
    private long mutationRevision;

    public static ErasedBodiesSavedData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage()
                .computeIfAbsent(new Factory<>(ErasedBodiesSavedData::new, ErasedBodiesSavedData::load), FILE_NAME);
    }

    public static ErasedBodiesSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new ErasedBodiesSavedData();
        ListTag list = tag.getList("Erased", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), MAX); i++) {
            CompoundTag e = list.getCompound(i);
            String id = e.getString("Id");
            if (!id.isEmpty()) {
                double x = e.contains("X", Tag.TAG_DOUBLE) ? e.getDouble("X") : Double.NaN;
                double z = e.contains("Z", Tag.TAG_DOUBLE) ? e.getDouble("Z") : Double.NaN;
                data.erased.put(id, new Entry(e.getLong("At"), e.getString("By"), x, z));
            }
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        erased.forEach((id, entry) -> {
            CompoundTag e = new CompoundTag();
            e.putString("Id", id); e.putLong("At", entry.erasedAtMillis()); e.putString("By", entry.by());
            if (entry.hasPosition()) { e.putDouble("X", entry.x()); e.putDouble("Z", entry.z()); }
            list.add(e);
        });
        tag.put("Erased", list);
        return tag;
    }

    public Map<String, Entry> erased() { return Collections.unmodifiableMap(erased); }

    public boolean isErased(String id) { return erased.containsKey(id); }

    public boolean erase(String id, String by, long now) {
        return erase(id, by, now, Double.NaN, Double.NaN);
    }

    /** Stores the last authoritative chart position so an erased body's Atlas marker can survive chart removal. */
    public boolean erase(String id, String by, long now, double x, double z) {
        if (erased.size() >= MAX || erased.containsKey(id)) return false;
        boolean legacyWithoutPosition = Double.isNaN(x) && Double.isNaN(z);
        if (!legacyWithoutPosition && (!coordinate(x) || !coordinate(z))) return false;
        erased.put(id, new Entry(now, by, x, z));
        mutationRevision++;
        setDirty();
        return true;
    }

    /** Records Hakai first; an unsupported map position degrades to a persisted, explicitly unmapped row. */
    public boolean eraseAtPositionOrUnmapped(String id, String by, long now, double x, double z) {
        return coordinate(x) && coordinate(z) ? erase(id, by, now, x, z) : erase(id, by, now);
    }

    public boolean restore(String id) {
        if (erased.remove(id) == null) return false;
        mutationRevision++;
        setDirty();
        return true;
    }

    public int restoreAll() {
        int n = erased.size();
        erased.clear();
        if (n > 0) { mutationRevision++; setDirty(); }
        return n;
    }

    /** Changes when the erased-body registry mutates; not persisted. */
    public long mutationRevision() { return mutationRevision; }

    private static boolean coordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 30_000_000.0;
    }
}
