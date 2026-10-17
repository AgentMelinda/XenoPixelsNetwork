package net.bullettrain.xenopixelsmod.space.asteroid;

import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.SplittableRandom;

/**
 * The blocks of one belt asteroid (S11 B3), derived only from its {@link AsteroidField.Candidate} so every server builds
 * the same rock for the same id. The shape is a sphere of the candidate's radius pushed out by a few seeded lobes and cut
 * by a few seeded craters (no per-voxel noise, so the silhouette stays rock-like rather than fuzzy). Inside, material
 * goes crust -> mantle -> core by depth, and a handful of seeded ore pockets replace rock. Icy asteroids swap the rock
 * for packed/blue ice with a stone core. Vanilla blocks only; a planet material set can replace {@link #ores} later.
 * Pure and immutable once built: {@link #voxels()} runs on the builder's background thread.
 */
public final class AsteroidVoxels {
    private static final int LOBES = 6, CRATERS = 3, POCKETS_PER_RADIUS = 1;
    private static final BlockState[] ROCK_CRUST = {Blocks.ANDESITE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.TUFF.defaultBlockState()};
    private static final BlockState[] ICE_CRUST = {Blocks.PACKED_ICE.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState()};
    private static final BlockState ROCK_MANTLE = Blocks.STONE.defaultBlockState(), ICE_MANTLE = Blocks.BLUE_ICE.defaultBlockState();
    private static final BlockState CORE = Blocks.DEEPSLATE.defaultBlockState();
    /** Ore table: shallow ores first, deep ores last; weights are relative. */
    private static final BlockState[] ores = {
            Blocks.COAL_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(), Blocks.COPPER_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(), Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState(), Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState()};
    private static final int[] ORE_WEIGHTS = {30, 26, 20, 9, 7, 5, 3};

    private final int radius, extent;
    private final boolean icy;
    private final double[][] lobes;      // unit direction x,y,z + amplitude (fraction of radius)
    private final double[][] craters;    // centre x,y,z (local) + crater radius
    private final double[][] pockets;    // centre x,y,z + pocket radius + ore index

    public AsteroidVoxels(AsteroidField.Candidate candidate) {
        this.radius = candidate.radius();
        this.icy = candidate.icy();
        var random = new SplittableRandom(candidate.seed() ^ 0x2545F4914F6CDD1DL);
        lobes = new double[LOBES][];
        for (int i = 0; i < LOBES; i++) {
            double[] d = unit(random);
            lobes[i] = new double[]{d[0], d[1], d[2], 0.10 + random.nextDouble(0.25)};
        }
        craters = new double[CRATERS][];
        for (int i = 0; i < CRATERS; i++) {
            double[] d = unit(random);
            double r = radius * (0.25 + random.nextDouble(0.25));
            double at = radius * (1.05 + random.nextDouble(0.15)) + r * 0.4;
            craters[i] = new double[]{d[0] * at, d[1] * at, d[2] * at, r};
        }
        int count = icy ? 0 : Math.max(1, radius * POCKETS_PER_RADIUS / 2);
        pockets = new double[count][];
        for (int i = 0; i < count; i++) {
            double[] d = unit(random);
            double depth = random.nextDouble(0.15, 0.85);
            int ore = pick(random, depth);
            double size = 0.9 + random.nextDouble(0.9);
            pockets[i] = new double[]{d[0] * radius * depth, d[1] * radius * depth, d[2] * radius * depth, size, ore};
        }
        // Lobes add up where they overlap, so the bound is the radius plus every lobe's amplitude.
        double lobeSum = 0;
        for (double[] lobe : lobes) lobeSum += lobe[3];
        this.extent = (int) Math.ceil(radius * (1 + lobeSum)) + 1;
    }

    /** Half-size of the voxel cube that contains the asteroid, for {@link PlotBodyBuilder#start}. */
    public int extent() { return extent; }

    public PlotBodyBuilder.Voxels voxels() { return this::at; }

    /** The block at body-local offset (x, y, z) from the centre voxel, or null for empty space. */
    public BlockState at(int x, int y, int z) {
        double distance = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
        double surface = surfaceRadius(x, y, z, distance);
        if (distance > surface) return null;
        for (double[] crater : craters) {
            double dx = x - crater[0], dy = y - crater[1], dz = z - crater[2];
            if (dx * dx + dy * dy + dz * dz < crater[3] * crater[3]) return null;
        }
        for (double[] pocket : pockets) {
            double dx = x - pocket[0], dy = y - pocket[1], dz = z - pocket[2];
            if (dx * dx + dy * dy + dz * dz <= pocket[3] * pocket[3]) return ores[(int) pocket[4]];
        }
        double depth = surface - distance;
        if (depth < 1.5) return (icy ? ICE_CRUST : ROCK_CRUST)[Math.floorMod(x * 7 + y * 3 + z * 5, 3)];
        if (distance < radius * 0.35) return CORE;
        return icy ? ICE_MANTLE : ROCK_MANTLE;
    }

    private double surfaceRadius(int x, int y, int z, double distance) {
        if (distance < 1e-9) return radius;
        double surface = radius;
        for (double[] lobe : lobes) {
            double dot = (x * lobe[0] + y * lobe[1] + z * lobe[2]) / distance;
            if (dot > 0) surface += radius * lobe[3] * dot * dot * dot;
        }
        return surface;
    }

    private static int pick(SplittableRandom random, double depth) {
        // Deeper pockets favour the deep (later) entries of the ore table.
        double[] weights = new double[ORE_WEIGHTS.length];
        for (int i = 0; i < weights.length; i++) {
            double bias = 1.0 + depth * 2.0 * i / (weights.length - 1);
            weights[i] = ORE_WEIGHTS[i] * bias;
        }
        double sum = 0;
        for (double w : weights) sum += w;
        double roll = random.nextDouble(sum);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) return i;
        }
        return weights.length - 1;
    }

    private static double[] unit(SplittableRandom random) {
        double z = random.nextDouble(-1, 1), angle = random.nextDouble(Math.PI * 2), r = Math.sqrt(1 - z * z);
        return new double[]{r * Math.cos(angle), r * Math.sin(angle), z};
    }
}
