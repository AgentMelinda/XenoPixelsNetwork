package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.common.network.ITTargetEntry;
import com.dragonminez.compat.DistExecutor;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

public class OpenITMenuS2C {
   private final List<ITTargetEntry> entries;

   public OpenITMenuS2C(List<ITTargetEntry> entries) {
      this.entries = entries;
   }

   public OpenITMenuS2C(FriendlyByteBuf buf) {
      int count = buf.readVarInt();
      this.entries = new ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         this.entries.add(ITTargetEntry.read(buf));
      }
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeVarInt(this.entries.size());

      for (ITTargetEntry entry : this.entries) {
         entry.write(buf);
      }
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      NetworkEvent.Context context = ctx.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleOpenITMenu(this.entries)));
      context.setPacketHandled(true);
   }
}
