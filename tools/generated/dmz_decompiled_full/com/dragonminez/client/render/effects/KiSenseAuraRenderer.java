package com.dragonminez.client.render.effects;

import com.dragonminez.client.render.shader.DMZShaders;
import com.dragonminez.client.render.shader.SearchGrayscaleManager;
import com.dragonminez.client.render.util.AuraMeshFactory;
import com.dragonminez.client.render.util.IrisCompat;
import com.dragonminez.client.render.util.ModRenderTypes;
import com.dragonminez.client.systems.kisense.KiSenseScan;
import com.dragonminez.client.systems.kisense.KiSenseState;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Matrix4f;

@EventBusSubscriber(
   modid = "dragonminez",
   value = {Dist.CLIENT}
)
public final class KiSenseAuraRenderer {
   private static final double LOD_DISTANCE = 24.0;

   private KiSenseAuraRenderer() {
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void onRenderLevelStage(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         boolean iris = IrisCompat.isShaderPackInUse(mc.level.getGameTime());
         Stage targetStage = iris ? Stage.AFTER_LEVEL : Stage.AFTER_WEATHER;
         if (event.getStage() == targetStage) {
            if (!KiSenseState.isSearch()) {
               SearchGrayscaleManager.reset();
            } else {
               if (iris) {
                  mc.getMainRenderTarget().bindWrite(false);
                  SearchGrayscaleManager.process(event.getPartialTick().getGameTimeDeltaPartialTick(false), false);
                  renderAuras(mc, event, true);
               } else {
                  SearchGrayscaleManager.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
                  renderAuras(mc, event, false);
               }
            }
         }
      }
   }

   private static void renderAuras(Minecraft mc, RenderLevelStageEvent event, boolean iris) {
      StatsData myData = StatsProvider.get(StatsCapability.INSTANCE, mc.player).orElse(null);
      if (myData != null) {
         ShaderInstance shader = DMZShaders.auraShader;
         if (shader != null) {
            double myBP = (double)KiSenseScan.getMyBP();
            if (myBP <= 0.0) {
               myBP = (double)Math.max(1.0F, myData.getBattlePower());
            }

            ResourceLocation tex = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/aura/kakarot_cross.png");
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 camPos = camera.getPosition();
            Matrix4f proj = event.getProjectionMatrix();
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            long gameTime = mc.level.getGameTime();
            PoseStack poseStack = iris ? viewStack(mc) : event.getPoseStack();
            VertexBuffer mesh = AuraMeshFactory.getBillboardQuad();
            RenderType renderType = auraType(tex);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            for (int id : KiSenseScan.getSearchEntities()) {
               Entity entity = mc.level.getEntity(id);
               if (entity instanceof LivingEntity) {
                  LivingEntity living = (LivingEntity)entity;
                  if (living.isAlive()) {
                     double dist = (double)mc.player.distanceTo(living);
                     boolean lod = dist > 24.0;
                     double bp = (double)KiSenseScan.getCachedBP(id);
                     double ratio = bp / Math.max(1.0, myBP);
                     float[][] colors = auraColors(living, ratio);
                     float[] border = colors[0];
                     float[] inner = colors[1];
                     double lerpX = Mth.lerp((double)partialTick, living.xo, living.getX());
                     double lerpY = Mth.lerp((double)partialTick, living.yo, living.getY());
                     double lerpZ = Mth.lerp((double)partialTick, living.zo, living.getZ());
                     float bbHeight = living.getBbHeight();
                     float hitbox = Math.max(living.getBbWidth(), bbHeight);
                     float baseRadius = hitbox * 0.9F + 0.6F;
                     float powerMul = (float)Mth.clamp(1.0 + 0.5 * Math.log10(Math.max(1.0, ratio)), 1.0, 3.0);
                     float speed = lod ? 0.0F : ((float)gameTime + partialTick) * 0.5F;
                     float scale = baseRadius * powerMul;
                     drawAuraLayer(poseStack, camera, camPos, lerpX, lerpY, lerpZ, bbHeight, proj, mesh, renderType, tex, shader, border, scale, 0.9F, speed);
                     drawAuraLayer(
                        poseStack, camera, camPos, lerpX, lerpY, lerpZ, bbHeight, proj, mesh, renderType, tex, shader, inner, scale * 0.66F, 0.5F, speed * 1.2F
                     );
                     drawAuraLayer(
                        poseStack,
                        camera,
                        camPos,
                        lerpX,
                        lerpY,
                        lerpZ,
                        bbHeight,
                        proj,
                        mesh,
                        renderType,
                        tex,
                        shader,
                        inner,
                        scale * 0.78F,
                        0.4F,
                        speed * 1.45F
                     );
                  }
               }
            }

            VertexBuffer.unbind();
            shader.clear();
         }
      }
   }

   private static void drawAuraLayer(
      PoseStack poseStack,
      Camera camera,
      Vec3 camPos,
      double lerpX,
      double lerpY,
      double lerpZ,
      float bbHeight,
      Matrix4f proj,
      VertexBuffer mesh,
      RenderType renderType,
      ResourceLocation tex,
      ShaderInstance shader,
      float[] color,
      float scale,
      float alpha,
      float speed
   ) {
      poseStack.pushPose();
      poseStack.translate(lerpX - camPos.x, lerpY - camPos.y + (double)bbHeight * 0.5, lerpZ - camPos.z);
      poseStack.mulPose(camera.rotation());
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
      poseStack.scale(scale, scale, scale);
      shader.safeGetUniform("speed").set(speed);
      shader.safeGetUniform("ProjMat").set(proj);
      shader.safeGetUniform("color1").set(color[0] * 1.6F, color[1] * 1.6F, color[2] * 1.6F, 1.0F);
      shader.safeGetUniform("color2").set(color[0] * 1.3F, color[1] * 1.3F, color[2] * 1.3F, 1.0F);
      shader.safeGetUniform("color3").set(color[0], color[1], color[2], 0.85F);
      shader.safeGetUniform("color4").set(color[0] * 0.75F, color[1] * 0.75F, color[2] * 0.75F, 0.65F);
      shader.safeGetUniform("alp1").set(alpha);
      shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
      customSetup(renderType, tex, shader);
      mesh.bind();
      mesh.drawWithShader(poseStack.last().pose(), proj, shader);
      customClear(renderType);
      poseStack.popPose();
   }

   private static float[][] auraColors(LivingEntity entity, double ratio) {
      if (isPassive(entity)) {
         return new float[][]{{0.3F, 0.5F, 1.0F}, {0.55F, 0.85F, 1.0F}};
      } else if (ratio > 1.15) {
         return new float[][]{{1.0F, 0.25F, 0.2F}, {1.0F, 0.6F, 0.2F}};
      } else {
         return ratio < 0.85 ? new float[][]{{0.3F, 0.5F, 1.0F}, {0.55F, 0.85F, 1.0F}} : new float[][]{{1.0F, 0.9F, 0.4F}, {1.0F, 1.0F, 0.7F}};
      }
   }

   private static boolean isPassive(LivingEntity entity) {
      return !(entity instanceof Player) && !(entity instanceof Enemy) && !(entity instanceof NeutralMob);
   }

   private static PoseStack viewStack(Minecraft mc) {
      PoseStack stack = new PoseStack();
      Camera cam = mc.gameRenderer.getMainCamera();
      stack.mulPose(Axis.XP.rotationDegrees(cam.getXRot()));
      stack.mulPose(Axis.YP.rotationDegrees(cam.getYRot() + 180.0F));
      return stack;
   }

   private static RenderType auraType(ResourceLocation texture) {
      return IrisCompat.isShaderPackInUse() ? ModRenderTypes.getCustomAuraCompat(texture) : ModRenderTypes.getCustomAura(texture);
   }

   private static void customSetup(RenderType type, ResourceLocation texture, ShaderInstance shader) {
      if (IrisCompat.isShaderPackInUse()) {
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.enableDepthTest();
         RenderSystem.depthFunc(515);
         RenderSystem.depthMask(false);
         RenderSystem.disableCull();
         RenderSystem.setShaderTexture(0, texture);
         RenderSystem.setShader(() -> shader);
      } else {
         type.setupRenderState();
      }
   }

   private static void customClear(RenderType type) {
      if (IrisCompat.isShaderPackInUse()) {
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.disableBlend();
      } else {
         type.clearRenderState();
      }
   }
}
