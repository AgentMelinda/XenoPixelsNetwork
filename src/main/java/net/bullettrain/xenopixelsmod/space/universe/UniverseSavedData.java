package net.bullettrain.xenopixelsmod.space.universe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * The known universe: which system cells are revealed, how (timer or discovery) and on which game day, plus the growth
 * bookkeeping. Only these few records are stored; every system itself is recomputed from the seed by {@link UniverseField}.
 * Kept in the Voidsea level's data folder as {@code xenopixels_universe}.
 */
public final class UniverseSavedData extends SavedData {
    private static final String FILE_NAME = "xenopixels_universe";
    public enum Source { HOME, TIMER, DISCOVERY, ADMIN }
    public record Entry(Source source, long day) {}

    private final Map<UniverseField.Cell, Entry> revealed = new LinkedHashMap<>();
    private final List<UniverseField.Cell> revealOrder = new ArrayList<>();
    private final NavigableSet<UniverseField.Cell> timerFrontier = new TreeSet<>(this::compareFrontier);
    private int frontierCursor;
    private long frontierSeed;
    private boolean frontierStarted;
    private boolean frontierReady;
    long lastTimerDay = Long.MIN_VALUE;
    long discoveryDay = Long.MIN_VALUE;
    int discoveredToday;

    public static UniverseSavedData get(ServerLevel voidsea) {
        var data = voidsea.getDataStorage().computeIfAbsent(new Factory<>(UniverseSavedData::new, UniverseSavedData::load), FILE_NAME);
        if (data.revealed.isEmpty()) {
            var home = new UniverseField.Cell(0, 0);
            data.revealed.put(home, new Entry(Source.HOME, 0));
            data.revealOrder.add(home);
            data.setDirty();
        }
        return data;
    }

    public static UniverseSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        UniverseSavedData data = new UniverseSavedData();
        ListTag list = tag.getList("Revealed", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            int x = e.getInt("x"), z = e.getInt("z");
            if (!UniverseField.Cell.inRange(x) || !UniverseField.Cell.inRange(z)) continue;
            Source source;
            try { source = Source.valueOf(e.getString("source")); } catch (IllegalArgumentException bad) { source = Source.ADMIN; }
            var cell = new UniverseField.Cell(x, z);
            if (data.revealed.putIfAbsent(cell, new Entry(source, e.getLong("day"))) == null)
                data.revealOrder.add(cell);
        }
        data.lastTimerDay = tag.contains("LastTimerDay") ? tag.getLong("LastTimerDay") : Long.MIN_VALUE;
        data.discoveryDay = tag.contains("DiscoveryDay") ? tag.getLong("DiscoveryDay") : Long.MIN_VALUE;
        data.discoveredToday = tag.getInt("DiscoveredToday");
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        revealed.forEach((cell, entry) -> {
            CompoundTag e = new CompoundTag();
            e.putInt("x", cell.x()); e.putInt("z", cell.z());
            e.putString("source", entry.source().name()); e.putLong("day", entry.day());
            list.add(e);
        });
        tag.put("Revealed", list);
        if (lastTimerDay != Long.MIN_VALUE) tag.putLong("LastTimerDay", lastTimerDay);
        if (discoveryDay != Long.MIN_VALUE) tag.putLong("DiscoveryDay", discoveryDay);
        tag.putInt("DiscoveredToday", discoveredToday);
        return tag;
    }

    public Set<UniverseField.Cell> revealedCells() { return Collections.unmodifiableSet(revealed.keySet()); }
    public Map<UniverseField.Cell, Entry> entries() { return Collections.unmodifiableMap(revealed); }
    public boolean isRevealed(UniverseField.Cell cell) { return revealed.containsKey(cell); }
    public int size() { return revealed.size(); }

    boolean reveal(UniverseField.Cell cell, Source source, long day, long fieldSeed) {
        if (revealed.putIfAbsent(cell, new Entry(source, day)) != null) return false;
        revealOrder.add(cell);
        setDirty();
        if (frontierStarted && frontierSeed == fieldSeed) addFrontierFrom(cell, fieldSeed);
        else if (frontierStarted) {
            timerFrontier.clear();
            frontierCursor = 0;
            frontierStarted = false;
            frontierReady = false;
        }
        return true;
    }

    /** Advances legacy-frontier reconstruction by at most {@code cellBudget} revealed cells. */
    boolean advanceTimerFrontier(long fieldSeed, int cellBudget) {
        if (cellBudget <= 0) throw new IllegalArgumentException("frontier cell budget must be positive");
        if (!frontierStarted || frontierSeed != fieldSeed) {
            timerFrontier.clear();
            frontierCursor = 0;
            frontierSeed = fieldSeed;
            frontierStarted = true;
            frontierReady = false;
        }
        if (frontierReady) return true;
        int end = (int) Math.min((long) revealOrder.size(), (long) frontierCursor + cellBudget);
        while (frontierCursor < end) addFrontierFrom(revealOrder.get(frontierCursor++), fieldSeed);
        frontierReady = frontierCursor >= revealOrder.size();
        return frontierReady;
    }

    public boolean timerFrontierReady() { return frontierReady; }

    public boolean timerFrontierStarted() { return frontierStarted; }

    public int timerFrontierProcessed() { return frontierReady ? revealOrder.size() : frontierCursor; }

    public int timerFrontierTotal() { return revealOrder.size(); }

    UniverseField.Cell nextTimerCell(long fieldSeed) {
        if (!frontierReady || frontierSeed != fieldSeed) return null;
        UniverseField.Cell best = null;
        if (!timerFrontier.isEmpty()) best = timerFrontier.first();
        return best != null ? best : UniverseGrowth.fallbackTimerCell(fieldSeed, revealed.keySet());
    }

    private void addFrontierFrom(UniverseField.Cell cell, long fieldSeed) {
        timerFrontier.remove(cell);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            long x = (long) cell.x() + dx, z = (long) cell.z() + dz;
            if (!UniverseField.Cell.inRange(x) || !UniverseField.Cell.inRange(z)) continue;
            var candidate = new UniverseField.Cell((int) x, (int) z);
            if (!revealed.containsKey(candidate) && UniverseField.systemAt(fieldSeed, candidate) != null)
                timerFrontier.add(candidate);
        }
    }

    private static long timerKey(long fieldSeed, UniverseField.Cell cell) {
        return UniverseField.mix(fieldSeed ^ ((long) cell.x() << 32 ^ (cell.z() & 0xFFFFFFFFL))
                ^ 0x6A09E667F3BCC909L);
    }

    private int compareFrontier(UniverseField.Cell left, UniverseField.Cell right) {
        int ring = Integer.compare(left.ring(), right.ring());
        if (ring != 0) return ring;
        int key = Long.compare(timerKey(frontierSeed, left), timerKey(frontierSeed, right));
        if (key != 0) return key;
        int x = Integer.compare(left.x(), right.x());
        return x != 0 ? x : Integer.compare(left.z(), right.z());
    }
}
