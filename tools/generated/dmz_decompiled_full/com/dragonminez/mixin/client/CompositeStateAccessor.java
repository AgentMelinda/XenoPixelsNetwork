package com.dragonminez.mixin.client;

import net.minecraft.client.renderer.RenderStateShard.EmptyTextureStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({CompositeState.class})
public interface CompositeStateAccessor {
   @Accessor("textureState")
   EmptyTextureStateShard dmz$textureState();
}
