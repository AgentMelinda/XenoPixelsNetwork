package com.dragonminez.server.world.gen;

import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.server.world.biome.HTCBiomes;
import com.dragonminez.server.world.dimension.HTCDimension;
import java.util.List;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceRules.RuleSource;

public class HTCGeneration {
   public static final ResourceKey<LevelStem> HTC_STEM = ResourceKey.create(
      Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath("dragonminez", "time_chamber")
   );
   public static final ResourceKey<NoiseGeneratorSettings> HTC_NOISE_SETTINGS = ResourceKey.create(
      Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("dragonminez", "time_chamber")
   );

   public static void bootstrap(BootstrapContext<LevelStem> context) {
      HolderGetter<Biome> biomeRegistry = context.lookup(Registries.BIOME);
      HolderGetter<DimensionType> dimTypes = context.lookup(Registries.DIMENSION_TYPE);
      HolderGetter<NoiseGeneratorSettings> noiseSettings = context.lookup(Registries.NOISE_SETTINGS);
      BiomeSource biomeSource = new FixedBiomeSource(biomeRegistry.getOrThrow(HTCBiomes.TIME_CHAMBER));
      ChunkGenerator chunkGenerator = new NoiseBasedChunkGenerator(biomeSource, noiseSettings.getOrThrow(HTC_NOISE_SETTINGS));
      context.register(HTC_STEM, new LevelStem(dimTypes.getOrThrow(HTCDimension.HTC_TYPE), chunkGenerator));
   }

   public static void bootstrapNoise(BootstrapContext<NoiseGeneratorSettings> context) {
      RuleSource htcSurfaceRule = SurfaceRules.sequence(
         new RuleSource[]{SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.state(((Block)MainBlocks.TIME_CHAMBER_BLOCK.get()).defaultBlockState()))}
      );
      NoiseSettings noiseSettings = NoiseSettings.create(-16, 96, 1, 2);
      context.register(
         HTC_NOISE_SETTINGS,
         new NoiseGeneratorSettings(
            noiseSettings,
            ((Block)MainBlocks.TIME_CHAMBER_BLOCK.get()).defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            createRouter(),
            htcSurfaceRule,
            List.of(),
            0,
            false,
            false,
            false,
            false
         )
      );
   }

   private static NoiseRouter createRouter() {
      DensityFunction constantNegative = DensityFunctions.constant(-1.0);
      DensityFunction depthFunction = DensityFunctions.yClampedGradient(0, 62, 1.0, -1.0);
      return new NoiseRouter(
         constantNegative,
         constantNegative,
         constantNegative,
         constantNegative,
         constantNegative,
         constantNegative,
         constantNegative,
         constantNegative,
         depthFunction,
         constantNegative,
         depthFunction,
         depthFunction,
         constantNegative,
         constantNegative,
         constantNegative
      );
   }
}
