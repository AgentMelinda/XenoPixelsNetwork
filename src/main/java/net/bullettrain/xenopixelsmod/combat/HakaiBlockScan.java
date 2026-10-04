package net.bullettrain.xenopixelsmod.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The positions an area Hakai covers, produced lazily (2026-09-29 owner: "remove limit of
 * hakaiAreaRadius 32 ... i want to be able to set it to 500").
 *
 * <p>Listing a radius-500 sphere up front is about 500 million positions and froze the server. This
 * hands positions out one at a time so {@link HakaiBlockErasure} can spread the work over ticks,
 * never touches an unloaded chunk (nothing is loaded or generated), and skips 16-block sections that
 * hold only air.
 *
 * <p><b>Layer after layer</b> (owner: "its not always clean all of the first layer after layer").
 * Each height layer is finished across the whole area - every chunk, nearest first - before the next
 * one down, so the erasure sinks evenly instead of leaving steps at every chunk border.
 *
 * <p>World access is behind {@link World} so the walk is testable without a level.
 */
public final class HakaiBlockScan implements Iterator<BlockPos> {

    /** What the scan needs to know about the level. */
    public interface World {
        boolean loaded(int chunkX, int chunkZ);

        /** Whether the 16-block section {@code sectionY} ({@code y >> 4}) holds only air. */
        boolean sectionEmpty(int chunkX, int sectionY, int chunkZ);

        /** Natural ground the raze shape stops at (unused by the sphere). */
        boolean ground(BlockPos pos);

        boolean air(BlockPos pos);
    }

    /** Chunk-column bounds, inclusive (a Sable ship's plot); null for the open world. */
    public record Bounds(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
    }

    private final Vec3 centre;
    private final double radius;
    private final boolean raze;
    private final World world;
    private final int bottom;

    // One entry per chunk column touching the disc, nearest first.
    private final int[] chunkX;
    private final int[] chunkZ;
    private final int[] chunkTop;
    private final int[] chunkLow;
    private final int[] checkedSection;
    private final boolean[] emptySection;
    private final BitSet[] grounded;

    // Cursor: layer, chunk within the layer, column within the chunk.
    private int y;
    private int chunk;
    private int column;
    private BlockPos next;

    private HakaiBlockScan(Vec3 centre, double radius, int minY, int maxYExclusive, boolean raze,
                           World world, Bounds bounds) {
        this.centre = centre;
        this.radius = Math.max(0.0, radius);
        this.raze = raze;
        this.world = world;
        List<double[]> order = chunkOrder(centre, this.radius, bounds);
        int n = order.size();
        chunkX = new int[n];
        chunkZ = new int[n];
        chunkTop = new int[n];
        chunkLow = new int[n];
        checkedSection = new int[n];
        emptySection = new boolean[n];
        grounded = new BitSet[n];
        int top = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            double[] c = order.get(i);
            chunkX[i] = (int) c[0];
            chunkZ[i] = (int) c[1];
            double near = Math.sqrt(c[2]);
            if (raze) {
                chunkTop[i] = maxYExclusive - 1;
                chunkLow[i] = minY;
            } else {
                double half = Math.sqrt(Math.max(0.0, this.radius * this.radius - near * near));
                chunkTop[i] = (int) Math.min(maxYExclusive - 1, Math.floor(centre.y + half));
                chunkLow[i] = (int) Math.max(minY, Math.floor(centre.y - half));
            }
            checkedSection[i] = Integer.MIN_VALUE;
            top = Math.max(top, chunkTop[i]);
        }
        this.y = top;
        this.bottom = raze ? minY : (int) Math.max(minY, Math.floor(centre.y - this.radius));
    }

    /** Every position whose centre is in the sphere, within the world's height. */
    public static HakaiBlockScan sphere(Vec3 centre, double radius, int minY, int maxYExclusive,
                                        World world, Bounds bounds) {
        return new HakaiBlockScan(centre, radius, minY, maxYExclusive, false, world, bounds);
    }

    /**
     * Every non-air position in each column within {@code radius} across the ground, from
     * {@code top} down to {@code bottom}, until the column's first ground block (which stays).
     */
    public static HakaiBlockScan raze(Vec3 centre, double radius, int top, int bottom,
                                      World world, Bounds bounds) {
        return new HakaiBlockScan(centre, radius, bottom, top + 1, true, world, bounds);
    }

    /** Chunk columns touching the disc, nearest first: {x, z, squared distance}. */
    private static List<double[]> chunkOrder(Vec3 centre, double radius, Bounds bounds) {
        int minCx = (int) Math.floor((centre.x - radius) / 16.0);
        int maxCx = (int) Math.floor((centre.x + radius) / 16.0);
        int minCz = (int) Math.floor((centre.z - radius) / 16.0);
        int maxCz = (int) Math.floor((centre.z + radius) / 16.0);
        if (bounds != null) {
            minCx = Math.max(minCx, bounds.minChunkX());
            maxCx = Math.min(maxCx, bounds.maxChunkX());
            minCz = Math.max(minCz, bounds.minChunkZ());
            maxCz = Math.min(maxCz, bounds.maxChunkZ());
        }
        List<double[]> out = new ArrayList<>();
        double r2 = radius * radius;
        for (int x = minCx; x <= maxCx; x++) {
            for (int z = minCz; z <= maxCz; z++) {
                double nx = Math.max(x * 16.0, Math.min(centre.x, x * 16.0 + 16.0)) - centre.x;
                double nz = Math.max(z * 16.0, Math.min(centre.z, z * 16.0 + 16.0)) - centre.z;
                double d2 = nx * nx + nz * nz;
                if (d2 <= r2) out.add(new double[]{x, z, d2});
            }
        }
        out.sort(Comparator.comparingDouble(c -> c[2]));
        return out;
    }

    /** Whether this chunk has anything to give in layer {@code y}; refreshes its section check. */
    private boolean chunkLive(int i) {
        if (y > chunkTop[i] || y < chunkLow[i]) return false;
        int section = y >> 4;
        if (checkedSection[i] != section) {
            checkedSection[i] = section;
            emptySection[i] = !world.loaded(chunkX[i], chunkZ[i])
                    || world.sectionEmpty(chunkX[i], section, chunkZ[i]);
        }
        return !emptySection[i];
    }

    private BlockPos advance() {
        double r2 = radius * radius + 1.0e-9;
        while (y >= bottom) {
            while (chunk < chunkX.length) {
                if (column == 0 && !chunkLive(chunk)) {
                    chunk++;
                    continue;
                }
                int cx = chunkX[chunk];
                int cz = chunkZ[chunk];
                while (column < 256) {
                    int lx = column & 15;
                    int lz = column >> 4;
                    int idx = column;
                    column++;
                    int bx = (cx << 4) + lx;
                    int bz = (cz << 4) + lz;
                    double dx = bx + 0.5 - centre.x;
                    double dz = bz + 0.5 - centre.z;
                    if (raze) {
                        if (dx * dx + dz * dz > r2) continue;
                        BitSet g = grounded[chunk];
                        if (g != null && g.get(idx)) continue;
                        BlockPos p = new BlockPos(bx, y, bz);
                        if (world.ground(p)) {
                            if (g == null) grounded[chunk] = g = new BitSet(256);
                            g.set(idx);
                            continue;
                        }
                        if (!world.air(p)) return p;
                    } else {
                        double dy = y + 0.5 - centre.y;
                        if (dx * dx + dy * dy + dz * dz <= r2) return new BlockPos(bx, y, bz);
                    }
                }
                column = 0;
                chunk++;
            }
            chunk = 0;
            y--;
        }
        return null;
    }

    @Override
    public boolean hasNext() {
        if (next == null) next = advance();
        return next != null;
    }

    @Override
    public BlockPos next() {
        if (!hasNext()) throw new NoSuchElementException();
        BlockPos p = next;
        next = null;
        return p;
    }
}
