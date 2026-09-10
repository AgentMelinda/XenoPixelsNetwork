package com.dragonminez.common.network.C2S;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class UpgradeTechniqueC2S {
   private final String techniqueId;
   private final String statType;

   public UpgradeTechniqueC2S(String techniqueId, String statType) {
      this.techniqueId = techniqueId;
      this.statType = statType;
   }

   public UpgradeTechniqueC2S(FriendlyByteBuf buf) {
      this.techniqueId = buf.readUtf();
      this.statType = buf.readUtf();
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeUtf(this.techniqueId);
      buf.writeUtf(this.statType);
   }

   public boolean handle(Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(
         () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
               StatsProvider.get(StatsCapability.INSTANCE, player)
                  .ifPresent(
                     data -> {
                        TechniqueData tech = data.getTechniques().getUnlockedTechniques().get(this.techniqueId);
                        if (tech != null) {
                           int cost = tech instanceof KiAttackData ki
                              ? ki.getUpgradeXpCost(this.statType)
                              : (tech instanceof StrikeAttackData st ? st.getUpgradeXpCost(this.statType) : 100);
                           if (tech.getExperience() >= cost) {
                              if (tech instanceof KiAttackData kix && !kix.canUpgradeStat(this.statType)) {
                                 return;
                              }

                              if (tech instanceof StrikeAttackData stx && !stx.canUpgradeStat(this.statType)) {
                                 return;
                              }

                              tech.setExperience(tech.getExperience() - cost);
                              String var11 = this.statType;
                              switch (var11) {
                                 case "damage":
                                    if (tech instanceof KiAttackData kix) {
                                       kix.setDamageMultiplier(kix.getDamageMultiplier() + 0.05F);
                                       kix.setDamageLevel(kix.getDamageLevel() + 1);
                                    } else if (tech instanceof StrikeAttackData stx) {
                                       stx.setDamageMultiplier(stx.getDamageMultiplier() + 0.075F);
                                       stx.setDamageLevel(stx.getDamageLevel() + 1);
                                    }
                                    break;
                                 case "size":
                                    if (tech instanceof KiAttackData kix) {
                                       kix.setSize(kix.getSize() + 0.1F);
                                       kix.setSizeLevel(kix.getSizeLevel() + 1);
                                    }
                                    break;
                                 case "speed":
                                    if (tech instanceof KiAttackData kix) {
                                       kix.setSpeed(kix.getSpeed() + 0.05F);
                                       kix.setSpeedLevel(kix.getSpeedLevel() + 1);
                                    }
                                    break;
                                 case "armor_pen":
                                    if (tech instanceof KiAttackData kix) {
                                       kix.setArmorPenetration(kix.getArmorPenetration() + 1);
                                       kix.setArmorPenLevel(kix.getArmorPenLevel() + 1);
                                    }
                                    break;
                                 case "cooldown":
                                    if (tech instanceof KiAttackData kix) {
                                       kix.setCooldownLevel(kix.getCooldownLevel() + 1);
                                    } else if (tech instanceof StrikeAttackData stx) {
                                       stx.setCooldownLevel(stx.getCooldownLevel() + 1);
                                    }
                                    break;
                                 case "cast":
                                    if (tech instanceof KiAttackData kix) {
                                       kix.setCastTimeLevel(kix.getCastTimeLevel() + 1);
                                    }
                              }

                              if (tech instanceof KiAttackData kix) {
                                 kix.calculateDerivedValues();
                              }

                              NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
                           }
                        }
                     }
                  );
            }
         }
      );
      context.setPacketHandled(true);
      return true;
   }
}
