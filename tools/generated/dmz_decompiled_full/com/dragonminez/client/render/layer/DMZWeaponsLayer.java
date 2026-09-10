package com.dragonminez.client.render.layer;

import com.dragonminez.client.render.util.ModRenderTypes;
import com.dragonminez.common.combat.logic.weapon.KiWeaponHelper;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.world.entity.HumanoidArm;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class DMZWeaponsLayer<T extends AbstractClientPlayer & GeoAnimatable> extends GeoRenderLayer<T> {
   private static final float OOZARU_WEAPON_SCALE = 3.8F;
   private static final float[] HUMAN_ARM_RIGHT = new float[]{-5.0F, 22.0F, 0.0F};
   private static final float[] HUMAN_ARM_LEFT = new float[]{5.0F, 22.0F, 0.0F};
   private static final float[] OOZARU_ARM_RIGHT = new float[]{-12.0F, 74.0F, 0.0F};
   private static final float[] OOZARU_ARM_LEFT = new float[]{21.0F, 74.0F, 0.0F};

   public DMZWeaponsLayer(GeoRenderer<T> entityRendererIn) {
      super(entityRendererIn);
   }

   public void renderForBone(
      PoseStack poseStack,
      T animatable,
      GeoBone playerBone,
      RenderType renderType,
      MultiBufferSource bufferSource,
      VertexConsumer buffer,
      float partialTick,
      int packedLight,
      int packedOverlay
   ) {
      if ("right_arm".equals(playerBone.getName()) || "left_arm".equals(playerBone.getName())) {
         if (!animatable.isSpectator()) {
            StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, animatable).orElse(null);
            if (stats != null && stats.getSkills().isSkillActive("kimanipulation")) {
               String weaponType = stats.getStatus().getKiWeaponType();
               if (weaponType != null && !weaponType.equalsIgnoreCase("none")) {
                  if (animatable.getMainHandItem().isEmpty()) {
                     boolean isRight = animatable.getMainArm() == HumanoidArm.RIGHT;
                     boolean isRightArm = "right_arm".equals(playerBone.getName());
                     if (isRight == isRightArm) {
                        String type = weaponType.toLowerCase();
                        ResourceLocation modelLoc = weaponModel(type);
                        if (modelLoc != null) {
                           BakedGeoModel weaponModel = this.getGeoModel().getBakedModel(modelLoc);
                           if (weaponModel != null) {
                              ResourceLocation texture = weaponTexture(type);
                              if (texture != null) {
                                 weaponModel.getBone(weaponBone(type))
                                    .ifPresent(
                                       targetBone -> {
                                          Character character = stats.getCharacter();
                                          boolean isOozaru = character != null && character.isOozaruCached();
                                          poseStack.pushPose();
                                          if (type.equals("clawlance")) {
                                             poseStack.mulPose(Axis.YP.rotationDegrees(35.0F));
                                             poseStack.mulPose(Axis.XP.rotationDegrees(35.0F));
                                             poseStack.translate(0.0F, -0.1F, -1.0F);
                                          }

                                          if (isOozaru) {
                                             float[] humanPivot = isRight ? HUMAN_ARM_RIGHT : HUMAN_ARM_LEFT;
                                             float[] oozaruPivot = isRight ? OOZARU_ARM_RIGHT : OOZARU_ARM_LEFT;
                                             float k = 3.8F;
                                             poseStack.translate(oozaruPivot[0] / 16.0F, oozaruPivot[1] / 16.0F, oozaruPivot[2] / 16.0F);
                                             poseStack.scale(k, k, k);
                                             poseStack.translate(-humanPivot[0] / 16.0F, -humanPivot[1] / 16.0F, -humanPivot[2] / 16.0F);
                                          }

                                          float[] color = KiWeaponHelper.resolveColorForType(weaponType, this.getKiColor(stats));
                                          RenderType weaponRenderType = ModRenderTypes.energy2(texture);
                                          VertexConsumer vertexConsumer = bufferSource.getBuffer(weaponRenderType);
                                          this.getRenderer()
                                             .renderRecursively(
                                                poseStack,
                                                animatable,
                                                targetBone,
                                                weaponRenderType,
                                                bufferSource,
                                                vertexConsumer,
                                                true,
                                                partialTick,
                                                packedLight,
                                                OverlayTexture.NO_OVERLAY,
                                                ARGB32.colorFromFloat(0.65F, color[0], color[1], color[2])
                                             );
                                          poseStack.popPose();
                                       }
                                    );
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static ResourceLocation weaponModel(String type) {
      ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/weapons/kiweapon_" + type + ".geo.json");
      return Minecraft.getInstance().getResourceManager().getResource(loc).isPresent() ? loc : null;
   }

   private static ResourceLocation weaponTexture(String type) {
      ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/weapons/kiweapon_" + type + ".png");
      return Minecraft.getInstance().getResourceManager().getResource(loc).isPresent() ? loc : null;
   }

   private static String weaponBone(String type) {
      return "kiweapon_" + type;
   }

   private float[] getKiColor(StatsData stats) {
      Character character = stats.getCharacter();
      float[] kiColor = character.getRgbAuraColor();
      if (character.hasActiveForm() && character.getActiveFormData() != null) {
         String formColor = character.getActiveFormData().getAuraColor();
         if (formColor != null && !formColor.isEmpty()) {
            kiColor = character.getActiveFormData().getRgbAuraColor();
         }
      }

      return kiColor;
   }
}
