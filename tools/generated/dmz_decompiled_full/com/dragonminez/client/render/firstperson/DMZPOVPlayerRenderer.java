package com.dragonminez.client.render.firstperson;

import com.dragonminez.client.render.DMZPlayerRenderer;
import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.client.render.firstperson.dto.DMZCameraBuffer;
import com.dragonminez.client.render.firstperson.dto.FirstPersonManager;
import com.dragonminez.client.util.BoneVisibilityHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class DMZPOVPlayerRenderer<T extends AbstractClientPlayer & GeoAnimatable> extends DMZPlayerRenderer<T> {
   public DMZPOVPlayerRenderer(Context renderManager, GeoModel model) {
      super(renderManager, model);
   }

   protected void applyRotations(T animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
      this.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick, 1.0F);
   }

   protected void applyRotations(T animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
      LocalPlayer localPlayer = Minecraft.getInstance().player;
      if (localPlayer != null && animatable == localPlayer && FirstPersonManager.shouldRenderFirstPerson(animatable)) {
         Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
         Vec3 playerPos = localPlayer.getPosition(partialTick);
         Vector3f offset = FirstPersonManager.offsetFirstPersonView(localPlayer);
         float BODY_PUSHBACK_Z = 0.25F;
         Vec3 camShift = DMZCameraBuffer.getFirstPersonShift();
         Vector3f modelScale = poseStack.last().pose().getScale(new Vector3f());
         float invX = modelScale.x() != 0.0F ? 1.0F / modelScale.x() : 1.0F;
         float invY = modelScale.y() != 0.0F ? 1.0F / modelScale.y() : 1.0F;
         float invZ = modelScale.z() != 0.0F ? 1.0F / modelScale.z() : 1.0F;
         poseStack.translate(
            (playerPos.x - cameraPos.x - camShift.x) * (double)invX,
            (playerPos.y - cameraPos.y - camShift.y) * (double)invY,
            (playerPos.z - cameraPos.z - camShift.z) * (double)invZ
         );
         super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
         poseStack.translate((double)offset.x(), 0.0, (double)(offset.z() + 0.25F));
      } else {
         super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
      }
   }

   @Override
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
      int renderColour = animatable.isSpectator() ? ARGB32.color(38, colour) : colour;
      super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, renderColour);
      BoneVisibilityHandler.updateVisibility(model, animatable, this.caller);
   }

   public void renderRecursively(
      PoseStack poseStack,
      T animatable,
      GeoBone bone,
      RenderType renderType,
      MultiBufferSource bufferSource,
      VertexConsumer buffer,
      boolean isReRender,
      float partialTick,
      int packedLight,
      int packedOverlay,
      int colour
   ) {
      boolean originallyHidden = bone.isHidden();
      boolean isLocalPlayer = animatable == Minecraft.getInstance().player;
      if (isLocalPlayer && bone.getName().equals("head") && FirstPersonManager.shouldRenderFirstPerson(animatable) && !EntityPreviewRenderContext.isRendering()
         )
       {
         bone.setHidden(true);
      }

      super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
      bone.setHidden(originallyHidden);
   }

   public boolean shouldRender(T pLivingEntity, Frustum pCamera, double pCamX, double pCamY, double pCamZ) {
      return pLivingEntity == Minecraft.getInstance().player
         ? !pLivingEntity.isSleeping()
         : super.shouldRender(pLivingEntity, pCamera, pCamX, pCamY, pCamZ) && !pLivingEntity.isSleeping();
   }
}
