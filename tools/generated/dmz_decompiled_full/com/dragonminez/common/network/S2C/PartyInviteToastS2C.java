package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.compat.DistExecutor;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

public class PartyInviteToastS2C {
   private final String inviterName;

   public PartyInviteToastS2C(String inviterName) {
      this.inviterName = inviterName == null ? "" : inviterName;
   }

   public PartyInviteToastS2C(FriendlyByteBuf buf) {
      this.inviterName = buf.readUtf();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeUtf(this.inviterName);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handlePartyInviteToastPacket(this.inviterName)));
      context.setPacketHandled(true);
   }
}
