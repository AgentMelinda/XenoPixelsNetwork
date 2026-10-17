package net.bullettrain.xenopixelsmod.space.asteroid;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Modified asteroids that must no longer be recreated from the deterministic field (S11 B3). Their IDs are reserved
 * on the first accepted edit, before release, so a full bounded ledger can reject an edit instead of losing it later.
 * On release, Sable keeps the body as an ordinary stored sub-level. Untouched asteroids are not listed and rebuild
 * from the seed. Stored in the Voidsea level's data folder.
 */
public final class AsteroidSavedData extends SavedData {
    private static final String FILE_NAME = "xenopixels_asteroid_field";
    /** Bound on the file; past it the oldest detached ids would respawn as fresh rock (logged). */
    public static final int MAX_DETACHED = 65_536;

    private final Set<AsteroidField.ObjectId> detached = new HashSet<>();
    /** Stable Sable UUIDs for detached IDs; legacy entries may not have one. */
    private final Map<AsteroidField.ObjectId, UUID> detachedBodies = new HashMap<>();
    private final Map<UUID, AsteroidField.ObjectId> detachedIdsByBody = new HashMap<>();

    public static AsteroidSavedData get(ServerLevel voidsea) {
        return voidsea.getDataStorage().computeIfAbsent(new Factory<>(AsteroidSavedData::new, AsteroidSavedData::load), FILE_NAME);
    }

    public static AsteroidSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AsteroidSavedData data = new AsteroidSavedData();
        ListTag entries = tag.getList("Detached", Tag.TAG_LONG_ARRAY);
        for (int i = 0; i < entries.size() && data.detached.size() < MAX_DETACHED; i++) {
            AsteroidField.ObjectId id = idFrom(((LongArrayTag) entries.get(i)).getAsLongArray());
            if (id != null) data.detached.add(id);
        }
        ListTag bodyEntries = tag.getList("DetachedBodies", Tag.TAG_COMPOUND);
        for (int i = 0; i < bodyEntries.size() && data.detachedBodies.size() < MAX_DETACHED; i++) {
            CompoundTag bodyEntry = bodyEntries.getCompound(i);
            AsteroidField.ObjectId id = idFrom(bodyEntry.getLongArray("id"));
            if (id == null || !data.detached.contains(id) || !bodyEntry.contains("uuid", Tag.TAG_INT_ARRAY)) continue;
            UUID bodyId = bodyEntry.getUUID("uuid");
            AsteroidField.ObjectId existingId = data.detachedIdsByBody.get(bodyId);
            UUID existingBody = data.detachedBodies.get(id);
            if (existingId != null && !existingId.equals(id) || existingBody != null && !existingBody.equals(bodyId)) continue;
            data.detachedBodies.put(id, bodyId);
            data.detachedIdsByBody.put(bodyId, id);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (var id : detached) entries.add(new LongArrayTag(new long[]{id.cellX(), id.cellY(), id.cellZ(), id.slot()}));
        tag.put("Detached", entries);
        ListTag bodyEntries = new ListTag();
        for (var entry : detachedBodies.entrySet()) {
            CompoundTag bodyTag = new CompoundTag();
            bodyTag.putLongArray("id", new long[]{entry.getKey().cellX(), entry.getKey().cellY(),
                    entry.getKey().cellZ(), entry.getKey().slot()});
            bodyTag.putUUID("uuid", entry.getValue());
            bodyEntries.add(bodyTag);
        }
        tag.put("DetachedBodies", bodyEntries);
        return tag;
    }

    public boolean isDetached(AsteroidField.ObjectId id) { return detached.contains(id); }

    /** @return false when the bound is reached and the id was not recorded */
    public boolean detach(AsteroidField.ObjectId id) {
        if (detached.contains(id)) return true;
        if (detached.size() >= MAX_DETACHED) return false;
        if (detached.add(id)) setDirty();
        return true;
    }

    /** Records both the deterministic field tombstone and Sable's persistent body identity. */
    public boolean detach(AsteroidField.ObjectId id, UUID bodyId) {
        if (id == null || bodyId == null) return false;
        UUID existingBody = detachedBodies.get(id);
        if (existingBody != null) return existingBody.equals(bodyId);
        AsteroidField.ObjectId existingId = detachedIdsByBody.get(bodyId);
        if (existingId != null && !existingId.equals(id)) return false;
        if (!detached.contains(id) && detached.size() >= MAX_DETACHED) return false;
        boolean changed = detached.add(id);
        detachedBodies.put(id, bodyId);
        detachedIdsByBody.put(bodyId, id);
        if (changed || existingBody == null) setDirty();
        return true;
    }

    public boolean isDetachedBody(UUID bodyId) { return detachedIdsByBody.containsKey(bodyId); }
    public int detachedBodyCount() { return detachedBodies.size(); }
    /** Legacy tombstones without a serialized body UUID reserve capacity conservatively. */
    public int unmappedDetachedCount() { return detached.size() - detachedBodies.size(); }

    public int size() { return detached.size(); }

    private static AsteroidField.ObjectId idFrom(long[] id) {
        if (id.length != 4 || id[3] < 0 || id[3] >= AsteroidField.CANDIDATES_PER_CELL) return null;
        return new AsteroidField.ObjectId(id[0], id[1], id[2], (int) id[3]);
    }
}
