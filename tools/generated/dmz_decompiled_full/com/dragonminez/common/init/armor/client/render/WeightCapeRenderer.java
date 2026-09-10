package com.dragonminez.common.init.armor.client.render;

import com.dragonminez.common.init.armor.client.model.WeightCapeModel;
import com.dragonminez.common.init.item.WeightItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class WeightCapeRenderer extends GeoArmorRenderer<WeightItem> {
   public WeightCapeRenderer() {
      super(new WeightCapeModel());
   }

   public ResourceLocation getTextureLocation(WeightItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/weighted_items.png");
   }

   public RenderType getRenderType(WeightItem animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return RenderType.armorCutoutNoCull(texture);
   }
}
