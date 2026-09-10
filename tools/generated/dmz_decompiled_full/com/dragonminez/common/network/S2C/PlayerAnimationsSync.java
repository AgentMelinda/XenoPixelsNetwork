package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.compat.DistExecutor;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

public class PlayerAnimationsSync {
   private final UUID playerUUID;
   private final boolean isFlying;

   public PlayerAnimationsSync(UUID playerUUID, boolean isFlying) {
      this.playerUUID = playerUUID;
      this.isFlying = isFlying;
   }

   public PlayerAnimationsSync(FriendlyByteBuf buf) {
      this.playerUUID = buf.readUUID();
      this.isFlying = buf.readBoolean();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeUUID(this.playerUUID);
      buf.writeBoolean(this.isFlying);
   }

   public boolean handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get()
         .enqueueWork(
            () -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handlePlayerAnimationsSyncPacket(this.playerUUID, this.isFlying))
         );
      ctx.get().setPacketHandled(true);
      return true;
   }
}
