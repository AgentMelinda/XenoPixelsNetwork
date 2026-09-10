package net.bullettrain.xenopixelsmod.combat.technique;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Persists KI cleanup barriers so stale chunk data cannot resurrect removed attacks. */
public final class KiCleanupSavedData extends SavedData {
    private static final String FILE_NAME = "xenopixels_ki_cleanup";
    private static final String ENTITY_GENERATION_TAG = "XenoPixelsKiCleanupGeneration";
    private static final int MAX_TOMBSTONES = 16_384;

    private long generation;
    private final LinkedHashSet<UUID> tombstones = new LinkedHashSet<>();

    public static KiCleanupSavedData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new Factory<>(KiCleanupSavedData::new, KiCleanupSavedData::load), FILE_NAME);
    }

    public static KiCleanupSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        KiCleanupSavedData data = new KiCleanupSavedData();
        data.generation = tag.getLong("Generation");
        ListTag entries = tag.getList("Tombstones", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("Id")) {
                data.tombstones.add(entry.getUUID("Id"));
            }
        }
        data.trimTombstones();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("Generation", generation);
        ListTag entries = new ListTag();
        for (UUID id : tombstones) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entries.add(entry);
        }
        tag.put("Tombstones", entries);
        return tag;
    }

    public void beginGlobalClear() {
        generation++;
        tombstones.clear();
        setDirty();
    }

    public void tombstone(Entity entity) {
        if (entity != null && tombstones.add(entity.getUUID())) {
            trimTombstones();
            setDirty();
        }
    }

    public boolean shouldSuppress(Entity entity, boolean loadedFromDisk) {
        if (tombstones.contains(entity.getUUID())) {
            return true;
        }
        return loadedFromDisk && entity.getPersistentData().getLong(ENTITY_GENERATION_TAG) < generation;
    }

    public void markCurrent(Entity entity) {
        entity.getPersistentData().putLong(ENTITY_GENERATION_TAG, generation);
    }

    public long generation() {
        return generation;
    }

    public Set<UUID> tombstones() {
        return Set.copyOf(tombstones);
    }

    private void trimTombstones() {
        while (tombstones.size() > MAX_TOMBSTONES) {
            tombstones.remove(tombstones.iterator().next());
        }
    }
}
