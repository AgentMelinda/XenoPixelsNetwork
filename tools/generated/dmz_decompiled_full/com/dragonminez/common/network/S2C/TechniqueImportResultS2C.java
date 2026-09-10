package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.compat.DistExecutor;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

public class TechniqueImportResultS2C {
   private final TechniqueImportResultS2C.Status status;
   private final int value;

   public TechniqueImportResultS2C(TechniqueImportResultS2C.Status status, int value) {
      this.status = status;
      this.value = value;
   }

   public TechniqueImportResultS2C(FriendlyByteBuf buf) {
      this.status = (TechniqueImportResultS2C.Status)buf.readEnum(TechniqueImportResultS2C.Status.class);
      this.value = buf.readInt();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeEnum(this.status);
      buf.writeInt(this.value);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get()
         .enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleTechniqueImportResult(this.status, this.value)));
      ctx.get().setPacketHandled(true);
   }

   public static enum Status {
      INVALID,
      NOT_ENOUGH_TP,
      IMPORTED;
   }
}
