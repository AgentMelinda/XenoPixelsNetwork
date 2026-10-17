package net.bullettrain.xenopixelsmod.space.worldgen;

/** Stateless, metre-scale crater field. Work is bounded to 45 candidate cells per column. */
public final class LunarTerrain {
    /** The shared planet-surface dimension spans [-64, 320); keep relief inside its loaded build range. */
    public static final double MIN_HEIGHT = -40.0;
    public static final double MAX_HEIGHT = 252.0;
    private static final int[] RADII = {50, 250, 500, 1000, 2500};
    private static final double[] OCCURRENCE = {.85, .65, .45, .28, .15};

    private LunarTerrain() {}

    public static int radius(int scale) { return RADII[scale]; }

    public static double height(long seed, double x, double z, double maria, double relief) {
        double mariaWeight = smooth(Math.max(0, Math.min(1, (maria + .15) / .5)));
        double height = 220 - 70 * mariaWeight + 35 * relief * (1 - .75 * mariaWeight);
        for (int scale = 0; scale < RADII.length; scale++) {
            double radius = RADII[scale];
            int cellSize = RADII[scale] * 4;
            long cellX = (long) Math.floor(x / cellSize);
            long cellZ = (long) Math.floor(z / cellSize);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long hash = cellHash(seed, cellX + dx, cellZ + dz, scale);
                    // Fade marginal craters through mare transitions instead of switching their
                    // contribution abruptly as the sampled column crosses a noise threshold.
                    double survival = smooth(Math.max(0, Math.min(1,
                            (OCCURRENCE[scale] * (1 - .6 * mariaWeight) - unit(hash))
                                    / (OCCURRENCE[scale] * .1))));
                    if (survival == 0) continue;
                    double cx = (cellX + dx + unit(mix(hash + 1))) * cellSize;
                    double cz = (cellZ + dz + unit(mix(hash + 2))) * cellSize;
                    double vx = x - cx, vz = z - cz;
                    if (vx * vx + vz * vz >= radius * radius * 2.56) continue;
                    double age = unit(mix(hash + 3));
                    height += survival * craterProfile(Math.sqrt(vx * vx + vz * vz) / radius, radius, age);
                }
            }
        }
        return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, height));
    }

    /** Compact support at 1.6 radii gives continuous chunk boundaries without neighbouring loads. */
    public static double craterProfile(double distanceInRadii, double radius, double age) {
        double r = Math.abs(distanceInRadii);
        if (r >= 1.6) return 0;
        double depth = Math.min(180, radius * .15) * (1 - .45 * age);
        double bowl = r < 1 ? -depth * Math.pow(1 - r * r, 2) : 0;
        double rimDistance = Math.abs(r - 1) / (.12 + .10 * age);
        double rim = rimDistance < 1 ? depth * .24 * Math.pow(1 - rimDistance * rimDistance, 2) : 0;
        double ejectaT = (r - 1) / .6;
        double ejecta = r > 1 ? depth * .035 * 16 * ejectaT * ejectaT
                * (1 - ejectaT) * (1 - ejectaT) : 0;
        return bowl + rim + ejecta;
    }

    public static long cellHash(long seed, long x, long z, int scale) {
        return mix(seed ^ mix(x * 0x9E3779B97F4A7C15L) ^ mix(z * 0xC2B2AE3D27D4EB4FL) ^ mix(scale + 19));
    }

    public static double unit(long hash) { return (hash >>> 11) * 0x1.0p-53; }

    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }
}
