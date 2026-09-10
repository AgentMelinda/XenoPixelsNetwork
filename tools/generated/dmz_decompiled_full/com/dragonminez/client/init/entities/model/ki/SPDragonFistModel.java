package com.dragonminez.client.init.entities.model.ki;

import com.dragonminez.common.init.entities.ki.SPDragonFistEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class SPDragonFistModel<T extends SPDragonFistEntity> extends GeoModel<T> {
   public ResourceLocation getModelResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/skills/sp_dragonfist.geo.json");
   }

   public ResourceLocation getTextureResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/skills/sp_dragonfist.png");
   }

   public ResourceLocation getAnimationResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/skills/sp_dragonfist.animation.json");
   }

   public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
      super.setCustomAnimations(animatable, instanceId, animationState);
      GeoBone rootBone = this.getAnimationProcessor().getBone("root");
      if (rootBone != null) {
         float lockedYaw = animatable.getLockedYaw();
         float lockedPitch = animatable.getLockedPitch();
         rootBone.setRotX(-lockedPitch * (float) (Math.PI / 180.0));
         rootBone.setRotY(-lockedYaw * (float) (Math.PI / 180.0));
      }
   }
}
