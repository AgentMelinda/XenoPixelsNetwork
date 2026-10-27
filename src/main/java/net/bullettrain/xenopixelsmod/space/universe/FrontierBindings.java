package net.bullettrain.xenopixelsmod.space.universe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Persistent, append-only mapping from long logical addresses to vacant finite Voidsea cells. */
public final class FrontierBindings extends SavedData {
    /** Hard server and packet cap; logical chart reveals may be larger than physical incarnations. */
    public static final int MAX_BINDINGS = 512;
    private static final String FILE_NAME = "xenopixels_universe_frontier_v1";
    private static final int SCHEMA = 1;

    public enum BindResult { ADDED, ALREADY_BOUND, EMPTY_LOGICAL_CELL, CAP_REACHED, NO_VACANT_SLOT, FIELD_SEED_MISMATCH, ALLOCATION_LOCKED }

    private final Map<InfiniteUniverseField.Cell, UniverseField.Cell> logicalToPhysical = new LinkedHashMap<>();
    private final Map<UniverseField.Cell, InfiniteUniverseField.Cell> physicalToLogical = new LinkedHashMap<>();
    /** Slots from rejected records remain permanently reserved so repaired saves cannot reuse a world address. */
    private final Set<UniverseField.Cell> tombstones = new LinkedHashSet<>();
    private long fieldSeed;
    private boolean fieldSeedSet;
    private boolean fieldSeedMismatch;
    private boolean repairedDuringLoad;
    /** Fail closed when the stored format is too damaged to identify every previously allocated slot. */
    private boolean allocationLocked;
    /** Preserve future-schema or structurally malformed data byte-for-byte through older server saves. */
    private CompoundTag unrecognizedRawTag;

    FrontierBindings() {}

    public static FrontierBindings get(ServerLevel level) {
        FrontierBindings data = level.getDataStorage().computeIfAbsent(
                new Factory<>(FrontierBindings::new, FrontierBindings::load), FILE_NAME);
        long currentSeed = UniverseService.fieldSeed(level.getServer());
        if (!data.fieldSeedSet) {
            data.fieldSeed = currentSeed;
            data.fieldSeedSet = true;
            data.setDirty();
        } else if (data.fieldSeed != currentSeed) {
            data.fieldSeedMismatch = true;
        }
        return data;
    }

    public static FrontierBindings load(CompoundTag tag, HolderLookup.Provider registries) {
        FrontierBindings data = new FrontierBindings();
        data.allocationLocked = tag.getBoolean("AllocationLocked");
        data.fieldSeedSet = tag.getBoolean("FieldSeedSet");
        data.fieldSeed = tag.getLong("FieldSeed");
        long[] bindings = tag.getLongArray("Bindings");
        if (tag.getInt("Schema") != SCHEMA || bindings.length % 4 != 0
                || bindings.length / 4 > MAX_BINDINGS) {
            data.repairedDuringLoad = true;
            data.allocationLocked = true;
            data.unrecognizedRawTag = tag.copy();
            net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.error(
                    "Frontier bindings {} has an unsupported schema or malformed binding array; preserving its raw data and locking new allocations",
                    FILE_NAME);
            return data;
        }
        if (tag.contains("Tombstones", CompoundTag.TAG_LONG_ARRAY)) {
            long[] savedTombstones = tag.getLongArray("Tombstones");
            if (savedTombstones.length % 2 != 0) {
                data.repairedDuringLoad = true;
                data.allocationLocked = true;
            } else {
                for (int i = 0; i < savedTombstones.length; i += 2) {
                    try {
                        data.tombstones.add(new UniverseField.Cell(Math.toIntExact(savedTombstones[i]),
                                Math.toIntExact(savedTombstones[i + 1])));
                    } catch (IllegalArgumentException | ArithmeticException malformed) {
                        data.repairedDuringLoad = true;
                        data.allocationLocked = true;
                    }
                }
            }
        }
        for (int i = 0; i < bindings.length; i += 4) {
            UniverseField.Cell physical = null;
            try {
                physical = new UniverseField.Cell(Math.toIntExact(bindings[i + 2]), Math.toIntExact(bindings[i + 3]));
                var logical = new InfiniteUniverseField.Cell(bindings[i], bindings[i + 1]);
                // SavedData is untrusted input: an occupied logical address or a finite cell that
                // now has a natural star must not poison every client's bounded snapshot at login.
                if (InfiniteUniverseField.systemAt(data.fieldSeed, logical) == null
                        || !data.fieldSeedSet
                        || UniverseField.systemAt(data.fieldSeed, physical) != null
                        || data.logicalToPhysical.containsKey(logical) || data.physicalToLogical.containsKey(physical)) {
                    data.repairedDuringLoad = true;
                    if (!data.physicalToLogical.containsKey(physical)
                            && UniverseField.systemAt(data.fieldSeed, physical) == null)
                        data.tombstones.add(physical);
                    continue;
                }
                data.logicalToPhysical.put(logical, physical);
                data.physicalToLogical.put(physical, logical);
            } catch (IllegalArgumentException | ArithmeticException malformed) {
                data.repairedDuringLoad = true;
                if (physical == null) data.allocationLocked = true;
                else if (!data.physicalToLogical.containsKey(physical)) data.tombstones.add(physical);
            }
        }
        if (bindings.length > 0 && !data.fieldSeedSet) data.allocationLocked = true;
        if (data.repairedDuringLoad) data.setDirty();
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (unrecognizedRawTag != null) return unrecognizedRawTag.copy();
        tag.putInt("Schema", SCHEMA);
        tag.putBoolean("FieldSeedSet", fieldSeedSet);
        if (fieldSeedSet) tag.putLong("FieldSeed", fieldSeed);
        long[] bindings = new long[logicalToPhysical.size() * 4];
        int index = 0;
        for (var entry : logicalToPhysical.entrySet()) {
            bindings[index++] = entry.getKey().x();
            bindings[index++] = entry.getKey().z();
            bindings[index++] = entry.getValue().x();
            bindings[index++] = entry.getValue().z();
        }
        tag.putLongArray("Bindings", bindings);
        long[] savedTombstones = new long[tombstones.size() * 2];
        index = 0;
        for (UniverseField.Cell tombstone : tombstones) {
            savedTombstones[index++] = tombstone.x();
            savedTombstones[index++] = tombstone.z();
        }
        tag.putLongArray("Tombstones", savedTombstones);
        tag.putBoolean("AllocationLocked", allocationLocked);
        return tag;
    }

    /** Bind only a valid logical system. Existing bindings are never reused or reassigned. */
    public BindResult bind(long worldSeed, InfiniteUniverseField.Cell logical, int maximumBindings) {
        if (logical == null) throw new IllegalArgumentException("logical system address is required");
        if (maximumBindings < 1 || maximumBindings > MAX_BINDINGS)
            throw new IllegalArgumentException("frontier binding cap must be in 1.." + MAX_BINDINGS);
        if (fieldSeedSet && fieldSeed != worldSeed) {
            fieldSeedMismatch = true;
            return BindResult.FIELD_SEED_MISMATCH;
        }
        if (logicalToPhysical.containsKey(logical)) return BindResult.ALREADY_BOUND;
        if (allocationLocked) return BindResult.ALLOCATION_LOCKED;
        if (InfiniteUniverseField.systemAt(worldSeed, logical) == null) return BindResult.EMPTY_LOGICAL_CELL;
        if (logicalToPhysical.size() >= maximumBindings) return BindResult.CAP_REACHED;

        Set<UniverseField.Cell> reserved = new LinkedHashSet<>(physicalToLogical.keySet());
        reserved.addAll(tombstones);
        UniverseField.Cell slot = FrontierSlots.findVacant(worldSeed, logical, reserved);
        if (slot == null) return BindResult.NO_VACANT_SLOT;
        logicalToPhysical.put(logical, slot);
        physicalToLogical.put(slot, logical);
        if (!fieldSeedSet) {
            fieldSeed = worldSeed;
            fieldSeedSet = true;
        }
        setDirty();
        return BindResult.ADDED;
    }

    public UniverseField.Cell physicalCell(InfiniteUniverseField.Cell logical) {
        return logicalToPhysical.get(logical);
    }

    public InfiniteUniverseField.Cell logicalCell(UniverseField.Cell physical) {
        return physicalToLogical.get(physical);
    }

    public int size() { return logicalToPhysical.size(); }
    public boolean fieldSeedMismatch() { return fieldSeedMismatch; }
    public boolean allocationLocked() { return allocationLocked; }
    public Map<InfiniteUniverseField.Cell, UniverseField.Cell> entries() {
        return Collections.unmodifiableMap(logicalToPhysical);
    }

    /** Resolves the natural finite system first, then a bound logical system hosted in an empty finite cell. */
    public UniverseField.StarSystem systemAt(long worldSeed, UniverseField.Cell physical) {
        UniverseField.StarSystem natural = UniverseField.systemAt(worldSeed, physical);
        if (natural != null || fieldSeedMismatch || (fieldSeedSet && fieldSeed != worldSeed)) return natural;
        InfiniteUniverseField.Cell logical = physicalToLogical.get(physical);
        if (logical == null) return null;
        InfiniteUniverseField.StarSystem source = InfiniteUniverseField.systemAt(worldSeed, logical);
        if (source == null) return null;
        double x = physical.x() * (double) UniverseField.CELL_SIZE + source.localX();
        double z = physical.z() * (double) UniverseField.CELL_SIZE + source.localZ();
        return new UniverseField.StarSystem(physical, source.seed(), source.name(), source.starClass(),
                x, source.y(), z, source.planetSlots());
    }

    public Set<UniverseField.Cell> occupiedSlots() {
        var occupied = new LinkedHashSet<>(physicalToLogical.keySet());
        occupied.addAll(tombstones);
        return Collections.unmodifiableSet(occupied);
    }
}
