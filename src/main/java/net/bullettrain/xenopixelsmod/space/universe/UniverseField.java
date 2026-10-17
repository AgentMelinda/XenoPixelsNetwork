package net.bullettrain.xenopixelsmod.space.universe;

import java.util.SplittableRandom;

/**
 * The XenoPixels universe as pure, deterministic math (no Minecraft types). Star systems live in square cells of
 * {@link #CELL_SIZE} blocks on the Voidsea's XZ plane; cell (0, 0) holds Sol, the charted home system, and every other
 * cell holds at most one generated system decided only by the world seed and the cell. Nothing is stored for a system;
 * the server and every client can recompute it. Which systems players know about is {@link UniverseGrowth}'s concern.
 */
public final class UniverseField {
    public static final int CELL_SIZE = 131_072;
    /** Vanilla's maximum world-border edge from the origin. */
    public static final double WORLD_BORDER_LIMIT = 29_999_984.0;
    /** Furthest cell whose deterministic star jitter always remains inside the maximum vanilla world border. */
    public static final int MAX_CELL = 228;
    /** Number of addressable universe cells on one axis, including both signed edges. */
    public static final int GRID_DIAMETER = MAX_CELL * 2 + 1;
    /** Finite cell count inside the current world-border-safe field. */
    public static final int GRID_CELL_COUNT = GRID_DIAMETER * GRID_DIAMETER;
    /** Systems sit on the Voidsea mid-plane (height 0..1024). */
    public static final double PLANE_Y = 512;
    /** Share of non-home cells that hold a system. */
    static final double OCCUPANCY = 0.62;
    /** A system's star stays inside the central part of its cell, so neighbours never crowd a shared edge. */
    static final double JITTER = 0.30;

    private UniverseField() {}

    public record Cell(int x, int z) {
        public Cell {
            // Explicit signed checks: Math.abs(Integer.MIN_VALUE) overflows and would pass (Codex review 2026-10-16).
            if (!inRange(x) || !inRange(z)) throw new IllegalArgumentException("cell outside the universe");
        }

        public boolean home() { return x == 0 && z == 0; }

        /** True when a cell coordinate is inside the universe. */
        public static boolean inRange(long value) { return value >= -MAX_CELL && value <= MAX_CELL; }

        /** Chebyshev ring distance from Sol's cell. */
        public int ring() { return Math.max(Math.abs(x), Math.abs(z)); }

        public String id() { return x + ":" + z; }
    }

    /** Row-major index in the finite grid, used for bounded client-side spatial lookup. */
    public static int index(Cell cell) {
        return index(cell.x(), cell.z());
    }

    public static int index(int x, int z) {
        if (!Cell.inRange(x) || !Cell.inRange(z)) throw new IllegalArgumentException("cell outside the universe");
        return (x + MAX_CELL) * GRID_DIAMETER + z + MAX_CELL;
    }

    /** Star classes, with their relative frequency and a display colour (ARGB) for later rendering. */
    public enum StarClass {
        EMBER_DWARF(46, 0xFFFF7A4D), AMBER_STAR(26, 0xFFFFC46B), PALE_SUN(14, 0xFFFFF4D6), AZURE_GIANT(7, 0xFF8FB8FF),
        CINDER_REMNANT(5, 0xFFD9D9E8), VOID_PULSAR(2, 0xFFB98CFF);

        final int weight;
        public final int colour;

        StarClass(int weight, int colour) { this.weight = weight; this.colour = colour; }
    }

    public record StarSystem(Cell cell, long seed, String name, StarClass starClass, double x, double y, double z, int planetSlots) {}

    /** The system in a cell, or null for an empty cell. Cell (0, 0) is always Sol. */
    public static StarSystem systemAt(long worldSeed, Cell cell) {
        if (cell.home()) return new StarSystem(cell, worldSeed, "Sol", StarClass.PALE_SUN, 0, PLANE_Y, 0, 0);
        long seed = cellSeed(worldSeed, cell);
        var random = new SplittableRandom(seed);
        if (random.nextDouble() >= OCCUPANCY) return null;
        double x = cell.x() * (double) CELL_SIZE + random.nextDouble(-JITTER, JITTER) * CELL_SIZE;
        double z = cell.z() * (double) CELL_SIZE + random.nextDouble(-JITTER, JITTER) * CELL_SIZE;
        StarClass starClass = pick(random);
        int planets = random.nextInt(starClass == StarClass.VOID_PULSAR ? 2 : 9);
        return new StarSystem(cell, seed, name(random), starClass, x, PLANE_Y, z, planets);
    }

    /** Occupancy-only query used by bounded server page jobs; avoids allocating names and star-detail records. */
    static boolean hasSystemAt(long worldSeed, Cell cell) {
        if (cell.home()) return true;
        return new SplittableRandom(cellSeed(worldSeed, cell)).nextDouble() < OCCUPANCY;
    }

    private static long cellSeed(long worldSeed, Cell cell) {
        return mix(worldSeed ^ mix(cell.x() * 0x9E3779B97F4A7C15L)
                ^ mix(cell.z() * 0xC2B2AE3D27D4EB4FL) ^ 0x5EED_0F_5A5AL);
    }

    /** The cell containing a Voidsea position inside the vanilla border; edge coordinates clamp to the last safe cell. */
    public static Cell cellAt(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)
                || x < -WORLD_BORDER_LIMIT || x > WORLD_BORDER_LIMIT
                || z < -WORLD_BORDER_LIMIT || z > WORLD_BORDER_LIMIT) return null;
        // Range-check in double before narrowing; border-edge coordinates clamp to the last safe cell.
        double fx = Math.floor(x / CELL_SIZE + 0.5), fz = Math.floor(z / CELL_SIZE + 0.5);
        int cellX = (int) Math.max(-MAX_CELL, Math.min(MAX_CELL, fx));
        int cellZ = (int) Math.max(-MAX_CELL, Math.min(MAX_CELL, fz));
        return new Cell(cellX, cellZ);
    }

    static StarClass pick(SplittableRandom random) {
        int total = 0;
        for (StarClass c : StarClass.values()) total += c.weight;
        int roll = random.nextInt(total);
        for (StarClass c : StarClass.values()) {
            roll -= c.weight;
            if (roll < 0) return c;
        }
        return StarClass.EMBER_DWARF;
    }

    // XenoPixels' own name syllables (not taken from any other mod).
    private static final String[] START = {"Ka", "Zor", "Vel", "Ny", "Thra", "Oru", "Sei", "Mak", "Ilo", "Quen", "Bra", "Ezu", "Tal", "Rhy"};
    private static final String[] MIDDLE = {"ri", "va", "lon", "mi", "zeth", "or", "an", "ku", "the", "si", "ra", "do"};
    private static final String[] END = {"x", "n", "ra", "dis", "mor", "ion", "a", "eth", "us", "ka", "or", "il"};

    static String name(SplittableRandom random) {
        String base = START[random.nextInt(START.length)] + (random.nextInt(3) == 0 ? "" : MIDDLE[random.nextInt(MIDDLE.length)])
                + END[random.nextInt(END.length)];
        return random.nextInt(4) == 0 ? base + " " + (char) ('A' + random.nextInt(6)) : base;
    }

    static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
