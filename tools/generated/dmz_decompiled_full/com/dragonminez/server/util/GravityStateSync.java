package com.dragonminez.server.util;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.GravityZoneSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class GravityStateSync {
   private static final Map<UUID, GravityStateSync.Snapshot> LAST = new HashMap<>();

   private GravityStateSync() {
   }

   public static void sync(ServerPlayer player) {
      GravityStateSync.Snapshot now = build(player);
      if (changed(LAST.get(player.getUUID()), now)) {
         LAST.put(player.getUUID(), now);
         NetworkHandler.sendToPlayer(
            new GravityZoneSyncS2C(
               now.machineGravity,
               now.environmentalGravity,
               now.netGravity,
               now.statMult,
               now.tpGravityMult,
               now.idealWeight,
               now.totalWeight,
               now.effectiveWeight,
               now.loadRatio,
               now.weightTpMult,
               now.zone
            ),
            player
         );
      }
   }

   public static void clear(UUID playerId) {
      LAST.remove(playerId);
   }

   private static GravityStateSync.Snapshot build(ServerPlayer player) {
      double tpGravityMult = StatsProvider.get(StatsCapability.INSTANCE, player).map(StatsData::getTpGravityMultiplier).orElse(1.0);
      return new GravityStateSync.Snapshot(
         (float)GravityLogic.getMachineGravity(player),
         (float)GravityLogic.getGravityMultiplier(player),
         (float)GravityLogic.getPenalizationGravity(player),
         (float)(1.0 - GravityLogic.getStatReduction(player)),
         (float)tpGravityMult,
         GravityLogic.getIdealWeight(player),
         GravityLogic.getTotalWeight(player),
         GravityLogic.getEffectiveWeight(player),
         (float)GravityLogic.getLoadRatio(player),
         (float)GravityLogic.getWeightTpMultiplier(player),
         GravityLogic.getTrainingZone(player)
      );
   }

   private static boolean changed(GravityStateSync.Snapshot a, GravityStateSync.Snapshot b) {
      return a == null
         ? true
         : Math.abs(a.machineGravity - b.machineGravity) >= 0.5F
            || Math.abs(a.environmentalGravity - b.environmentalGravity) >= 0.1F
            || Math.abs(a.netGravity - b.netGravity) >= 0.1F
            || Math.abs(a.statMult - b.statMult) >= 0.01F
            || Math.abs(a.tpGravityMult - b.tpGravityMult) >= 0.01F
            || Math.abs(a.weightTpMult - b.weightTpMult) >= 0.01F
            || Math.abs(a.loadRatio - b.loadRatio) >= 0.02F
            || a.idealWeight != b.idealWeight
            || a.totalWeight != b.totalWeight
            || a.effectiveWeight != b.effectiveWeight
            || a.zone != b.zone;
   }

   private static record Snapshot(
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
   }
}
