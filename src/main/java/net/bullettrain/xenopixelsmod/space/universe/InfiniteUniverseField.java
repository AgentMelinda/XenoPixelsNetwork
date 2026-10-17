package net.bullettrain.xenopixelsmod.space.universe;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Predicate;

/**
 * Deterministic logical star systems addressed independently of Minecraft's finite physical world border.
 *
 * <p>This class only generates logical identities and cell-local positions. It does not map distant systems into
 * Voidsea coordinates, persist discoveries, create dimensions, or materialize bodies. Those operations need their
 * own bounded page, slot, storage, and activation policies.
 */
public final class InfiniteUniverseField {
    /** Hard cap for a single logical-space query; future page packets must use the same or a smaller bound. */
    public static final int MAX_PAGE_CELLS = 4_096;
    private static final long X_SALT = 0x9E3779B97F4A7C15L;
    private static final long Z_SALT = 0xC2B2AE3D27D4EB4FL;
    private static final long FIELD_SALT = 0x5EED_0F_5A5AL;

    private InfiniteUniverseField() {}

    /** A full-width logical cell address. Its string form preserves both coordinates without hash collisions. */
    public record Cell(long x, long z) {
        public boolean home() { return x == 0L && z == 0L; }
        public String id() { return x + ":" + z; }
    }

    /**
     * A deterministic system in logical space. {@code localX/localZ} are offsets inside the cell; they are not
     * Voidsea world coordinates and must not be used directly as physical entity positions.
     */
    public record StarSystem(Cell cell, long seed, String name, UniverseField.StarClass starClass,
                             double localX, double y, double localZ, int planetSlots) {}

    /** A bounded query result. Empty cells count against the request's work cap even though they are not returned. */
    public record Page(long originX, long originZ, int width, int height, List<StarSystem> systems) {
        public Page {
            if (width <= 0 || height <= 0 || (long) width * height > MAX_PAGE_CELLS)
                throw new IllegalArgumentException("logical universe page must contain 1.." + MAX_PAGE_CELLS + " cells");
            long endX;
            long endZ;
            try {
                endX = Math.addExact(originX, width - 1L);
                endZ = Math.addExact(originZ, height - 1L);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("logical universe page exceeds signed-long coordinates", overflow);
            }
            systems = List.copyOf(systems);
            if (systems.size() > (long) width * height)
                throw new IllegalArgumentException("logical universe page has more systems than queried cells");
            for (StarSystem system : systems) {
                if (system.cell().x() < originX || system.cell().x() > endX
                        || system.cell().z() < originZ || system.cell().z() > endZ)
                    throw new IllegalArgumentException("system lies outside its logical universe page");
            }
        }
        public int queriedCells() { return width * height; }
    }

    /** Incremental page generation. Call step from a server tick with a fixed cell allowance. */
    public static final class PageJob {
        private final long worldSeed;
        private final long originX, originZ;
        private final int width, height, totalCells;
        private final ArrayList<StarSystem> systems;
        private final byte[] occupied;
        private final byte[] revealed;
        private final Predicate<Cell> isRevealed;
        private int cursor;

        private PageJob(long worldSeed, long originX, long originZ, int width, int height, boolean collectSystems,
                        Predicate<Cell> isRevealed) {
            validatePage(originX, originZ, width, height);
            this.worldSeed = worldSeed;
            this.originX = originX;
            this.originZ = originZ;
            this.width = width;
            this.height = height;
            this.totalCells = width * height;
            this.systems = collectSystems ? new ArrayList<>(Math.min(totalCells, 256)) : null;
            this.occupied = new byte[(totalCells + 7) >>> 3];
            this.revealed = new byte[occupied.length];
            this.isRevealed = isRevealed;
        }

        /** Returns the number of cells evaluated, never greater than cellBudget. */
        public int step(int cellBudget) {
            if (cellBudget <= 0) throw new IllegalArgumentException("page job budget must be positive");
            int end = cursor + Math.min(cellBudget, totalCells - cursor);
            int begin = cursor;
            while (cursor < end) {
                int dx = cursor / height;
                int dz = cursor % height;
                Cell cell = new Cell(originX + dx, originZ + dz);
                boolean hasSystem;
                if (systems == null) hasSystem = isSystemPresent(worldSeed, cell);
                else {
                    StarSystem system = systemAt(worldSeed, cell);
                    hasSystem = system != null;
                    if (hasSystem) systems.add(system);
                }
                if (hasSystem) {
                    occupied[cursor >>> 3] |= (byte) (1 << (cursor & 7));
                }
                if (hasSystem && isRevealed != null && isRevealed.test(cell))
                    revealed[cursor >>> 3] |= (byte) (1 << (cursor & 7));
                cursor++;
            }
            return cursor - begin;
        }

        public boolean complete() { return cursor == totalCells; }
        public int processedCells() { return cursor; }
        public int totalCells() { return totalCells; }
        public long originX() { return originX; }
        public long originZ() { return originZ; }
        public int width() { return width; }
        public int height() { return height; }

        public byte[] occupancyBits() {
            if (!complete()) throw new IllegalStateException("logical universe page is still being generated");
            return occupied.clone();
        }

        /** Persisted discovery bits accumulated during the same bounded cell slices as occupancy. */
        public byte[] revealedBits() {
            if (!complete()) throw new IllegalStateException("logical universe page is still being generated");
            return revealed.clone();
        }

        /**
         * Applies an authoritative discovery that happened while this bounded page was being built.
         * The caller only invokes this for a saved reveal, and the deterministic occupancy check
         * protects the packet snapshot from a stale or malformed event.
         */
        public boolean markRevealed(long x, long z) {
            if (x < originX || z < originZ || x > originX + width - 1L || z > originZ + height - 1L
                    || !isSystemPresent(worldSeed, new Cell(x, z))) return false;
            int index = (int) (x - originX) * height + (int) (z - originZ);
            int mask = 1 << (index & 7);
            occupied[index >>> 3] |= (byte) mask;
            revealed[index >>> 3] |= (byte) mask;
            return true;
        }

        public Page result() {
            if (!complete()) throw new IllegalStateException("logical universe page is still being generated");
            if (systems == null) throw new IllegalStateException("occupancy-only page job does not retain system details");
            return new Page(originX, originZ, width, height, systems);
        }
    }

    /** The system in a logical cell, or {@code null} when its deterministic occupancy roll is empty. */
    public static StarSystem systemAt(long worldSeed, Cell cell) {
        if (cell == null) throw new IllegalArgumentException("logical cell is required");

        // Keep every existing world-border-safe system byte-for-byte stable while the logical address space grows.
        if (UniverseField.Cell.inRange(cell.x()) && UniverseField.Cell.inRange(cell.z())) {
            var physicalCell = new UniverseField.Cell((int) cell.x(), (int) cell.z());
            var existing = UniverseField.systemAt(worldSeed, physicalCell);
            if (existing == null) return null;
            return new StarSystem(cell, existing.seed(), existing.name(), existing.starClass(),
                    existing.x() - cell.x() * (double) UniverseField.CELL_SIZE, existing.y(),
                    existing.z() - cell.z() * (double) UniverseField.CELL_SIZE, existing.planetSlots());
        }

        long seed = UniverseField.mix(worldSeed
                ^ UniverseField.mix(cell.x() * X_SALT)
                ^ UniverseField.mix(cell.z() * Z_SALT)
                ^ FIELD_SALT);
        var random = new SplittableRandom(seed);
        if (random.nextDouble() >= UniverseField.OCCUPANCY) return null;
        double localX = random.nextDouble(-UniverseField.JITTER, UniverseField.JITTER) * UniverseField.CELL_SIZE;
        double localZ = random.nextDouble(-UniverseField.JITTER, UniverseField.JITTER) * UniverseField.CELL_SIZE;
        var starClass = UniverseField.pick(random);
        int planets = random.nextInt(starClass == UniverseField.StarClass.VOID_PULSAR ? 2 : 9);
        return new StarSystem(cell, seed, UniverseField.name(random), starClass,
                localX, UniverseField.PLANE_Y, localZ, planets);
    }

    /** Occupancy-only form for server page sync; avoids building unneeded star names/details. */
    static boolean isSystemPresent(long worldSeed, Cell cell) {
        if (cell == null) throw new IllegalArgumentException("logical cell is required");
        if (UniverseField.Cell.inRange(cell.x()) && UniverseField.Cell.inRange(cell.z()))
            return UniverseField.hasSystemAt(worldSeed, new UniverseField.Cell((int) cell.x(), (int) cell.z()));
        long seed = UniverseField.mix(worldSeed
                ^ UniverseField.mix(cell.x() * X_SALT)
                ^ UniverseField.mix(cell.z() * Z_SALT)
                ^ FIELD_SALT);
        return new SplittableRandom(seed).nextDouble() < UniverseField.OCCUPANCY;
    }

    /**
     * Generates one bounded rectangular window of logical space. The cap applies to every visited cell, including
     * empty cells, so a sparse region cannot turn a request into an unbounded search. Coordinates that would wrap
     * past the signed-long address range are rejected.
     */
    public static Page page(long worldSeed, long originX, long originZ, int width, int height) {
        PageJob job = pageJob(worldSeed, originX, originZ, width, height);
        job.step(MAX_PAGE_CELLS);
        return job.result();
    }

    public static PageJob pageJob(long worldSeed, long originX, long originZ, int width, int height) {
        return new PageJob(worldSeed, originX, originZ, width, height, true, null);
    }

    /** Page job for occupancy synchronization; avoids retaining system records on the server. */
    public static PageJob occupancyPageJob(long worldSeed, long originX, long originZ, int width, int height) {
        return occupancyPageJob(worldSeed, originX, originZ, width, height, null);
    }

    /** Occupancy and persisted discovery share one bounded pass without retaining any star details. */
    public static PageJob occupancyPageJob(long worldSeed, long originX, long originZ, int width, int height,
                                           Predicate<Cell> isRevealed) {
        return new PageJob(worldSeed, originX, originZ, width, height, false, isRevealed);
    }

    private static void validatePage(long originX, long originZ, int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PAGE_CELLS)
            throw new IllegalArgumentException("logical universe page must contain 1.." + MAX_PAGE_CELLS + " cells");
        try {
            Math.addExact(originX, width - 1L);
            Math.addExact(originZ, height - 1L);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("logical universe page exceeds signed-long coordinates", overflow);
        }
    }
}
