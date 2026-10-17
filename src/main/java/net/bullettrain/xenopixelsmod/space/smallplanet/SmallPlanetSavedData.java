package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;

import java.util.HashSet;
import java.util.Set;

/** Stable ids for small planets handed to Sable storage after a player edits them. */
public final class SmallPlanetSavedData extends SavedData {
    private static final String FILE_NAME = "xenopixels_small_planet_field";
    public static final int MAX_DETACHED = 65_536;
    private final Set<SmallPlanetField.ObjectId> detached = new HashSet<>();

    public static SmallPlanetSavedData get(ServerLevel voidsea) {
        return voidsea.getDataStorage().computeIfAbsent(
                new Factory<>(SmallPlanetSavedData::new, SmallPlanetSavedData::load), FILE_NAME);
    }

    public static SmallPlanetSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new SmallPlanetSavedData();
        ListTag entries = tag.getList("Detached", Tag.TAG_LONG_ARRAY);
        for (int i = 0; i < entries.size() && data.detached.size() < MAX_DETACHED; i++) {
            long[] id = ((LongArrayTag) entries.get(i)).getAsLongArray();
            if (id.length == 3 && id[0] >= -UniverseField.MAX_CELL && id[0] <= UniverseField.MAX_CELL
                    && id[1] >= -UniverseField.MAX_CELL && id[1] <= UniverseField.MAX_CELL
                    && id[2] >= 0 && id[2] < SmallPlanetField.MAX_PLANETS_PER_SYSTEM
                    && !(id[0] == 0 && id[1] == 0)) {
                data.detached.add(new SmallPlanetField.ObjectId(
                        new UniverseField.Cell((int) id[0], (int) id[1]), (int) id[2], 0, 0));
            }
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (var id : detached) entries.add(new LongArrayTag(new long[]{
                id.systemCell().x(), id.systemCell().z(), id.slot()}));
        tag.put("Detached", entries);
        return tag;
    }

    public boolean isDetached(SmallPlanetField.ObjectId id) { return detached.contains(identity(id)); }

    /** @return false when the bounded identity ledger is full and this id was not already present. */
    public boolean detach(SmallPlanetField.ObjectId id) {
        SmallPlanetField.ObjectId identity = identity(id);
        if (detached.contains(identity)) return true;
        if (detached.size() >= MAX_DETACHED) return false;
        detached.add(identity);
        setDirty();
        return true;
    }

    public int size() { return detached.size(); }

    private static SmallPlanetField.ObjectId identity(SmallPlanetField.ObjectId id) {
        return new SmallPlanetField.ObjectId(id.systemCell(), id.slot(), 0, 0);
    }
}
