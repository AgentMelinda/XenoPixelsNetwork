package com.dragonminez.common.network.C2S;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.network.S2C.TechniqueImportResultS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class ImportTechniqueC2S {
   private final String code;

   public ImportTechniqueC2S(String code) {
      this.code = code == null ? "" : code.trim();
   }

   public ImportTechniqueC2S(FriendlyByteBuf buf) {
      this.code = buf.readUtf(262144);
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeUtf(this.code, 262144);
   }

   public boolean handle(Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player != null) {
            KiAttackData imported = KiAttackData.importFromCode(this.code);
            if (imported == null) {
               NetworkHandler.sendToPlayer(new TechniqueImportResultS2C(TechniqueImportResultS2C.Status.INVALID, 0), player);
            } else {
               imported.calculateDerivedValues();
               int tpCost = Math.max(0, Math.round(imported.getTpCost()));
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
                  if (data.getResources().getTrainingPoints() < (float)tpCost) {
                     NetworkHandler.sendToPlayer(new TechniqueImportResultS2C(TechniqueImportResultS2C.Status.NOT_ENOUGH_TP, tpCost), player);
                  } else {
                     data.getResources().removeTrainingPoints((float)tpCost);
                     data.getTechniques().unlockTechnique(imported);
                     NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
                     NetworkHandler.sendToPlayer(new TechniqueImportResultS2C(TechniqueImportResultS2C.Status.IMPORTED, tpCost), player);
                  }
               });
            }
         }
      });
      context.setPacketHandled(true);
      return true;
   }
}
