package com.dragonminez.client.render.shader;

import com.dragonminez.common.init.block.entity.GravityDeviceBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

@EventBusSubscriber(
   modid = "dragonminez",
   value = {Dist.CLIENT}
)
public final class GravityRangeRenderer {
   private static final Set<BlockPos> SHOWN = new HashSet<>();

   private GravityRangeRenderer() {
   }

   public static void toggle(BlockPos pos) {
      BlockPos key = pos.immutable();
      if (!SHOWN.remove(key)) {
         SHOWN.add(key);
      }
   }

   @SubscribeEvent
   public static void onRenderLevelStage(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_TRANSLUCENT_BLOCKS) {
         if (!SHOWN.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
               Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
               PoseStack poseStack = event.getPoseStack();
               BufferSource buffer = mc.renderBuffers().bufferSource();
               VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
               poseStack.pushPose();
               poseStack.translate(-cam.x, -cam.y, -cam.z);
               Iterator<BlockPos> it = SHOWN.iterator();

               while (it.hasNext()) {
                  BlockPos pos = it.next();
                  BlockEntity be = mc.level.getBlockEntity(pos);
                  if (be instanceof GravityDeviceBlockEntity) {
                     GravityDeviceBlockEntity device = (GravityDeviceBlockEntity)be;
                     boolean valid = device.isRoomValid();
                     AABB box;
                     if (valid) {
                        BlockPos min = device.getRoomMin();
                        BlockPos max = device.getRoomMax();
                        box = new AABB(
                           (double)min.getX(),
                           (double)min.getY(),
                           (double)min.getZ(),
                           (double)max.getX() + 1.0,
                           (double)max.getY() + 1.0,
                           (double)max.getZ() + 1.0
                        );
                     } else {
                        box = new AABB(pos).inflate(0.02);
                     }

                     float r = valid ? 0.2F : 1.0F;
                     float g = valid ? 1.0F : 0.2F;
                     float b = valid ? 0.4F : 0.2F;
                     LevelRenderer.renderLineBox(poseStack, consumer, box, r, g, b, 0.9F);
                  } else {
                     it.remove();
                  }
               }

               poseStack.popPose();
               buffer.endBatch(RenderType.lines());
            }
         }
      }
   }
}
