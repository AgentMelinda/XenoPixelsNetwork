package net.bullettrain.xenopixelsmod.space.asteroid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Pure deterministic candidate generation for the S11 asteroid field. This class does not load chunks,
 * create entities, or touch Sable; it is safe to use in tests and as the common server/client field rule.
 */
public final class AsteroidField {
    public static final int CELL_SIZE = 384;
    public static final int CANDIDATES_PER_CELL = 2;
    public static final int MIN_RADIUS = 2;
    public static final int MAX_RADIUS = 16;
    private static final long SEED_X = 0x9E3779B97F4A7C15L;
    private static final long SEED_Y = 0xC2B2AE3D27D4EB4FL;
    private static final long SEED_Z = 0x165667B19E3779F9L;

    private AsteroidField() {}

    /** Integer cell coordinates in the 3D Voidsea field. */
    public record Cell(long x, long y, long z) {}

    /** Stable persistent/network identity; it contains no process-local UUID. */
    public record ObjectId(long cellX, long cellY, long cellZ, int slot) implements Comparable<ObjectId> {
        public ObjectId {
            if (slot < 0 || slot >= CANDIDATES_PER_CELL) throw new IllegalArgumentException("invalid candidate slot");
        }

        @Override public int compareTo(ObjectId other) {
            int order = Long.compare(cellX, other.cellX);
            if (order == 0) order = Long.compare(cellY, other.cellY);
            if (order == 0) order = Long.compare(cellZ, other.cellZ);
            if (order == 0) order = Integer.compare(slot, other.slot);
            return order;
        }

        @Override public String toString() { return cellX + "," + cellY + "," + cellZ + "," + slot; }
    }

    /** A circular belt in the XZ plane around a chart position, with a bounded vertical half-thickness. */
    public record Belt(String id, double centerX, double centerY, double centerZ,
                       double innerRadius, double outerRadius, double halfThickness, boolean icy) {
        public Belt {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("belt id is required");
            if (!Double.isFinite(centerX) || !Double.isFinite(centerY) || !Double.isFinite(centerZ)
                    || !Double.isFinite(innerRadius) || !Double.isFinite(outerRadius)
                    || !Double.isFinite(halfThickness) || innerRadius < 0 || outerRadius <= innerRadius
                    || halfThickness <= 0) throw new IllegalArgumentException("invalid belt bounds");
        }

        public boolean contains(double x, double y, double z) {
            double dx = x - centerX, dy = y - centerY, dz = z - centerZ;
            double radial = Math.hypot(dx, dz);
            return Double.isFinite(radial) && radial >= innerRadius && radial <= outerRadius
                    && Math.abs(dy) <= halfThickness;
        }
    }

    public record Candidate(ObjectId id, long seed, double x, double y, double z, int radius, boolean icy) {
        public Candidate {
            if (id == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || radius < MIN_RADIUS || radius > MAX_RADIUS) throw new IllegalArgumentException("invalid asteroid candidate");
        }
    }

    /**
     * Generates the accepted candidates owned by one cell. Neighbor cells participate only in overlap
     * arbitration, so asking for cells in any order yields the same field and does not duplicate objects.
     */
    public static List<Candidate> inCell(long worldSeed, Cell cell, Belt belt) {
        if (cell == null || belt == null) throw new IllegalArgumentException("cell and belt are required");
        var nearby = new ArrayList<Candidate>(27 * CANDIDATES_PER_CELL);
        for (long cy = cell.y - 1; cy <= cell.y + 1; cy++)
            for (long cz = cell.z - 1; cz <= cell.z + 1; cz++)
                for (long cx = cell.x - 1; cx <= cell.x + 1; cx++)
                    for (int slot = 0; slot < CANDIDATES_PER_CELL; slot++) {
                        Candidate candidate = candidate(worldSeed, new Cell(cx, cy, cz), slot, belt.icy());
                        if (belt.contains(candidate.x(), candidate.y(), candidate.z())) nearby.add(candidate);
                    }

        var accepted = new ArrayList<Candidate>(CANDIDATES_PER_CELL);
        for (Candidate candidate : nearby) {
            if (candidate.id().cellX() != cell.x || candidate.id().cellY() != cell.y
                    || candidate.id().cellZ() != cell.z) continue;
            if (winsLocalArbitration(candidate, nearby)) accepted.add(candidate);
        }
        return List.copyOf(accepted);
    }

    static boolean overlaps(Candidate a, Candidate b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        double reach = (double) a.radius() + b.radius();
        return dx * dx + dy * dy + dz * dz < reach * reach;
    }

    /** Stable, order-independent conflict resolution. Rejected candidates can still block a higher id. */
    static List<Candidate> arbitrate(List<Candidate> candidates) {
        var ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparing(Candidate::id));
        var accepted = new ArrayList<Candidate>(ordered.size());
        for (Candidate candidate : ordered) {
            if (winsLocalArbitration(candidate, ordered)) accepted.add(candidate);
        }
        return List.copyOf(accepted);
    }

    private static boolean winsLocalArbitration(Candidate candidate, List<Candidate> nearby) {
        for (Candidate possibleWinner : nearby)
            if (possibleWinner.id().compareTo(candidate.id()) < 0 && overlaps(candidate, possibleWinner)) return false;
        // A stable id owns an overlap cluster; priority does not depend on generation or query order.
        return true;
    }

    private static Candidate candidate(long worldSeed, Cell cell, int slot, boolean icy) {
        long seed = mix64(worldSeed ^ mix64(cell.x * SEED_X) ^ mix64(cell.y * SEED_Y)
                ^ mix64(cell.z * SEED_Z) ^ mix64(slot + 0xD1B54A32D192ED03L));
        var random = new SplittableRandom(seed);
        double x = cell.x * (double) CELL_SIZE + random.nextDouble(CELL_SIZE);
        double y = cell.y * (double) CELL_SIZE + random.nextDouble(CELL_SIZE);
        double z = cell.z * (double) CELL_SIZE + random.nextDouble(CELL_SIZE);
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        return new Candidate(new ObjectId(cell.x, cell.y, cell.z, slot), seed, x, y, z, radius, icy);
    }

    /** SplitMix64 finalizer; also used by {@link AsteroidFieldSnapshot#fieldSeed} so there is one mixer. */
    static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
