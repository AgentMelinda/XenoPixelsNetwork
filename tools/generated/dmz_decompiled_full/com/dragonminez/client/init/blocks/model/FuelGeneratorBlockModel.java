package com.dragonminez.client.init.blocks.model;

import com.dragonminez.common.init.block.custom.FuelGeneratorBlock;
import com.dragonminez.common.init.block.entity.FuelGeneratorBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.model.GeoModel;

public class FuelGeneratorBlockModel extends GeoModel<FuelGeneratorBlockEntity> {
   public ResourceLocation getModelResource(FuelGeneratorBlockEntity fuelGeneratorBlockEntity) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/block/fuel_generator.geo.json");
   }

   public ResourceLocation getTextureResource(FuelGeneratorBlockEntity fuelGeneratorBlockEntity) {
      BlockState state = fuelGeneratorBlockEntity.getBlockState();
      boolean isLit = state.hasProperty(FuelGeneratorBlock.LIT) && (Boolean)state.getValue(FuelGeneratorBlock.LIT);
      String textureState = isLit ? "_on" : "_off";
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/block/custom/fuel_generator" + textureState + ".png");
   }

   public ResourceLocation getAnimationResource(FuelGeneratorBlockEntity fuelGeneratorBlockEntity) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/block/fuel_generator.animation.json");
   }
}
