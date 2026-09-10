package com.dragonminez.server.world.biome;

import com.dragonminez.common.init.MainEntities;
import com.dragonminez.server.world.feature.NamekPlacedFeatures;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.BiomeBuilder;
import net.minecraft.world.level.biome.MobSpawnSettings.Builder;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class NamekBiomes {
   public static final ResourceKey<Biome> AJISSA_PLAINS = ResourceKey.create(
      Registries.BIOME, ResourceLocation.fromNamespaceAndPath("dragonminez", "ajissa_plains")
   );
   public static final ResourceKey<Biome> SACRED_LAND = ResourceKey.create(
      Registries.BIOME, ResourceLocation.fromNamespaceAndPath("dragonminez", "sacred_land")
   );
   public static final ResourceKey<Biome> NAMEKIAN_RIVERS = ResourceKey.create(
      Registries.BIOME, ResourceLocation.fromNamespaceAndPath("dragonminez", "namekian_rivers")
   );

   public static void bootstrap(BootstrapContext<Biome> context) {
      context.register(AJISSA_PLAINS, ajissaPlains(context));
      context.register(SACRED_LAND, sacredLand(context));
      context.register(NAMEKIAN_RIVERS, namekRiver(context));
   }

   private static void addWaterMobs(Builder builder) {
      builder.addSpawn(MobCategory.WATER_CREATURE, new SpawnerData(EntityType.SQUID, 10, 1, 4));
      builder.addSpawn(MobCategory.WATER_AMBIENT, new SpawnerData(EntityType.SALMON, 15, 1, 5));
      builder.addSpawn(MobCategory.WATER_AMBIENT, new SpawnerData(EntityType.COD, 10, 1, 5));
      builder.addSpawn(MobCategory.WATER_AMBIENT, new SpawnerData(EntityType.PUFFERFISH, 5, 1, 3));
      builder.addSpawn(MobCategory.WATER_AMBIENT, new SpawnerData(EntityType.TROPICAL_FISH, 8, 1, 4));
      builder.addSpawn(MobCategory.AXOLOTLS, new SpawnerData(EntityType.AXOLOTL, 5, 1, 2));
      builder.addMobCharge(EntityType.SQUID, 0.7, 0.15);
      builder.addMobCharge(EntityType.AXOLOTL, 1.0, 0.12);
   }

   private static Biome ajissaPlains(BootstrapContext<Biome> context) {
      HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
      HolderGetter<ConfiguredWorldCarver<?>> carvers = context.lookup(Registries.CONFIGURED_CARVER);
      Builder spawnBuilder = new Builder();
      net.minecraft.world.level.biome.BiomeGenerationSettings.Builder biomeBuilder = new net.minecraft.world.level.biome.BiomeGenerationSettings.Builder(
         placedFeatures, carvers
      );
      addWaterMobs(spawnBuilder);
      spawnBuilder.addMobCharge((EntityType)MainEntities.SAGA_FRIEZA_SOLDIER.get(), 0.9, 0.1);
      spawnBuilder.addMobCharge((EntityType)MainEntities.SAGA_FRIEZA_SOLDIER2.get(), 0.9, 0.1);
      spawnBuilder.addMobCharge((EntityType)MainEntities.SAGA_FRIEZA_SOLDIER3.get(), 0.9, 0.1);
      biomeBuilder.addFeature(Decoration.LAKES, NamekPlacedFeatures.NAMEK_LAKE_LAVA_PLACED);
      biomeBuilder.addFeature(Decoration.FLUID_SPRINGS, NamekPlacedFeatures.NAMEK_SPRING_LAVA_PLACED);
      biomeBuilder.addFeature(Decoration.FLUID_SPRINGS, NamekPlacedFeatures.NAMEK_SPRING_WATER_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_COAL_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_COPPER_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_IRON_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_GOLD_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_REDSTONE_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_EMERALD_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_LAPIS_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_DIAMOND_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_KIKONO_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_GETE_DEBRIS_LARGE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_GETE_DEBRIS_SMALL_PLACED);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, NamekPlacedFeatures.NAMEK_PATCH_GRASS_PLAIN);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, NamekPlacedFeatures.NAMEK_PLAINS_FLOWERS);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, NamekPlacedFeatures.AJISSA_TREE_PLACED);
      return biome(spawnBuilder, biomeBuilder, 6530427);
   }

   private static Biome sacredLand(BootstrapContext<Biome> context) {
      HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
      HolderGetter<ConfiguredWorldCarver<?>> carvers = context.lookup(Registries.CONFIGURED_CARVER);
      Builder spawnBuilder = new Builder();
      net.minecraft.world.level.biome.BiomeGenerationSettings.Builder biomeBuilder = new net.minecraft.world.level.biome.BiomeGenerationSettings.Builder(
         placedFeatures, carvers
      );
      addWaterMobs(spawnBuilder);
      spawnBuilder.addMobCharge((EntityType)MainEntities.SAGA_FRIEZA_SOLDIER.get(), 0.9, 0.1);
      spawnBuilder.addMobCharge((EntityType)MainEntities.SAGA_FRIEZA_SOLDIER2.get(), 0.9, 0.1);
      spawnBuilder.addMobCharge((EntityType)MainEntities.SAGA_FRIEZA_SOLDIER3.get(), 0.9, 0.1);
      biomeBuilder.addFeature(Decoration.LAKES, NamekPlacedFeatures.NAMEK_LAKE_LAVA_PLACED);
      biomeBuilder.addFeature(Decoration.FLUID_SPRINGS, NamekPlacedFeatures.NAMEK_SPRING_LAVA_PLACED);
      biomeBuilder.addFeature(Decoration.FLUID_SPRINGS, NamekPlacedFeatures.NAMEK_SPRING_WATER_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_COAL_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_COPPER_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_IRON_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_GOLD_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_REDSTONE_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_EMERALD_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_LAPIS_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_DIAMOND_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_KIKONO_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_GETE_DEBRIS_LARGE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_GETE_DEBRIS_SMALL_PLACED);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, NamekPlacedFeatures.NAMEK_PATCH_SACRED_GRASS_PLAIN);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, NamekPlacedFeatures.NAMEK_SACRED_FLOWERS);
      biomeBuilder.addFeature(Decoration.VEGETAL_DECORATION, NamekPlacedFeatures.SACRED_TREE_PLACED);
      return biome(spawnBuilder, biomeBuilder, 6530427);
   }

   private static Biome namekRiver(BootstrapContext<Biome> context) {
      HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
      HolderGetter<ConfiguredWorldCarver<?>> carvers = context.lookup(Registries.CONFIGURED_CARVER);
      Builder spawnBuilder = new Builder();
      net.minecraft.world.level.biome.BiomeGenerationSettings.Builder biomeBuilder = new net.minecraft.world.level.biome.BiomeGenerationSettings.Builder(
         placedFeatures, carvers
      );
      addWaterMobs(spawnBuilder);
      biomeBuilder.addFeature(Decoration.FLUID_SPRINGS, NamekPlacedFeatures.NAMEK_SPRING_WATER_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_COAL_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_COPPER_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_IRON_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_GOLD_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_REDSTONE_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_EMERALD_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_LAPIS_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_DIAMOND_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_KIKONO_ORE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_GETE_DEBRIS_LARGE_PLACED);
      biomeBuilder.addFeature(Decoration.UNDERGROUND_ORES, NamekPlacedFeatures.NAMEK_GETE_DEBRIS_SMALL_PLACED);
      return biome(spawnBuilder, biomeBuilder, 6530427);
   }

   private static Biome biome(Builder spawnBuilder, net.minecraft.world.level.biome.BiomeGenerationSettings.Builder biomeBuilder, int skyColor) {
      return new BiomeBuilder()
         .hasPrecipitation(false)
         .temperature(2.0F)
         .downfall(0.0F)
         .specialEffects(
            new net.minecraft.world.level.biome.BiomeSpecialEffects.Builder()
               .waterColor(9956722)
               .waterFogColor(329011)
               .skyColor(skyColor)
               .fogColor(12638463)
               .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
               .build()
         )
         .mobSpawnSettings(spawnBuilder.build())
         .generationSettings(biomeBuilder.build())
         .build();
   }
}
