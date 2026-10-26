package net.bullettrain.xenopixelsmod.space.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** World-seeded profile terrain and caves for the shared planet-surface dimension. */
public final class PlanetSurfaceTerrainDensity implements DensityFunction.SimpleFunction {
    public static final MapCodec<PlanetSurfaceTerrainDensity> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NoiseHolder.CODEC.fieldOf("seed_noise").forGetter((PlanetSurfaceTerrainDensity f) -> f.seedNoise),
            NoiseHolder.CODEC.fieldOf("landform_noise").forGetter((PlanetSurfaceTerrainDensity f) -> f.landformNoise),
            NoiseHolder.CODEC.fieldOf("detail_noise").forGetter((PlanetSurfaceTerrainDensity f) -> f.detailNoise),
            NoiseHolder.CODEC.fieldOf("ridge_noise").forGetter((PlanetSurfaceTerrainDensity f) -> f.ridgeNoise),
            NoiseHolder.CODEC.fieldOf("cave_noise").forGetter((PlanetSurfaceTerrainDensity f) -> f.caveNoise)
    ).apply(instance, PlanetSurfaceTerrainDensity::new));
    private static final KeyDispatchDataCodec<PlanetSurfaceTerrainDensity> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private final NoiseHolder seedNoise;
    private final NoiseHolder landformNoise;
    private final NoiseHolder detailNoise;
    private final NoiseHolder ridgeNoise;
    private final NoiseHolder caveNoise;
    /** Each density instance owns a per-worker cache; the associated NoiseHolder never changes for that instance. */
    private final ThreadLocal<PlanetSurfaceCellContextCache> cellContext =
            ThreadLocal.withInitial(PlanetSurfaceCellContextCache::new);

    public PlanetSurfaceTerrainDensity(NoiseHolder seedNoise, NoiseHolder landformNoise, NoiseHolder detailNoise,
                                       NoiseHolder ridgeNoise, NoiseHolder caveNoise) {
        this.seedNoise = seedNoise;
        this.landformNoise = landformNoise;
        this.detailNoise = detailNoise;
        this.ridgeNoise = ridgeNoise;
        this.caveNoise = caveNoise;
    }

    @Override public double compute(FunctionContext context) {
        int x = context.blockX(), y = context.blockY(), z = context.blockZ();
        PlanetSurfaceCellContextCache cache = cellContext.get();
        if (cache.needsRefresh(x, z)) {
            var address = PlanetSurfaceCells.addressAtBlock(x, z).orElse(null);
            if (address == null) {
                cache.store(x, z, null, 0L);
                return -1.0;
            }
            double worldSeed = seedNoise.getValue(address.surfaceCell().centerBlockX(), 0.0,
                    address.surfaceCell().centerBlockZ());
            long seed = PlanetSurfaceTerrain.addressSeed(address, Double.doubleToLongBits(worldSeed));
            cache.store(x, z, address, seed);
        }
        var cell = cache.address();
        if (cell == null) return -1.0;
        double height = PlanetSurfaceTerrain.height(cell.profile(), cache.seed(), x, z,
                landformNoise.getValue(x, 0.0, z), detailNoise.getValue(x, 0.0, z),
                ridgeNoise.getValue(x, 0.0, z));
        return PlanetSurfaceTerrain.density(height, y, caveNoise.getValue(x, y, z));
    }

    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new PlanetSurfaceTerrainDensity(visitor.visitNoise(seedNoise),
                visitor.visitNoise(landformNoise), visitor.visitNoise(detailNoise), visitor.visitNoise(ridgeNoise),
                visitor.visitNoise(caveNoise)));
    }

    @Override public double minValue() { return PlanetSurfaceTerrain.MIN_SURFACE_Y - 320.0; }
    @Override public double maxValue() { return PlanetSurfaceTerrain.MAX_SURFACE_Y + 64.0; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
