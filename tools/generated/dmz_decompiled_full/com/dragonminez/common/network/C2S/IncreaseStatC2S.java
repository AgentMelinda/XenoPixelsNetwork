package com.dragonminez.common.network.C2S;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.compat.network.NetworkEvent;
import com.dragonminez.server.events.players.StatsEvents;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class IncreaseStatC2S {
   private final IncreaseStatC2S.StatType statType;
   private final int multiplier;

   public IncreaseStatC2S(IncreaseStatC2S.StatType statType, int multiplier) {
      this.statType = statType;
      this.multiplier = multiplier;
   }

   public static void encode(IncreaseStatC2S msg, FriendlyByteBuf buf) {
      buf.writeEnum(msg.statType);
      buf.writeInt(msg.multiplier);
   }

   public static IncreaseStatC2S decode(FriendlyByteBuf buf) {
      return new IncreaseStatC2S((IncreaseStatC2S.StatType)buf.readEnum(IncreaseStatC2S.StatType.class), buf.readInt());
   }

   public static void handle(IncreaseStatC2S msg, Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> {
         ServerPlayer player = ctx.get().getSender();
         if (player != null) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               String statNameStr = msg.statType.name();
               int pendingAP = data.getResources().getPendingAttributePoints();
               float availableTPs = data.getResources().getTrainingPoints();
               if (pendingAP > 0 || !(availableTPs <= 0.0F)) {
                  int maxStats = data.getConfiguredMaxValue();
                  int statsCanIncrease = data.getMaxAllowedIncreaseForStat(statNameStr, msg.multiplier);
                  if (statsCanIncrease > 0) {
                     int apToUse = Math.min(pendingAP, statsCanIncrease);
                     boolean changed = false;
                     if (apToUse > 0) {
                        increaseStat(data, player, statNameStr, apToUse);
                        data.getResources().removePendingAttributePoints(apToUse);
                        changed = true;
                     }

                     int remaining = statsCanIncrease - apToUse;
                     if (remaining > 0 && availableTPs > 0.0F) {
                        int remainingCap = data.getMaxAllowedIncreaseForStat(statNameStr, remaining);
                        if (remainingCap > 0) {
                           int tpStats = data.calculateStatIncrease(remainingCap, availableTPs, maxStats);
                           if (tpStats > 0) {
                              int tpCost = data.calculateRecursiveCost(tpStats, maxStats);
                              if ((float)tpCost <= availableTPs) {
                                 increaseStat(data, player, statNameStr, tpStats);
                                 data.getResources().removeTrainingPoints((float)tpCost);
                                 changed = true;
                              }
                           }
                        }
                     }

                     if (changed) {
                        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                     }
                  }
               }
            });
         }
      });
      ctx.get().setPacketHandled(true);
   }

   private static void increaseStat(StatsData data, ServerPlayer player, String statName, int amount) {
      String var4 = statName.toUpperCase();
      switch (var4) {
         case "STR":
            data.getStats().addStrength(amount);
            break;
         case "SKP":
            data.getStats().addStrikePower(amount);
            break;
         case "RES":
            float oldMaxStamina = data.getMaxStamina();
            data.getStats().addResistance(amount);
            data.getResources().grantMaxPoolIncrease(oldMaxStamina, data.getMaxStamina(), false);
            break;
         case "VIT":
            float oldHealthBonus = data.getHealthBonus();
            data.getStats().addVitality(amount);
            float newHealthBonus = data.getHealthBonus();
            float healthDiff = newHealthBonus - oldHealthBonus;
            if (healthDiff > 0.0F) {
               StatsEvents.applyHealthBonus(player);
               player.heal(healthDiff);
            }
            break;
         case "PWR":
            data.getStats().addKiPower(amount);
            break;
         case "ENE":
            float oldMaxEnergy = data.getMaxEnergy();
            data.getStats().addEnergy(amount);
            data.getResources().grantMaxPoolIncrease(oldMaxEnergy, data.getMaxEnergy(), true);
      }
   }

   public static enum StatType {
      STR,
      SKP,
      RES,
      VIT,
      PWR,
      ENE;
   }
}
