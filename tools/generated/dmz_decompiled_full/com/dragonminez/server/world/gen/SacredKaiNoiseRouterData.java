package com.dragonminez.server.world.gen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.DensityFunctions.HolderHolder;
import net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters;

public class SacredKaiNoiseRouterData extends NoiseRouterData {
   private static final ResourceKey<DensityFunction> SLOPED_CHEESE = ResourceKey.create(
      Registries.DENSITY_FUNCTION, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld/sloped_cheese")
   );

   public static NoiseRouter createSacredKaiRouter(HolderGetter<DensityFunction> density, HolderGetter<NoiseParameters> noise) {
      NoiseRouter base = overworld(density, noise, false, false);
      DensityFunction slopedCheese = new HolderHolder(density.getOrThrow(SLOPED_CHEESE));
      DensityFunction caveFreeFinalDensity = postProcessTerrain(slideOverworldTerrain(slopedCheese));
      return new NoiseRouter(
         base.barrierNoise(),
         base.fluidLevelFloodednessNoise(),
         base.fluidLevelSpreadNoise(),
         base.lavaNoise(),
         base.temperature(),
         base.vegetation(),
         base.continents(),
         base.erosion(),
         base.depth(),
         base.ridges(),
         base.initialDensityWithoutJaggedness(),
         caveFreeFinalDensity,
         base.veinToggle(),
         base.veinRidged(),
         base.veinGap()
      );
   }

   private static DensityFunction slideOverworldTerrain(DensityFunction df) {
      return slide(df, -64, 384, 80, 64, -0.078125, 0, 24, 0.1171875);
   }

   private static DensityFunction slide(
      DensityFunction df,
      int minY,
      int maxY,
      int topRunoffStart,
      int topRunoffEnd,
      double topTarget,
      int bottomRunoffStart,
      int bottomRunoffEnd,
      double bottomTarget
   ) {
      DensityFunction top = DensityFunctions.yClampedGradient(minY + maxY - topRunoffStart, minY + maxY - topRunoffEnd, 1.0, 0.0);
      DensityFunction withTop = DensityFunctions.lerp(top, topTarget, df);
      DensityFunction bottom = DensityFunctions.yClampedGradient(minY + bottomRunoffStart, minY + bottomRunoffEnd, 0.0, 1.0);
      return DensityFunctions.lerp(bottom, bottomTarget, withTop);
   }

   private static DensityFunction postProcessTerrain(DensityFunction df) {
      DensityFunction blended = DensityFunctions.blendDensity(df);
      return DensityFunctions.mul(DensityFunctions.interpolated(blended), DensityFunctions.constant(0.64)).squeeze();
   }
}
