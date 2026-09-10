package com.dragonminez.server.world.biome;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.BiomeBuilder;
import net.minecraft.world.level.biome.MobSpawnSettings.Builder;

public class OtherworldBiomes {
   public static final ResourceKey<Biome> OTHERWORLD = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("dragonminez", "other_world"));

   public static void bootstrap(BootstrapContext<Biome> context) {
      context.register(OTHERWORLD, otherworld(context));
   }

   private static Biome otherworld(BootstrapContext<Biome> context) {
      Builder spawnBuilder = new Builder();
      net.minecraft.world.level.biome.BiomeGenerationSettings.Builder biomeBuilder = new net.minecraft.world.level.biome.BiomeGenerationSettings.Builder(
         context.lookup(Registries.PLACED_FEATURE), context.lookup(Registries.CONFIGURED_CARVER)
      );
      return new BiomeBuilder()
         .hasPrecipitation(false)
         .downfall(0.0F)
         .temperature(1.0F)
         .generationSettings(biomeBuilder.build())
         .mobSpawnSettings(spawnBuilder.build())
         .specialEffects(
            new net.minecraft.world.level.biome.BiomeSpecialEffects.Builder()
               .waterColor(4214155)
               .waterFogColor(4214120)
               .skyColor(12473770)
               .grassColorOverride(14480127)
               .foliageColorOverride(14480127)
               .fogColor(13532861)
               .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
               .build()
         )
         .build();
   }
}
