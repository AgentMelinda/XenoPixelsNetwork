package com.dragonminez.server.world.biome;

import com.dragonminez.server.world.feature.OverworldPlacedFeatures;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BiomeDefaultFeatures;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.BiomeBuilder;
import net.minecraft.world.level.biome.MobSpawnSettings.Builder;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import net.minecraft.world.level.levelgen.GenerationStep.Carving;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;

public class OverworldBiomes {
   public static final ResourceKey<Biome> ROCKY = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("dragonminez", "rocky"));

   public static void bootstrap(BootstrapContext<Biome> context) {
      context.register(ROCKY, rockyBiome(context));
   }

   public static Biome rockyBiome(BootstrapContext<Biome> context) {
      Builder spawnBuilder = new Builder();
      BiomeDefaultFeatures.commonSpawns(spawnBuilder);
      spawnBuilder.addSpawn(MobCategory.MONSTER, new SpawnerData(EntityType.SKELETON, 100, 4, 4));
      net.minecraft.world.level.biome.BiomeGenerationSettings.Builder biomeBuilder = new net.minecraft.world.level.biome.BiomeGenerationSettings.Builder(
         context.lookup(Registries.PLACED_FEATURE), context.lookup(Registries.CONFIGURED_CARVER)
      );
      BiomeDefaultFeatures.addDefaultCarversAndLakes(biomeBuilder);
      biomeBuilder.addCarver(Carving.AIR, context.lookup(Registries.CONFIGURED_CARVER).getOrThrow(Carvers.CANYON));
      biomeBuilder.addCarver(Carving.AIR, context.lookup(Registries.CONFIGURED_CARVER).getOrThrow(Carvers.CANYON));
      BiomeDefaultFeatures.addDefaultCrystalFormations(biomeBuilder);
      BiomeDefaultFeatures.addDefaultOres(biomeBuilder);
      BiomeDefaultFeatures.addDefaultSoftDisks(biomeBuilder);
      BiomeDefaultFeatures.addDesertVegetation(biomeBuilder);
      biomeBuilder.addFeature(Decoration.RAW_GENERATION, context.lookup(Registries.PLACED_FEATURE).getOrThrow(OverworldPlacedFeatures.STONE_SPIKE_PLACED_KEY));
      biomeBuilder.addFeature(Decoration.RAW_GENERATION, context.lookup(Registries.PLACED_FEATURE).getOrThrow(OverworldPlacedFeatures.ROCKY_PEAK_PLACED_KEY));
      biomeBuilder.addFeature(Decoration.RAW_GENERATION, context.lookup(Registries.PLACED_FEATURE).getOrThrow(OverworldPlacedFeatures.KARST_PILLAR_PLACED_KEY));
      biomeBuilder.addFeature(Decoration.RAW_GENERATION, context.lookup(Registries.PLACED_FEATURE).getOrThrow(OverworldPlacedFeatures.ROCKY_CLIFF_PLACED_KEY));
      net.minecraft.world.level.biome.BiomeSpecialEffects.Builder effectsBuilder = new net.minecraft.world.level.biome.BiomeSpecialEffects.Builder()
         .waterColor(4159204)
         .waterFogColor(329011)
         .skyColor(7842047)
         .fogColor(12638463)
         .grassColorOverride(9733734)
         .foliageColorOverride(9733734);
      return new BiomeBuilder()
         .hasPrecipitation(false)
         .temperature(2.0F)
         .downfall(0.0F)
         .specialEffects(effectsBuilder.build())
         .mobSpawnSettings(spawnBuilder.build())
         .generationSettings(biomeBuilder.build())
         .build();
   }
}
