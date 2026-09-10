package com.dragonminez.client.init.blocks.model;

import com.dragonminez.common.init.block.entity.EnergyCableBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class EnergyCableBlockModel extends GeoModel<EnergyCableBlockEntity> {
   public ResourceLocation getModelResource(EnergyCableBlockEntity animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/block/energy_cable.geo.json");
   }

   public ResourceLocation getTextureResource(EnergyCableBlockEntity animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/block/custom/energy_cable.png");
   }

   public ResourceLocation getAnimationResource(EnergyCableBlockEntity animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/block/energy_cable.animation.json");
   }
}
