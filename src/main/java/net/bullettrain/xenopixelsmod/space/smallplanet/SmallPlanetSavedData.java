package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Stable ids for small planets handed to Sable storage after a player edits them. */
public final class SmallPlanetSavedData extends SavedData {
    private static final String FILE_NAME = "xenopixels_small_planet_field";
    public static final int MAX_DETACHED = 65_536;
    public static final int MAX_RELEASED_QUARANTINES = 65_536;
    private final Set<SmallPlanetField.ObjectId> detached = new HashSet<>();
    private final Set<UUID> releasedQuarantines = new HashSet<>();

    public static SmallPlanetSavedData get(ServerLevel voidsea) {
        return voidsea.getDataStorage().computeIfAbsent(
                new Factory<>(SmallPlanetSavedData::new, SmallPlanetSavedData::load), FILE_NAME);
    }

    public static SmallPlanetSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new SmallPlanetSavedData();
        ListTag entries = tag.getList("Detached", Tag.TAG_LONG_ARRAY);
        for (int i = 0; i < entries.size(); i++) {
            long[] id = ((LongArrayTag) entries.get(i)).getAsLongArray();
            if (id.length == 3 && id[0] >= -UniverseField.MAX_CELL && id[0] <= UniverseField.MAX_CELL
                    && id[1] >= -UniverseField.MAX_CELL && id[1] <= UniverseField.MAX_CELL
                    && id[2] >= 0 && id[2] < SmallPlanetField.MAX_PLANETS_PER_SYSTEM
                    && !(id[0] == 0 && id[1] == 0)) {
                data.detached.add(new SmallPlanetField.ObjectId(
                        new UniverseField.Cell((int) id[0], (int) id[1]), (int) id[2], 0, 0));
            }
        }
        if (data.detached.size() > MAX_DETACHED)
            XenoPixelsMod.LOGGER.error("Persisted small-planet detached ledger has {} identities above its {} write limit; loading all valid decisions to prevent detached bodies from regenerating",
                    data.detached.size(), MAX_DETACHED);
        ListTag released = tag.getList("ReleasedQuarantines", Tag.TAG_LONG_ARRAY);
        for (int i = 0; i < released.size(); i++) {
            long[] id = ((LongArrayTag) released.get(i)).getAsLongArray();
            if (id.length == 2) data.releasedQuarantines.add(new UUID(id[0], id[1]));
        }
        if (data.releasedQuarantines.size() > MAX_RELEASED_QUARANTINES)
            XenoPixelsMod.LOGGER.error("Persisted small-planet quarantine-release ledger has {} identities above its {} write limit; loading all valid decisions to preserve operator releases",
                    data.releasedQuarantines.size(), MAX_RELEASED_QUARANTINES);
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (var id : detached) entries.add(new LongArrayTag(new long[]{
                id.systemCell().x(), id.systemCell().z(), id.slot()}));
        tag.put("Detached", entries);
        ListTag released = new ListTag();
        for (UUID id : releasedQuarantines)
            released.add(new LongArrayTag(new long[]{id.getMostSignificantBits(), id.getLeastSignificantBits()}));
        tag.put("ReleasedQuarantines", released);
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

    /**
     * Persists an operator decision independently of Sable's body serializer. The body UUID is stable across
     * Sable unload/reload, while the body's user-data tag may not be serialized when its final ticket is dropped.
     */
    public boolean markQuarantineReleased(UUID bodyId) {
        if (releasedQuarantines.contains(bodyId)) return true;
        if (releasedQuarantines.size() >= MAX_RELEASED_QUARANTINES) return false;
        releasedQuarantines.add(bodyId);
        setDirty();
        return true;
    }

    public boolean isQuarantineReleased(UUID bodyId) { return releasedQuarantines.contains(bodyId); }

    /** Package-private: call only after an exact Sable storage-slot read-back proves UUID and body marker. */
    boolean pruneQuarantineReleasedAfterVerifiedBodyReadback(UUID bodyId) {
        if (!releasedQuarantines.remove(bodyId)) return false;
        setDirty();
        return true;
    }

    /** Removes only a test-owned release decision when its isolated fixture aborts before verification. */
    boolean clearQuarantineReleaseForGameTest(UUID bodyId) {
        if (!releasedQuarantines.remove(bodyId)) return false;
        setDirty();
        return true;
    }

    public Set<UUID> releasedQuarantineIds() { return Set.copyOf(releasedQuarantines); }

    public int releasedQuarantineCount() { return releasedQuarantines.size(); }

    private static SmallPlanetField.ObjectId identity(SmallPlanetField.ObjectId id) {
        return new SmallPlanetField.ObjectId(id.systemCell(), id.slot(), 0, 0);
    }
}
