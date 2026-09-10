package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.compat.DistExecutor;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

public class KnockbackFlightS2C {
   private final double x;
   private final double y;
   private final double z;

   public KnockbackFlightS2C(double x, double y, double z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public KnockbackFlightS2C(FriendlyByteBuf buf) {
      this.x = buf.readDouble();
      this.y = buf.readDouble();
      this.z = buf.readDouble();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeDouble(this.x);
      buf.writeDouble(this.y);
      buf.writeDouble(this.z);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get()
         .enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleKnockbackFlightPacket(this.x, this.y, this.z)));
      ctx.get().setPacketHandled(true);
   }
}
