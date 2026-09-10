package com.dragonminez.server.world.gen;

import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.server.world.biome.NamekBiomes;
import com.dragonminez.server.world.dimension.NamekDimension;
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
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters;

public class NamekGeneration {
   public static final ResourceKey<LevelStem> NAMEK_STEM = ResourceKey.create(
      Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath("dragonminez", "namek")
   );
   public static final ResourceKey<NoiseGeneratorSettings> NAMEK_NOISE_SETTINGS = ResourceKey.create(
      Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("dragonminez", "namek")
   );

   public static void bootstrap(BootstrapContext<LevelStem> context) {
      HolderGetter<Biome> biomeRegistry = context.lookup(Registries.BIOME);
      HolderGetter<DimensionType> dimTypes = context.lookup(Registries.DIMENSION_TYPE);
      HolderGetter<NoiseGeneratorSettings> noiseSettings = context.lookup(Registries.NOISE_SETTINGS);
      MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.createFromList(createNamekBiomeParameters(biomeRegistry));
      ChunkGenerator chunkGenerator = new NoiseBasedChunkGenerator(biomeSource, noiseSettings.getOrThrow(NAMEK_NOISE_SETTINGS));
      context.register(NAMEK_STEM, new LevelStem(dimTypes.getOrThrow(NamekDimension.NAMEK_TYPE), chunkGenerator));
   }

   private static ParameterList<Holder<Biome>> createNamekBiomeParameters(HolderGetter<Biome> biomeRegistry) {
      return new ParameterList(
         List.of(
            Pair.of(Climate.parameters(0.0F, 0.0F, 0.1F, 0.0F, 0.0F, 0.0F, 0.0F), biomeRegistry.getOrThrow(NamekBiomes.AJISSA_PLAINS)),
            Pair.of(Climate.parameters(0.0F, 0.0F, 0.6F, 0.0F, 0.0F, 0.0F, 0.0F), biomeRegistry.getOrThrow(NamekBiomes.SACRED_LAND)),
            Pair.of(Climate.parameters(0.0F, 0.0F, -0.45F, 0.0F, 0.0F, 0.0F, 0.0F), biomeRegistry.getOrThrow(NamekBiomes.NAMEKIAN_RIVERS))
         )
      );
   }

   public static void bootstrapNoise(BootstrapContext<NoiseGeneratorSettings> context) {
      RuleSource bedrockRule = SurfaceRules.ifTrue(
         SurfaceRules.verticalGradient("bedrock_floor", VerticalAnchor.aboveBottom(0), VerticalAnchor.aboveBottom(5)),
         SurfaceRules.state(Blocks.BEDROCK.defaultBlockState())
      );
      RuleSource namekSurfaceRule = SurfaceRules.sequence(
         new RuleSource[]{
            SurfaceRules.ifTrue(
               SurfaceRules.isBiome(new ResourceKey[]{NamekBiomes.AJISSA_PLAINS, NamekBiomes.NAMEKIAN_RIVERS}),
               SurfaceRules.sequence(
                  new RuleSource[]{
                     SurfaceRules.ifTrue(
                        SurfaceRules.abovePreliminarySurface(),
                        SurfaceRules.ifTrue(
                           SurfaceRules.stoneDepthCheck(0, false, 0, CaveSurface.FLOOR),
                           SurfaceRules.sequence(
                              new RuleSource[]{
                                 SurfaceRules.ifTrue(
                                    SurfaceRules.waterBlockCheck(-1, 0), SurfaceRules.state(((Block)MainBlocks.NAMEK_GRASS_BLOCK.get()).defaultBlockState())
                                 ),
                                 SurfaceRules.state(((Block)MainBlocks.NAMEK_DIRT.get()).defaultBlockState())
                              }
                           )
                        )
                     ),
                     SurfaceRules.ifTrue(
                        SurfaceRules.yStartCheck(VerticalAnchor.absolute(50), 4),
                        SurfaceRules.sequence(
                           new RuleSource[]{
                              SurfaceRules.ifTrue(
                                 SurfaceRules.stoneDepthCheck(0, true, 5, CaveSurface.FLOOR),
                                 SurfaceRules.state(((Block)MainBlocks.NAMEK_DIRT.get()).defaultBlockState())
                              )
                           }
                        )
                     )
                  }
               )
            )
         }
      );
      RuleSource sacredLandSurfaceRule = SurfaceRules.sequence(
         new RuleSource[]{
            SurfaceRules.ifTrue(
               SurfaceRules.isBiome(new ResourceKey[]{NamekBiomes.SACRED_LAND}),
               SurfaceRules.sequence(
                  new RuleSource[]{
                     SurfaceRules.ifTrue(
                        SurfaceRules.abovePreliminarySurface(),
                        SurfaceRules.ifTrue(
                           SurfaceRules.stoneDepthCheck(0, false, 0, CaveSurface.FLOOR),
                           SurfaceRules.sequence(
                              new RuleSource[]{
                                 SurfaceRules.ifTrue(
                                    SurfaceRules.waterBlockCheck(0, 0),
                                    SurfaceRules.state(((Block)MainBlocks.NAMEK_SACRED_GRASS_BLOCK.get()).defaultBlockState())
                                 ),
                                 SurfaceRules.state(((Block)MainBlocks.NAMEK_DIRT.get()).defaultBlockState())
                              }
                           )
                        )
                     ),
                     SurfaceRules.ifTrue(
                        SurfaceRules.yStartCheck(VerticalAnchor.absolute(50), 4),
                        SurfaceRules.sequence(
                           new RuleSource[]{
                              SurfaceRules.ifTrue(
                                 SurfaceRules.stoneDepthCheck(0, true, 5, CaveSurface.FLOOR),
                                 SurfaceRules.state(((Block)MainBlocks.NAMEK_DIRT.get()).defaultBlockState())
                              )
                           }
                        )
                     )
                  }
               )
            )
         }
      );
      RuleSource deepslateRule = SurfaceRules.ifTrue(
         SurfaceRules.verticalGradient("deepslate", VerticalAnchor.absolute(0), VerticalAnchor.absolute(8)),
         SurfaceRules.state(((Block)MainBlocks.NAMEK_DEEPSLATE.get()).defaultBlockState())
      );
      RuleSource finalRules = SurfaceRules.sequence(
         new RuleSource[]{
            bedrockRule, namekSurfaceRule, sacredLandSurfaceRule, deepslateRule, SurfaceRules.state(((Block)MainBlocks.NAMEK_STONE.get()).defaultBlockState())
         }
      );
      NoiseSettings noiseSettings = NoiseSettings.create(-64, 384, 1, 2);
      HolderGetter<DensityFunction> densityFunctions = context.lookup(Registries.DENSITY_FUNCTION);
      HolderGetter<NoiseParameters> noiseParams = context.lookup(Registries.NOISE);
      NoiseRouter router = NamekNoiseRouterData.createNamekRouter(densityFunctions, noiseParams);
      context.register(
         NAMEK_NOISE_SETTINGS,
         new NoiseGeneratorSettings(
            noiseSettings,
            ((Block)MainBlocks.NAMEK_STONE.get()).defaultBlockState(),
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
