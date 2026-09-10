package com.dragonminez.common.init.armor.client.model;

import com.dragonminez.common.init.armor.DbzArmorCapeItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ArmorCapeModel extends GeoModel<DbzArmorCapeItem> {
   public ResourceLocation getModelResource(DbzArmorCapeItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/armor/armorcape.geo.json");
   }

   public ResourceLocation getTextureResource(DbzArmorCapeItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/armor/blank.png");
   }

   public ResourceLocation getAnimationResource(DbzArmorCapeItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/armorcape.animation.json");
   }
}
