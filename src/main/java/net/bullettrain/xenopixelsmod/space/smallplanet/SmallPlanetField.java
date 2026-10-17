package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidField;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.Set;

/**
 * Pure, bounded small-planet placement for S11 C1. It creates no chunks or physics bodies; callers only query the
 * handful of deterministic candidates belonging to a star system and physicalize them under their own budgets.
 */
public final class SmallPlanetField {
    public static final int SECTOR_SIZE = 4_096;
    public static final int MAX_PLANETS_PER_SYSTEM = 4;
    public static final int MIN_RADIUS = 10;
    public static final int MAX_RADIUS = 48;
    public static final int WALKABLE_RADIUS_LIMIT = 24;
    public static final int MAX_SECTOR_OFFSET = 4;
    public static final double SYSTEM_Y_JITTER = 256.0;
    public static final double ORBITAL_CLEARANCE = 512.0;
    public static final double VOIDSEA_HEIGHT = 1_024.0;
    /** Nearby physicalization only needs short ranges; this keeps the fixed 3x3 system query complete and bounded. */
    public static final double MAX_NEARBY_RANGE = 8_192.0;

    private static final long SECTOR_SALT = 0x6A09E667F3BCC909L;
    private static final long PLANET_SALT = 0xBB67AE8584CAA73BL;
    private static final int SECTORS_PER_AXIS = MAX_SECTOR_OFFSET * 2 + 1;
    private static final int SECTOR_COUNT = SECTORS_PER_AXIS * SECTORS_PER_AXIS;

    private SmallPlanetField() {}

    public enum SurfaceMode { WALKABLE, ENTERABLE }

    /** Palette and ore range for generated bodies; rare profiles remain lifeless unless a later feature says otherwise. */
    public enum Profile {
        ROCK(48, 0xFF756D65, 0xFF514C48, 0xFFB0A18F, 0.20, 0.62, false),
        ICE(25, 0xFFB4D4E4, 0xFF607C91, 0xFFE7F2F2, 0.04, 0.38, false),
        DESERT(17, 0xFFC69B65, 0xFF805B3D, 0xFFE0C48C, 0.10, 0.52, false),
        JUNGLE(7, 0xFF436B51, 0xFF263F36, 0xFF9DBA62, 0.02, 0.28, true),
        LAVA(3, 0xFF57484A, 0xFF251F27, 0xFFE16B36, 0.30, 0.85, true),
        /** Reserved for the charted Moon cell; zero weight keeps procedural small-planet profiles unchanged. */
        LUNAR(0, 0xFF85827C, 0xFF494844, 0xFFB1ADA4, 0.0, 0.12, false);

        public final int weight;
        public final int surfaceArgb;
        public final int shadowArgb;
        public final int accentArgb;
        public final double minOreRichness;
        public final double maxOreRichness;
        public final boolean rare;

        Profile(int weight, int surfaceArgb, int shadowArgb, int accentArgb,
                double minOreRichness, double maxOreRichness, boolean rare) {
            this.weight = weight;
            this.surfaceArgb = surfaceArgb;
            this.shadowArgb = shadowArgb;
            this.accentArgb = accentArgb;
            this.minOreRichness = minOreRichness;
            this.maxOreRichness = maxOreRichness;
            this.rare = rare;
        }
    }

    public record ObjectId(UniverseField.Cell systemCell, int slot, int sectorX, int sectorZ)
            implements Comparable<ObjectId> {
        public ObjectId {
            Objects.requireNonNull(systemCell, "systemCell");
            if (slot < 0 || slot >= MAX_PLANETS_PER_SYSTEM
                    || sectorX < -MAX_SECTOR_OFFSET || sectorX > MAX_SECTOR_OFFSET
                    || sectorZ < -MAX_SECTOR_OFFSET || sectorZ > MAX_SECTOR_OFFSET)
                throw new IllegalArgumentException("small planet sector is outside its system region");
        }

        @Override public int compareTo(ObjectId other) {
            int order = Integer.compare(systemCell.x(), other.systemCell.x());
            if (order == 0) order = Integer.compare(systemCell.z(), other.systemCell.z());
            if (order == 0) order = Integer.compare(slot, other.slot);
            return order;
        }

        /** Stable renderer/storage label; sector offsets are deterministic from the cell and slot seed. */
        public String bodyId() {
            return "xenopixelsmod:smallplanet/" + systemCell.x() + "_" + systemCell.z() + "_" + slot;
        }
    }

    /** A charted body represented only by the exclusion sphere needed during procedural placement. */
    public record Sphere(double x, double y, double z, double radius) {
        public Sphere {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Double.isFinite(radius) || radius <= 0)
                throw new IllegalArgumentException("charted-body exclusion sphere must be finite and positive");
        }
    }

    public record Candidate(ObjectId id, long seed, double x, double y, double z, int radius,
                            Profile profile, double oreRichness, boolean nativeLife) {
        public Candidate {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(profile, "profile");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || radius < MIN_RADIUS || radius > MAX_RADIUS
                    || !Double.isFinite(oreRichness) || oreRichness < 0 || oreRichness > 1
                    || nativeLife)
                throw new IllegalArgumentException("invalid small-planet candidate");
        }

        public SurfaceMode surfaceMode() {
            return radius <= WALKABLE_RADIUS_LIMIT ? SurfaceMode.WALKABLE : SurfaceMode.ENTERABLE;
        }
    }

    /**
     * Returns at most four deterministic bodies for a generated system. Non-home systems use their existing
     * {@code planetSlots} value; Sol has a procedural slot budget too, with charted-body and belt exclusions keeping
     * generated bodies clear of its authored planets. The caller's requested cap applies to both cases. Exclusions and
     * local overlap arbitration can reduce the result, never increase it.
     */
    public static List<Candidate> generateSystem(long worldSeed, UniverseField.StarSystem system, int requested,
                                                  List<Sphere> chartedBodies,
                                                  List<AsteroidField.Belt> asteroidBelts) {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(chartedBodies, "chartedBodies");
        Objects.requireNonNull(asteroidBelts, "asteroidBelts");
        int systemSlots = system.cell().home() ? MAX_PLANETS_PER_SYSTEM : Math.max(0, system.planetSlots());
        int count = Math.min(systemSlots, Math.max(0, Math.min(requested, MAX_PLANETS_PER_SYSTEM)));
        if (count == 0) return List.of();

        List<SlotSector> sectors = chooseSectors(worldSeed, system, count);
        var candidates = new ArrayList<Candidate>(sectors.size());
        for (SlotSector sector : sectors) candidates.add(candidate(worldSeed, system, sector));
        candidates.sort(Comparator.comparing(Candidate::id));

        var accepted = new ArrayList<Candidate>(count);
        for (Candidate candidate : candidates) {
            if (overlapsCharted(candidate, chartedBodies) || intersectsBelt(candidate, asteroidBelts)) continue;
            boolean overlapsAccepted = false;
            for (Candidate prior : accepted) if (overlaps(candidate, prior)) {
                overlapsAccepted = true;
                break;
            }
            if (!overlapsAccepted) accepted.add(candidate);
        }
        return List.copyOf(accepted);
    }

    /**
     * Returns candidates close enough to one Voidsea position for proximity physicalization. It examines only the
     * containing system cell and its eight neighbours (at most 9 systems × 4 slots), independent of universe size.
     * The candidate placement bounds are within 57,188 blocks of a cell centre including body radius; with the
     * 8,192-block maximum query range, a cell two indices away cannot intersect this query.
     */
    public static List<Candidate> nearby(long worldSeed, double x, double y, double z, double range, int requested,
                                         List<Sphere> chartedBodies,
                                         List<AsteroidField.Belt> asteroidBelts) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(range) || range < 0 || range > MAX_NEARBY_RANGE)
            throw new IllegalArgumentException("small-planet nearby query is outside its finite range");
        UniverseField.Cell centre = UniverseField.cellAt(x, z);
        if (centre == null) return List.of();
        var nearby = new ArrayList<Candidate>(9 * MAX_PLANETS_PER_SYSTEM);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            long cellX = (long) centre.x() + dx, cellZ = (long) centre.z() + dz;
            if (!UniverseField.Cell.inRange(cellX) || !UniverseField.Cell.inRange(cellZ)) continue;
            UniverseField.StarSystem system = UniverseField.systemAt(worldSeed,
                    new UniverseField.Cell((int) cellX, (int) cellZ));
            if (system == null) continue;
            for (Candidate candidate : generateSystem(worldSeed, system, requested, chartedBodies, asteroidBelts)) {
                double reach = range + candidate.radius();
                if (distanceSquared(candidate.x() - x, candidate.y() - y, candidate.z() - z) <= reach * reach)
                    nearby.add(candidate);
            }
        }
        nearby.sort(Comparator.comparing(Candidate::id));
        return List.copyOf(nearby);
    }

    private static List<SlotSector> chooseSectors(long worldSeed, UniverseField.StarSystem system, int count) {
        var cell = system.cell();
        Set<Sector> chosen = new HashSet<>(count * 2);
        var result = new ArrayList<SlotSector>(count);
        for (int slot = 0; slot < count; slot++) {
            long seed = planetSeed(worldSeed, system, slot);
            var random = new SplittableRandom(mix64(seed ^ SECTOR_SALT));
            int start = random.nextInt(SECTOR_COUNT);
            Sector sector = null;
            for (int offset = 0; offset < SECTOR_COUNT; offset++) {
                int index = (start + offset) % SECTOR_COUNT;
                Sector attempt = new Sector(index / SECTORS_PER_AXIS - MAX_SECTOR_OFFSET,
                        index % SECTORS_PER_AXIS - MAX_SECTOR_OFFSET);
                if (chosen.add(attempt)) {
                    sector = attempt;
                    break;
                }
            }
            if (sector == null) throw new IllegalStateException("small-planet sector pool exhausted");
            result.add(new SlotSector(slot, sector));
        }
        return List.copyOf(result);
    }

    private static Candidate candidate(long worldSeed, UniverseField.StarSystem system, SlotSector slotSector) {
        Sector sector = slotSector.sector();
        var cell = system.cell();
        long seed = planetSeed(worldSeed, system, slotSector.slot());
        var random = new SplittableRandom(seed);
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        Profile profile = pickProfile(random);
        double x = system.x() + sector.x() * (double) SECTOR_SIZE + random.nextDouble(-0.35, 0.35) * SECTOR_SIZE;
        double y = clamp(system.y() + random.nextDouble(-SYSTEM_Y_JITTER, SYSTEM_Y_JITTER),
                MAX_RADIUS, VOIDSEA_HEIGHT - MAX_RADIUS);
        double z = system.z() + sector.z() * (double) SECTOR_SIZE + random.nextDouble(-0.35, 0.35) * SECTOR_SIZE;
        double richness = profile.minOreRichness
                + random.nextDouble() * (profile.maxOreRichness - profile.minOreRichness);
        return new Candidate(new ObjectId(cell, slotSector.slot(), sector.x(), sector.z()), seed, x, y, z,
                radius, profile, richness, false);
    }

    private static long planetSeed(long worldSeed, UniverseField.StarSystem system, int slot) {
        var cell = system.cell();
        return mix64(worldSeed ^ mix64(system.seed())
                ^ mix64(((long) cell.x() << 32) ^ (cell.z() & 0xFFFFFFFFL))
                ^ mix64(slot + PLANET_SALT));
    }

    private static Profile pickProfile(SplittableRandom random) {
        int total = 0;
        for (Profile profile : Profile.values()) total += profile.weight;
        int roll = random.nextInt(total);
        for (Profile profile : Profile.values()) {
            roll -= profile.weight;
            if (roll < 0) return profile;
        }
        return Profile.ROCK;
    }

    private static boolean overlapsCharted(Candidate candidate, List<Sphere> bodies) {
        for (Sphere body : bodies) {
            double reach = candidate.radius() + body.radius() + ORBITAL_CLEARANCE;
            if (distanceSquared(candidate.x() - body.x(), candidate.y() - body.y(), candidate.z() - body.z())
                    < reach * reach) return true;
        }
        return false;
    }

    private static boolean intersectsBelt(Candidate candidate, List<AsteroidField.Belt> belts) {
        for (AsteroidField.Belt belt : belts) {
            double radial = Math.hypot(candidate.x() - belt.centerX(), candidate.z() - belt.centerZ());
            double radialGap = radial < belt.innerRadius() ? belt.innerRadius() - radial
                    : radial > belt.outerRadius() ? radial - belt.outerRadius() : 0;
            double verticalGap = Math.max(0, Math.abs(candidate.y() - belt.centerY()) - belt.halfThickness());
            if (Math.hypot(radialGap, verticalGap) <= candidate.radius() + ORBITAL_CLEARANCE) return true;
        }
        return false;
    }

    private static boolean overlaps(Candidate first, Candidate second) {
        double reach = first.radius() + second.radius() + ORBITAL_CLEARANCE;
        return distanceSquared(first.x() - second.x(), first.y() - second.y(), first.z() - second.z())
                < reach * reach;
    }

    private static double distanceSquared(double x, double y, double z) { return x * x + y * y + z * z; }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record Sector(int x, int z) {}
    private record SlotSector(int slot, Sector sector) {}
}
