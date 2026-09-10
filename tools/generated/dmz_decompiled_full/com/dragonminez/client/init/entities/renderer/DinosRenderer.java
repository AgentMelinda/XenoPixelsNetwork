package com.dragonminez.client.init.entities.renderer;

import com.dragonminez.client.init.entities.model.DinoGlobalModel;
import com.dragonminez.common.init.entities.animal.DinoGlobalEntity;
import com.dragonminez.common.init.entities.animal.SabertoothEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DinosRenderer<T extends DinoGlobalEntity> extends GeoEntityRenderer<T> {
   public DinosRenderer(Context renderManager) {
      super(renderManager, new DinoGlobalModel());
      this.shadowRadius = 0.8F;
   }

   public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return RenderType.entityCutout(texture);
   }

   public ResourceLocation getTextureLocation(T animatable) {
      return animatable instanceof SabertoothEntity tigre ? tigre.getCurrentTexture() : super.getTextureLocation(animatable);
   }
}
