package com.dragonminez.server.world.dimension;

import java.util.OptionalLong;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.DimensionType.MonsterSettings;

public class NamekDimension {
   public static final ResourceKey<Level> NAMEK_KEY = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dragonminez", "namek"));
   public static final ResourceKey<DimensionType> NAMEK_TYPE = ResourceKey.create(
      Registries.DIMENSION_TYPE, ResourceLocation.fromNamespaceAndPath("dragonminez", "namek")
   );

   public static void bootstrap(BootstrapContext<DimensionType> context) {
      context.register(
         NAMEK_TYPE,
         new DimensionType(
            OptionalLong.of(7500L),
            true,
            false,
            false,
            true,
            3.0,
            true,
            true,
            -64,
            384,
            384,
            BlockTags.INFINIBURN_OVERWORLD,
            CustomSpecialEffects.NAMEK_EFFECTS,
            0.0F,
            new MonsterSettings(false, false, ConstantInt.of(0), 0)
         )
      );
   }
}
