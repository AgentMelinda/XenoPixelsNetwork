package com.dragonminez.server.world.feature;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.NoiseThresholdCountPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.RarityFilter;

public class OverworldPlacedFeatures {
   public static final ResourceKey<PlacedFeature> STONE_SPIKE_PLACED_KEY = createKey("stone_spike_placed");
   public static final ResourceKey<PlacedFeature> ROCKY_PEAK_PLACED_KEY = createKey("rocky_peak_placed");
   public static final ResourceKey<PlacedFeature> KARST_PILLAR_PLACED_KEY = createKey("karst_pillar_placed");
   public static final ResourceKey<PlacedFeature> ROCKY_CLIFF_PLACED_KEY = createKey("rocky_cliff_placed");

   public static void bootstrap(BootstrapContext<PlacedFeature> context) {
      HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);
      Holder<ConfiguredFeature<?, ?>> stoneSpikeHolder = configuredFeatures.getOrThrow(OverworldConfiguredFeatures.STONE_SPIKE_KEY);
      Holder<ConfiguredFeature<?, ?>> rockyPeakHolder = configuredFeatures.getOrThrow(OverworldConfiguredFeatures.ROCKY_PEAK_KEY);
      Holder<ConfiguredFeature<?, ?>> karstPillarHolder = configuredFeatures.getOrThrow(OverworldConfiguredFeatures.KARST_PILLAR_KEY);
      Holder<ConfiguredFeature<?, ?>> rockyCliffHolder = configuredFeatures.getOrThrow(OverworldConfiguredFeatures.ROCKY_CLIFF_KEY);
      context.register(
         STONE_SPIKE_PLACED_KEY,
         new PlacedFeature(
            stoneSpikeHolder,
            ImmutableList.builder()
               .add(NoiseThresholdCountPlacement.of(-0.8F, 15, 5))
               .add(RarityFilter.onAverageOnceEvery(80))
               .add(InSquarePlacement.spread())
               .add(HeightmapPlacement.onHeightmap(Types.WORLD_SURFACE_WG))
               .add(BiomeFilter.biome())
               .build()
         )
      );
      context.register(
         ROCKY_PEAK_PLACED_KEY,
         new PlacedFeature(
            rockyPeakHolder,
            ImmutableList.builder()
               .add(NoiseThresholdCountPlacement.of(-0.8F, 4, 2))
               .add(RarityFilter.onAverageOnceEvery(32))
               .add(InSquarePlacement.spread())
               .add(HeightmapPlacement.onHeightmap(Types.WORLD_SURFACE_WG))
               .add(BiomeFilter.biome())
               .build()
         )
      );
      context.register(
         KARST_PILLAR_PLACED_KEY,
         new PlacedFeature(
            karstPillarHolder,
            ImmutableList.builder()
               .add(NoiseThresholdCountPlacement.of(-0.8F, 6, 2))
               .add(RarityFilter.onAverageOnceEvery(28))
               .add(InSquarePlacement.spread())
               .add(HeightmapPlacement.onHeightmap(Types.WORLD_SURFACE_WG))
               .add(BiomeFilter.biome())
               .build()
         )
      );
      context.register(
         ROCKY_CLIFF_PLACED_KEY,
         new PlacedFeature(
            rockyCliffHolder,
            ImmutableList.builder()
               .add(NoiseThresholdCountPlacement.of(-0.8F, 4, 2))
               .add(RarityFilter.onAverageOnceEvery(18))
               .add(InSquarePlacement.spread())
               .add(HeightmapPlacement.onHeightmap(Types.WORLD_SURFACE_WG))
               .add(BiomeFilter.biome())
               .build()
         )
      );
   }

   private static ResourceKey<PlacedFeature> createKey(String name) {
      return ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath("dragonminez", name));
   }
}
