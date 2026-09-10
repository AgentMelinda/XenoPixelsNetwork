package com.dragonminez.server.events.players.statuseffect;

import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.server.events.players.IStatusEffectHandler;
import com.dragonminez.server.util.PotionEffectHelper;
import net.minecraft.server.level.ServerPlayer;

public class CooldownEffectHandler implements IStatusEffectHandler {
   @Override
   public void handleStatusEffects(ServerPlayer player, StatsData data) {
      PotionEffectHelper.syncCooldownIndicator(player, data, "DashCooldown", MainEffects.DASH_CD);
      PotionEffectHelper.syncCooldownIndicator(player, data, "DoubleDashCooldown", MainEffects.DOUBLEDASH_CD);
      PotionEffectHelper.syncCooldownIndicator(player, data, "TeleportCooldown", MainEffects.TELEPORT_CD);
      PotionEffectHelper.syncCooldownIndicator(player, data, "FusionCooldown", MainEffects.FUSION_CD);
      PotionEffectHelper.syncCooldownIndicator(player, data, "KiBlastCooldown", MainEffects.KI_BLAST_CD);
      PotionEffectHelper.syncCooldownIndicator(player, data, "PoiseCooldown", MainEffects.POISE_CD);
      PotionEffectHelper.syncCooldownIndicator(player, data, "MajinReviveCooldown", MainEffects.MAJIN_REVIVE);
      PotionEffectHelper.syncCooldownIndicator(player, data, "Zenkai", MainEffects.SAIYAN_PASSIVE);
      PotionEffectHelper.syncCooldownIndicator(player, data, "Drain", MainEffects.BIOANDROID_PASSIVE);
   }

   @Override
   public void onPlayerTick(ServerPlayer serverPlayer, StatsData data) {
   }

   @Override
   public void onPlayerSecond(ServerPlayer serverPlayer, StatsData data) {
   }
}
