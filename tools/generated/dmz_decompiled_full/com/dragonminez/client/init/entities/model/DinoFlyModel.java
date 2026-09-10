package com.dragonminez.client.init.entities.model;

import com.dragonminez.common.init.entities.animal.DinoFlyEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class DinoFlyModel extends GeoModel<DinoFlyEntity> {
   public ResourceLocation getModelResource(DinoFlyEntity animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/animal/dino3.geo.json");
   }

   public ResourceLocation getTextureResource(DinoFlyEntity animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/animal/dino3.png");
   }

   public ResourceLocation getAnimationResource(DinoFlyEntity animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/animal/dino3.animation.json");
   }

   public void setCustomAnimations(DinoFlyEntity animatable, long instanceId, AnimationState<DinoFlyEntity> animationState) {
      GeoBone head = this.getAnimationProcessor().getBone("head");
      if (head != null) {
         EntityModelData entityData = (EntityModelData)animationState.getData(DataTickets.ENTITY_MODEL_DATA);
         head.setRotX(entityData.headPitch() * (float) (Math.PI / 180.0));
         head.setRotY(entityData.netHeadYaw() * (float) (Math.PI / 180.0));
      }
   }
}
