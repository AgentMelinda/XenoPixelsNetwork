package com.dragonminez.server.world.biome;

import com.dragonminez.common.init.MainEntities;
import com.dragonminez.server.world.feature.SacredKaiPlacedFeatures;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.BiomeBuilder;
import net.minecraft.world.level.biome.BiomeGenerationSettings.Builder;
import net.minecraft.world.level.levelgen.GenerationStep.Carving;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class SacredKaiBiomes {
   public static final ResourceKey<Biome> SACREDKAI_PLAINS = ResourceKey.create(
      Registries.BIOME, ResourceLocation.fromNamespaceAndPath("dragonminez", "sacredkai_plains")
   );
   public static final ResourceKey<Biome> SACREDKAI_HILLS = ResourceKey.create(
      Registries.BIOME, ResourceLocation.fromNamespaceAndPath("dragonminez", "sacredkai_hills")
   );
   public static final ResourceKey<Biome> SACREDKAI_RIVERS = ResourceKey.create(
      Registries.BIOME, ResourceLocation.fromNamespaceAndPath("dragonminez", "sacredkai_rivers")
   );

   public static void bootstrap(BootstrapContext<Biome> context) {
      context.register(SACREDKAI_PLAINS, plains(context));
      context.register(SACREDKAI_HILLS, hills(context));
      context.register(SACREDKAI_RIVERS, rivers(context));
   }

   private static void addNormalCaves(Builder builder, BootstrapContext<Biome> context) {
      HolderGetter<ConfiguredWorldCarver<?>> carvers = context.lookup(Registries.CONFIGURED_CARVER);
      builder.addCarver(Carving.AIR, carvers.getOrThrow(Carvers.CAVE));
      builder.addCarver(Carving.AIR, carvers.getOrThrow(Carvers.CAVE_EXTRA_UNDERGROUND));
      builder.addCarver(Carving.AIR, carvers.getOrThrow(Carvers.CANYON));
   }

   private static Biome plains(BootstrapContext<Biome> context) {
      HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
      HolderGetter<ConfiguredWorldCarver<?>> carvers = context.lookup(Registries.CONFIGURED_CARVER);
      net.minecraft.world.level.biome.MobSpawnSettings.Builder spawnBuilder = new net.minecraft.world.level.biome.MobSpawnSettings.Builder();
      addMonsterCharges(spawnBuilder);
      Builder biomeBuilder = new Builder(placedFeatures, carvers);
      addNormalCaves(biomeBuilder, context);
      biomeBuilder.addFeature(Decoration.LAKES, SacredKaiPlacedFeatures.WATER_LAKE_PLACED);
      biomeBuilder.addFeature(Decoration.LOCAL_MODIFICATIONS, SacredKaiPlacedFeatures.SAND_PATCH_PLACED);
      biomeBuilder.addFeature(Decoration.LOCAL_MODIFICATIONS, SacredKaiPlacedFeatures.ROCK_CLUSTER_PLACED);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, SacredKaiPlacedFeatures.OAK_TREE_PLACED);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, SacredKaiPlacedFeatures.GRASS_PATCH_PLACED);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, SacredKaiPlacedFeatures.FLOWERS_PLACED);
      return biome(spawnBuilder, biomeBuilder);
   }

   private static Biome hills(BootstrapContext<Biome> context) {
      HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
      HolderGetter<ConfiguredWorldCarver<?>> carvers = context.lookup(Registries.CONFIGURED_CARVER);
      net.minecraft.world.level.biome.MobSpawnSettings.Builder spawnBuilder = new net.minecraft.world.level.biome.MobSpawnSettings.Builder();
      addMonsterCharges(spawnBuilder);
      Builder biomeBuilder = new Builder(placedFeatures, carvers);
      addNormalCaves(biomeBuilder, context);
      biomeBuilder.addFeature(Decoration.RAW_GENERATION, SacredKaiPlacedFeatures.GRASSY_PEAK_PLACED);
      biomeBuilder.addFeature(Decoration.RAW_GENERATION, SacredKaiPlacedFeatures.GRASSY_CLIFF_PLACED);
      biomeBuilder.addFeature(Decoration.RAW_GENERATION, SacredKaiPlacedFeatures.KARST_PILLAR_PLACED);
      biomeBuilder.addFeature(Decoration.RAW_GENERATION, SacredKaiPlacedFeatures.STONE_SPIKE_PLACED);
      biomeBuilder.addFeature(Decoration.LOCAL_MODIFICATIONS, SacredKaiPlacedFeatures.SAND_PATCH_PLACED);
      biomeBuilder.addFeature(Decoration.LOCAL_MODIFICATIONS, SacredKaiPlacedFeatures.ROCK_CLUSTER_SPARSE_PLACED);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, SacredKaiPlacedFeatures.OAK_TREE_PLACED);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, SacredKaiPlacedFeatures.GRASS_PATCH_PLACED);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, SacredKaiPlacedFeatures.FLOWERS_PLACED);
      return biome(spawnBuilder, biomeBuilder);
   }

   private static Biome rivers(BootstrapContext<Biome> context) {
      HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
      HolderGetter<ConfiguredWorldCarver<?>> carvers = context.lookup(Registries.CONFIGURED_CARVER);
      net.minecraft.world.level.biome.MobSpawnSettings.Builder spawnBuilder = new net.minecraft.world.level.biome.MobSpawnSettings.Builder();
      addMonsterCharges(spawnBuilder);
      Builder biomeBuilder = new Builder(placedFeatures, carvers);
      addNormalCaves(biomeBuilder, context);
      biomeBuilder.addFeature(Decoration.LOCAL_MODIFICATIONS, SacredKaiPlacedFeatures.SAND_PATCH_PLACED);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, SacredKaiPlacedFeatures.GRASS_PATCH_PLACED);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, SacredKaiPlacedFeatures.FLOWERS_PLACED);
      return biome(spawnBuilder, biomeBuilder);
   }

   private static void addMonsterCharges(net.minecraft.world.level.biome.MobSpawnSettings.Builder builder) {
      builder.addMobCharge((EntityType)MainEntities.MINI_BUU.get(), 1.0, 0.12);
   }

   private static Biome biome(net.minecraft.world.level.biome.MobSpawnSettings.Builder spawnBuilder, Builder biomeBuilder) {
      return new BiomeBuilder()
         .hasPrecipitation(false)
         .temperature(2.0F)
         .downfall(0.0F)
         .specialEffects(
            new net.minecraft.world.level.biome.BiomeSpecialEffects.Builder()
               .waterColor(10257128)
               .waterFogColor(6968736)
               .skyColor(10120163)
               .fogColor(14133734)
               .grassColorOverride(8642619)
               .foliageColorOverride(8115766)
               .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
               .build()
         )
         .mobSpawnSettings(spawnBuilder.build())
         .generationSettings(biomeBuilder.build())
         .build();
   }
}
