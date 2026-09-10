package com.dragonminez.common.init.item.weapons.model;

import com.dragonminez.common.init.item.weapons.DimensionalSwordItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class DimensionalSwordModel extends GeoModel<DimensionalSwordItem> {
   public ResourceLocation getModelResource(DimensionalSwordItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/weapons/dimensional_sword.geo.json");
   }

   public ResourceLocation getTextureResource(DimensionalSwordItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/item/weapons/dimensional_sword.png");
   }

   public ResourceLocation getAnimationResource(DimensionalSwordItem animatable) {
      return null;
   }
}
