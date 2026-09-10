package com.dragonminez.mixin.client;

import java.util.List;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({LivingEntityRenderer.class})
public interface LivingEntityRendererAccessor {
   @Accessor("layers")
   List<RenderLayer<?, ?>> dragonminez$getLayers();
}
