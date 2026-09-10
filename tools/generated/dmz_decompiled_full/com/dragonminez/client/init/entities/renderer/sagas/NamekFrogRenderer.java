package com.dragonminez.client.init.entities.renderer.sagas;

import com.dragonminez.client.init.entities.model.NamekFrogModel;
import com.dragonminez.common.init.entities.animal.NamekFrogEntity;
import com.dragonminez.common.init.entities.animal.NamekFrogGinyuEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NamekFrogRenderer<T extends NamekFrogEntity> extends GeoEntityRenderer<T> {
   private static final ResourceLocation GINYU_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/animal/namekfrog_0.png");

   public NamekFrogRenderer(Context renderManager) {
      super(renderManager, new NamekFrogModel());
      this.shadowRadius = 0.25F;
   }

   public ResourceLocation getTextureLocation(T animatable) {
      return animatable instanceof NamekFrogGinyuEntity ? GINYU_TEXTURE : animatable.getCurrentTexture();
   }

   public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return RenderType.entityCutout(texture);
   }
}
