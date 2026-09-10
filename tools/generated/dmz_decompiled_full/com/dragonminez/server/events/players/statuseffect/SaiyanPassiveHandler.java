package com.dragonminez.server.events.players.statuseffect;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.server.events.players.IStatusEffectHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;

public class SaiyanPassiveHandler implements IStatusEffectHandler {
   private static final Map<UUID, Integer> SAIYAN_ZENKAI_SECONDS = new HashMap<>();

   @Override
   public void handleStatusEffects(ServerPlayer player, StatsData data) {
   }

   @Override
   public void onPlayerTick(ServerPlayer serverPlayer, StatsData data) {
   }

   @Override
   public void onPlayerSecond(ServerPlayer serverPlayer, StatsData data) {
      if (ConfigManager.getServerConfig().getRacialSkills().getEnableRacialSkills()
         && ConfigManager.getServerConfig().getRacialSkills().getSaiyanRacialSkill()
         && ConfigManager.getRaceCharacter(data.getCharacter().getRace()).getRacialSkill().equals("saiyan")) {
         handleSaiyanPassive(serverPlayer, data);
      } else if (SAIYAN_ZENKAI_SECONDS.containsKey(serverPlayer.getUUID())) {
         resetSaiyanZenkaiTimer(serverPlayer);
      }
   }

   private static void handleSaiyanPassive(ServerPlayer player, StatsData data) {
      GeneralServerConfig.RacialSkillsConfig config = ConfigManager.getServerConfig().getRacialSkills();
      if (data.getLevel() < config.getSaiyanZenkaiMinLevel()) {
         resetSaiyanZenkaiTimer(player);
      } else if (data.getResources().getRacialSkillCount() >= config.getSaiyanZenkaiAmount()) {
         resetSaiyanZenkaiTimer(player);
      } else if (data.getCooldowns().hasCooldown("Zenkai")) {
         resetSaiyanZenkaiTimer(player);
      } else {
         float maxHealth = player.getMaxHealth();
         if ((double)player.getHealth() <= (double)maxHealth * 0.15) {
            int seconds = SAIYAN_ZENKAI_SECONDS.getOrDefault(player.getUUID(), 0) + 1;
            SAIYAN_ZENKAI_SECONDS.put(player.getUUID(), seconds);
            int remaining = 8 - seconds;
            if (remaining > 0) {
               player.displayClientMessage(Component.translatable("message.dragonminez.racial.zenkai.approaching", new Object[]{remaining}), true);
            }

            if (SAIYAN_ZENKAI_SECONDS.getOrDefault(player.getUUID(), 0) >= 8) {
               player.heal((float)((double)maxHealth * config.getSaiyanZenkaiHealthRegen()));
               double boostMult = config.getSaiyanZenkaiStatBoost();
               String[] statsToBoost = config.getSaiyanZenkaiBoosts();
               int maxBonus = ConfigManager.getServerConfig().getGameplay().getMaxValue();

               for (String statKey : statsToBoost) {
                  int currentStat = getStatValue(data, statKey);
                  int bonus = (int)Math.max(1.0, Math.min((double)maxBonus, (double)currentStat * boostMult));
                  data.getBonusStats().addBonusSplit(statKey, "Zenkai_" + (data.getResources().getRacialSkillCount() + 1), "+", (double)bonus, true);
               }

               player.displayClientMessage(Component.translatable("message.dragonminez.racial.zenkai.used"), true);
               player.level()
                  .playSound(null, player.getX(), player.getY(), player.getZ(), (SoundEvent)MainSounds.TRANSFORM_ON.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
               data.getResources().addRacialSkillCount(1);
               data.getCooldowns().setCooldown("Zenkai", config.getSaiyanZenkaiCooldownSeconds() * 20);
               player.addEffect(new MobEffectInstance(MainEffects.SAIYAN_PASSIVE, config.getSaiyanZenkaiCooldownSeconds() * 20, 0, false, false, true));
               NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
               resetSaiyanZenkaiTimer(player);
            }
         } else {
            resetSaiyanZenkaiTimer(player);
         }
      }
   }

   private static void resetSaiyanZenkaiTimer(ServerPlayer player) {
      SAIYAN_ZENKAI_SECONDS.remove(player.getUUID());
   }

   private static int getStatValue(StatsData data, String statName) {
      return switch (statName) {
         case "STR" -> data.getStats().getStrength();
         case "SKP" -> data.getStats().getStrikePower();
         case "RES" -> data.getStats().getResistance();
         case "VIT" -> data.getStats().getVitality();
         case "PWR" -> data.getStats().getKiPower();
         case "ENE" -> data.getStats().getEnergy();
         default -> 0;
      };
   }
}
