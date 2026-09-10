package com.dragonminez.client.init.entities.model;

import com.dragonminez.common.init.entities.FlyingNimbusEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class FlyingNimbusModel<T extends FlyingNimbusEntity> extends GeoModel<T> {
   public ResourceLocation getModelResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/kinton.geo.json");
   }

   public ResourceLocation getTextureResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/kinton.png");
   }

   public ResourceLocation getAnimationResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/kinton.animation.json");
   }
}
