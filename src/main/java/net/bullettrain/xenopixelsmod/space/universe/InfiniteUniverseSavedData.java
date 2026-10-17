package net.bullettrain.xenopixelsmod.space.universe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Persistent discoveries for the full-width logical field. Kept separate from the legacy finite Atlas bitmap so
 * existing cells and packet formats remain stable. The configured maximum bounds both memory and save payload size.
 */
public final class InfiniteUniverseSavedData extends SavedData {
    public static final int MAX_REVEALED = 200_000;
    private static final String FILE_NAME = "xenopixels_universe_logical_v1";

    public enum Source { LEGACY, TIMER, DISCOVERY, ADMIN }
    public enum RevealResult { ADDED, ALREADY_REVEALED, CAP_REACHED }
    public record Entry(Source source, long day) {}

    private final Map<InfiniteUniverseField.Cell, Entry> revealed = new LinkedHashMap<>();
    private boolean legacyImported;
    private boolean repairedDuringLoad;

    private InfiniteUniverseSavedData() {}

    public static InfiniteUniverseSavedData get(ServerLevel level) {
        var data = level.getDataStorage().computeIfAbsent(
                new Factory<>(InfiniteUniverseSavedData::new, InfiniteUniverseSavedData::load), FILE_NAME);
        if (!data.legacyImported) data.importLegacy(UniverseSavedData.get(level));
        return data;
    }

    public static InfiniteUniverseSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new InfiniteUniverseSavedData();
        long[] coordinates = tag.getLongArray("Coordinates");
        byte[] sources = tag.getByteArray("Sources");
        long[] days = tag.getLongArray("Days");
        int available = Math.min(coordinates.length / 2, MAX_REVEALED);
        for (int i = 0; i < available; i++) {
            long x = coordinates[i * 2];
            long z = coordinates[i * 2 + 1];
            int sourceOrdinal = i < sources.length ? sources[i] & 0xff : -1;
            Source source = sourceOrdinal >= 0 && sourceOrdinal < Source.values().length
                    ? Source.values()[sourceOrdinal] : Source.LEGACY;
            long day = i < days.length ? days[i] : 0L;
            if (data.revealed.putIfAbsent(new InfiniteUniverseField.Cell(x, z), new Entry(source, day)) != null)
                data.repairedDuringLoad = true;
        }
        if ((coordinates.length & 1) != 0 || coordinates.length / 2 > MAX_REVEALED
                || sources.length != coordinates.length / 2 || days.length != coordinates.length / 2)
            data.repairedDuringLoad = true;
        data.legacyImported = tag.getBoolean("LegacyImported");
        if (data.repairedDuringLoad) data.setDirty();
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        int count = revealed.size();
        long[] coordinates = new long[count * 2];
        byte[] sources = new byte[count];
        long[] days = new long[count];
        int i = 0;
        for (var entry : revealed.entrySet()) {
            coordinates[i * 2] = entry.getKey().x();
            coordinates[i * 2 + 1] = entry.getKey().z();
            sources[i] = (byte) entry.getValue().source().ordinal();
            days[i] = entry.getValue().day();
            i++;
        }
        tag.putLongArray("Coordinates", coordinates);
        tag.putByteArray("Sources", sources);
        tag.putLongArray("Days", days);
        tag.putBoolean("LegacyImported", legacyImported);
        return tag;
    }

    public RevealResult reveal(long x, long z, Source source, long day, int configuredMaximum) {
        if (configuredMaximum < 1 || configuredMaximum > MAX_REVEALED)
            throw new IllegalArgumentException("logical universe maximum must be 1.." + MAX_REVEALED);
        Objects.requireNonNull(source, "source");
        var cell = new InfiniteUniverseField.Cell(x, z);
        if (revealed.containsKey(cell)) return RevealResult.ALREADY_REVEALED;
        if (revealed.size() >= configuredMaximum) return RevealResult.CAP_REACHED;
        revealed.put(cell, new Entry(source, day));
        setDirty();
        return RevealResult.ADDED;
    }

    public boolean isRevealed(long x, long z) {
        return isRevealed(new InfiniteUniverseField.Cell(x, z));
    }

    public boolean isRevealed(InfiniteUniverseField.Cell cell) { return revealed.containsKey(cell); }

    public int size() { return revealed.size(); }
    public Map<InfiniteUniverseField.Cell, Entry> entries() { return Collections.unmodifiableMap(revealed); }
    public boolean legacyImported() { return legacyImported; }

    void importLegacy(UniverseSavedData legacy) {
        importLegacy(legacy, MAX_REVEALED);
    }

    /**
     * Imports finite-era discoveries without allowing migration to exceed the hard logical-save cap. Existing
     * logical discoveries win; any finite entries that do not fit remain untouched in their original SavedData.
     * The alternate cap is package-private for boundary tests and can never exceed the production hard cap.
     */
    void importLegacy(UniverseSavedData legacy, int hardMaximum) {
        Objects.requireNonNull(legacy, "legacy");
        if (hardMaximum < 1 || hardMaximum > MAX_REVEALED)
            throw new IllegalArgumentException("legacy import maximum must be 1.." + MAX_REVEALED);
        if (legacyImported) return;
        int remaining = Math.max(0, hardMaximum - revealed.size());
        for (var entry : legacy.entries().entrySet()) {
            if (remaining <= 0) break;
            var old = entry.getKey();
            var source = switch (entry.getValue().source()) {
                case TIMER -> Source.TIMER;
                case DISCOVERY -> Source.DISCOVERY;
                case HOME, ADMIN -> Source.LEGACY;
            };
            if (revealed.putIfAbsent(new InfiniteUniverseField.Cell(old.x(), old.z()),
                    new Entry(source, entry.getValue().day())) == null) remaining--;
        }
        legacyImported = true;
        setDirty();
    }
}
