package net.bullettrain.xenopixelsmod.space;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spatial index for things that need to find each other in the Voidsea: dimension → chunk → ids.
 *
 * <p>§21 of the house performance guidelines is explicit that a system which repeatedly queries nearby
 * custom objects should maintain an index rather than scan the world, and the space design needs
 * exactly that several times over — radar and proximity sensors, black-hole tidal band membership
 * (§15), "which pods are near this ship" for drag and collision (§13's Q21), and finding the handoff
 * anchor a departing ship should be climbing toward.
 *
 * <p>The alternative it replaces is {@code level.getEntitiesOfClass(...)} over an inflated bounding
 * box, which is an entity-section walk per query. In the Voidsea that box can be tens of thousands of
 * blocks across, and the guideline's rule about not scanning the world every tick applies with full
 * force: at 200 active pods, a per-tick area query per pod is the difference between a budget met and
 * a server that cannot hold 20 TPS.
 *
 * <p><b>Keyed by primitives, not Minecraft types.</b> The dimension is a namespaced {@code String}, the
 * same representation {@code OrbitalGravityConfig} and {@code CelestialBody} use, and the cell is a
 * packed {@code long} rather than a {@code ChunkPos}. That keeps this class testable without booting
 * the game, avoids allocating a {@code ChunkPos} per query on the hot path, and means a space mod
 * reorganising its dimension classes cannot break it.
 *
 * <p>Not thread-safe. The server owns one instance and mutates it on the server thread; a client that
 * needs one builds its own from synced data. That matches every other index in this mod and avoids
 * locking on a path that runs per tick.
 */
public final class SpaceSpatialIndex {

    /**
     * Cell edge in blocks. 16 matches a Minecraft chunk, which is the natural unit: a body's cell is
     * then the chunk it is in, and a radius query walks the same cells a chunk loader would.
     */
    public static final int DEFAULT_CELL_SIZE = 16;

    private final int cellSize;
    /** dimension id → (packed cell key → member ids). */
    private final Map<String, Map<Long, Set<UUID>>> byDimension = new HashMap<>();
    /** Reverse lookup so {@link #remove} does not have to scan every cell to find an id. */
    private final Map<UUID, Entry> locations = new HashMap<>();

    private final List<UUID> queryScratch = new ArrayList<>();

    public SpaceSpatialIndex() {
        this(DEFAULT_CELL_SIZE);
    }

    public SpaceSpatialIndex(int cellSize) {
        // A power of two would let the division become a shift; 16 already is one. Anything below 1
        // would make every position its own cell and defeat the point.
        this.cellSize = Math.max(1, cellSize);
    }

    /**
     * Where one id is. Mutable and allocated once per id rather than per move, so the per-tick update
     * path for a ship in flight writes into an existing object instead of producing garbage — which
     * matters because this is exactly the "hundreds of pods, every tick" case §13's Q21 is about.
     */
    private static final class Entry {
        String dimension;
        long cell;
        double x;
        double z;
    }

    /** Packs cell coordinates into one long, the same trick {@code ChunkPos#asLong} uses. */
    private static long cellKey(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xFFFFFFFFL);
    }

    /** Unpacks the X half of a cell key. */
    public static int cellX(long key) {
        return (int) (key >> 32);
    }

    /** Unpacks the Z half of a cell key. */
    public static int cellZ(long key) {
        return (int) key;
    }

    /** The cell a world position falls in, along the given axis. */
    private int cellOf(double coordinate) {
        // Floor division, not truncation: a position at -1 must land in cell -1, not cell 0, or the
        // cells either side of the origin would be double width and queries near zero would miss.
        return (int) Math.floor(coordinate / cellSize);
    }

    /**
     * Inserts or moves an id. Moving is the common case — this is called every tick for a ship or pod
     * in flight — so the entry is updated in place when it stays in its cell, which is the overwhelmingly
     * common outcome at a 16-block cell size and costs one map lookup and two double writes.
     */
    public void put(UUID id, String dimensionId, double x, double z) {
        if (id == null || dimensionId == null) return;
        long cell = cellKey(cellOf(x), cellOf(z));
        Entry existing = locations.get(id);
        if (existing != null && existing.cell == cell && dimensionId.equals(existing.dimension)) {
            existing.x = x;
            existing.z = z;
            return;
        }
        if (existing != null) remove(id);
        byDimension.computeIfAbsent(dimensionId, ignored -> new HashMap<>())
                .computeIfAbsent(cell, ignored -> new HashSet<>())
                .add(id);
        Entry entry = new Entry();
        entry.dimension = dimensionId;
        entry.cell = cell;
        entry.x = x;
        entry.z = z;
        locations.put(id, entry);
    }

    /**
     * Removes an id. Safe to call for one that was never indexed, which is what makes the unload and
     * death paths simple — they can call this unconditionally.
     */
    public void remove(UUID id) {
        if (id == null) return;
        Entry entry = locations.remove(id);
        if (entry == null) return;
        Map<Long, Set<UUID>> cells = byDimension.get(entry.dimension);
        if (cells == null) return;
        Set<UUID> members = cells.get(entry.cell);
        if (members == null) return;
        members.remove(id);
        // Prune empty cells and empty dimensions, or a server that has run for days accumulates cells
        // for every position anything ever occupied.
        if (members.isEmpty()) cells.remove(entry.cell);
        if (cells.isEmpty()) byDimension.remove(entry.dimension);
    }

    /** Drops everything in one dimension, for a dimension unload. */
    public void clearDimension(String dimensionId) {
        if (dimensionId == null) return;
        Map<Long, Set<UUID>> cells = byDimension.remove(dimensionId);
        if (cells == null) return;
        for (Set<UUID> members : cells.values()) {
            for (UUID id : members) locations.remove(id);
        }
    }

    /** Drops everything, for a server stop. */
    public void clear() {
        byDimension.clear();
        locations.clear();
    }

    /**
     * Collects every id within {@code radius} blocks of a point, in one dimension.
     *
     * <p>Two-stage: the cell walk is a coarse reject that touches only the cells the radius could
     * possibly reach, then each surviving candidate is distance-checked exactly against the position
     * stored in its entry. Cost scales with what is actually nearby, not with the size of the index —
     * which is the whole reason this exists instead of an area query over the level's entity sections.
     *
     * @param out   caller-owned list, cleared and filled; reused across calls so a per-tick query
     *              allocates nothing
     * @param limit maximum entries to collect, or a value &lt;= 0 for no cap. A cap matters because a
     *              caller asking for a huge radius in a crowded dimension would otherwise build an
     *              unbounded list on a tick.
     * @return how many ids were collected
     */
    public int query(String dimensionId, double x, double z, double radius, List<UUID> out, int limit) {
        out.clear();
        if (dimensionId == null || !(radius >= 0.0) || !Double.isFinite(radius)) return 0;
        Map<Long, Set<UUID>> cells = byDimension.get(dimensionId);
        if (cells == null || cells.isEmpty()) return 0;

        double radiusSq = radius * radius;
        int minCellX = cellOf(x - radius);
        int maxCellX = cellOf(x + radius);
        int minCellZ = cellOf(z - radius);
        int maxCellZ = cellOf(z + radius);

        for (int cx = minCellX; cx <= maxCellX; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                Set<UUID> members = cells.get(cellKey(cx, cz));
                if (members == null) continue;
                for (UUID id : members) {
                    Entry entry = locations.get(id);
                    if (entry == null) continue;
                    double dx = entry.x - x;
                    double dz = entry.z - z;
                    // Cells are square and the radius is round, so a cell the circle only clips still
                    // holds members outside it. This is the exact test the coarse pass cannot make.
                    if (dx * dx + dz * dz > radiusSq) continue;
                    if (limit > 0 && out.size() >= limit) return out.size();
                    out.add(id);
                }
            }
        }
        return out.size();
    }

    /** Convenience overload using the internal scratch list. Not safe to nest or retain. */
    public List<UUID> query(String dimensionId, double x, double z, double radius) {
        query(dimensionId, x, z, radius, queryScratch, 0);
        return queryScratch;
    }

    /** Number of indexed ids in one dimension. */
    public int countIn(String dimensionId) {
        Map<Long, Set<UUID>> cells = dimensionId == null ? null : byDimension.get(dimensionId);
        if (cells == null) return 0;
        int total = 0;
        for (Set<UUID> members : cells.values()) total += members.size();
        return total;
    }

    /** Number of populated cells in one dimension — the number that shows an index leaking cells. */
    public int cellCountIn(String dimensionId) {
        Map<Long, Set<UUID>> cells = dimensionId == null ? null : byDimension.get(dimensionId);
        return cells == null ? 0 : cells.size();
    }

    public int totalIndexed() {
        return locations.size();
    }

    /** Total populated cells across every dimension, for {@code /xenospace perf}. */
    public int totalCells() {
        int total = 0;
        for (Map<Long, Set<UUID>> cells : byDimension.values()) total += cells.size();
        return total;
    }

    public boolean contains(UUID id) {
        return id != null && locations.containsKey(id);
    }

    /** The cell an id currently occupies, or null when it is not indexed. For diagnostics. */
    public Long cellOf(UUID id) {
        Entry entry = id == null ? null : locations.get(id);
        return entry == null ? null : entry.cell;
    }

    public int cellSize() {
        return cellSize;
    }
}
