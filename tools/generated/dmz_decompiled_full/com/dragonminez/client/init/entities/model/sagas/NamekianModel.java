package com.dragonminez.client.init.entities.model.sagas;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PathfinderMob;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class NamekianModel<T extends PathfinderMob & GeoAnimatable> extends GeoModel<T> {
   public ResourceLocation getModelResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/enemies/namekian.geo.json");
   }

   public ResourceLocation getTextureResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/enemies/namek_trader_0.png");
   }

   public ResourceLocation getAnimationResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/enemies/namekian.animation.json");
   }

   public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
      GeoBone head = this.getAnimationProcessor().getBone("head");
      if (head != null) {
         EntityModelData entityData = (EntityModelData)animationState.getData(DataTickets.ENTITY_MODEL_DATA);
         head.setRotX(entityData.headPitch() * (float) (Math.PI / 180.0));
         head.setRotY(entityData.netHeadYaw() * (float) (Math.PI / 180.0));
      }
   }
}
