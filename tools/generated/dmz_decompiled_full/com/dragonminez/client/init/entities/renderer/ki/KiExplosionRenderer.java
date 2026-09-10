package com.dragonminez.client.init.entities.renderer.ki;

import com.dragonminez.client.render.shader.DMZShaders;
import com.dragonminez.client.render.util.KiMeshFactory;
import com.dragonminez.client.render.util.PlayerEffectQueue;
import com.dragonminez.common.init.entities.ki.KiExplosionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class KiExplosionRenderer extends EntityRenderer<KiExplosionEntity> {
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/ki/ki_laser.png");

   public KiExplosionRenderer(Context pContext) {
      super(pContext);
   }

   public void render(KiExplosionEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
      Matrix4f basePose = new Matrix4f(poseStack.last().pose());
      PlayerEffectQueue.addKiAttack((stack, proj) -> {
         stack.pushPose();
         stack.last().pose().mul(basePose);
         float ageInTicks = (float)entity.tickCount + partialTick;
         boolean isFiring = entity.isFiring();
         float scale;
         if (!isFiring) {
            scale = entity.getSize();
         } else {
            int activeTicks = entity.tickCount - entity.getFireTick();
            scale = entity.getSize() + (float)activeTicks * 0.4F;
            float limit = Math.max(entity.getMaxRadius(), entity.getSize() * 5.0F);
            if (scale > limit) {
               scale = limit;
            }
         }

         float[] coreColor = entity.getRgbColorMain();
         float[] borderColor = entity.getRgbColorBorder();
         float[] outlineColor = entity.getRgbColorOutline();
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
            shader.safeGetUniform("alphaMult").set(0.65F);
            shader.apply();
            mesh.drawWithShader(stack.last().pose(), proj, shader);
            stack.popPose();
            VertexBuffer.unbind();
            shader.clear();
         }

         stack.popPose();
      });
   }

   public ResourceLocation getTextureLocation(KiExplosionEntity pEntity) {
      return TEXTURE;
   }
}
