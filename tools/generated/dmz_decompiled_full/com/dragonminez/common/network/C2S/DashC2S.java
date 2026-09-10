package com.dragonminez.common.network.C2S;

import com.dragonminez.compat.network.NetworkEvent;
import com.dragonminez.server.events.players.combat.DashHandler;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class DashC2S {
   private final float xInput;
   private final float zInput;
   private final boolean isDoubleDash;

   public DashC2S(float xInput, float zInput, boolean isDoubleDash) {
      this.xInput = xInput;
      this.zInput = zInput;
      this.isDoubleDash = isDoubleDash;
   }

   public DashC2S(FriendlyByteBuf buffer) {
      this.xInput = buffer.readFloat();
      this.zInput = buffer.readFloat();
      this.isDoubleDash = buffer.readBoolean();
   }

   public void encode(FriendlyByteBuf buffer) {
      buffer.writeFloat(this.xInput);
      buffer.writeFloat(this.zInput);
      buffer.writeBoolean(this.isDoubleDash);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player != null) {
            DashHandler.handleDash(player, this.xInput, this.zInput, this.isDoubleDash);
         }
      });
      context.setPacketHandled(true);
   }
}
