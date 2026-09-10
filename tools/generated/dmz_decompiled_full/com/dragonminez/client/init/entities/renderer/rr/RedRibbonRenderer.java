package com.dragonminez.client.init.entities.renderer.rr;

import com.dragonminez.client.init.entities.model.rr.RedRibbonModel;
import com.dragonminez.client.init.entities.renderer.rr.layer.DMZRedRibbonArmorLayer;
import com.dragonminez.client.init.entities.renderer.rr.layer.DMZRedRibbonItemInHandLayer;
import com.dragonminez.common.init.entities.redribbon.RedRibbonEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class RedRibbonRenderer<T extends RedRibbonEntity> extends GeoEntityRenderer<T> {
   public RedRibbonRenderer(Context renderManager) {
      super(renderManager, new RedRibbonModel());
      this.shadowRadius = 0.8F;
      this.addRenderLayer(new DMZRedRibbonItemInHandLayer(this));
      this.addRenderLayer(new DMZRedRibbonArmorLayer(this));
   }

   public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return RenderType.entityCutout(texture);
   }
}
