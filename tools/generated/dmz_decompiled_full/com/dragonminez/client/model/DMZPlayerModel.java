package com.dragonminez.client.model;

import com.dragonminez.client.animation.IPlayerAnimatable;
import com.dragonminez.client.render.util.RenderUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.loading.math.MolangQueries;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class DMZPlayerModel<T extends AbstractClientPlayer & GeoAnimatable> extends GeoModel<T> {
   private static final ResourceLocation BASE_DEFAULT = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/human.geo.json");
   private static final ResourceLocation BASE_SLIM = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/human_slim.geo.json");
   private static final ResourceLocation MAJIN_FAT = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/majin.geo.json");
   private static final ResourceLocation MAJIN_SLIM = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/majin_slim.geo.json");
   private static final ResourceLocation JANEMBA_SUPER = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/janemba_super.geo.json");
   private static final ResourceLocation JANEMBA_FAT = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/janemba_fat.geo.json");
   private static final ResourceLocation FROST_DEMON = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/frostdemon.geo.json");
   private static final ResourceLocation FROST_DEMON_SECOND = ResourceLocation.fromNamespaceAndPath(
      "dragonminez", "geo/entity/races/frostdemon_second.geo.json"
   );
   private static final ResourceLocation FROST_DEMON_THIRD = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/frostdemon_third.geo.json");
   private static final ResourceLocation FROST_DEMON_FIFTH = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/frostdemon_fifth.geo.json");
   private static final ResourceLocation FROSTDEMON_BUFFED = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/frostdemon_fp.geo.json");
   private static final ResourceLocation FROSTDEMON_METALCORE = ResourceLocation.fromNamespaceAndPath(
      "dragonminez", "geo/entity/races/frostdemon_metalcore.geo.json"
   );
   private static final ResourceLocation BIO_ANDROID = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/bioandroid.geo.json");
   private static final ResourceLocation BIO_ANDROID_SEMI = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/bioandroid_semi.geo.json");
   private static final ResourceLocation BIO_ANDROID_PERFECT = ResourceLocation.fromNamespaceAndPath(
      "dragonminez", "geo/entity/races/bioandroid_perfect.geo.json"
   );
   private static final ResourceLocation BIO_ANDROID_ULTRA = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/bioandroid_ultra.geo.json");
   private static final ResourceLocation BIO_ANDROID_XENO = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/bioandroid_xeno.geo.json");
   private static final ResourceLocation OOZARU = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/oozaru.geo.json");
   private static final ResourceLocation HUMAN_SAIYAN_BUFFED = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/hbuffed.geo.json");
   private static final ResourceLocation HUMAN_SAIYAN_SLIM_BUFFED = ResourceLocation.fromNamespaceAndPath(
      "dragonminez", "geo/entity/races/hbuffed_slim.geo.json"
   );
   private static final ResourceLocation HUMAN_SAIYAN_FEMALE_BUFFED = ResourceLocation.fromNamespaceAndPath(
      "dragonminez", "geo/entity/races/hbuffed_fem.geo.json"
   );
   private static final ResourceLocation HUMAN_SAIYAN_4ARMS = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/h4arms.geo.json");
   private static final ResourceLocation HUMAN_SAIYAN_4ARMS_SLIM = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/h4armsslim.geo.json");
   private static final ResourceLocation HUMAN_SAIYAN_4ARMS_FEM = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/h4armsfem.geo.json");
   private static final ResourceLocation CANDY_MODEL = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/candy.geo.json");
   private static final ResourceLocation CANDY_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/candy.png");
   private static final Map<ResourceLocation, Boolean> FILE_EXISTS_CACHE = new ConcurrentHashMap<>();
   private static final Map<String, ResourceLocation> MODEL_RESOLUTION_CACHE = new ConcurrentHashMap<>();
   private static final ResourceLocation ANIM_PRIMARY = ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/races/movement.animation.json");
   private static final ResourceLocation[] ANIM_FALLBACKS = new ResourceLocation[]{
      ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/races/combat.animation.json"),
      ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/races/ki.animation.json"),
      ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/races/transf.animation.json"),
      ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/races/skp.animation.json")
   };
   private final ResourceLocation textureLocation;
   private final String customModel;

   public DMZPlayerModel(String raceName, String customModel) {
      this.customModel = customModel;
      this.textureLocation = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/null.png");
   }

   public ResourceLocation getModelResource(T player) {
      return player.hasEffect(MainEffects.CANDY)
         ? CANDY_MODEL
         : StatsProvider.get(StatsCapability.INSTANCE, player)
            .map(
               data -> {
                  Character character = data.getCharacter();
                  String race = character.getRaceName().toLowerCase();
                  String gender = character.getGender().toLowerCase();
                  String currentForm = character.getActiveForm();
                  int bodyType = character.getBodyType();
                  String playerModelName = player.getSkin().model().id();
                  RaceCharacterConfig raceConfig = ConfigManager.getRaceCharacter(race);
                  FormConfig.FormData activeStackFormData = character.getActiveStackFormData();
                  FormConfig.FormData activeFormData = character.getActiveFormData();
                  String activeCustomModel;
                  if (activeStackFormData != null
                     && Boolean.TRUE.equals(activeStackFormData.hasCustomModel())
                     && !activeStackFormData.getCustomModel().isEmpty()) {
                     activeCustomModel = activeStackFormData.getCustomModel().toLowerCase();
                  } else if (activeFormData != null && activeFormData.hasCustomModel() && !activeFormData.getCustomModel().isEmpty()) {
                     activeCustomModel = activeFormData.getCustomModel().toLowerCase();
                  } else {
                     activeCustomModel = "";
                  }

                  String raceCustomModel = raceConfig != null && raceConfig.hasCustomModel() ? raceConfig.getCustomModel().toLowerCase() : "";
                  String fallbackCustomModel = this.customModel != null ? this.customModel.toLowerCase() : "";
                  String formKey = currentForm != null ? currentForm.toLowerCase() : "";
                  String stateKey = String.join(
                     "|",
                     race,
                     gender,
                     formKey,
                     Integer.toString(bodyType),
                     playerModelName,
                     activeCustomModel,
                     raceCustomModel,
                     fallbackCustomModel,
                     Boolean.toString(raceConfig != null && raceConfig.getHasGender())
                  );
                  return MODEL_RESOLUTION_CACHE.computeIfAbsent(stateKey, ignored -> {
                     boolean isMale = gender.equals("male");
                     boolean isSlimSkin = playerModelName.contains("slim");
                     boolean isBaseForm = currentForm == null || currentForm.isEmpty() || currentForm.equalsIgnoreCase("base");
                     if (!race.equals("saiyan") || !Objects.equals(currentForm, "oozaru") && !Objects.equals(currentForm, "goldenoozaru")) {
                        String modelKey = "";
                        if (!activeCustomModel.isEmpty()) {
                           modelKey = activeCustomModel;
                        } else if (!raceCustomModel.isEmpty()) {
                           modelKey = raceCustomModel;
                        } else if (!fallbackCustomModel.isEmpty()) {
                           modelKey = fallbackCustomModel;
                        }

                        if (!modelKey.isEmpty()) {
                           String customRaceGender = raceConfig != null && raceConfig.getHasGender() ? gender : "";
                           if (modelKey.equals("finalbase")) {
                              switch (race) {
                                 case "human":
                                 case "saiyan":
                                    if (!isMale) {
                                       return MAJIN_SLIM;
                                    }

                                    if (bodyType == 0) {
                                       return isSlimSkin ? BASE_SLIM : BASE_DEFAULT;
                                    }

                                    return BASE_DEFAULT;
                                 case "majin":
                                    return isMale ? BASE_DEFAULT : MAJIN_SLIM;
                                 case "namekian":
                                    return BASE_DEFAULT;
                                 case "frostdemon":
                                    return FROST_DEMON;
                                 case "bioandroid":
                                    return BIO_ANDROID_PERFECT;
                              }
                           }

                           return this.resolveCustomModel(modelKey, isSlimSkin, isMale, bodyType, customRaceGender);
                        } else if (race.equals("bioandroid")) {
                           return isBaseForm ? BIO_ANDROID : BIO_ANDROID_PERFECT;
                        } else if (race.equals("frostdemon")) {
                           return FROST_DEMON;
                        } else if (race.equals("namekian")) {
                           return BASE_DEFAULT;
                        } else if (race.equals("majin")) {
                           if (bodyType == 2) {
                              return isMale ? BASE_DEFAULT : MAJIN_SLIM;
                           } else {
                              return !isMale ? MAJIN_SLIM : MAJIN_FAT;
                           }
                        } else if (!race.equals("human") && !race.equals("saiyan")) {
                           if (!isMale) {
                              return MAJIN_SLIM;
                           } else {
                              return isSlimSkin ? BASE_SLIM : BASE_DEFAULT;
                           }
                        } else if (!isMale) {
                           return MAJIN_SLIM;
                        } else if (bodyType == 0) {
                           return isSlimSkin ? BASE_SLIM : BASE_DEFAULT;
                        } else {
                           return BASE_DEFAULT;
                        }
                     } else {
                        return OOZARU;
                     }
                  });
               }
            )
            .orElse(BASE_DEFAULT);
   }

   private ResourceLocation resolveCustomModel(String modelName, boolean isSlimSkin, boolean isMale, int bodyType, String customRaceGender) {
      String key = modelName.toLowerCase();
      switch (key) {
         case "human":
         case "saiyan":
            if (!isMale) {
               return MAJIN_SLIM;
            } else {
               if (bodyType == 0) {
                  return isSlimSkin ? BASE_SLIM : BASE_DEFAULT;
               }

               return BASE_DEFAULT;
            }
         case "oozaru":
            return OOZARU;
         case "ssj4gt":
            if (bodyType == 0) {
               return isSlimSkin ? BASE_SLIM : BASE_DEFAULT;
            } else {
               if (!isMale) {
                  return MAJIN_SLIM;
               }

               return BASE_DEFAULT;
            }
         case "ssj4d":
            if (bodyType == 0) {
               return isSlimSkin ? HUMAN_SAIYAN_SLIM_BUFFED : HUMAN_SAIYAN_BUFFED;
            } else {
               if (!isMale) {
                  return HUMAN_SAIYAN_FEMALE_BUFFED;
               }

               return HUMAN_SAIYAN_BUFFED;
            }
         case "buffed":
            if (bodyType == 0) {
               return isSlimSkin ? HUMAN_SAIYAN_SLIM_BUFFED : HUMAN_SAIYAN_BUFFED;
            } else {
               if (!isMale) {
                  return HUMAN_SAIYAN_FEMALE_BUFFED;
               }

               return HUMAN_SAIYAN_BUFFED;
            }
         case "4arms":
            if (bodyType == 0) {
               return isSlimSkin ? HUMAN_SAIYAN_4ARMS_SLIM : HUMAN_SAIYAN_4ARMS;
            } else {
               if (!isMale) {
                  return HUMAN_SAIYAN_4ARMS_FEM;
               }

               return HUMAN_SAIYAN_4ARMS;
            }
         case "namekian":
            return BASE_DEFAULT;
         case "namekian_orange":
         case "namekian_buffed":
            return HUMAN_SAIYAN_BUFFED;
         case "majin":
            if (bodyType == 2) {
               return isMale ? BASE_DEFAULT : MAJIN_SLIM;
            } else {
               if (!isMale) {
                  return MAJIN_SLIM;
               }

               return MAJIN_FAT;
            }
         case "majin_super":
            return isMale ? BASE_DEFAULT : MAJIN_SLIM;
         case "majin_ultra":
            return isMale ? HUMAN_SAIYAN_BUFFED : HUMAN_SAIYAN_FEMALE_BUFFED;
         case "majin_evil":
         case "majin_kid":
            return isMale ? BASE_SLIM : MAJIN_SLIM;
         case "janemba_fat":
            return JANEMBA_FAT;
         case "janemba_super":
            return JANEMBA_SUPER;
         case "frostdemon":
         case "frostdemon_final":
         case "frostdemon_mecha":
            return FROST_DEMON;
         case "frostdemon_second":
            return FROST_DEMON_SECOND;
         case "frostdemon_fifth":
            return FROST_DEMON_FIFTH;
         case "frostdemon_fp":
            return FROSTDEMON_BUFFED;
         case "frostdemon_third":
            return FROST_DEMON_THIRD;
         case "frostdemon_metalcore":
            return FROSTDEMON_METALCORE;
         case "bioandroid":
            return BIO_ANDROID;
         case "bioandroid_base":
            return BIO_ANDROID;
         case "bioandroid_semi":
            return BIO_ANDROID_SEMI;
         case "bioandroid_perfect":
            return BIO_ANDROID_PERFECT;
         case "bioandroid_ultra":
            return BIO_ANDROID_ULTRA;
         case "bioandroid_xeno":
            return BIO_ANDROID_XENO;
         default:
            String suffix = customRaceGender != null && !customRaceGender.isEmpty() ? "_" + customRaceGender : "";
            ResourceLocation customLoc = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/races/" + modelName + suffix + ".geo.json");
            if (this.fileExists(customLoc)) {
               return customLoc;
            } else {
               return isSlimSkin ? BASE_SLIM : BASE_DEFAULT;
            }
      }
   }

   public ResourceLocation getTextureResource(T t) {
      return t.hasEffect(MainEffects.CANDY) ? CANDY_TEXTURE : this.textureLocation;
   }

   public ResourceLocation getAnimationResource(T t) {
      return ANIM_PRIMARY;
   }

   public ResourceLocation[] getAnimationResourceFallbacks(T t) {
      return ANIM_FALLBACKS;
   }

   public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
      boolean var10000;
      label91: {
         super.setCustomAnimations(animatable, instanceId, animationState);
         if (animatable instanceof IPlayerAnimatable pa && pa.dragonminez$getCurrentPlayingAnimation().startsWith("transf.")) {
            var10000 = true;
            break label91;
         }

         var10000 = false;
      }

      boolean transfAnimPlaying = var10000;
      boolean actuallyTransforming = StatsProvider.get(StatsCapability.INSTANCE, animatable).map(data -> data.getStatus().isActionCharging()).orElse(false);
      boolean skipHead = transfAnimPlaying && actuallyTransforming;
      float partialTick = animationState.getPartialTick();
      float bodyYaw = Mth.lerp(partialTick, animatable.yBodyRotO, animatable.yBodyRot);
      float headYawDeg = Mth.wrapDegrees(Mth.lerp(partialTick, animatable.yHeadRotO, animatable.yHeadRot) - bodyYaw);
      float lookYaw = -headYawDeg * (float) (Math.PI / 180.0);
      float lookPitch = -Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot()) * (float) (Math.PI / 180.0);
      GeoBone head = this.getAnimationProcessor().getBone("head");
      GeoBone waist = this.getAnimationProcessor().getBone("waist");
      GeoBone root = this.getAnimationProcessor().getBone("root");
      GeoBone rightArm = this.getAnimationProcessor().getBone("right_arm");
      GeoBone leftArm = this.getAnimationProcessor().getBone("left_arm");
      if (head != null && !skipHead) {
         EntityModelData entityModelData = (EntityModelData)animationState.getData(DataTickets.ENTITY_MODEL_DATA);
         float lookPitchRad = entityModelData.headPitch() * (float) (Math.PI / 180.0);
         float lookYawRad = entityModelData.netHeadYaw() * (float) (Math.PI / 180.0);
         float waistRotX = waist != null ? waist.getRotX() : 0.0F;
         float waistRotY = waist != null ? waist.getRotY() : 0.0F;
         float rootRotX = root != null ? root.getRotX() : 0.0F;
         float rootRotY = root != null ? root.getRotY() : 0.0F;
         head.setRotX(Mth.clamp(lookPitchRad - waistRotX - rootRotX, (float) (-Math.PI / 2), (float) (Math.PI / 2)));
         head.setRotY(lookYawRad - waistRotY - rootRotY);
      }

      if (animatable instanceof IPlayerAnimatable playerAnim && playerAnim.dragonminez$isShootingKi() && rightArm != null) {
         rightArm.setRotX(lookPitch + 1.5708F);
         rightArm.setRotY(lookYaw);
      }

      label78: {
         if (animatable instanceof IPlayerAnimatable pa && pa.dragonminez$isPlayingCombatAnimation()) {
            var10000 = true;
            break label78;
         }

         var10000 = false;
      }

      boolean isInCombat = var10000;
      if (isInCombat) {
         float walkSpeed = animatable.walkAnimation.speed(partialTick);
         float walkPos = animatable.walkAnimation.position(partialTick);
         if (walkSpeed > 0.01F) {
            float amplitude = Mth.clamp(walkSpeed, 0.0F, 3.0F) * 0.15F;
            if (rightArm != null) {
               rightArm.setRotX(rightArm.getRotX() + Mth.sin(walkPos + (float) Math.PI) * amplitude);
            }

            if (leftArm != null) {
               leftArm.setRotX(leftArm.getRotX() + Mth.sin(walkPos) * amplitude);
            }
         }
      }

      float ageInTicks = (float)animatable.getTick(animatable);

      try {
         if (rightArm != null) {
            RenderUtil.animateHand(animatable, rightArm, partialTick, ageInTicks);
         }

         if (leftArm != null) {
            RenderUtil.animateHand(animatable, leftArm, partialTick, ageInTicks);
         }
      } catch (Exception var25) {
      }

      this.applyBoobScale(animatable);
   }

   private void applyBoobScale(T animatable) {
      GeoBone boobas = this.getAnimationProcessor().getBone("boobas");
      if (boobas != null) {
         float factor = StatsProvider.get(StatsCapability.INSTANCE, animatable).map(data -> {
            Character c = data.getCharacter();
            String gender = c.getGender() != null ? c.getGender().toLowerCase() : "";
            boolean isFemale = gender.equals("female") || c.getBodyType() == 1;
            return isFemale ? Mth.clamp(c.getBoobScale(), 0.75F, 1.25F) : 1.0F;
         }).orElse(1.0F);
         float[] axis = computeBoobAxisScale(factor);
         boobas.setScaleX(axis[0]);
         boobas.setScaleY(axis[1]);
         boobas.setScaleZ(axis[2]);
      }
   }

   public static float[] computeBoobAxisScale(float factor) {
      float delta = factor - 1.0F;
      return new float[]{1.0F + delta * 0.15F, 1.0F + delta * 0.3F, 1.0F + delta * 1.4F};
   }

   private boolean fileExists(ResourceLocation location) {
      return FILE_EXISTS_CACHE.computeIfAbsent(location, loc -> Minecraft.getInstance().getResourceManager().getResource(loc).isPresent());
   }

   public void applyMolangQueries(AnimationState<T> animationState, double animTime) {
      T animatable;
      boolean var10000;
      label54: {
         super.applyMolangQueries(animationState, animTime);
         animatable = (T)animationState.getAnimatable();
         if (animatable instanceof IPlayerAnimatable pa
            && (pa.dragonminez$getCurrentPlayingAnimation().startsWith("transf.") || pa.dragonminez$getCurrentPlayingAnimation().startsWith("ki."))) {
            var10000 = true;
            break label54;
         }

         var10000 = false;
      }

      boolean headAnimPlaying;
      label60: {
         headAnimPlaying = var10000;
         label44:
         if (!StatsProvider.get(StatsCapability.INSTANCE, animatable)
            .map(data -> data.getStatus().isActionCharging() || data.getStatus().isChargingKi())
            .orElse(false)) {
            if (animatable instanceof IPlayerAnimatable pa2 && pa2.dragonminez$isShootingKi()) {
               break label44;
            }

            var10000 = false;
            break label60;
         }

         var10000 = true;
      }

      boolean actuallyBusy = var10000;
      boolean skipHead = headAnimPlaying && actuallyBusy;
      float pt = Minecraft.getInstance().screen != null ? 1.0F : Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
      float pitch = Mth.lerp(pt, animatable.xRotO, animatable.getXRot());
      float bodyYaw = Mth.lerp(pt, animatable.yBodyRotO, animatable.yBodyRot);
      float headYaw = Mth.lerp(pt, animatable.yHeadRotO, animatable.yHeadRot);
      float clampedPitch = skipHead ? 0.0F : -Mth.clamp(pitch, -85.0F, 85.0F);
      float relativeYaw = -Mth.wrapDegrees(headYaw - bodyYaw);
      float clampedYaw = skipHead ? 0.0F : Mth.clamp(relativeYaw, -90.0F, 90.0F);
      double headX = (double)clampedPitch;
      double headY = (double)clampedYaw;
      MolangQueries.setActorVariable("query.head_x_rotation", actor -> headX);
      MolangQueries.setActorVariable("query.head_y_rotation", actor -> headY);
      MolangQueries.setActorVariable("query.head_pitch", actor -> headX);
      MolangQueries.setActorVariable("query.head_yaw", actor -> headY);
   }
}
