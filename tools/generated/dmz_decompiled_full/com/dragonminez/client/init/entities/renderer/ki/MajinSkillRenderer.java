package com.dragonminez.client.init.entities.renderer.ki;

import com.dragonminez.client.init.entities.model.MajinSkillModel;
import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.init.entities.MajinSkillEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

public class MajinSkillRenderer<T extends MajinSkillEntity> extends GeoEntityRenderer<T> {
   public MajinSkillRenderer(Context renderManager) {
      super(renderManager, new MajinSkillModel());
      this.shadowRadius = 0.4F;
   }

   public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return RenderType.entityCutout(texture);
   }

   public Color getRenderColor(T animatable, float partialTick, int packedLight) {
      int colorInt = animatable.getBodyColor();
      float[] rgb = ColorUtils.rgbIntToFloat(colorInt);
      return Color.ofRGBA(rgb[0], rgb[1], rgb[2], 1.0F);
   }
}
