package net.bullettrain.xenopixelsmod.space.worldgen;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LunarHeightDensityTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void inlineNoiseCodecRoundTrip() {
        var holder = new DensityFunction.NoiseHolder(Holder.direct(new NormalNoise.NoiseParameters(-4, 1.0)));
        var original = new LunarHeightDensity(holder, holder, holder);
        var json = LunarHeightDensity.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var decoded = LunarHeightDensity.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(original.compute(new DensityFunction.SinglePointContext(-99, 30, 127)),
                decoded.compute(new DensityFunction.SinglePointContext(-99, 900, 127)));
    }

    @Test void visitsEveryNoiseAndWorldSeedChangesPlacement() {
        var params = new NormalNoise.NoiseParameters(-4, 1.0);
        var unwired = new DensityFunction.NoiseHolder(Holder.direct(params));
        var base = new LunarHeightDensity(unwired, unwired, unwired);
        int[] visited = {0};
        DensityFunction first = base.mapAll(new DensityFunction.Visitor() {
            public DensityFunction apply(DensityFunction value) { return value; }
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder holder) {
                visited[0]++;
                return new DensityFunction.NoiseHolder(holder.noiseData(), NormalNoise.create(RandomSource.create(77), params));
            }
        });
        assertEquals(3, visited[0]);
        DensityFunction second = base.mapAll(new DensityFunction.Visitor() {
            public DensityFunction apply(DensityFunction value) { return value; }
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder holder) {
                return new DensityFunction.NoiseHolder(holder.noiseData(), NormalNoise.create(RandomSource.create(99), params));
            }
        });
        var point = new DensityFunction.SinglePointContext(217, 0, -523);
        assertNotEquals(first.compute(point), second.compute(point));
        assertEquals(first.compute(point), first.compute(new DensityFunction.SinglePointContext(217, -500, -523)));
    }
}
