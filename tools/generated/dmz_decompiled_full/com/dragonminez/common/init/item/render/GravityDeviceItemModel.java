package com.dragonminez.common.init.item.render;

import com.dragonminez.common.init.item.GravityDeviceItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class GravityDeviceItemModel extends GeoModel<GravityDeviceItem> {
   public ResourceLocation getModelResource(GravityDeviceItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/block/gravitydevice.geo.json");
   }

   public ResourceLocation getTextureResource(GravityDeviceItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/block/custom/gravitydevice.png");
   }

   public ResourceLocation getAnimationResource(GravityDeviceItem animatable) {
      return null;
   }
}
