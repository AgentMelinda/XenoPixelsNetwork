package com.dragonminez.server.world.biome;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.BiomeBuilder;
import net.minecraft.world.level.biome.MobSpawnSettings.Builder;

public class HTCBiomes {
   public static final ResourceKey<Biome> TIME_CHAMBER = ResourceKey.create(
      Registries.BIOME, ResourceLocation.fromNamespaceAndPath("dragonminez", "hyperbolic_time_chamber")
   );

   public static void bootstrap(BootstrapContext<Biome> context) {
      context.register(TIME_CHAMBER, timeChamber(context));
   }

   private static Biome timeChamber(BootstrapContext<Biome> context) {
      Builder spawnBuilder = new Builder();
      net.minecraft.world.level.biome.BiomeGenerationSettings.Builder biomeBuilder = new net.minecraft.world.level.biome.BiomeGenerationSettings.Builder(
         context.lookup(Registries.PLACED_FEATURE), context.lookup(Registries.CONFIGURED_CARVER)
      );
      return new BiomeBuilder()
         .hasPrecipitation(false)
         .downfall(0.0F)
         .temperature(0.7F)
         .generationSettings(biomeBuilder.build())
         .mobSpawnSettings(spawnBuilder.build())
         .specialEffects(
            new net.minecraft.world.level.biome.BiomeSpecialEffects.Builder()
               .waterColor(14480127)
               .waterFogColor(14480127)
               .skyColor(16252159)
               .grassColorOverride(14480127)
               .foliageColorOverride(14480127)
               .fogColor(14480127)
               .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
               .build()
         )
         .build();
   }
}
