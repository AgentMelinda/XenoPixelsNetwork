package com.dragonminez.client.render.layer;

import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.client.render.compat.AeroCamSyncCompat;
import com.dragonminez.client.render.compat.CosmeticArmorCompat;
import com.dragonminez.client.render.firstperson.dto.FirstPersonManager;
import com.dragonminez.client.render.hair.HairRenderer;
import com.dragonminez.client.render.shader.TransformationMaskBufferSource;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairManager;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.common.util.TransformationsHelper;
import com.dragonminez.compat.util.LazyOptional;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

public class DMZHairLayer<T extends AbstractClientPlayer & GeoAnimatable> extends GeoRenderLayer<T> {
   private final Map<Integer, Float> progressMap = new HashMap<>();
   private final Map<Integer, CustomHair> fadeTargetHairMap = new HashMap<>();
   private final Map<Integer, float[]> fadeTargetRgbMap = new HashMap<>();
   private final Map<Integer, Boolean> fadeTargetForceMap = new HashMap<>();
   private final Map<Integer, Long> lastSeenMsMap = new HashMap<>();
   private final Map<Integer, Float> kiChargeProgressMap = new HashMap<>();
   private static final Map<Integer, float[]> PUBLISHED_BASE_COLOR = new ConcurrentHashMap<>();
   private static final Map<Integer, Long> PUBLISHED_BASE_TIME = new ConcurrentHashMap<>();
   private static final double PHYSICS_LOD_NEAR_DISTANCE_SQR = 576.0;
   private static final double PHYSICS_LOD_FAR_DISTANCE_SQR = 2304.0;
   private static final float FADE_OUT_RATE = 0.05F;
   private static final long TRACKING_TTL_MS = 30000L;
   private static final long CLEANUP_INTERVAL_MS = 5000L;
   private long lastCleanupMs = 0L;

   public DMZHairLayer(GeoRenderer<T> renderer) {
      super(renderer);
   }

   public void renderForBone(
      PoseStack poseStack,
      T animatable,
      GeoBone bone,
      RenderType renderType,
      MultiBufferSource bufferSource,
      VertexConsumer buffer,
      float partialTick,
      int packedLight,
      int packedOverlay
   ) {
      if (bone.getName().contentEquals("head")) {
         TransformationMaskBufferSource maskBuffer = null;
         if (bufferSource instanceof TransformationMaskBufferSource) {
            maskBuffer = (TransformationMaskBufferSource)bufferSource;
            maskBuffer.setMaskCaptureEnabled(true);
         }

         poseStack.pushPose();
         RenderUtil.translateToPivotPoint(poseStack, bone);
         this.renderHair(poseStack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
         if (renderType != null) {
            bufferSource.getBuffer(renderType);
         }

         poseStack.popPose();
         if (maskBuffer != null) {
            maskBuffer.setMaskCaptureEnabled(false);
         }
      }
   }

   public void renderHair(PoseStack poseStack, T animatable, MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
      if (!animatable.isInvisible() || animatable.isSpectator()) {
         Minecraft minecraft = Minecraft.getInstance();
         if (animatable != minecraft.player || !FirstPersonManager.shouldRenderFirstPerson(animatable) || EntityPreviewRenderContext.isRendering()) {
            if (!FirstPersonManager.shouldRenderFirstPerson(animatable) || AeroCamSyncCompat.isLoaded() || EntityPreviewRenderContext.isRendering()) {
               ItemStack headItem = this.resolveHeadArmorStack(animatable);
               if (!headItem.isEmpty()) {
                  ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(headItem.getItem());
                  if (itemId != null) {
                     List<String> allowedHelmets = ConfigManager.getServerConfig().getGameplay().getHelmetsThatKeepHair();
                     boolean createGoggles = itemId.getNamespace().equals("create") && itemId.getPath().equals("goggles");
                     if (!createGoggles && !allowedHelmets.contains(itemId.toString())) {
                        return;
                     }
                  }
               }

               LazyOptional<StatsData> statsCap = StatsProvider.get(StatsCapability.INSTANCE, animatable);
               StatsData stats = statsCap.orElse(new StatsData(animatable));
               Character character = stats.getCharacter();
               if (!animatable.hasEffect(MainEffects.CANDY)) {
                  if (HairManager.canUseHair(character)) {
                     CustomHair hairFrom = character.getHairBase();
                     float[] rgbFrom = character.getRgbHairColor();
                     if (character.hasActiveForm()) {
                        hairFrom = this.getHairForForm(character, character.getActiveFormGroup(), character.getActiveForm());
                        rgbFrom = this.getRgbForForm(character, character.getActiveFormGroup(), character.getActiveForm());
                        if (character.isOozaruCached()) {
                           return;
                        }
                     }

                     if (character.hasActiveStackForm()) {
                        hairFrom = this.getHairForStackForm(character, character.getActiveStackFormGroup(), character.getActiveStackForm(), hairFrom);
                        rgbFrom = this.getRgbForStackForm(character.getActiveStackFormGroup(), character.getActiveStackForm(), rgbFrom);
                     }

                     boolean overrideFrom = false;
                     if (character.hasActiveForm()
                        && character.getActiveFormData() != null
                        && Boolean.TRUE.equals(character.getActiveFormData().hasHairColorOverride())) {
                        overrideFrom = true;
                     }

                     if (character.hasActiveStackForm()
                        && character.getActiveStackFormData() != null
                        && Boolean.TRUE.equals(character.getActiveStackFormData().hasHairColorOverride())) {
                        overrideFrom = true;
                     }

                     CustomHair hairTo = hairFrom;
                     float[] rgbTo = rgbFrom;
                     float factor = 0.0F;
                     boolean forceTo = overrideFrom;
                     int entityId = animatable.getId();
                     long nowMs = System.currentTimeMillis();
                     long gameTime = animatable.level().getGameTime();
                     this.lastSeenMsMap.put(entityId, nowMs);
                     float curHairProgress = this.progressMap.getOrDefault(entityId, 0.0F);
                     if (stats.getStatus().isActionCharging()) {
                        FormConfig.FormData nextForm = null;
                        CustomHair targetHair = null;
                        float[] targetRgb = null;
                        int chargeMastery = 0;
                        if (stats.getStatus().getSelectedAction() == ActionMode.FORM) {
                           String targetGroup = character.getSelectedFormGroup();
                           nextForm = TransformationsHelper.getNextAvailableForm(stats);
                           if (nextForm != null) {
                              targetHair = this.getHairForForm(character, targetGroup, nextForm.getName());
                              targetRgb = this.getRgbForForm(character, targetGroup, nextForm.getName());
                              String masteryGroup = character.hasActiveForm() ? character.getActiveFormGroup() : targetGroup;
                              chargeMastery = (int)character.getFormMasteries().getMastery(masteryGroup, nextForm.getName());
                           }
                        } else if (stats.getStatus().getSelectedAction() == ActionMode.STACK) {
                           String targetGroup = character.getSelectedStackFormGroup();
                           nextForm = TransformationsHelper.getNextAvailableStackForm(stats);
                           if (nextForm != null) {
                              targetHair = this.getHairForStackForm(character, targetGroup, nextForm.getName(), hairFrom);
                              targetRgb = this.getRgbForStackForm(targetGroup, nextForm.getName(), rgbFrom);
                              String masteryGroup = character.hasActiveStackForm() ? character.getActiveStackFormGroup() : targetGroup;
                              chargeMastery = (int)character.getStackFormMasteries().getMastery(masteryGroup, nextForm.getName());
                           }
                        }

                        if (nextForm != null && targetHair != null && targetRgb != null) {
                           int increment = 5 + Math.max(20, chargeMastery);
                           float ratePerTick = (float)increment / 2000.0F;
                           float dt = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
                           curHairProgress = Math.min(1.0F, curHairProgress + ratePerTick * dt);
                           this.progressMap.put(entityId, curHairProgress);
                           hairTo = targetHair;
                           rgbTo = nextForm.hasHairColorOverride() ? targetRgb : rgbFrom;
                           forceTo = nextForm.hasHairColorOverride() || overrideFrom;
                           this.fadeTargetHairMap.put(entityId, targetHair);
                           this.fadeTargetRgbMap.put(entityId, rgbTo);
                           this.fadeTargetForceMap.put(entityId, forceTo);
                           factor = curHairProgress;
                        }
                     } else if (curHairProgress > 0.0F) {
                        float dt = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
                        curHairProgress = Math.max(0.0F, curHairProgress - 0.05F * dt);
                        CustomHair fadeTarget = this.fadeTargetHairMap.get(entityId);
                        if (!(curHairProgress <= 0.0F) && fadeTarget != null) {
                           this.progressMap.put(entityId, curHairProgress);
                           float[] fadeRgb = this.fadeTargetRgbMap.get(entityId);
                           hairTo = fadeTarget;
                           rgbTo = fadeRgb != null ? fadeRgb : rgbFrom;
                           forceTo = this.fadeTargetForceMap.getOrDefault(entityId, overrideFrom);
                           factor = curHairProgress;
                        } else {
                           this.clearHairTracking(entityId);
                        }
                     } else {
                        this.clearHairTracking(entityId);
                     }

                     if (nowMs - this.lastCleanupMs >= 5000L) {
                        this.cleanupStaleTracking(nowMs);
                        this.lastCleanupMs = nowMs;
                     }

                     FormConfig.FormData tintForm = DMZSkinLayer.resolveTintForm(stats);
                     float[] formTintColor = tintForm != null ? tintForm.getRgbTintColor() : null;
                     float formTintIntensity = tintForm != null ? (float)tintForm.getTintIntensity() : 0.0F;
                     boolean hasFormTint = formTintIntensity > 0.0F && formTintColor != null;
                     boolean shouldFadeIn = stats.getStatus().isChargingKi() || stats.getStatus().isAuraActive() || stats.getStatus().isPermanentAura();
                     float auraTintProgress = AuraTintTracker.update(entityId, gameTime, shouldFadeIn);
                     if (hasFormTint || auraTintProgress > 0.0F) {
                        float[] baseFrom = rgbFrom;
                        rgbFrom = (float[])rgbFrom.clone();
                        rgbTo = rgbTo == baseFrom ? rgbFrom : (float[])rgbTo.clone();
                     }

                     if (hasFormTint) {
                        this.applyFormTintToRgb(rgbFrom, formTintColor, formTintIntensity);
                        this.applyFormTintToRgb(rgbTo, formTintColor, formTintIntensity);
                     } else if (auraTintProgress > 0.0F) {
                        float[] rgbAura = character.getRgbAuraColor();
                        FormConfig.FormData activeFormData = character.hasActiveForm() ? character.getActiveFormData() : null;
                        FormConfig.FormData activeStackFormData = character.hasActiveStackForm() ? character.getActiveStackFormData() : null;
                        if (activeFormData != null && activeFormData.getRgbAuraColor() != null) {
                           rgbAura = activeFormData.getRgbAuraColor();
                        }

                        if (activeStackFormData != null && activeStackFormData.getRgbAuraColor() != null) {
                           rgbAura = activeStackFormData.getRgbAuraColor();
                        }

                        float intensity = 0.2F * auraTintProgress;
                        this.applyAuraTintToRgb(rgbFrom, rgbAura, intensity);
                        this.applyAuraTintToRgb(rgbTo, rgbAura, intensity);
                     }

                     float alpha = animatable.isSpectator() ? 0.15F : 1.0F;
                     float physicsLodMultiplier = this.getPhysicsLodMultiplier(animatable);
                     boolean isCharging = stats.getStatus().isChargingKi()
                        || stats.getStatus().isPermanentAura()
                        || stats.getStatus().isActionCharging()
                           && !stats.getStatus().getSelectedAction().equals(ActionMode.STACK)
                           && !stats.getStatus().getSelectedAction().equals(ActionMode.FORM);
                     float kiChargeProgress = this.kiChargeProgressMap.getOrDefault(entityId, 0.0F);
                     float dt = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
                     if (isCharging) {
                        kiChargeProgress = Math.min(1.0F, kiChargeProgress + dt * 0.25F);
                     } else {
                        kiChargeProgress = Math.max(0.0F, kiChargeProgress - dt * 0.15F);
                     }

                     if (kiChargeProgress > 0.0F) {
                        this.kiChargeProgressMap.put(entityId, kiChargeProgress);
                     } else {
                        this.kiChargeProgressMap.remove(entityId);
                     }

                     this.publishHairBaseColor(entityId, gameTime, rgbFrom, rgbTo, factor);
                     if (hairFrom != null && !hairFrom.isEmpty() || hairTo != null && !hairTo.isEmpty()) {
                        poseStack.pushPose();
                        HairRenderer.render(
                           poseStack,
                           bufferSource,
                           hairFrom,
                           hairTo,
                           factor,
                           character,
                           stats,
                           animatable,
                           rgbFrom,
                           rgbTo,
                           overrideFrom,
                           forceTo,
                           partialTick,
                           packedLight,
                           packedOverlay,
                           alpha,
                           physicsLodMultiplier,
                           kiChargeProgress
                        );
                        poseStack.popPose();
                     }
                  }
               }
            }
         }
      }
   }

   private ItemStack resolveHeadArmorStack(T animatable) {
      ItemStack stack = animatable.getItemBySlot(EquipmentSlot.HEAD);
      if (CosmeticArmorCompat.isLoaded()) {
         ItemStack cosmeticStack = CosmeticArmorCompat.getCosmeticStack(animatable, EquipmentSlot.HEAD);
         if (cosmeticStack != null) {
            return cosmeticStack;
         }
      }

      return stack;
   }

   private float getPhysicsLodMultiplier(AbstractClientPlayer animatable) {
      Entity cameraEntity = Minecraft.getInstance().getCameraEntity();
      if (cameraEntity == null) {
         return 1.0F;
      } else {
         double distanceSqr = animatable.distanceToSqr(cameraEntity);
         if (distanceSqr <= 576.0) {
            return 1.0F;
         } else if (distanceSqr >= 2304.0) {
            return 0.0F;
         } else {
            double t = (distanceSqr - 576.0) / 1728.0;
            return (float)(1.0 - t);
         }
      }
   }

   private void clearHairTracking(int entityId) {
      this.progressMap.remove(entityId);
      this.fadeTargetHairMap.remove(entityId);
      this.fadeTargetRgbMap.remove(entityId);
      this.fadeTargetForceMap.remove(entityId);
   }

   public static float[] getPublishedHairBaseColor(int entityId, long gameTime) {
      Long t = PUBLISHED_BASE_TIME.get(entityId);
      return t != null && gameTime - t <= 2L ? PUBLISHED_BASE_COLOR.get(entityId) : null;
   }

   private void publishHairBaseColor(int entityId, long gameTime, float[] rgbFrom, float[] rgbTo, float factor) {
      float smooth = factor;
      if (factor > 1.0E-4F && factor < 0.9999F) {
         smooth = factor * factor * (3.0F - 2.0F * factor);
      }

      float[] color;
      if (smooth <= 1.0E-4F) {
         color = (float[])rgbFrom.clone();
      } else if (smooth >= 0.9999F) {
         color = (float[])rgbTo.clone();
      } else {
         color = new float[]{Mth.lerp(smooth, rgbFrom[0], rgbTo[0]), Mth.lerp(smooth, rgbFrom[1], rgbTo[1]), Mth.lerp(smooth, rgbFrom[2], rgbTo[2])};
      }

      PUBLISHED_BASE_COLOR.put(entityId, color);
      PUBLISHED_BASE_TIME.put(entityId, gameTime);
   }

   private void cleanupStaleTracking(long nowMs) {
      if (this.lastSeenMsMap.size() >= 64) {
         this.lastSeenMsMap.entrySet().removeIf(entry -> {
            boolean stale = nowMs - entry.getValue() > 30000L;
            if (stale) {
               this.clearHairTracking(entry.getKey());
            }

            return stale;
         });
      }
   }

   private CustomHair getHairForForm(Character character, String group, String formName) {
      FormConfig config = ConfigManager.getFormGroup(character.getRaceName(), group);
      if (config != null) {
         FormConfig.FormData formData = config.getForm(formName);
         if (formData != null && formData.hasHairCodeOverride()) {
            CustomHair override = HairManager.fromCode(formData.getForcedHairCode());
            if (override != null) {
               return override;
            }
         } else if (formData != null && formData.hasDefinedHairType()) {
            return this.resolveHairType(character, formData.getHairType());
         }
      }

      return character.getHairBase();
   }

   private CustomHair getHairForStackForm(Character character, String group, String formName, CustomHair fallback) {
      FormConfig config = ConfigManager.getStackFormGroup(group);
      if (config != null) {
         FormConfig.FormData formData = config.getForm(formName);
         if (formData != null && formData.hasHairCodeOverride()) {
            CustomHair override = HairManager.fromCode(formData.getForcedHairCode());
            if (override != null) {
               return override;
            }
         } else if (formData != null && formData.hasDefinedHairType()) {
            return this.resolveHairType(character, formData.getHairType());
         }
      }

      return fallback;
   }

   private CustomHair resolveHairType(Character character, String type) {
      String var3 = type.toLowerCase();

      return switch (var3) {
         case "base" -> character.getHairBase();
         case "ssj" -> character.getHairSSJ();
         case "ssj2" -> character.getHairSSJ2();
         case "ssj3" -> character.getHairSSJ3();
         default -> character.emptyHair();
      };
   }

   private float[] getRgbForForm(Character character, String group, String formName) {
      FormConfig config = ConfigManager.getFormGroup(character.getRaceName(), group);
      if (config != null) {
         FormConfig.FormData formData = config.getForm(formName);
         if (formData != null && formData.getRgbHairColor() != null) {
            return formData.getRgbHairColor();
         }
      }

      return character.getRgbHairColor();
   }

   private float[] getRgbForStackForm(String group, String formName, float[] fallback) {
      FormConfig config = ConfigManager.getStackFormGroup(group);
      if (config != null) {
         FormConfig.FormData formData = config.getForm(formName);
         if (formData != null && formData.getRgbHairColor() != null) {
            return formData.getRgbHairColor();
         }
      }

      return fallback;
   }

   private void applyFormTintToRgb(float[] rgb, float[] tint, float intensity) {
      intensity = Mth.clamp(intensity, 0.0F, 1.0F) * AuraTintTracker.darkTintScale(rgb);
      rgb[0] = Mth.clamp(rgb[0] * (1.0F - intensity) + tint[0] * intensity, 0.0F, 1.0F);
      rgb[1] = Mth.clamp(rgb[1] * (1.0F - intensity) + tint[1] * intensity, 0.0F, 1.0F);
      rgb[2] = Mth.clamp(rgb[2] * (1.0F - intensity) + tint[2] * intensity, 0.0F, 1.0F);
   }

   private void applyAuraTintToRgb(float[] rgb, float[] auraRgb, float intensity) {
      intensity *= AuraTintTracker.darkTintScale(rgb);
      rgb[0] = rgb[0] * (1.0F - intensity) + auraRgb[0] * intensity;
      rgb[1] = rgb[1] * (1.0F - intensity) + auraRgb[1] * intensity;
      rgb[2] = rgb[2] * (1.0F - intensity) + auraRgb[2] * intensity;
   }
}
