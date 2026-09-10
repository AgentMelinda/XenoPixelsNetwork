package com.dragonminez.client.init.entities.renderer.rr.layer;

import com.dragonminez.common.init.entities.redribbon.RedRibbonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class DMZRedRibbonItemInHandLayer<T extends RedRibbonEntity> extends GeoRenderLayer<T> {
   public DMZRedRibbonItemInHandLayer(GeoEntityRenderer<T> entityRendererIn) {
      super(entityRendererIn);
   }

   public void renderForBone(
      PoseStack poseStack,
      RedRibbonEntity animatable,
      GeoBone bone,
      RenderType renderType,
      MultiBufferSource bufferSource,
      VertexConsumer buffer,
      float partialTick,
      int packedLight,
      int packedOverlay
   ) {
      if (bone.getName().equals("right_hand_item")) {
         ItemStack mainHandItem = animatable.getItemBySlot(EquipmentSlot.MAINHAND);
         if (!mainHandItem.isEmpty()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(0.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(0.0F));
            poseStack.translate(0.4, 0.1, 0.73);
            Minecraft.getInstance()
               .getItemRenderer()
               .renderStatic(
                  mainHandItem,
                  ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                  packedLight,
                  packedOverlay,
                  poseStack,
                  bufferSource,
                  animatable.level(),
                  animatable.getId()
               );
            if (renderType != null) {
               bufferSource.getBuffer(renderType);
            }

            poseStack.popPose();
         }
      }
   }
}
