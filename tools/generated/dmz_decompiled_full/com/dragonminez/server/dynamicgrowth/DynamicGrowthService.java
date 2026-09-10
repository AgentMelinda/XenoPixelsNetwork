package com.dragonminez.server.dynamicgrowth;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.init.entities.MastersEntity;
import com.dragonminez.common.init.entities.ShadowDummyEntity;
import com.dragonminez.common.init.entities.dragon.DragonWishEntity;
import com.dragonminez.common.init.entities.namek.NamekTraderEntity;
import com.dragonminez.common.init.entities.namek.NamekVillagerEntity;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.extras.DynamicGrowthData;
import com.dragonminez.common.stats.extras.DynamicGrowthMath;
import com.dragonminez.common.stats.extras.DynamicGrowthStat;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

public final class DynamicGrowthService {
   private static final long COMBAT_WINDOW_MS = 15000L;

   private DynamicGrowthService() {
   }

   private static GeneralServerConfig.DynamicGrowthConfig config() {
      return ConfigManager.getServerConfig().getDynamicGrowth();
   }

   public static void award(ServerPlayer player, StatsData data, DynamicGrowthStat stat, double baseXp, LivingEntity repeatedTarget) {
      if (player != null && data != null) {
         GeneralServerConfig.DynamicGrowthConfig cfg = config();
         if (cfg.isEnabled()) {
            if (!(baseXp <= 0.0)) {
               if (!player.isCreative() && !player.isSpectator()) {
                  if (data.getStatus().isHasCreatedCharacter()) {
                     DynamicGrowthData growth = data.getDynamicGrowth();
                     if (growth.isGrowthEnabled(stat)) {
                        long nowMs = System.currentTimeMillis();
                        double xp = baseXp;
                        if (repeatedTarget != null) {
                           double repeatMultiplier = growth.recordTargetAndGetMultiplier(
                              repeatedTarget.getUUID().toString(),
                              nowMs,
                              cfg.getRepeatTargetWindowSeconds(),
                              cfg.getRepeatTargetSoftCap(),
                              cfg.getRepeatTargetHardCap(),
                              cfg.getRepeatTargetSoftMultiplier(),
                              cfg.getRepeatTargetHardMultiplier()
                           );
                           xp = baseXp * repeatMultiplier;
                        }

                        xp *= cfg.getPracticeXpMultiplier();
                        xp *= cfg.getStatPracticeMultiplier(stat.key());
                        if (Double.isFinite(xp) && !(xp <= 0.0)) {
                           int currentStat = data.getCurrentStatValue(stat.key());
                           int requiredXp = DynamicGrowthMath.requiredXp(currentStat);
                           if (requiredXp > 0) {
                              double perInstanceCap = (double)requiredXp * 0.1;
                              if (xp > perInstanceCap) {
                                 xp = perInstanceCap;
                              }
                           }

                           growth.addPracticeXp(stat, xp);
                           processLevelUps(player, data, stat);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void awardStaminaSpent(ServerPlayer player, StatsData data, double spent) {
      if (!(spent <= 0.0)) {
         award(player, data, DynamicGrowthStat.RES, spent * config().getStaminaSpentXpRatio(), null);
      }
   }

   public static void awardEnergySpent(ServerPlayer player, StatsData data, double spent) {
      if (!(spent <= 0.0)) {
         award(player, data, DynamicGrowthStat.ENE, spent * config().getEnergySpentXpRatio(), null);
      }
   }

   public static void awardStrike(ServerPlayer player, StatsData data, LivingEntity target, double damage) {
      double xp = practiceDamageXp(player, target, (float)damage);
      award(player, data, DynamicGrowthStat.SKP, xp, target);
   }

   public static void markCombat(StatsData data) {
      if (data != null) {
         data.getDynamicGrowth().markCombat(System.currentTimeMillis());
      }
   }

   public static boolean isRecentlyInCombat(StatsData data) {
      return data != null && data.getDynamicGrowth().isRecentlyInCombat(System.currentTimeMillis(), 15000L);
   }

   public static double practiceDamageXp(ServerPlayer player, LivingEntity target, float damage) {
      GeneralServerConfig.DynamicGrowthConfig cfg = config();
      double cappedDamage = Math.min((double)damage, Math.max(1.0, (double)target.getMaxHealth() * 0.2));
      double multiplier = 1.0;
      if (damage < 0.5F) {
         multiplier *= cfg.getLowDamagePracticeMultiplier();
      }

      if (target instanceof ShadowDummyEntity) {
         multiplier *= cfg.getShadowDummyPracticeMultiplier();
      } else if (isProtectedNpc(target)) {
         multiplier *= cfg.getVillagerPracticeMultiplier();
      } else if (target instanceof Animal) {
         multiplier *= cfg.getPassiveAnimalPracticeMultiplier();
      }

      if (isNoRiskTarget(player, target)) {
         multiplier *= cfg.getNoRiskPracticeMultiplier();
      }

      return cappedDamage * multiplier;
   }

   private static boolean isProtectedNpc(LivingEntity target) {
      return target instanceof Villager
         || target instanceof NamekVillagerEntity
         || target instanceof NamekTraderEntity
         || target instanceof MastersEntity
         || target instanceof DragonWishEntity;
   }

   private static boolean isNoRiskTarget(ServerPlayer player, LivingEntity target) {
      if (target instanceof Player || target instanceof Monster) {
         return false;
      } else if (!(target instanceof Mob mob)) {
         return true;
      } else {
         LivingEntity currentTarget = mob.getTarget();
         return currentTarget == null || !currentTarget.is(player);
      }
   }

   private static void processLevelUps(ServerPlayer player, StatsData data, DynamicGrowthStat stat) {
      DynamicGrowthData growth = data.getDynamicGrowth();
      int currentStat = data.getCurrentStatValue(stat.key());
      int requiredXp = DynamicGrowthMath.requiredXp(currentStat);
      double availableXp = growth.getPracticeXp(stat);
      boolean canIncrease = data.getMaxAllowedIncreaseForStat(stat.key(), 1) > 0;
      if (Double.isFinite(availableXp) && !(availableXp < 0.0) && (canIncrease || requiredXp <= 0 || !(availableXp > (double)requiredXp))) {
         if (canIncrease) {
            if (requiredXp > 0 && !(availableXp < (double)requiredXp)) {
               growth.resetPracticeXp(stat);
               grantStatPoint(player, data, stat);
               notifyStatGain(player, stat, data.getCurrentStatValue(stat.key()));
               NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
            }
         }
      } else {
         growth.resetPracticeXp(stat);
      }
   }

   private static void grantStatPoint(ServerPlayer player, StatsData data, DynamicGrowthStat stat) {
      switch (stat) {
         case RES:
            float oldMaxStamina = data.getMaxStamina();
            data.getStats().addResistance(1);
            float newMaxStamina = data.getMaxStamina();
            if (newMaxStamina > oldMaxStamina) {
               data.getResources().addStamina(newMaxStamina - oldMaxStamina);
            }
            break;
         case VIT:
            float oldMaxHealth = data.getMaxHealth();
            data.getStats().addVitality(1);
            float newMaxHealth = data.getMaxHealth();
            if (newMaxHealth > oldMaxHealth) {
               player.heal(newMaxHealth - oldMaxHealth);
            }
            break;
         case ENE:
            float oldMaxEnergy = data.getMaxEnergy();
            data.getStats().addEnergy(1);
            float newMaxEnergy = data.getMaxEnergy();
            if (newMaxEnergy > oldMaxEnergy) {
               data.getResources().addEnergy(newMaxEnergy - oldMaxEnergy);
            }
            break;
         case STR:
            data.getStats().addStrength(1);
            break;
         case SKP:
            data.getStats().addStrikePower(1);
            break;
         case PWR:
            data.getStats().addKiPower(1);
      }
   }

   private static void notifyStatGain(ServerPlayer player, DynamicGrowthStat stat, int newValue) {
      Component message = Component.translatable(
            "dynamicgrowth.dragonminez.stat_gain",
            new Object[]{Component.translatable("gui.dragonminez.character_stats." + stat.key().toLowerCase(Locale.ROOT)), newValue}
         )
         .withStyle(ChatFormatting.GREEN);
      player.displayClientMessage(message, true);
      player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.35F, 1.25F);
   }

   private static String fmt(double value) {
      return String.format(Locale.ROOT, "%.3f", value);
   }
}
