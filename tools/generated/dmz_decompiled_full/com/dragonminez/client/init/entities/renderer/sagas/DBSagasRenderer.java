package com.dragonminez.client.init.entities.renderer.sagas;

import com.dragonminez.client.init.entities.model.sagas.DBSagaModel;
import com.dragonminez.client.init.entities.renderer.sagas.layer.DMZSagaArmorLayer;
import com.dragonminez.client.init.entities.renderer.sagas.layer.DMZSagaItemInHandLayer;
import com.dragonminez.client.render.effects.AuraRenderer;
import com.dragonminez.client.render.shader.DMZShaders;
import com.dragonminez.client.render.util.AuraMeshFactory;
import com.dragonminez.client.render.util.IrisCompat;
import com.dragonminez.client.render.util.PlayerEffectQueue;
import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.init.entities.ShadowDummyEntity;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.init.entities.sagas.SagaNappaEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

public class DBSagasRenderer<T extends DBSagasEntity> extends GeoEntityRenderer<T> {
   private static final ResourceLocation NAPPA_NORMAL = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/sagas/saga_nappa.png");
   private static final ResourceLocation NAPPA_DAMAGED = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/sagas/saga_nappa2.png");

   public DBSagasRenderer(Context renderManager) {
      super(renderManager, new DBSagaModel());
      this.shadowRadius = 0.4F;
      this.addRenderLayer(new DMZSagaItemInHandLayer(this));
      this.addRenderLayer(new DMZSagaArmorLayer(this));
   }

   public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return animatable instanceof ShadowDummyEntity ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture);
   }

   public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      poseStack.pushPose();
      float sc = entity.getScale();
      poseStack.scale(sc, sc, sc);
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      boolean showAura = entity.isTransforming() || entity.isCharge();
      boolean showLightning = entity.isLightning();
      if (showAura || showLightning) {
         if (IrisCompat.isShaderPackInUse()) {
            Matrix4f captured = new Matrix4f(poseStack.last().pose());
            PlayerEffectQueue.addEntityEffect(() -> this.drawEffects(entity, captured, partialTick, showAura, showLightning));
         } else {
            this.drawEffectsInline(entity, poseStack, partialTick, showAura, showLightning);
         }
      }

      poseStack.popPose();
   }

   private void drawEffects(T entity, Matrix4f baseMatrix, float partialTick, boolean showAura, boolean showLightning) {
      PoseStack poseStack = new PoseStack();
      poseStack.last().pose().set(baseMatrix);
      this.drawEffectsInline(entity, poseStack, partialTick, showAura, showLightning);
   }

   private void drawEffectsInline(T entity, PoseStack poseStack, float partialTick, boolean showAura, boolean showLightning) {
      Minecraft mc = Minecraft.getInstance();
      poseStack.pushPose();
      if (showAura) {
         if (entity.onGround()) {
            poseStack.pushPose();
            poseStack.translate(0.0, 0.05, 0.0);
            this.renderPulseAura(entity, poseStack, mc, partialTick);
            poseStack.popPose();
         }

         poseStack.pushPose();
         this.executeAuraShaderDraw(entity, poseStack, mc, partialTick);
         poseStack.popPose();
      }

      if (showLightning) {
         poseStack.pushPose();
         this.executeLightningShaderDraw(entity, poseStack, partialTick);
         poseStack.popPose();
      }

      poseStack.popPose();
   }

   public ResourceLocation getTextureLocation(T animatable) {
      if (animatable instanceof SagaNappaEntity nappa) {
         return nappa.isBattleDamaged() ? NAPPA_DAMAGED : NAPPA_NORMAL;
      } else {
         return super.getTextureLocation(animatable);
      }
   }

   public Color getRenderColor(T animatable, float partialTick, int packedLight) {
      return animatable instanceof ShadowDummyEntity ? Color.ofRGBA(1.0F, 1.0F, 1.0F, 0.7F) : super.getRenderColor(animatable, partialTick, packedLight);
   }

   private void renderPulseAura(T animatable, PoseStack poseStack, Minecraft mc, float partialTick) {
      float time = ((float)animatable.tickCount + partialTick) * 0.02F;
      float progress1 = time % 1.0F;
      float progress2 = (progress1 + 0.5F) % 1.0F;
      this.drawSinglePulse(animatable, poseStack, mc, partialTick, progress1);
      this.drawSinglePulse(animatable, poseStack, mc, partialTick, progress2);
   }

   private void drawSinglePulse(T animatable, PoseStack poseStack, Minecraft mc, float partialTick, float progress) {
      float expansion = 1.0F + 6.0F * progress;
      float alphaCurve = (float)Math.sin((double)progress * Math.PI);
      String auraType = animatable.getAuraType() != null && !animatable.getAuraType().isEmpty() ? animatable.getAuraType().toLowerCase() : "kakarot";
      ResourceLocation crossTex = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/aura/" + auraType + "_cross.png");
      ShaderInstance shader = DMZShaders.auraShader;
      if (shader != null) {
         Matrix4f projectionMatrix = RenderSystem.getProjectionMatrix();
         poseStack.pushPose();
         float cameraPitch = mc.gameRenderer.getMainCamera().getXRot();
         if (cameraPitch < 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
         }

         float scaleMultiplier = 0.7F;
         float sX = expansion * scaleMultiplier;
         float sZ = expansion * scaleMultiplier;
         poseStack.scale(sX, 1.0F, sZ);
         float animSpeed = ((float)animatable.tickCount + partialTick) * 0.5F;
         shader.safeGetUniform("speed").set(animSpeed);
         shader.safeGetUniform("ProjMat").set(projectionMatrix);
         shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
         float[] color = ColorUtils.rgbIntToFloat(animatable.getAuraColor());
         shader.safeGetUniform("color1").set(color[0] * 1.6F, color[1] * 1.6F, color[2] * 1.6F, 1.0F);
         shader.safeGetUniform("color2").set(color[0] * 1.3F, color[1] * 1.3F, color[2] * 1.3F, 1.0F);
         shader.safeGetUniform("color3").set(color[0] * 1.0F, color[1] * 1.0F, color[2] * 1.0F, 0.85F);
         shader.safeGetUniform("color4").set(color[0] * 0.75F, color[1] * 0.75F, color[2] * 0.75F, 0.65F);
         shader.safeGetUniform("alp1").set(alphaCurve * 0.6F);
         RenderType pulseRender = AuraRenderer.auraType(crossTex);
         AuraRenderer.customSetup(pulseRender, crossTex, shader);
         shader.apply();
         VertexBuffer mesh = AuraMeshFactory.getGroundQuad();
         mesh.bind();
         mesh.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
         AuraRenderer.customClear(pulseRender);
         VertexBuffer.unbind();
         shader.clear();
         poseStack.popPose();
      }
   }

   private void executeAuraShaderDraw(T animatable, PoseStack poseStack, Minecraft mc, float partialTick) {
      ShaderInstance shader = DMZShaders.auraShader;
      if (shader != null) {
         Matrix4f projectionMatrix = RenderSystem.getProjectionMatrix();
         String auraType = animatable.getAuraType() != null && !animatable.getAuraType().isEmpty() ? animatable.getAuraType().toLowerCase() : "kakarot";
         ResourceLocation mainTex = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/aura/" + auraType + "_aura.png");
         ResourceLocation crossTex = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/aura/" + auraType + "_cross.png");
         ResourceLocation sparkingTex = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/aura/sparking_effects.png");
         float animSpeed = ((float)animatable.tickCount + partialTick) * 0.5F;
         shader.safeGetUniform("speed").set(animSpeed);
         shader.safeGetUniform("ProjMat").set(projectionMatrix);
         float[] color = ColorUtils.rgbIntToFloat(animatable.getAuraColor());
         shader.safeGetUniform("color1").set(color[0] * 1.6F, color[1] * 1.6F, color[2] * 1.6F, 1.0F);
         shader.safeGetUniform("color2").set(color[0] * 1.3F, color[1] * 1.3F, color[2] * 1.3F, 1.0F);
         shader.safeGetUniform("color3").set(color[0] * 1.0F, color[1] * 1.0F, color[2] * 1.0F, 0.85F);
         shader.safeGetUniform("color4").set(color[0] * 0.75F, color[1] * 0.75F, color[2] * 0.75F, 0.65F);
         float cameraPitch = mc.gameRenderer.getMainCamera().getXRot();
         float cameraYaw = mc.gameRenderer.getMainCamera().getYRot();
         float absPitch = Math.abs(cameraPitch);
         float crossFactor = 0.0F;
         float pitchSquash = 1.0F;
         if (absPitch > 45.0F) {
            crossFactor = (float)Math.pow((double)((absPitch - 45.0F) / 45.0F), 2.0);
            pitchSquash = 1.0F - crossFactor * 0.5F;
         }

         float scaleMultiplier = 2.2F;
         VertexBuffer mesh = AuraMeshFactory.getBillboardQuad();
         if (crossFactor < 1.0F) {
            poseStack.pushPose();
            poseStack.translate(0.0, (double)(animatable.getBbHeight() / 2.0F + 0.8F), 0.0);
            poseStack.mulPose(Axis.YP.rotationDegrees(-cameraYaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(cameraPitch));
            poseStack.scale(scaleMultiplier, scaleMultiplier * pitchSquash, scaleMultiplier);
            shader.safeGetUniform("alp1").set(1.0F - crossFactor);
            shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
            RenderType mainRender = AuraRenderer.auraType(mainTex);
            AuraRenderer.customSetup(mainRender, mainTex, shader);
            shader.apply();
            mesh.bind();
            mesh.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
            AuraRenderer.customClear(mainRender);
            poseStack.pushPose();
            float sparkingPulse = 1.0F + (float)Math.sin((double)(((float)animatable.tickCount + partialTick) * 0.2F)) * 0.05F;
            poseStack.scale(0.8F * sparkingPulse, 0.65F * sparkingPulse, 0.8F * sparkingPulse);
            poseStack.translate(0.0, -0.25, 0.0);
            RenderType sparkingRender = AuraRenderer.auraType(sparkingTex);
            AuraRenderer.customSetup(sparkingRender, sparkingTex, shader);
            shader.safeGetUniform("alp1").set((1.0F - crossFactor) * 0.8F);
            shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
            shader.apply();
            mesh.bind();
            mesh.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
            AuraRenderer.customClear(sparkingRender);
            poseStack.popPose();
            poseStack.popPose();
         }

         if (crossFactor > 0.0F) {
            poseStack.pushPose();
            poseStack.translate(0.0, 0.05, 0.0);
            poseStack.mulPose(Axis.YP.rotationDegrees(-cameraYaw));
            if (cameraPitch < 0.0F) {
               poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            }

            poseStack.scale(scaleMultiplier, 1.0F, scaleMultiplier);
            shader.safeGetUniform("alp1").set(crossFactor);
            shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
            RenderType crossRender = AuraRenderer.auraType(crossTex);
            AuraRenderer.customSetup(crossRender, crossTex, shader);
            shader.apply();
            VertexBuffer groundMesh = AuraMeshFactory.getGroundQuad();
            groundMesh.bind();
            groundMesh.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
            AuraRenderer.customClear(crossRender);
            poseStack.popPose();
         }

         VertexBuffer.unbind();
         shader.clear();
      }
   }

   private void executeLightningShaderDraw(T animatable, PoseStack poseStack, float partialTick) {
      ShaderInstance shader = DMZShaders.lightningShader;
      if (shader != null) {
         boolean isAuraActive = animatable.isCharge() || animatable.isTransforming();
         float speedMod = isAuraActive ? 1.0F : 0.2F;
         int maxBranches = isAuraActive ? 5 : 3;
         float maxScale = isAuraActive ? 0.5F : 0.25F;
         float[] colorRgb = ColorUtils.rgbIntToFloat(animatable.getLightningColor());
         float time = ((float)animatable.tickCount + partialTick) / 20.0F;
         Matrix4f projectionMatrix = RenderSystem.getProjectionMatrix();
         shader.safeGetUniform("projectionMatrix").set(projectionMatrix);
         shader.safeGetUniform("time").set(time);
         shader.safeGetUniform("speedModifier").set(speedMod);
         shader.safeGetUniform("color1").set(Mth.lerp(0.8F, colorRgb[0], 1.0F), Mth.lerp(0.8F, colorRgb[1], 1.0F), Mth.lerp(0.8F, colorRgb[2], 1.0F));
         shader.safeGetUniform("color2").set(colorRgb[0], colorRgb[1], colorRgb[2]);
         shader.safeGetUniform("alp1").set(1.0F);
         shader.safeGetUniform("alp2").set(0.1F);
         shader.safeGetUniform("power").set(3.0F);
         shader.safeGetUniform("divis").set(1.0F);
         ResourceLocation lightningTex = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/null.png");
         RenderType renderType = AuraRenderer.lightningType(lightningTex);
         AuraRenderer.customSetup(renderType, lightningTex, shader);
         shader.apply();
         VertexBuffer mesh = AuraRenderer.getLightningMesh();
         mesh.bind();
         long timeHash = animatable.level().getGameTime() / 2L;
         Random seededRand = new Random((long)animatable.getId() + timeHash);
         float bbHeight = animatable.getBbHeight() - 0.6F;

         for (int i = 0; i < maxBranches; i++) {
            poseStack.pushPose();
            float spread = isAuraActive ? 1.8F : 1.2F;
            float randomY = seededRand.nextFloat() * bbHeight;
            poseStack.translate((seededRand.nextFloat() - 0.5F) * spread, randomY, (seededRand.nextFloat() - 0.5F) * spread);
            poseStack.mulPose(Axis.YP.rotationDegrees(seededRand.nextFloat() * 360.0F));
            float scale = 0.15F + seededRand.nextFloat() * maxScale;
            poseStack.scale(scale, scale, scale);
            shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
            shader.safeGetUniform("normalMatrix").set(new Matrix4f(new Matrix3f(poseStack.last().normal())));
            shader.apply();
            mesh.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
            poseStack.popPose();
         }

         VertexBuffer.unbind();
         shader.clear();
         AuraRenderer.customClear(renderType);
      }
   }
}
