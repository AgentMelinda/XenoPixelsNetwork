package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.compat.DistExecutor;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

public class TaiyokenBlindS2C {
   private final int durationTicks;

   public TaiyokenBlindS2C(int durationTicks) {
      this.durationTicks = durationTicks;
   }

   public TaiyokenBlindS2C(FriendlyByteBuf buf) {
      this.durationTicks = buf.readInt();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeInt(this.durationTicks);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleTaiyokenBlind(this.durationTicks)));
      ctx.get().setPacketHandled(true);
   }
}
