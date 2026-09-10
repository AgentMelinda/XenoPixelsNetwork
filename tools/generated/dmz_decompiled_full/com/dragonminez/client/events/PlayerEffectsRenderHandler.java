package com.dragonminez.client.events;

import com.dragonminez.client.render.effects.AuraRenderer;
import com.dragonminez.client.render.shader.DMZShaders;
import com.dragonminez.client.render.shader.KiBloomRenderer;
import com.dragonminez.client.render.shader.TransformationPostShaderManager;
import com.dragonminez.client.render.util.IrisCompat;
import com.dragonminez.client.render.util.PlayerEffectQueue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent.Pre;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

@EventBusSubscriber(
   modid = "dragonminez",
   value = {Dist.CLIENT}
)
public class PlayerEffectsRenderHandler {
   private static final Set<Integer> CURRENT_FRAME_PLAYERS = new HashSet<>();

   @SubscribeEvent
   public static void onRenderTick(Pre event) {
      PlayerEffectQueue.getAndClearAuras();
      PlayerEffectQueue.getAndClearSparks();
      PlayerEffectQueue.getAndClearFirstPersonAuras();
      PlayerEffectQueue.getAndClearKiAttacks();
      PlayerEffectQueue.getAndClearEntityEffects();
   }

   @SubscribeEvent
   public static void onRenderLevelStage(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         boolean shaderPack = IrisCompat.isShaderPackInUse(mc.level.getGameTime());
         Stage stage = event.getStage();
         if (shaderPack) {
            if (stage == Stage.AFTER_SKY) {
               PlayerEffectQueue.getAndClearAuras();
               PlayerEffectQueue.getAndClearSparks();
               PlayerEffectQueue.getAndClearFirstPersonAuras();
               PlayerEffectQueue.getAndClearKiAttacks();
               PlayerEffectQueue.getAndClearEntityEffects();
               TransformationPostShaderManager.setShaderpackMainPass(true);
            } else if (stage == Stage.AFTER_LEVEL) {
               TransformationPostShaderManager.setShaderpackMainPass(false);
               mc.getMainRenderTarget().bindWrite(false);
               renderEffects(mc, event);
               TransformationPostShaderManager.processShaderpackOutline(event.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
         } else {
            if (stage == Stage.AFTER_LEVEL) {
               mc.getMainRenderTarget().bindWrite(false);
               renderEffects(mc, event);
            }
         }
      }
   }

   private static void renderEffects(Minecraft mc, RenderLevelStageEvent event) {
      BufferSource buffers = mc.renderBuffers().bufferSource();
      PoseStack poseStack = createDeferredEffectPose(event);
      Matrix4f projectionMatrix = event.getProjectionMatrix();
      float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
      long gameTime = mc.level.getGameTime();
      CURRENT_FRAME_PLAYERS.clear();
      boolean isFirstPerson = mc.options.getCameraType().isFirstPerson();
      boolean isCameraColliding = false;
      if (!isFirstPerson && mc.cameraEntity != null) {
         Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
         Vec3 entityPos = mc.cameraEntity.getPosition(partialTick);
         if (cameraPos.distanceToSqr(entityPos) < 0.25) {
            isCameraColliding = true;
         }
      }

      AuraRenderer.processFusionFlashes(mc, gameTime, partialTick, poseStack, buffers);
      buffers.endBatch();
      List<PlayerEffectQueue.KiRenderTask> kiAttacks = PlayerEffectQueue.getAndClearKiAttacks();
      if (!kiAttacks.isEmpty()) {
         float kiAlpha = isFirstPerson ? 0.35F : 0.85F;
         if (DMZShaders.ki3dShader != null) {
            DMZShaders.ki3dShader.safeGetUniform("globalAlpha").set(kiAlpha);
         }

         RenderSystem.depthMask(false);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableCull();

         for (PlayerEffectQueue.KiRenderTask task : kiAttacks) {
            task.render(poseStack, projectionMatrix);
         }

         RenderSystem.enableCull();
         RenderSystem.depthMask(true);
         RenderSystem.disableBlend();
         if (!IrisCompat.isShaderPackInUse(gameTime)) {
            KiBloomRenderer.render(kiAttacks, poseStack, projectionMatrix, partialTick);
         }

         if (DMZShaders.ki3dShader != null) {
            DMZShaders.ki3dShader.safeGetUniform("globalAlpha").set(1.0F);
         }
      }

      AuraRenderer.processThirdPersonAuras(mc, poseStack, projectionMatrix, CURRENT_FRAME_PLAYERS, isFirstPerson, isCameraColliding);
      AuraRenderer.processFirstPersonAuras(mc, poseStack, projectionMatrix, partialTick, CURRENT_FRAME_PLAYERS, isFirstPerson);
      AuraRenderer.processGhostAuras(mc, poseStack, projectionMatrix, partialTick, CURRENT_FRAME_PLAYERS);
      AuraRenderer.processSparks(poseStack, projectionMatrix, isFirstPerson);

      for (PlayerEffectQueue.DeferredEffectTask task : PlayerEffectQueue.getAndClearEntityEffects()) {
         task.render();
      }

      AuraRenderer.cleanCaches(CURRENT_FRAME_PLAYERS);
   }

   private static PoseStack createDeferredEffectPose(RenderLevelStageEvent event) {
      PoseStack poseStack = new PoseStack();
      poseStack.last().pose().set(event.getModelViewMatrix());
      poseStack.last().normal().set(new Matrix3f(event.getModelViewMatrix()));
      return poseStack;
   }
}
