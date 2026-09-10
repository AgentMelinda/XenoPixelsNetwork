package com.dragonminez.common.init.item.weapons.model;

import com.dragonminez.common.init.item.weapons.BraveSwordItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class BraveSwordModel extends GeoModel<BraveSwordItem> {
   public ResourceLocation getModelResource(BraveSwordItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/weapons/brave_sword.geo.json");
   }

   public ResourceLocation getTextureResource(BraveSwordItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/item/weapons/brave_sword.png");
   }

   public ResourceLocation getAnimationResource(BraveSwordItem animatable) {
      return null;
   }
}
