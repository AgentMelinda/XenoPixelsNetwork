package com.dragonminez.client.init.entities.model;

import com.dragonminez.common.dragonball.DragonAssetDefinition;
import com.dragonminez.common.dragonball.DragonDefinition;
import com.dragonminez.common.init.entities.dragon.DragonWishEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class DragonDBModel<T extends DragonWishEntity> extends GeoModel<T> {
   public ResourceLocation getModelResource(T animatable) {
      DragonAssetDefinition assets = this.resolveAssets(animatable);
      if (assets != null && assets.getModelPath().isPresent()) {
         return ResourceLocation.parse(assets.getModelPath().get());
      } else {
         String name = BuiltInRegistries.ENTITY_TYPE.getKey(animatable.getType()).getPath();
         return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/dragon/" + name + ".geo.json");
      }
   }

   public ResourceLocation getTextureResource(T animatable) {
      DragonAssetDefinition assets = this.resolveAssets(animatable);
      if (assets != null && assets.getTexturePath().isPresent()) {
         return ResourceLocation.parse(assets.getTexturePath().get());
      } else {
         String name = BuiltInRegistries.ENTITY_TYPE.getKey(animatable.getType()).getPath();
         return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/dragon/" + name + ".png");
      }
   }

   public ResourceLocation getAnimationResource(T animatable) {
      DragonAssetDefinition assets = this.resolveAssets(animatable);
      if (assets != null && assets.getAnimationPath().isPresent()) {
         return ResourceLocation.parse(assets.getAnimationPath().get());
      } else {
         String name = BuiltInRegistries.ENTITY_TYPE.getKey(animatable.getType()).getPath();
         return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/dragon/" + name + ".animation.json");
      }
   }

   private DragonAssetDefinition resolveAssets(T animatable) {
      DragonDefinition definition = animatable.getDragonDefinition();
      return definition == null ? null : definition.resolveAssetDefinition();
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
