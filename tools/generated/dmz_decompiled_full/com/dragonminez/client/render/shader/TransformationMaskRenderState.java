package com.dragonminez.client.render.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;

public final class TransformationMaskRenderState {
   @Nullable
   private static RenderTarget currentMaskTarget;

   private TransformationMaskRenderState() {
   }

   public static void setCurrentTargets(@Nullable RenderTarget maskTarget) {
      currentMaskTarget = maskTarget;
   }

   public static void bindMaskTarget() {
      Minecraft mc = Minecraft.getInstance();
      RenderTarget target = currentMaskTarget != null ? currentMaskTarget : mc.getMainRenderTarget();
      target.bindWrite(false);
   }

   public static void bindMainTarget() {
      Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
   }
}
