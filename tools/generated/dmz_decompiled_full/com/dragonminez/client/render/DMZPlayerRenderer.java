package com.dragonminez.client.render;

import com.dragonminez.client.events.FlySkillEvent;
import com.dragonminez.client.flight.FlightRollHandler;
import com.dragonminez.client.render.layer.DMZAuraLayer;
import com.dragonminez.client.render.layer.DMZCapeLayer;
import com.dragonminez.client.render.layer.DMZCustomArmorLayer;
import com.dragonminez.client.render.layer.DMZHairLayer;
import com.dragonminez.client.render.layer.DMZPlayerArmorLayer;
import com.dragonminez.client.render.layer.DMZPlayerItemInHandLayer;
import com.dragonminez.client.render.layer.DMZRacePartsLayer;
import com.dragonminez.client.render.layer.DMZSkinLayer;
import com.dragonminez.client.render.layer.DMZThirdPartyLayerForwarder;
import com.dragonminez.client.render.layer.DMZWeaponsLayer;
import com.dragonminez.client.render.layer.DMZWeightCapeLayer;
import com.dragonminez.client.render.shader.TransformationMaskBufferSource;
import com.dragonminez.client.render.shader.TransformationPostShaderManager;
import com.dragonminez.client.render.util.IrisCompat;
import com.dragonminez.client.util.BoneVisibilityHandler;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.compat.util.LazyOptional;
import com.dragonminez.mixin.client.GeoModelAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class DMZPlayerRenderer<T extends AbstractClientPlayer & GeoAnimatable> extends GeoEntityRenderer<T> {
   protected GeoRenderLayer<T> caller = null;

   public DMZPlayerRenderer(Context renderManager, GeoModel<T> model) {
      super(renderManager, model);
      this.addRenderLayer(new DMZPlayerItemInHandLayer(this));
      this.addRenderLayer(new DMZPlayerArmorLayer(this));
      this.addRenderLayer(new DMZCapeLayer(this));
      this.addRenderLayer(new DMZWeightCapeLayer(this));
      this.addRenderLayer(new DMZCustomArmorLayer(this));
      this.addRenderLayer(new DMZRacePartsLayer(this));
      this.addRenderLayer(new DMZWeaponsLayer(this));
      this.addRenderLayer(new DMZAuraLayer(this));
      this.addRenderLayer(new DMZSkinLayer(this));
      this.addRenderLayer(new DMZHairLayer(this));
      this.addRenderLayer(new DMZThirdPartyLayerForwarder(this));
   }

   public void reRender(
      GeoRenderLayer<T> calledFrom,
      BakedGeoModel model,
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      T animatable,
      RenderType renderType,
      VertexConsumer buffer,
      float partialTick,
      int packedLight,
      int packedOverlay,
      int colour
   ) {
      this.caller = calledFrom;
      super.reRender(model, poseStack, bufferSource, animatable, renderType, buffer, partialTick, packedLight, packedOverlay, colour);
      this.caller = null;
   }

   public void preRender(
      PoseStack poseStack,
      T animatable,
      BakedGeoModel model,
      MultiBufferSource bufferSource,
      VertexConsumer buffer,
      boolean isReRender,
      float partialTick,
      int packedLight,
      int packedOverlay,
      int colour
   ) {
      int finalColour = colour;
      if (animatable.isSpectator()) {
         finalColour = 637534208 | colour & 16777215;
      }

      super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, finalColour);
      BoneVisibilityHandler.updateVisibility(model, animatable, this.caller);
   }

   public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      if (entity == null) {
         super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      } else {
         ((GeoModelAccessor)this.getGeoModel()).dmz$setLastRenderedInstance(-1L);
         LazyOptional<StatsData> statsCap = StatsProvider.get(StatsCapability.INSTANCE, entity);
         StatsData stats = statsCap.orElse(new StatsData(entity));
         Character character = stats.getCharacter();
         String race = character.getRaceName().toLowerCase();
         String currentForm = character.getActiveForm();
         String logicKey = character.getRenderLogicKey();
         Float[] resolved = character.getResolvedModelScaling();
         float configScaleX = resolved[0];
         float configScaleY = resolved[1];
         float configScaleZ = resolved[2];
         boolean isOozaru = logicKey.startsWith("oozaru")
            || race.equals("saiyan") && (Objects.equals(currentForm, "oozaru") || Objects.equals(currentForm, "goldenoozaru"));
         float scalingX;
         float scalingY;
         float scalingZ;
         if (isOozaru) {
            scalingX = Math.max(0.1F, configScaleX - 2.8F);
            scalingY = Math.max(0.1F, configScaleY - 2.8F);
            scalingZ = Math.max(0.1F, configScaleZ - 2.8F);
         } else {
            scalingX = configScaleX;
            scalingY = configScaleY;
            scalingZ = configScaleZ;
         }

         poseStack.pushPose();
         boolean hudPortrait = EntityPreviewRenderContext.isHudPortrait();
         boolean shaderPack = !hudPortrait && IrisCompat.isShaderPackInUse();
         boolean captureMask = !shaderPack || TransformationPostShaderManager.isShaderpackMainPass();
         TransformationPostShaderManager.MaskData maskData = !hudPortrait && captureMask ? TransformationPostShaderManager.getEntityMaskData(entity) : null;
         TransformationMaskBufferSource maskBufferSource = null;
         if (maskData != null) {
            maskBufferSource = TransformationPostShaderManager.getMaskBufferSource();
            maskBufferSource.setEntityColors(
               maskData.primaryR(), maskData.primaryG(), maskData.primaryB(), maskData.secondaryR(), maskData.secondaryG(), maskData.secondaryB()
            );
         }

         if (!hudPortrait && FlySkillEvent.getInstance().isFlyingFast(entity)) {
            float roll = entity == Minecraft.getInstance().player ? FlightRollHandler.getRoll(partialTick) : 0.0F;
            float pitch = entity.getViewXRot(partialTick);
            float pivotY = entity.getBbHeight() / 2.0F;
            poseStack.translate(0.0F, pivotY, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
            poseStack.mulPose(Axis.YP.rotationDegrees(-(180.0F - entityYaw)));
            poseStack.translate(0.0F, -pivotY, 0.0F);
         }

         poseStack.scale(scalingX, scalingY, scalingZ);
         boolean isAuraActive = !hudPortrait && (stats.getStatus().isAuraActive() || stats.getStatus().isPermanentAura());
         if (isAuraActive) {
            if (bufferSource instanceof BufferSource bs) {
               bs.endBatch();
            }

            GL11.glEnable(2960);
            RenderSystem.stencilMask(255);
            RenderSystem.stencilFunc(519, 1, 255);
            RenderSystem.stencilOp(7680, 7680, 7681);
         }

         if (maskBufferSource != null) {
            maskBufferSource.wrap(bufferSource);
            maskBufferSource.setForceCaptureAll(true);

            try {
               super.render(entity, entityYaw, partialTick, poseStack, maskBufferSource, packedLight);
            } finally {
               maskBufferSource.setForceCaptureAll(false);
               maskBufferSource.setMaskCaptureEnabled(true);
            }
         } else {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
         }

         if (isAuraActive) {
            if (bufferSource instanceof BufferSource bs) {
               bs.endBatch();
            }

            GL11.glDisable(2960);
            RenderSystem.stencilMask(0);
         }

         this.shadowRadius = 0.4F * ((scalingX + scalingZ) / 2.0F);
         poseStack.popPose();
      }
   }

   public void applyRenderLayers(
      PoseStack poseStack,
      T animatable,
      BakedGeoModel model,
      RenderType renderType,
      MultiBufferSource bufferSource,
      VertexConsumer buffer,
      float partialTick,
      int packedLight,
      int packedOverlay
   ) {
      for (GeoRenderLayer<T> renderLayer : this.getRenderLayers()) {
         if (!EntityPreviewRenderContext.isHudPortrait()
            || renderLayer instanceof DMZPlayerArmorLayer
            || renderLayer instanceof DMZCustomArmorLayer
            || renderLayer instanceof DMZRacePartsLayer
            || renderLayer instanceof DMZSkinLayer
            || renderLayer instanceof DMZHairLayer) {
            renderLayer.render(poseStack, animatable, model, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
         }
      }
   }

   public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return super.getRenderType(animatable, texture, bufferSource, partialTick);
   }

   public boolean shouldShowName(T animatable) {
      return animatable == Minecraft.getInstance().getCameraEntity() ? false : super.shouldShowName(animatable);
   }
}
