package net.bullettrain.xenopixelsmod.space.worldgen;

import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetField;

/** Bounded deterministic terrain math for the shared small-planet surface dimension. */
public final class PlanetSurfaceTerrain {
    public static final double MIN_SURFACE_Y = -40.0;
    public static final double MAX_SURFACE_Y = 252.0;
    private static final int[] CRATER_RADII = {32, 96, 260, 700, 1_500};
    private static final double[] CRATER_OCCURRENCE = {0.86, 0.67, 0.48, 0.29, 0.16};
    private static final double CAVE_THRESHOLD = 0.62;
    private static final double CAVE_MIN_DEPTH = 10.0;
    private static final double CAVE_MAX_DEPTH = 128.0;
    private static final ThreadLocal<CraterCache> CRATER_CACHE = ThreadLocal.withInitial(CraterCache::new);

    private PlanetSurfaceTerrain() {}

    /** Noise inputs are expected in [-1,1]; non-finite values safely collapse to zero variation. */
    public static double height(SmallPlanetField.Profile profile, long seed, int x, int z,
                                double broadNoise, double detailNoise, double ridgeNoise) {
        if (profile == null) throw new IllegalArgumentException("planet profile is required");
        double broad = finiteClamp(broadNoise, -1.0, 1.0);
        double detail = finiteClamp(detailNoise, -1.0, 1.0);
        double ridge = finiteClamp(ridgeNoise, -1.0, 1.0);
        // The charted Moon uses its own mare/crater profile. Do not add generic small-planet
        // height variation after it: that would shift lunar terrain away from LunarTerrain's
        // authored bounded surface and make the shared-dimension integration disagree with its
        // source profile.
        if (profile == SmallPlanetField.Profile.LUNAR)
            return finiteClamp(LunarTerrain.height(seed, x, z, broad, ridge), MIN_SURFACE_Y, MAX_SURFACE_Y);
        double ridged = 1.0 - Math.abs(ridge);
        double phase = unit(mix(seed ^ 0x3C6EF372FE94F82BL));

        double surface = switch (profile) {
            case ROCK -> 94.0 + broad * 34.0 + detail * 7.0;
            case ICE -> 84.0 + broad * 48.0 + detail * 10.0 + ridged * 7.0;
            case DESERT -> 91.0 + broad * 18.0 + Math.abs(detail) * 17.0 + ridged * 6.0;
            case JUNGLE -> 102.0 + broad * 53.0 + detail * 12.0;
            case LAVA -> 78.0 + broad * 17.0 + detail * 6.0 + Math.pow(ridged, 3.0) * 84.0;
            case LUNAR -> throw new IllegalStateException("lunar terrain must use its dedicated profile path");
        };
        // Preserve subtle per-world/per-planet identity even on a locally flat noise sample.
        surface += (phase - 0.5) * 4.0;
        if (profile == SmallPlanetField.Profile.ROCK || profile == SmallPlanetField.Profile.ICE
                || profile == SmallPlanetField.Profile.DESERT)
            surface += craterRelief(seed, x, z);
        return finiteClamp(surface, MIN_SURFACE_Y, MAX_SURFACE_Y);
    }

    /** Positive values are solid, negative values air; caves are bounded below the near-surface layer. */
    public static double density(double surfaceY, int y, double caveNoise) {
        if (!Double.isFinite(surfaceY)) return -1.0;
        double depth = surfaceY - y;
        if (depth >= CAVE_MIN_DEPTH && depth <= CAVE_MAX_DEPTH
                && Double.isFinite(caveNoise) && caveNoise > CAVE_THRESHOLD) return -1.0;
        return surfaceY - y;
    }

    /** Stable identity salt for a cell's system and planet slot, mixed with a world-seeded noise sample. */
    public static long cellSeed(int systemX, int systemZ, int planetSlot, long worldSeedSample) {
        if (planetSlot < 0 || planetSlot >= SmallPlanetField.MAX_PLANETS_PER_SYSTEM)
            throw new IllegalArgumentException("planet slot is outside the generated system");
        long identity = mix((long) systemX * 0x9E3779B97F4A7C15L)
                ^ mix((long) systemZ * 0xC2B2AE3D27D4EB4FL)
                ^ mix((long) planetSlot * 0x165667B19E3779F9L);
        return mix(worldSeedSample ^ identity);
    }

    private static double craterRelief(long seed, int x, int z) {
        double offset = 0.0;
        CraterCache cache = CRATER_CACHE.get();
        for (int scale = 0; scale < CRATER_RADII.length; scale++) {
            int radius = CRATER_RADII[scale];
            int cellSize = radius * 4;
            long cellX = Math.floorDiv((long) x, cellSize);
            long cellZ = Math.floorDiv((long) z, cellSize);
            cache.prepare(seed, scale, cellX, cellZ, cellSize, radius);
            double influenceRadius = radius * 1.6;
            for (int candidate = 0; candidate < CraterCache.CANDIDATES_PER_SCALE; candidate++) {
                if (!cache.active[scale][candidate]) continue;
                double dxFromCenter = x - cache.centerX[scale][candidate];
                double dzFromCenter = z - cache.centerZ[scale][candidate];
                if (Math.abs(dxFromCenter) >= influenceRadius || Math.abs(dzFromCenter) >= influenceRadius) continue;
                double distanceInRadii = Math.sqrt(dxFromCenter * dxFromCenter + dzFromCenter * dzFromCenter) / radius;
                if (distanceInRadii >= 1.6) continue;
                double age = cache.age[scale][candidate];
                double depth = cache.depth[scale][candidate];
                double r = distanceInRadii;
                double bowl = r < 1.0 ? -depth * Math.pow(1.0 - r * r, 2.0) : 0.0;
                double rimDistance = Math.abs(r - 1.0) / (0.12 + 0.10 * age);
                double rim = rimDistance < 1.0
                        ? depth * 0.24 * Math.pow(1.0 - rimDistance * rimDistance, 2.0) : 0.0;
                double ejectaT = (r - 1.0) / 0.6;
                double ejecta = r > 1.0
                        ? depth * 0.035 * 16.0 * ejectaT * ejectaT * (1.0 - ejectaT) * (1.0 - ejectaT)
                        : 0.0;
                offset += bowl + rim + ejecta;
            }
        }
        return offset;
    }

    /** Bounded last-cell cache: one fixed 5 x 9 candidate grid per worker thread. */
    private static final class CraterCache {
        private static final int CANDIDATES_PER_SCALE = 9;
        private final boolean[] initialized = new boolean[CRATER_RADII.length];
        private final long[] seeds = new long[CRATER_RADII.length];
        private final long[] cellXs = new long[CRATER_RADII.length];
        private final long[] cellZs = new long[CRATER_RADII.length];
        private final boolean[][] active = new boolean[CRATER_RADII.length][CANDIDATES_PER_SCALE];
        private final double[][] centerX = new double[CRATER_RADII.length][CANDIDATES_PER_SCALE];
        private final double[][] centerZ = new double[CRATER_RADII.length][CANDIDATES_PER_SCALE];
        private final double[][] depth = new double[CRATER_RADII.length][CANDIDATES_PER_SCALE];
        private final double[][] age = new double[CRATER_RADII.length][CANDIDATES_PER_SCALE];

        private void prepare(long seed, int scale, long cellX, long cellZ, int cellSize, int radius) {
            if (initialized[scale] && seeds[scale] == seed && cellXs[scale] == cellX && cellZs[scale] == cellZ)
                return;

            int candidate = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long hash = mix(seed ^ mix((cellX + dx) * 0x9E3779B97F4A7C15L)
                            ^ mix((cellZ + dz) * 0xC2B2AE3D27D4EB4FL) ^ mix(scale + 19L));
                    boolean hasCrater = unit(hash) < CRATER_OCCURRENCE[scale];
                    active[scale][candidate] = hasCrater;
                    if (hasCrater) {
                        double craterAge = unit(mix(hash + 3));
                        centerX[scale][candidate] = (cellX + dx + unit(mix(hash + 1))) * cellSize;
                        centerZ[scale][candidate] = (cellZ + dz + unit(mix(hash + 2))) * cellSize;
                        age[scale][candidate] = craterAge;
                        depth[scale][candidate] = Math.min(70.0, radius * 0.07) * (1.0 - 0.45 * craterAge);
                    }
                    candidate++;
                }
            }
            seeds[scale] = seed;
            cellXs[scale] = cellX;
            cellZs[scale] = cellZ;
            initialized[scale] = true;
        }
    }

    private static double finiteClamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double unit(long hash) { return (hash >>> 11) * 0x1.0p-53; }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
