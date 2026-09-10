package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.compat.DistExecutor;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

public class GravityZoneSyncS2C {
   private final float machineGravity;
   private final float environmentalGravity;
   private final float netGravity;
   private final float statMult;
   private final float tpGravityMult;
   private final int idealWeight;
   private final int totalWeight;
   private final int effectiveWeight;
   private final float loadRatio;
   private final float weightTpMult;
   private final int zone;

   public GravityZoneSyncS2C(
      float machineGravity,
      float environmentalGravity,
      float netGravity,
      float statMult,
      float tpGravityMult,
      int idealWeight,
      int totalWeight,
      int effectiveWeight,
      float loadRatio,
      float weightTpMult,
      int zone
   ) {
      this.machineGravity = machineGravity;
      this.environmentalGravity = environmentalGravity;
      this.netGravity = netGravity;
      this.statMult = statMult;
      this.tpGravityMult = tpGravityMult;
      this.idealWeight = idealWeight;
      this.totalWeight = totalWeight;
      this.effectiveWeight = effectiveWeight;
      this.loadRatio = loadRatio;
      this.weightTpMult = weightTpMult;
      this.zone = zone;
   }

   public GravityZoneSyncS2C(FriendlyByteBuf buf) {
      this.machineGravity = buf.readFloat();
      this.environmentalGravity = buf.readFloat();
      this.netGravity = buf.readFloat();
      this.statMult = buf.readFloat();
      this.tpGravityMult = buf.readFloat();
      this.idealWeight = buf.readVarInt();
      this.totalWeight = buf.readVarInt();
      this.effectiveWeight = buf.readVarInt();
      this.loadRatio = buf.readFloat();
      this.weightTpMult = buf.readFloat();
      this.zone = buf.readVarInt();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeFloat(this.machineGravity);
      buf.writeFloat(this.environmentalGravity);
      buf.writeFloat(this.netGravity);
      buf.writeFloat(this.statMult);
      buf.writeFloat(this.tpGravityMult);
      buf.writeVarInt(this.idealWeight);
      buf.writeVarInt(this.totalWeight);
      buf.writeVarInt(this.effectiveWeight);
      buf.writeFloat(this.loadRatio);
      buf.writeFloat(this.weightTpMult);
      buf.writeVarInt(this.zone);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get()
         .enqueueWork(
            () -> DistExecutor.unsafeRunWhenOn(
                  Dist.CLIENT,
                  () -> () -> ClientPacketHandler.handleGravityZoneSync(
                           this.machineGravity,
                           this.environmentalGravity,
                           this.netGravity,
                           this.statMult,
                           this.tpGravityMult,
                           this.idealWeight,
                           this.totalWeight,
                           this.effectiveWeight,
                           this.loadRatio,
                           this.weightTpMult,
                           this.zone
                        )
               )
         );
      ctx.get().setPacketHandled(true);
   }
}
