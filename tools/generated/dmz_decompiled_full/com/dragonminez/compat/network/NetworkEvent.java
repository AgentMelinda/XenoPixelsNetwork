package com.dragonminez.compat.network;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NetworkEvent {
   private NetworkEvent() {
   }

   public static Supplier<NetworkEvent.Context> wrap(IPayloadContext ctx) {
      NetworkEvent.Context context = new NetworkEvent.Context(ctx);
      return () -> context;
   }

   public static final class Context {
      private final IPayloadContext payloadContext;
      private final ServerPlayer sender;

      public Context(IPayloadContext payloadContext) {
         this.payloadContext = payloadContext;
         this.sender = payloadContext.player() instanceof ServerPlayer sp ? sp : null;
      }

      public Context(ServerPlayer sender) {
         this.payloadContext = null;
         this.sender = sender;
      }

      public void enqueueWork(Runnable task) {
         if (this.payloadContext != null) {
            this.payloadContext.enqueueWork(task);
         } else {
            task.run();
         }
      }

      public void setPacketHandled(boolean handled) {
      }

      public ServerPlayer getSender() {
         return this.sender;
      }

      public IPayloadContext getPayloadContext() {
         return this.payloadContext;
      }
   }
}
