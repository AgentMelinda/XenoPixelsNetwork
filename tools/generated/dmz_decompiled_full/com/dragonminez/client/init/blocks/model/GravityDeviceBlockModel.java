package com.dragonminez.client.init.blocks.model;

import com.dragonminez.common.init.block.entity.GravityDeviceBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class GravityDeviceBlockModel extends GeoModel<GravityDeviceBlockEntity> {
   public ResourceLocation getModelResource(GravityDeviceBlockEntity blockEntity) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/block/gravitydevice.geo.json");
   }

   public ResourceLocation getTextureResource(GravityDeviceBlockEntity blockEntity) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/block/custom/gravitydevice.png");
   }

   public ResourceLocation getAnimationResource(GravityDeviceBlockEntity blockEntity) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/block/gravitydevice.animation.json");
   }
}
