package com.dragonminez.client.init.entities.model.sagas;

import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class DBSagaModel<T extends DBSagasEntity> extends GeoModel<T> {
   private static final Map<ResourceLocation, Boolean> RESOURCE_CACHE = new HashMap<>();

   public ResourceLocation getModelResource(T animatable) {
      ResourceLocation original = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/sagas/" + animatable.getGeckolibModelName() + ".geo.json");
      boolean exists = RESOURCE_CACHE.computeIfAbsent(original, this::resourceExists);
      return exists ? original : ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/enemies/robotxv.geo.json");
   }

   public ResourceLocation getTextureResource(T animatable) {
      String name = BuiltInRegistries.ENTITY_TYPE.getKey(animatable.getType()).getPath();
      int variant = animatable.getTextureVariant();
      String variantSuffix = variant == 0 ? "" : "_" + variant;
      ResourceLocation variantTexture = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/sagas/" + name + variantSuffix + ".png");
      boolean variantExists = RESOURCE_CACHE.computeIfAbsent(variantTexture, this::resourceExists);
      if (variantExists) {
         return variantTexture;
      } else {
         if (variant > 0) {
            ResourceLocation baseTexture = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/sagas/" + name + ".png");
            boolean baseExists = RESOURCE_CACHE.computeIfAbsent(baseTexture, this::resourceExists);
            if (baseExists) {
               return baseTexture;
            }
         }

         return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/enemies/robotxv.png");
      }
   }

   public ResourceLocation getAnimationResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/sagas/saga_base.animation.json");
   }

   public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
      GeoBone head = this.getAnimationProcessor().getBone("head");
      if (head != null) {
         EntityModelData entityData = (EntityModelData)animationState.getData(DataTickets.ENTITY_MODEL_DATA);
         head.setRotX(entityData.headPitch() * (float) (Math.PI / 180.0));
         head.setRotY(entityData.netHeadYaw() * (float) (Math.PI / 180.0));
      }
   }

   private boolean resourceExists(ResourceLocation location) {
      return Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
   }
}
