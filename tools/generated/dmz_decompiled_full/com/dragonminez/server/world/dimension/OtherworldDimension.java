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

public class OtherworldDimension {
   public static final ResourceKey<Level> OTHERWORLD_KEY = ResourceKey.create(
      Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dragonminez", "otherworld")
   );
   public static final ResourceKey<DimensionType> OTHERWORLD_TYPE = ResourceKey.create(
      Registries.DIMENSION_TYPE, ResourceLocation.fromNamespaceAndPath("dragonminez", "otherworld")
   );

   public static void bootstrap(BootstrapContext<DimensionType> context) {
      context.register(
         OTHERWORLD_TYPE,
         new DimensionType(
            OptionalLong.of(6000L),
            true,
            false,
            false,
            false,
            10.0,
            false,
            false,
            0,
            320,
            320,
            BlockTags.INFINIBURN_OVERWORLD,
            CustomSpecialEffects.OTHERWORLD_EFFECTS,
            0.0F,
            new MonsterSettings(false, false, ConstantInt.of(0), 0)
         )
      );
   }
}
