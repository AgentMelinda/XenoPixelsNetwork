package net.bullettrain.xenopixelsmod.space.hakai;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
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

/** Sparse persistent integrity overrides for chart bodies; untouched bodies begin at 100 percent. */
public final class PlanetIntegritySavedData extends SavedData {
    private static final String FILE_NAME = "xenopixels_planet_integrity_v1";
    /** Admission cap for new sparse rows; persisted rows are preserved even if an old file exceeds it. */
    public static final int MAX_ENTRIES = 200_000;

    public enum UpdateResult { UPDATED, UNCHANGED, INVALID_ID, INVALID_VALUE, CAP_REACHED }

    private final Map<String, Double> integrityByBody = new LinkedHashMap<>();
    private boolean repairedDuringLoad;
    private long mutationRevision;

    private PlanetIntegritySavedData() {}

    public static PlanetIntegritySavedData get(MinecraftServer server) {
        var overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld is unavailable for planet integrity SavedData");
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(PlanetIntegritySavedData::new, PlanetIntegritySavedData::load), FILE_NAME);
    }

    public static PlanetIntegritySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return load(tag, registries, MAX_ENTRIES);
    }

    /** Package-visible cap override keeps the over-cap load policy testable without allocating a huge NBT fixture. */
    static PlanetIntegritySavedData load(CompoundTag tag, HolderLookup.Provider registries, int admissionCap) {
        if (admissionCap < 1 || admissionCap > MAX_ENTRIES)
            throw new IllegalArgumentException("invalid integrity entry cap");
        var data = new PlanetIntegritySavedData();
        ListTag entries = tag.getList("Integrity", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            String id = entry.getString("Id");
            double value = entry.getDouble("Value");
            if (!entry.contains("Id", Tag.TAG_STRING) || !entry.contains("Value", Tag.TAG_DOUBLE)
                    || !validId(id) || !Double.isFinite(value) || value < 0.0 || value >= 100.0) {
                data.repairedDuringLoad = true;
                continue;
            }
            // Keep persisted zero canonical so all layers agree on equality and destroyed-state checks.
            if (value == 0.0) value = 0.0;
            if (data.integrityByBody.putIfAbsent(id, value) != null) data.repairedDuringLoad = true;
        }
        if (data.integrityByBody.size() >= admissionCap) {
            XenoPixelsMod.LOGGER.warn("Persisted planet-integrity map has {} valid unique rows at or above its {} admission cap; preserving existing rows and refusing new unique overrides until the map is below the cap",
                    data.integrityByBody.size(), admissionCap);
        }
        if (data.repairedDuringLoad) data.setDirty();
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        integrityByBody.forEach((id, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", id);
            entry.putDouble("Value", value);
            entries.add(entry);
        });
        tag.put("Integrity", entries);
        return tag;
    }

    /** Returns 100 for a body with no persisted damage. */
    public double integrity(String bodyId) {
        return integrityByBody.getOrDefault(bodyId, 100.0);
    }

    /**
     * Persists only non-default values. Returning to 100 removes the sparse entry; the admission
     * cap refuses new overrides but does not discard valid records already present in a save.
     */
    public UpdateResult setIntegrity(String bodyId, double value) {
        return setIntegrity(bodyId, value, MAX_ENTRIES);
    }

    /** Applies a resolved damage amount while keeping mutation and persistence in one SavedData operation. */
    public UpdateResult damage(String bodyId, double loss) {
        if (!validId(bodyId)) return UpdateResult.INVALID_ID;
        double next = PlanetIntegrityRules.damage(integrity(bodyId), loss);
        return setIntegrity(bodyId, next);
    }

    /** Applies configured recovery; destroyed bodies remain at zero until an explicit restore. */
    public UpdateResult regenerate(String bodyId, double pointsPerTick, long elapsedTicks) {
        if (!validId(bodyId)) return UpdateResult.INVALID_ID;
        double next = PlanetIntegrityRules.regenerate(integrity(bodyId), pointsPerTick, elapsedTicks);
        return setIntegrity(bodyId, next);
    }

    /** Explicit restoration is the only current transition from destroyed integrity (0) back to full. */
    public boolean restore(String bodyId) {
        return setIntegrity(bodyId, 100.0) == UpdateResult.UPDATED;
    }

    /** Clears all persisted damage overrides for admin-wide restore and season-reset callers. */
    public int restoreAll() {
        int restored = integrityByBody.size();
        if (restored > 0) {
            integrityByBody.clear();
            mutationRevision++;
            setDirty();
        }
        return restored;
    }

    /** Testable capacity boundary; the supplied limit can only lower the production hard cap. */
    UpdateResult setIntegrity(String bodyId, double value, int maximumEntries) {
        if (maximumEntries < 1 || maximumEntries > MAX_ENTRIES) throw new IllegalArgumentException("invalid integrity entry cap");
        if (!validId(bodyId)) return UpdateResult.INVALID_ID;
        if (!Double.isFinite(value) || value < 0.0 || value > 100.0) return UpdateResult.INVALID_VALUE;
        if (value == 0.0) value = 0.0; // Canonicalize negative zero before comparing or persisting.
        Double previous = integrityByBody.get(bodyId);
        if (value == 100.0) {
            if (previous == null) return UpdateResult.UNCHANGED;
            integrityByBody.remove(bodyId);
            mutationRevision++;
            setDirty();
            return UpdateResult.UPDATED;
        }
        if (previous != null && Double.compare(previous, value) == 0) return UpdateResult.UNCHANGED;
        if (previous == null && integrityByBody.size() >= maximumEntries) return UpdateResult.CAP_REACHED;
        integrityByBody.put(bodyId, value);
        mutationRevision++;
        setDirty();
        return UpdateResult.UPDATED;
    }

    public int size() { return integrityByBody.size(); }

    public Map<String, Double> entries() { return Collections.unmodifiableMap(integrityByBody); }
    /** Changes when the in-memory sparse integrity map changes; not persisted. */
    public long mutationRevision() { return mutationRevision; }

    private static boolean validId(String bodyId) {
        if (bodyId == null) return false;
        try {
            net.bullettrain.xenopixelsmod.space.chart.ChartDefinition.id(bodyId);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
