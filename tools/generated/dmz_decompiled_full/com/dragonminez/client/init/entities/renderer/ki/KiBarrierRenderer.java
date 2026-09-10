package com.dragonminez.client.init.entities.renderer.ki;

import com.dragonminez.client.render.shader.DMZShaders;
import com.dragonminez.client.render.util.KiMeshFactory;
import com.dragonminez.client.render.util.PlayerEffectQueue;
import com.dragonminez.common.init.entities.ki.KiBarrierEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

public class KiBarrierRenderer extends EntityRenderer<KiBarrierEntity> {
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/ki/ki_laser.png");
   private static final float SHIELD_SURROUND_FACTOR = 1.45F;

   public KiBarrierRenderer(Context pContext) {
      super(pContext);
   }

   public void render(KiBarrierEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
      if (entity.isFiring()) {
         Matrix4f basePose = new Matrix4f(poseStack.last().pose());
         PlayerEffectQueue.addKiAttack(
            (stack, proj) -> {
               stack.pushPose();
               stack.last().pose().mul(basePose);
               float ageInTicks = (float)entity.tickCount + partialTick;
               float scale = entity.getCurrentSize() * 1.45F * resolveAnchorModelScale(entity);
               float[] coreColor = entity.getRgbColorMain();
               float[] borderColor = entity.getRgbColorBorder();
               float[] outlineColor = entity.getRgbColorOutline();
               float barrierAlpha = 0.95F;
               Minecraft mc = Minecraft.getInstance();
               if (mc.player != null
                  && mc.options.getCameraType().isFirstPerson()
                  && entity.getShieldHost() == mc.player.getId()
                  && entity.getOwner() != mc.player) {
                  barrierAlpha = 0.6F;
               }

               ShaderInstance shader = DMZShaders.ki3dShader;
               if (shader != null) {
                  shader.safeGetUniform("colorCore").set(coreColor[0], coreColor[1], coreColor[2]);
                  shader.safeGetUniform("colorBorder").set(borderColor[0], borderColor[1], borderColor[2]);
                  shader.safeGetUniform("colorOutline").set(outlineColor[0], outlineColor[1], outlineColor[2]);
                  shader.safeGetUniform("time").set(ageInTicks / 20.0F);
                  shader.safeGetUniform("ProjMat").set(proj);
                  VertexBuffer mesh = KiMeshFactory.getSphereMesh();
                  mesh.bind();
                  stack.pushPose();
                  stack.translate(0.0, (double)entity.getBbHeight() / 2.0, 0.0);
                  stack.scale(scale, scale, scale);
                  shader.safeGetUniform("ModelViewMat").set(stack.last().pose());
                  shader.safeGetUniform("alphaMult").set(barrierAlpha);
                  shader.apply();
                  mesh.drawWithShader(stack.last().pose(), proj, shader);
                  stack.popPose();
                  VertexBuffer.unbind();
                  shader.clear();
               }

               stack.popPose();
            }
         );
      }
   }

   private static float resolveAnchorModelScale(KiBarrierEntity entity) {
      LivingEntity anchor = null;
      int hostId = entity.getShieldHost();
      if (hostId >= 0) {
         if (entity.level().getEntity(hostId) instanceof LivingEntity host) {
            anchor = host;
         }
      } else if (entity.getOwner() instanceof LivingEntity owner) {
         anchor = owner;
      }

      if (anchor == null) {
         return 1.0F;
      } else {
         Optional<StatsData> statsOpt = StatsProvider.get(StatsCapability.INSTANCE, anchor).resolve();
         if (statsOpt.isEmpty()) {
            return 1.0F;
         } else {
            Float[] resolved = statsOpt.get().getCharacter().getResolvedModelScaling();
            return Math.max(resolved[0], Math.max(resolved[1], resolved[2]));
         }
      }
   }

   public ResourceLocation getTextureLocation(KiBarrierEntity pEntity) {
      return TEXTURE;
   }
}
