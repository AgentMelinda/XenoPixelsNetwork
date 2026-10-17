package net.bullettrain.xenopixelsmod.space.worldgen;

import net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells;

/**
 * Last-cell cache for density workers. A surface cell's profile, system identity, and seed-noise sample are constant
 * throughout its 8,192-block square, so resolving them for every density sample only creates avoidable records and
 * noise evaluations.
 */
final class PlanetSurfaceCellContextCache {
    private boolean initialized;
    private long cellX;
    private long cellZ;
    private PlanetSurfaceCells.Address address;
    private long seed;

    boolean needsRefresh(long blockX, long blockZ) {
        long x = Math.floorDiv(blockX, PlanetSurfaceCells.CELL_SIZE);
        long z = Math.floorDiv(blockZ, PlanetSurfaceCells.CELL_SIZE);
        return !initialized || x != cellX || z != cellZ;
    }

    void store(long blockX, long blockZ, PlanetSurfaceCells.Address address, long seed) {
        this.cellX = Math.floorDiv(blockX, PlanetSurfaceCells.CELL_SIZE);
        this.cellZ = Math.floorDiv(blockZ, PlanetSurfaceCells.CELL_SIZE);
        this.address = address;
        this.seed = seed;
        initialized = true;
    }

    PlanetSurfaceCells.Address address() { return address; }
    long seed() { return seed; }
}
