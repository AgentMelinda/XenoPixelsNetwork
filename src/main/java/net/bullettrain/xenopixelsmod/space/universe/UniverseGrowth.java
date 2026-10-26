package net.bullettrain.xenopixelsmod.space.universe;

import java.util.Set;

/**
 * How the known universe grows, as pure rules. It starts as Sol's cell alone. The TIMER grows it by one system per game
 * day along the frontier (the nearest unrevealed occupied cell next to a revealed one, ties broken by a seeded hash, so
 * every server with the same seed grows the same way). DISCOVERY reveals a system when a player flies into its cell,
 * bounded per game day. Both stop at a total cap. Nothing here touches the world; {@link UniverseService} applies it.
 */
public final class UniverseGrowth {
    private UniverseGrowth() {}

    public enum Mode {
        OFF, TIMER, DISCOVERY, BOTH;

        public boolean timer() { return this == TIMER || this == BOTH; }
        public boolean discovery() { return this == DISCOVERY || this == BOTH; }

        public static Mode parse(String value) {
            if (value == null) return OFF;
            try { return Mode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException error) { return OFF; }
        }
    }

    /**
     * The next cell the timer reveals: an occupied, unrevealed cell adjacent (8-neighbourhood) to a revealed one, nearest
     * ring first, then the lowest seeded hash. Null when the frontier is empty or the universe edge is reached.
     */
    public static UniverseField.Cell nextTimerCell(long worldSeed, Set<UniverseField.Cell> revealed) {
        UniverseField.Cell best = null;
        long bestKey = Long.MAX_VALUE;
        int bestRing = Integer.MAX_VALUE;
        for (UniverseField.Cell cell : revealed)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int x = cell.x() + dx, z = cell.z() + dz;
                    if (!UniverseField.Cell.inRange(x) || !UniverseField.Cell.inRange(z)) continue;
                    var candidate = new UniverseField.Cell(x, z);
                    if (revealed.contains(candidate) || UniverseField.systemAt(worldSeed, candidate) == null) continue;
                    int ring = candidate.ring();
                    long key = UniverseField.mix(worldSeed ^ ((long) x << 32 ^ (z & 0xFFFFFFFFL)) ^ 0x6A09E667F3BCC909L);
                    if (ring < bestRing || (ring == bestRing && key < bestKey)) {
                        best = candidate; bestRing = ring; bestKey = key;
                    }
                }
        if (best != null) return best;
        return fallbackTimerCell(worldSeed, revealed);
    }

    /** Bounded Sol-neighbourhood fallback used only when the active frontier has no occupied systems. */
    static UniverseField.Cell fallbackTimerCell(long worldSeed, Set<UniverseField.Cell> revealed) {
        UniverseField.Cell best = null;
        long bestKey = Long.MAX_VALUE;
        // Fallback (Codex review of CL-108): when no occupied cell touches the known region (e.g. all eight cells around
        // Sol happen to be empty), take the nearest occupied unrevealed cell within FALLBACK_RING rings of Sol, by ring then
        // seeded key, so the timer never stalls. Bounded: at most (2*FALLBACK_RING+1)^2 cells, once per game day.
        for (int ring = 1; ring <= FALLBACK_RING; ring++) {
            for (int x = -ring; x <= ring; x++)
                for (int z = -ring; z <= ring; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != ring) continue;
                    var candidate = new UniverseField.Cell(x, z);
                    if (revealed.contains(candidate) || UniverseField.systemAt(worldSeed, candidate) == null) continue;
                    long key = UniverseField.mix(worldSeed ^ ((long) x << 32 ^ (z & 0xFFFFFFFFL)) ^ 0x6A09E667F3BCC909L);
                    if (key < bestKey) { best = candidate; bestKey = key; }
                }
            if (best != null) return best;
        }
        return null;
    }

    static final int FALLBACK_RING = 16;

    /** Whether a discovery may reveal another system today and overall. */
    public static boolean mayDiscover(int discoveredToday, int perDay, int revealedTotal, int cap) {
        return discoveredToday < perDay && revealedTotal < cap;
    }

    /**
     * Largest accepted star-proximity discovery range. A player is at most half a cell (65,536) from its own cell
     * centre and a star at most 0.30 cell (39,321.6) from its cell centre, so any star two cells away is at least
     * 2 x 131,072 - 39,321.6 - 65,536 = 157,286.4 blocks off: below this bound the 3x3 scan is always complete.
     */
    public static final int MAX_DISCOVERY_RANGE = 157_286;
    /**
     * Default reach: one cell. Measured over seeds 1..2000 from a player at Sol (0, 0): at least one neighbouring
     * star is in range for 75.1% of worlds (1.17 on average). Half a cell reached none.
     */
    public static final int DEFAULT_DISCOVERY_RANGE = UniverseField.CELL_SIZE;

    /**
     * Cells a player at (x, z) discovers this pass, nearest first. The player's own occupied cell always qualifies
     * (the original fly-into-the-cell rule). With {@code range > 0}, an occupied cell in the 3x3 neighbourhood also
     * qualifies when its STAR lies within {@code range} blocks horizontally. Range is clamped to
     * {@link #MAX_DISCOVERY_RANGE}, so no cell outside the 3x3 can be in range. Already revealed cells are skipped.
     * Bounded at 9 cells; pure and deterministic.
     */
    public static java.util.List<UniverseField.Cell> discoveryCandidates(long worldSeed, double x, double z, int range,
                                                                         java.util.function.Predicate<UniverseField.Cell> revealed) {
        UniverseField.Cell own = UniverseField.cellAt(x, z);
        if (own == null) return java.util.List.of();
        int reach = Math.max(0, Math.min(MAX_DISCOVERY_RANGE, range));
        record Hit(UniverseField.Cell cell, double distanceSquared) {}
        var hits = new java.util.ArrayList<Hit>(9);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            long cx = (long) own.x() + dx, cz = (long) own.z() + dz;
            if (!UniverseField.Cell.inRange(cx) || !UniverseField.Cell.inRange(cz)) continue;
            var cell = new UniverseField.Cell((int) cx, (int) cz);
            if (revealed.test(cell)) continue;
            var system = UniverseField.systemAt(worldSeed, cell);
            if (system == null) continue;
            double ddx = system.x() - x, ddz = system.z() - z, d2 = ddx * ddx + ddz * ddz;
            boolean ownCell = dx == 0 && dz == 0;
            if (ownCell || (reach > 0 && d2 <= (double) reach * reach)) hits.add(new Hit(cell, d2));
        }
        hits.sort(java.util.Comparator.comparingDouble(Hit::distanceSquared)
                .thenComparingInt(hit -> hit.cell().x()).thenComparingInt(hit -> hit.cell().z()));
        var out = new java.util.ArrayList<UniverseField.Cell>(hits.size());
        for (var hit : hits) out.add(hit.cell());
        return out;
    }

    /** Game day index for a game time in ticks (24,000 ticks per Minecraft day). */
    public static long day(long gameTime) { return Math.floorDiv(gameTime, 24_000L); }

}
