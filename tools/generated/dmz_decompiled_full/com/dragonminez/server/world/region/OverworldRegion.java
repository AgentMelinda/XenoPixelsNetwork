package com.dragonminez.server.world.region;

import com.dragonminez.server.world.biome.OverworldBiomes;
import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.Climate.Parameter;
import net.minecraft.world.level.biome.Climate.ParameterPoint;
import terrablender.api.Region;
import terrablender.api.RegionType;

public class OverworldRegion extends Region {
   private static final Parameter TEMPERATURE = Parameter.span(0.2F, 1.0F);
   private static final Parameter HUMIDITY = Parameter.span(-1.0F, -0.1F);
   private static final Parameter CONTINENTALNESS = Parameter.span(-0.11F, 1.0F);
   private static final Parameter[] EROSIONS = new Parameter[]{
      Parameter.span(-1.0F, -0.78F), Parameter.span(-0.78F, -0.375F), Parameter.span(-0.375F, -0.2225F)
   };
   private static final Parameter WEIRDNESS = Parameter.span(-1.0F, 1.0F);
   private static final Parameter DEPTH_SURFACE = Parameter.point(0.0F);

   public OverworldRegion(int weight) {
      super(ResourceLocation.fromNamespaceAndPath("dragonminez", "overworld_region"), RegionType.OVERWORLD, weight);
   }

   public void addBiomes(Registry<Biome> registry, Consumer<Pair<ParameterPoint, ResourceKey<Biome>>> mapper) {
      this.addModifiedVanillaOverworldBiomes(mapper, builder -> {
      });

      for (Parameter erosion : EROSIONS) {
         this.addBiome(mapper, Climate.parameters(TEMPERATURE, HUMIDITY, CONTINENTALNESS, erosion, DEPTH_SURFACE, WEIRDNESS, 0.0F), OverworldBiomes.ROCKY);
      }
   }
}
