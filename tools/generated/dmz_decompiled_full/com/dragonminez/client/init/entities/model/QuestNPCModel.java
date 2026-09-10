package com.dragonminez.client.init.entities.model;

import com.dragonminez.common.init.entities.questnpc.QuestNPCEntity;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class QuestNPCModel extends GeoModel<QuestNPCEntity> {
   private static final String FALLBACK_MODEL = "saga_goku";
   private static final String FALLBACK_TEXTURE = "saga_goku_early";
   private static final String SAGA_BASE_ANIMATION = "animations/entity/sagas/saga_base.animation.json";
   private static final QuestNPCModel.AssetPaths FALLBACK = saga("saga_goku", "saga_goku_early");
   private static final Map<String, QuestNPCModel.AssetPaths> NPC_ASSETS = Map.ofEntries(
      Map.entry("generic_npc", FALLBACK),
      Map.entry("goku", FALLBACK),
      Map.entry("bulma", saga("saga_bulma", "saga_bulma")),
      Map.entry("krillin", saga("saga_vegeta", "saga_krillin")),
      Map.entry("yamcha", saga("saga_yamcha", "saga_yamcha")),
      Map.entry("tien", saga("saga_goku", "saga_tien_early")),
      Map.entry("chiaotzu", saga("saga_chaoz", "saga_chaoz")),
      Map.entry("piccolo", saga("saga_piccolo", "saga_piccolo")),
      Map.entry("gohan", saga("saga_gohan_mid", "saga_gohan_mid_base")),
      Map.entry("vegeta", saga("saga_vegeta", "saga_vegeta")),
      Map.entry("trunks", saga("saga_trunks", "saga_ftrunks_base")),
      Map.entry("videl", saga("saga_videl", "saga_videl")),
      Map.entry("shin", saga("saga_shin", "saga_shin")),
      Map.entry("namek_elder", master("master_guru"))
   );
   private static final Set<String> VALID_GEO_KEYS = new HashSet<>();
   private static final Set<String> VALID_TEXTURE_KEYS = new HashSet<>();
   private static final Set<String> VALID_ANIMATION_KEYS = new HashSet<>();
   private static final Set<String> MISSING_GEO_KEYS = new HashSet<>();
   private static final Set<String> MISSING_TEXTURE_KEYS = new HashSet<>();
   private static final Set<String> MISSING_ANIMATION_KEYS = new HashSet<>();

   public ResourceLocation getModelResource(QuestNPCEntity animatable) {
      String modelKey = animatable.getModelKey();
      QuestNPCModel.AssetPaths asset = resolveAsset(animatable);
      return existingOrFallback(asset.model(), fallbackModel(), VALID_GEO_KEYS, MISSING_GEO_KEYS);
   }

   public ResourceLocation getTextureResource(QuestNPCEntity animatable) {
      String textureKey = animatable.getTextureKey();
      QuestNPCModel.AssetPaths asset = resolveAsset(animatable);
      ResourceLocation texture = assetFromKey(textureKey, "textures/entity/sagas/", ".png");
      return resourceExistsCached(texture, VALID_TEXTURE_KEYS, MISSING_TEXTURE_KEYS)
         ? texture
         : existingOrFallback(asset.texture(), fallbackTexture(), VALID_TEXTURE_KEYS, MISSING_TEXTURE_KEYS);
   }

   public ResourceLocation getAnimationResource(QuestNPCEntity animatable) {
      QuestNPCModel.AssetPaths asset = resolveAsset(animatable);
      return existingOrFallback(asset.animation(), fallbackAnimation(), VALID_ANIMATION_KEYS, MISSING_ANIMATION_KEYS);
   }

   public void setCustomAnimations(QuestNPCEntity animatable, long instanceId, AnimationState<QuestNPCEntity> animationState) {
      GeoBone head = this.getAnimationProcessor().getBone("head");
      if (head != null) {
         EntityModelData entityData = (EntityModelData)animationState.getData(DataTickets.ENTITY_MODEL_DATA);
         head.setRotX(entityData.headPitch() * (float) (Math.PI / 180.0));
         head.setRotY(entityData.netHeadYaw() * (float) (Math.PI / 180.0));
      }
   }

   private static boolean resourceExists(ResourceLocation location) {
      try {
         ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
         return resourceManager.getResource(location).isPresent();
      } catch (Exception var2) {
         return false;
      }
   }

   private static boolean resourceExistsCached(ResourceLocation location, Set<String> valid, Set<String> missing) {
      String key = location.toString();
      if (valid.contains(key)) {
         return true;
      } else if (missing.contains(key)) {
         return false;
      } else if (resourceExists(location)) {
         valid.add(key);
         return true;
      } else {
         missing.add(key);
         return false;
      }
   }

   private static ResourceLocation existingOrFallback(ResourceLocation candidate, ResourceLocation fallback, Set<String> valid, Set<String> missing) {
      return resourceExistsCached(candidate, valid, missing) ? candidate : fallback;
   }

   private static QuestNPCModel.AssetPaths resolveAsset(QuestNPCEntity animatable) {
      String modelKey = animatable.getModelKey();
      QuestNPCModel.AssetPaths explicit = NPC_ASSETS.get(modelKey);
      if (explicit != null) {
         return explicit;
      } else {
         QuestNPCModel.AssetPaths byNpc = NPC_ASSETS.get(animatable.getNpcId());
         if (byNpc == null || modelKey != null && !modelKey.isBlank() && !modelKey.equals(animatable.getNpcId())) {
            ResourceLocation sagaModel = assetFromKey(modelKey, "geo/entity/sagas/", ".geo.json");
            if (resourceExistsCached(sagaModel, VALID_GEO_KEYS, MISSING_GEO_KEYS)) {
               return saga(modelKey, animatable.getTextureKey());
            } else {
               ResourceLocation masterModel = assetFromKey(modelKey, "geo/entity/master/", ".geo.json");
               if (resourceExistsCached(masterModel, VALID_GEO_KEYS, MISSING_GEO_KEYS)) {
                  return master(modelKey);
               } else {
                  return byNpc != null ? byNpc : FALLBACK;
               }
            }
         } else {
            return byNpc;
         }
      }
   }

   private static QuestNPCModel.AssetPaths saga(String modelKey, String textureKey) {
      String safeTexture = textureKey != null && !textureKey.isBlank() ? textureKey : "saga_goku_early";
      return new QuestNPCModel.AssetPaths(
         assetFromKey(modelKey, "geo/entity/sagas/", ".geo.json"),
         assetFromKey(safeTexture, "textures/entity/sagas/", ".png"),
         ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/sagas/saga_base.animation.json")
      );
   }

   private static QuestNPCModel.AssetPaths master(String key) {
      return new QuestNPCModel.AssetPaths(
         assetFromKey(key, "geo/entity/master/", ".geo.json"),
         assetFromKey(key, "textures/entity/master/", ".png"),
         assetFromKey(key, "animations/entity/master/", ".animation.json")
      );
   }

   private static ResourceLocation assetFromKey(String key, String prefix, String suffix) {
      String safeKey = key != null && !key.isBlank() ? key : "saga_goku";
      if (safeKey.contains(":")) {
         return ResourceLocation.parse(safeKey);
      } else {
         return safeKey.contains("/")
            ? ResourceLocation.fromNamespaceAndPath("dragonminez", safeKey)
            : ResourceLocation.fromNamespaceAndPath("dragonminez", prefix + safeKey + suffix);
      }
   }

   private static ResourceLocation fallbackModel() {
      return FALLBACK.model();
   }

   private static ResourceLocation fallbackTexture() {
      return FALLBACK.texture();
   }

   private static ResourceLocation fallbackAnimation() {
      return FALLBACK.animation();
   }

   public static void clearCache() {
      VALID_GEO_KEYS.clear();
      VALID_TEXTURE_KEYS.clear();
      VALID_ANIMATION_KEYS.clear();
      MISSING_GEO_KEYS.clear();
      MISSING_TEXTURE_KEYS.clear();
      MISSING_ANIMATION_KEYS.clear();
   }

   private static record AssetPaths(ResourceLocation model, ResourceLocation texture, ResourceLocation animation) {
   }
}
