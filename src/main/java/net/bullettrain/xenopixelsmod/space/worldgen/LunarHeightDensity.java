package net.bullettrain.xenopixelsmod.space.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Horizontal height, intended to be wrapped by Minecraft's flat_cache then interpolated. */
public final class LunarHeightDensity implements DensityFunction.SimpleFunction {
    public static final MapCodec<LunarHeightDensity> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NoiseHolder.CODEC.fieldOf("seed_noise").forGetter((LunarHeightDensity f) -> f.seedNoise),
            NoiseHolder.CODEC.fieldOf("maria_noise").forGetter((LunarHeightDensity f) -> f.mariaNoise),
            NoiseHolder.CODEC.fieldOf("relief_noise").forGetter((LunarHeightDensity f) -> f.reliefNoise)
    ).apply(instance, LunarHeightDensity::new));
    private static final KeyDispatchDataCodec<LunarHeightDensity> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);
    private final NoiseHolder seedNoise;
    private final NoiseHolder mariaNoise;
    private final NoiseHolder reliefNoise;
    private final long placementSeed;

    public LunarHeightDensity(NoiseHolder seedNoise, NoiseHolder mariaNoise, NoiseHolder reliefNoise) {
        this.seedNoise = seedNoise;
        this.mariaNoise = mariaNoise;
        this.reliefNoise = reliefNoise;
        // RandomState.visitNoise supplies world-seeded NormalNoise. This is a stable seed fingerprint,
        // not a global random state or an independently fixed seed shared by all worlds.
        placementSeed = LunarTerrain.mix(Double.doubleToLongBits(seedNoise.getValue(173.25, 41.5, -291.75)))
                ^ LunarTerrain.mix(Double.doubleToLongBits(seedNoise.getValue(-991.5, 7.25, 601.125)));
    }

    @Override public double compute(FunctionContext context) {
        int x = context.blockX(), z = context.blockZ();
        return LunarTerrain.height(placementSeed, x, z,
                mariaNoise.getValue(x, 0, z), reliefNoise.getValue(x, 0, z));
    }

    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new LunarHeightDensity(visitor.visitNoise(seedNoise),
                visitor.visitNoise(mariaNoise), visitor.visitNoise(reliefNoise)));
    }

    @Override public double minValue() { return LunarTerrain.MIN_HEIGHT; }
    @Override public double maxValue() { return LunarTerrain.MAX_HEIGHT; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
