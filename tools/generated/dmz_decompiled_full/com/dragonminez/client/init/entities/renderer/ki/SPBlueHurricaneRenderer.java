package com.dragonminez.client.init.entities.renderer.ki;

import com.dragonminez.client.init.entities.model.ki.SPSkillsModel;
import com.dragonminez.common.init.entities.ki.SPBlueHurricaneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

public class SPBlueHurricaneRenderer<T extends SPBlueHurricaneEntity> extends GeoEntityRenderer<T> {
   public SPBlueHurricaneRenderer(Context renderManager) {
      super(renderManager, new SPSkillsModel());
      this.shadowRadius = 0.8F;
   }

   public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return RenderType.entityTranslucentEmissive(texture);
   }

   public Color getRenderColor(T animatable, float partialTick, int packedLight) {
      if (!animatable.isFiring()) {
         return Color.ofRGBA(1.0F, 1.0F, 1.0F, 0.0F);
      } else {
         float fadeDuration = 10.0F;
         float currentTick = (float)(animatable.tickCount - animatable.getCastTime()) + partialTick;
         float maxLife = 140.0F;
         float remainingLife = maxLife - currentTick;
         float alpha = 1.0F;
         if (currentTick < fadeDuration) {
            alpha = currentTick / fadeDuration;
         } else if (remainingLife < fadeDuration) {
            alpha = remainingLife / fadeDuration;
         }

         alpha = Mth.clamp(alpha, 0.0F, 1.0F);
         return Color.ofRGBA(1.0F, 1.0F, 1.0F, alpha);
      }
   }
}
