package com.dragonminez.common.network.C2S;

import com.dragonminez.common.init.block.entity.GravityDeviceBlockEntity;
import com.dragonminez.common.init.menu.menutypes.GravityDeviceMenu;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class GravityDeviceUpdateC2S {
   private final BlockPos pos;
   private final boolean active;
   private final int gravity;

   public GravityDeviceUpdateC2S(BlockPos pos, boolean active, int gravity) {
      this.pos = pos;
      this.active = active;
      this.gravity = gravity;
   }

   public GravityDeviceUpdateC2S(FriendlyByteBuf buf) {
      this.pos = buf.readBlockPos();
      this.active = buf.readBoolean();
      this.gravity = buf.readInt();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeBlockPos(this.pos);
      buf.writeBoolean(this.active);
      buf.writeInt(this.gravity);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> {
         ServerPlayer player = ctx.get().getSender();
         if (player != null) {
            if (!(player.containerMenu instanceof GravityDeviceMenu menu) || !menu.getBlockPos().equals(this.pos)) {
               return;
            }

            if (player.level().isLoaded(this.pos)) {
               if (!(player.distanceToSqr((double)this.pos.getX() + 0.5, (double)this.pos.getY() + 0.5, (double)this.pos.getZ() + 0.5) > 64.0)) {
                  if (player.level().getBlockEntity(this.pos) instanceof GravityDeviceBlockEntity device) {
                     device.applyMenuInput(this.active, this.gravity);
                  }
               }
            }
         }
      });
      ctx.get().setPacketHandled(true);
   }
}
