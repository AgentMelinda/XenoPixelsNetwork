package com.dragonminez.common.init.item.weapons.model;

import com.dragonminez.common.init.item.weapons.ZSwordItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ZSwordModel extends GeoModel<ZSwordItem> {
   public ResourceLocation getModelResource(ZSwordItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/weapons/z_sword.geo.json");
   }

   public ResourceLocation getTextureResource(ZSwordItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/item/weapons/z_sword.png");
   }

   public ResourceLocation getAnimationResource(ZSwordItem animatable) {
      return null;
   }
}
