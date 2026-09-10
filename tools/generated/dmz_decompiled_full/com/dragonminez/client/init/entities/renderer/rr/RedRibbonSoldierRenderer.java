package com.dragonminez.client.init.entities.renderer.rr;

import com.dragonminez.client.render.layer.RedRibbonOutfitLayer;
import com.dragonminez.client.util.SkinCacheManager;
import com.dragonminez.common.init.entities.redribbon.RedRibbonSoldierEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class RedRibbonSoldierRenderer<T extends RedRibbonSoldierEntity> extends RedRibbonRenderer<T> {
   public RedRibbonSoldierRenderer(Context renderManager) {
      super(renderManager);
      this.shadowRadius = 0.4F;
      this.addRenderLayer(new RedRibbonOutfitLayer(this));
   }

   public ResourceLocation getTextureLocation(T animatable) {
      return SkinCacheManager.resolveTexture(animatable.getSkinOwner());
   }

   public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return RenderType.entityCutout(texture);
   }
}
