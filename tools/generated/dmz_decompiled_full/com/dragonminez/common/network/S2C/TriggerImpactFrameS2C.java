package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.compat.DistExecutor;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

public class TriggerImpactFrameS2C {
   private final float threshold;
   private final float lerp;
   private final int duration;
   private final boolean invert;

   public TriggerImpactFrameS2C(float threshold, float lerp, int duration, boolean invert) {
      this.threshold = threshold;
      this.lerp = lerp;
      this.duration = duration;
      this.invert = invert;
   }

   public TriggerImpactFrameS2C(FriendlyByteBuf buf) {
      this.threshold = buf.readFloat();
      this.lerp = buf.readFloat();
      this.duration = buf.readInt();
      this.invert = buf.readBoolean();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeFloat(this.threshold);
      buf.writeFloat(this.lerp);
      buf.writeInt(this.duration);
      buf.writeBoolean(this.invert);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get()
         .enqueueWork(
            () -> DistExecutor.unsafeRunWhenOn(
                  Dist.CLIENT, () -> () -> ClientPacketHandler.handleImpactFrame(this.threshold, this.lerp, this.duration, this.invert)
               )
         );
      ctx.get().setPacketHandled(true);
   }
}
