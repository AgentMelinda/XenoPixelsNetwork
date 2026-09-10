package com.dragonminez.mixin.client;

import net.minecraft.client.renderer.RenderType.CompositeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(
   targets = {"net.minecraft.client.renderer.RenderType$CompositeRenderType"}
)
public interface CompositeRenderTypeAccessor {
   @Invoker("state")
   CompositeState dmz$state();
}
