package com.dragonminez.server.world.gen;

import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.server.world.biome.SacredKaiBiomes;
import com.dragonminez.server.world.dimension.SacredKaiDimension;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.Climate.ParameterList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.SurfaceRules.RuleSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters;

public class SacredKaiGeneration {
   public static final ResourceKey<LevelStem> SACREDKAI_STEM = ResourceKey.create(
      Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath("dragonminez", "sacredkaiplanet")
   );
   public static final ResourceKey<NoiseGeneratorSettings> SACREDKAI_NOISE_SETTINGS = ResourceKey.create(
      Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("dragonminez", "sacredkaiplanet")
   );

   public static void bootstrap(BootstrapContext<LevelStem> context) {
      HolderGetter<Biome> biomeRegistry = context.lookup(Registries.BIOME);
      HolderGetter<DimensionType> dimTypes = context.lookup(Registries.DIMENSION_TYPE);
      HolderGetter<NoiseGeneratorSettings> noiseSettings = context.lookup(Registries.NOISE_SETTINGS);
      MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.createFromList(createSacredKaiBiomeParameters(biomeRegistry));
      ChunkGenerator chunkGenerator = new NoiseBasedChunkGenerator(biomeSource, noiseSettings.getOrThrow(SACREDKAI_NOISE_SETTINGS));
      context.register(SACREDKAI_STEM, new LevelStem(dimTypes.getOrThrow(SacredKaiDimension.SACREDKAI_TYPE), chunkGenerator));
   }

   private static ParameterList<Holder<Biome>> createSacredKaiBiomeParameters(HolderGetter<Biome> biomeRegistry) {
      return new ParameterList(
         List.of(
            Pair.of(Climate.parameters(0.0F, 0.0F, 0.15F, 0.55F, 0.0F, 0.0F, 0.0F), biomeRegistry.getOrThrow(SacredKaiBiomes.SACREDKAI_PLAINS)),
            Pair.of(Climate.parameters(0.0F, 0.0F, 0.55F, -0.15F, 0.0F, 0.0F, 0.0F), biomeRegistry.getOrThrow(SacredKaiBiomes.SACREDKAI_HILLS)),
            Pair.of(Climate.parameters(0.0F, 0.0F, -0.45F, 0.0F, 0.0F, 0.0F, 0.0F), biomeRegistry.getOrThrow(SacredKaiBiomes.SACREDKAI_RIVERS))
         )
      );
   }

   public static void bootstrapNoise(BootstrapContext<NoiseGeneratorSettings> context) {
      RuleSource bedrockRule = SurfaceRules.ifTrue(
         SurfaceRules.verticalGradient("bedrock_floor", VerticalAnchor.aboveBottom(0), VerticalAnchor.aboveBottom(5)),
         SurfaceRules.state(Blocks.BEDROCK.defaultBlockState())
      );
      RuleSource grass = SurfaceRules.state(((Block)MainBlocks.SACRED_PLANET_GRASS_BLOCK.get()).defaultBlockState());
      RuleSource sand = SurfaceRules.state(Blocks.SAND.defaultBlockState());
      RuleSource rockyDirt = SurfaceRules.state(((Block)MainBlocks.ROCKY_DIRT.get()).defaultBlockState());
      RuleSource rockyStone = SurfaceRules.state(((Block)MainBlocks.ROCKY_STONE.get()).defaultBlockState());
      RuleSource sacredKaiSurface = SurfaceRules.ifTrue(
         SurfaceRules.isBiome(new ResourceKey[]{SacredKaiBiomes.SACREDKAI_PLAINS, SacredKaiBiomes.SACREDKAI_HILLS, SacredKaiBiomes.SACREDKAI_RIVERS}),
         SurfaceRules.ifTrue(
            SurfaceRules.abovePreliminarySurface(),
            SurfaceRules.sequence(
               new RuleSource[]{
                  SurfaceRules.ifTrue(
                     SurfaceRules.ON_FLOOR, SurfaceRules.sequence(new RuleSource[]{SurfaceRules.ifTrue(SurfaceRules.waterBlockCheck(-1, 0), grass), sand})
                  ),
                  SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, rockyDirt)
               }
            )
         )
      );
      RuleSource finalRules = SurfaceRules.sequence(new RuleSource[]{bedrockRule, sacredKaiSurface, rockyStone});
      NoiseSettings noiseSettings = NoiseSettings.create(-64, 384, 1, 2);
      HolderGetter<DensityFunction> densityFunctions = context.lookup(Registries.DENSITY_FUNCTION);
      HolderGetter<NoiseParameters> noiseParams = context.lookup(Registries.NOISE);
      NoiseRouter router = SacredKaiNoiseRouterData.createSacredKaiRouter(densityFunctions, noiseParams);
      context.register(
         SACREDKAI_NOISE_SETTINGS,
         new NoiseGeneratorSettings(
            noiseSettings,
            ((Block)MainBlocks.ROCKY_STONE.get()).defaultBlockState(),
            Blocks.WATER.defaultBlockState(),
            router,
            finalRules,
            List.of(),
            64,
            false,
            false,
            false,
            false
         )
      );
   }
}
