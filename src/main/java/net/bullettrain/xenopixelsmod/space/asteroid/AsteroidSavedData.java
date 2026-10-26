package net.bullettrain.xenopixelsmod.space.asteroid;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
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
    /** Bounds per-cell restore lookup work even if many edited bodies are moved into one location. */
    public static final int MAX_STORED_PER_CELL = 64;
    private static final long MAX_RESTORE_QUERY_CELLS = 4_096;

    private final Set<AsteroidField.ObjectId> detached = new HashSet<>();
    /** Stable Sable UUIDs for detached IDs; legacy entries may not have one. */
    private final Map<AsteroidField.ObjectId, UUID> detachedBodies = new HashMap<>();
    private final Map<UUID, AsteroidField.ObjectId> detachedIdsByBody = new HashMap<>();
    /** Exact Sable slot and last known world position for detached bodies that must be restored on approach. */
    private final Map<AsteroidField.ObjectId, StoredBody> storedBodies = new HashMap<>();
    private final Map<AsteroidField.Cell, LinkedHashSet<AsteroidField.ObjectId>> storedByCell = new HashMap<>();

    public record StoredBody(AsteroidField.ObjectId id, UUID bodyId, GlobalSavedSubLevelPointer pointer,
                             double x, double y, double z,
                             long seed, int radius, boolean icy) {}

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
            if (bodyEntry.contains("storage_x") && bodyEntry.contains("storage_z")
                    && bodyEntry.contains("storage_index") && bodyEntry.contains("sublevel_index")
                    && bodyEntry.contains("x") && bodyEntry.contains("y") && bodyEntry.contains("z")
                    && bodyEntry.contains("seed") && bodyEntry.contains("radius")) {
                int storageIndex = bodyEntry.getInt("storage_index");
                int subLevelIndex = bodyEntry.getInt("sublevel_index");
                double x = bodyEntry.getDouble("x"), y = bodyEntry.getDouble("y"), z = bodyEntry.getDouble("z");
                int radius = bodyEntry.getInt("radius");
                if (storageIndex >= 0 && storageIndex <= Short.MAX_VALUE
                        && subLevelIndex >= 0 && subLevelIndex <= Short.MAX_VALUE
                        && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                        && radius >= AsteroidField.MIN_RADIUS && radius <= AsteroidField.MAX_RADIUS) {
                    var pointer = new GlobalSavedSubLevelPointer(
                            new ChunkPos(bodyEntry.getInt("storage_x"), bodyEntry.getInt("storage_z")),
                            (short) storageIndex, (short) subLevelIndex);
                    data.indexStoredBody(id, new StoredBody(id, bodyId, pointer, x, y, z,
                            bodyEntry.getLong("seed"), radius, bodyEntry.getBoolean("icy")));
                }
            }
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
            StoredBody stored = storedBodies.get(entry.getKey());
            if (stored != null && stored.bodyId().equals(entry.getValue())) {
                bodyTag.putInt("storage_x", stored.pointer().chunkPos().x);
                bodyTag.putInt("storage_z", stored.pointer().chunkPos().z);
                bodyTag.putInt("storage_index", stored.pointer().storageIndex());
                bodyTag.putInt("sublevel_index", stored.pointer().subLevelIndex());
                bodyTag.putDouble("x", stored.x());
                bodyTag.putDouble("y", stored.y());
                bodyTag.putDouble("z", stored.z());
                bodyTag.putLong("seed", stored.seed());
                bodyTag.putInt("radius", stored.radius());
                bodyTag.putBoolean("icy", stored.icy());
            }
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
    public StoredBody storedBody(AsteroidField.ObjectId id) { return storedBodies.get(id); }

    /** Nearest detached saved bodies around one viewer, with both search volume and result count bounded. */
    public List<StoredBody> nearbyStored(double x, double y, double z, double range, int limit) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(range)
                || range < 0 || limit <= 0) return List.of();
        long minX = Math.floorDiv((long) Math.floor(x - range), AsteroidField.CELL_SIZE);
        long maxX = Math.floorDiv((long) Math.floor(x + range), AsteroidField.CELL_SIZE);
        long minY = Math.floorDiv((long) Math.floor(y - range), AsteroidField.CELL_SIZE);
        long maxY = Math.floorDiv((long) Math.floor(y + range), AsteroidField.CELL_SIZE);
        long minZ = Math.floorDiv((long) Math.floor(z - range), AsteroidField.CELL_SIZE);
        long maxZ = Math.floorDiv((long) Math.floor(z + range), AsteroidField.CELL_SIZE);
        long cells = saturatedMultiply(saturatedMultiply(maxX - minX + 1, maxY - minY + 1), maxZ - minZ + 1);
        if (cells <= 0 || cells > MAX_RESTORE_QUERY_CELLS) return List.of();

        double rangeSquared = range * range;
        var nearest = new PriorityQueue<Map.Entry<Double, StoredBody>>(
                Comparator.comparingDouble((Map.Entry<Double, StoredBody> entry) -> entry.getKey()).reversed());
        for (long cx = minX; cx <= maxX; cx++) for (long cy = minY; cy <= maxY; cy++)
            for (long cz = minZ; cz <= maxZ; cz++) {
                Set<AsteroidField.ObjectId> ids = storedByCell.get(new AsteroidField.Cell(cx, cy, cz));
                if (ids == null) continue;
                for (var id : ids) {
                    StoredBody body = storedBodies.get(id);
                    if (body == null || body.pointer() == null) continue;
                    double dx = body.x() - x, dy = body.y() - y, dz = body.z() - z;
                    double distanceSquared = dx * dx + dy * dy + dz * dz;
                    if (distanceSquared > rangeSquared) continue;
                    var entry = Map.entry(distanceSquared, body);
                    if (nearest.size() < limit) nearest.add(entry);
                    else if (distanceSquared < nearest.peek().getKey()) {
                        nearest.remove();
                        nearest.add(entry);
                    }
                }
            }
        var result = new ArrayList<StoredBody>(nearest.size());
        while (!nearest.isEmpty()) result.add(nearest.remove().getValue());
        result.sort(Comparator.comparingDouble(body -> {
            double dx = body.x() - x, dy = body.y() - y, dz = body.z() - z;
            return dx * dx + dy * dy + dz * dz;
        }));
        return List.copyOf(result);
    }

    /** Records a validated storage location only for the UUID already reserved to this field identity. */
    public boolean storeBody(AsteroidField.ObjectId id, UUID bodyId, GlobalSavedSubLevelPointer pointer,
                             double x, double y, double z, long seed, int radius, boolean icy) {
        if (id == null || bodyId == null || pointer == null || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || radius < AsteroidField.MIN_RADIUS || radius > AsteroidField.MAX_RADIUS
                || !bodyId.equals(detachedBodies.get(id))) return false;
        if (!indexStoredBody(id, new StoredBody(id, bodyId, pointer, x, y, z, seed, radius, icy))) return false;
        setDirty();
        return true;
    }

    private boolean indexStoredBody(AsteroidField.ObjectId id, StoredBody body) {
        AsteroidField.Cell cell = storageCell(body.x(), body.y(), body.z());
        StoredBody previous = storedBodies.get(id);
        LinkedHashSet<AsteroidField.ObjectId> nextCell = storedByCell.computeIfAbsent(cell, ignored -> new LinkedHashSet<>());
        AsteroidField.Cell oldCell = previous == null ? null : storageCell(previous.x(), previous.y(), previous.z());
        if ((previous == null || !oldCell.equals(cell)) && nextCell.size() >= MAX_STORED_PER_CELL) {
            if (nextCell.isEmpty()) storedByCell.remove(cell);
            return false;
        }
        if (previous != null) {
            if (!oldCell.equals(cell)) {
                Set<AsteroidField.ObjectId> oldIds = storedByCell.get(oldCell);
                if (oldIds != null) {
                    oldIds.remove(id);
                    if (oldIds.isEmpty()) storedByCell.remove(oldCell);
                }
            }
        }
        storedBodies.put(id, body);
        nextCell.add(id);
        return true;
    }

    private static AsteroidField.Cell storageCell(double x, double y, double z) {
        return new AsteroidField.Cell(Math.floorDiv((long) Math.floor(x), AsteroidField.CELL_SIZE),
                Math.floorDiv((long) Math.floor(y), AsteroidField.CELL_SIZE),
                Math.floorDiv((long) Math.floor(z), AsteroidField.CELL_SIZE));
    }

    private static long saturatedMultiply(long a, long b) {
        if (a < 0 || b < 0) return Long.MAX_VALUE;
        if (a == 0 || b == 0) return 0;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }
    public int detachedBodyCount() { return detachedBodies.size(); }
    /** Legacy tombstones without a serialized body UUID reserve capacity conservatively. */
    public int unmappedDetachedCount() { return detached.size() - detachedBodies.size(); }

    public int size() { return detached.size(); }

    /** Removes only the exact fixture reservation created by the manual storage GameTest. */
    boolean clearForGameTest(AsteroidField.ObjectId id, UUID bodyId) {
        if (id == null || bodyId == null || !bodyId.equals(detachedBodies.get(id))) return false;
        detachedBodies.remove(id);
        detachedIdsByBody.remove(bodyId);
        StoredBody stored = storedBodies.remove(id);
        if (stored != null) {
            AsteroidField.Cell cell = storageCell(stored.x(), stored.y(), stored.z());
            Set<AsteroidField.ObjectId> ids = storedByCell.get(cell);
            if (ids != null) {
                ids.remove(id);
                if (ids.isEmpty()) storedByCell.remove(cell);
            }
        }
        detached.remove(id);
        setDirty();
        return true;
    }

    private static AsteroidField.ObjectId idFrom(long[] id) {
        if (id.length != 4 || id[3] < 0 || id[3] >= AsteroidField.CANDIDATES_PER_CELL) return null;
        return new AsteroidField.ObjectId(id[0], id[1], id[2], (int) id[3]);
    }
}
