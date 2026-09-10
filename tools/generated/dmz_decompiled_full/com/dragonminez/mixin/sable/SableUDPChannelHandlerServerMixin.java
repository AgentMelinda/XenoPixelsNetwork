package com.dragonminez.mixin.sable;

import com.dragonminez.common.compat.SableUdpNoise;
import io.netty.channel.ChannelHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   targets = {"dev.ryanhcode.sable.network.udp.handler.SableUDPChannelHandlerServer"},
   remap = false
)
public abstract class SableUDPChannelHandlerServerMixin {
   @Inject(
      method = {"exceptionCaught"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false,
      require = 1
   )
   private void dragonminez$ignoreForeignUdpNoise(ChannelHandlerContext ctx, Throwable cause, CallbackInfo ci) {
      if (SableUdpNoise.isInvalidPacketIdNoise(cause)) {
         ci.cancel();
      }
   }
}
