package com.dragonminez.mixin.sable;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   targets = {"dev.ryanhcode.sable.network.udp.SableUDPPacketDecoder"},
   remap = false
)
public abstract class SableUDPPacketDecoderMixin {
   private static final Logger DRAGONMINEZ$LOGGER = LoggerFactory.getLogger("dragonminez/sable-udp");
   private static final long DRAGONMINEZ$ALWAYS_LOG_FIRST = 5L;
   private static final long DRAGONMINEZ$THEREAFTER = 200L;
   private static final AtomicLong DRAGONMINEZ$DROPPED = new AtomicLong();

   @Inject(
      method = {"decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/channel/socket/DatagramPacket;Ljava/util/List;)V"},
      at = {@At(
         value = "NEW",
         target = "java/io/IOException"
      )},
      cancellable = true,
      remap = false,
      require = 1
   )
   private void dragonminez$softDropUnknownEnumId(ChannelHandlerContext ctx, DatagramPacket msg, List<?> out, CallbackInfo ci) {
      long seen = DRAGONMINEZ$DROPPED.incrementAndGet();
      if (seen <= 5L || seen % 200L == 0L) {
         DRAGONMINEZ$LOGGER.warn(
            "Dropped a Sable UDP datagram from {} with unknown packet id {} ({} so far). If this is the other end of your connection rather than stray traffic, its Sable is speaking ids this build does not know - check both sides run the same Sable version.",
            new Object[]{dragonminez$sender(msg), dragonminez$packetId(msg), seen}
         );
      }

      ci.cancel();
   }

   private static String dragonminez$packetId(DatagramPacket msg) {
      try {
         ByteBuf content = (ByteBuf)msg.content();
         int index = content.readerIndex() - 1;
         return index >= 0 && index < content.writerIndex() ? Integer.toString(content.getUnsignedByte(index)) : "?";
      } catch (Throwable var3) {
         return "?";
      }
   }

   private static String dragonminez$sender(DatagramPacket msg) {
      try {
         return String.valueOf(msg.sender());
      } catch (Throwable var2) {
         return "?";
      }
   }
}
