package net.bullettrain.xenopixelsmod.plot;

import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * A claimed plot: a 2-D column extent inside one dimension.
 *
 * <p>Y is deliberately absent. The WorldEdit selection that produced a plot also carried a Y
 * range, but plots are column extents, so {@code minY}/{@code maxY} are read and discarded at
 * conversion time and never stored.</p>
 *
 * <p>Bounds are stored normalized ({@code min <= max}) so overlap and containment tests do not
 * have to reason about which corner the player clicked first.</p>
 *
 * @param dimension  dimension id the plot lives in
 * @param minX       lower X bound, inclusive
 * @param minZ       lower Z bound, inclusive
 * @param maxX       upper X bound, inclusive
 * @param maxZ       upper Z bound, inclusive
 * @param owner      player the plot belongs to
 * @param flags      packed permission bits, interpretation owned by {@link PlotFlags}
 */
public record PlotArea(ResourceLocation dimension, int minX, int minZ, int maxX, int maxZ,
                       UUID owner, int flags) {

    public PlotArea {
        if (minX > maxX) {
            int swap = minX;
            minX = maxX;
            maxX = swap;
        }
        if (minZ > maxZ) {
            int swap = minZ;
            minZ = maxZ;
            maxZ = swap;
        }
    }

    /** Builds a plot from a raw WorldEdit min/max corner, discarding Y. */
    public static PlotArea of(ResourceLocation dimension, int x1, int z1, int x2, int z2,
                              UUID owner, int flags) {
        return new PlotArea(dimension, Math.min(x1, x2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(z1, z2), owner, flags);
    }

    /** Blocks wide on the X axis. */
    public int width() {
        return maxX - minX + 1;
    }

    /** Blocks long on the Z axis. */
    public int length() {
        return maxZ - minZ + 1;
    }

    /** Footprint in blocks, ignoring Y. */
    public int area() {
        return width() * length();
    }

    /** True when {@code (x, z)} is inside this plot, inclusive of the bounds. */
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * 2-D rectangle intersection. A plot in another dimension never overlaps, and touching edges
     * are not an overlap.
     */
    public boolean overlaps(PlotArea other) {
        if (other == null || !dimension.equals(other.dimension)) {
            return false;
        }
        return minX <= other.maxX && maxX >= other.minX
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    /** True when {@code other} sits entirely inside this plot. */
    public boolean encloses(PlotArea other) {
        if (other == null || !dimension.equals(other.dimension)) {
            return false;
        }
        return other.minX >= minX && other.maxX <= maxX
                && other.minZ >= minZ && other.maxZ <= maxZ;
    }

    public boolean ownedBy(UUID playerId) {
        return owner != null && owner.equals(playerId);
    }
}