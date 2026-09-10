package com.dragonminez.server.world.gen;

import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.server.world.biome.OtherworldBiomes;
import com.dragonminez.server.world.dimension.OtherworldDimension;
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
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.SurfaceRules.RuleSource;

public class OtherworldGeneration {
   public static final ResourceKey<LevelStem> OTHERWORLD_STEM = ResourceKey.create(
      Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath("dragonminez", "otherworld")
   );
   public static final ResourceKey<NoiseGeneratorSettings> OTHERWORLD_NOISE_SETTINGS = ResourceKey.create(
      Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("dragonminez", "otherworld")
   );

   public static void bootstrap(BootstrapContext<LevelStem> context) {
      HolderGetter<Biome> biomeRegistry = context.lookup(Registries.BIOME);
      HolderGetter<DimensionType> dimTypes = context.lookup(Registries.DIMENSION_TYPE);
      HolderGetter<NoiseGeneratorSettings> noiseSettings = context.lookup(Registries.NOISE_SETTINGS);
      BiomeSource biomeSource = new FixedBiomeSource(biomeRegistry.getOrThrow(OtherworldBiomes.OTHERWORLD));
      ChunkGenerator chunkGenerator = new NoiseBasedChunkGenerator(biomeSource, noiseSettings.getOrThrow(OTHERWORLD_NOISE_SETTINGS));
      context.register(OTHERWORLD_STEM, new LevelStem(dimTypes.getOrThrow(OtherworldDimension.OTHERWORLD_TYPE), chunkGenerator));
   }

   public static void bootstrapNoise(BootstrapContext<NoiseGeneratorSettings> context) {
      RuleSource otherWorldRules = SurfaceRules.sequence(
         new RuleSource[]{
            SurfaceRules.ifTrue(
               SurfaceRules.verticalGradient("bedrock_floor", VerticalAnchor.aboveBottom(0), VerticalAnchor.aboveBottom(2)),
               SurfaceRules.state(Blocks.BEDROCK.defaultBlockState())
            ),
            SurfaceRules.ifTrue(
               SurfaceRules.verticalGradient("cloud_floor", VerticalAnchor.aboveBottom(2), VerticalAnchor.aboveBottom(5)),
               SurfaceRules.state(((Block)MainBlocks.OTHERWORLD_CLOUD.get()).defaultBlockState())
            )
         }
      );
      NoiseSettings noiseSettings = NoiseSettings.create(0, 320, 4, 2);
      context.register(
         OTHERWORLD_NOISE_SETTINGS,
         new NoiseGeneratorSettings(
            noiseSettings,
            ((Block)MainBlocks.OTHERWORLD_CLOUD.get()).defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            createRouter(),
            otherWorldRules,
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
      DensityFunction depthFunction = DensityFunctions.yClampedGradient(3, 5, 1.0, -1.0);
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
