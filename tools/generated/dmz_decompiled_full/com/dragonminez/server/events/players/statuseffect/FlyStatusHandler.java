package com.dragonminez.server.events.players.statuseffect;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.server.events.players.IStatusEffectHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;

public class FlyStatusHandler implements IStatusEffectHandler {
   @Override
   public void handleStatusEffects(ServerPlayer player, StatsData data) {
      if (data.getSkills().isSkillActive("fly")) {
         if (!player.hasEffect(MainEffects.FLY)) {
            player.addEffect(new MobEffectInstance(MainEffects.FLY, -1, 0, false, false, true));
         }
      } else {
         player.removeEffect(MainEffects.FLY);
      }
   }

   @Override
   public void onPlayerTick(ServerPlayer serverPlayer, StatsData data) {
      if (data.getSkills().isSkillActive("fly") && !serverPlayer.isCreative() && !serverPlayer.isSpectator()) {
         serverPlayer.resetFallDistance();
         if (serverPlayer.horizontalCollision) {
            double dx = serverPlayer.getX() - serverPlayer.xOld;
            double dz = serverPlayer.getZ() - serverPlayer.zOld;
            double speed = Math.sqrt(dx * dx + dz * dz);
            double minImpactSpeed = 0.35;
            if (speed > minImpactSpeed) {
               float maxHealth = serverPlayer.getMaxHealth();
               double maxImpactSpeedRef = 1.5;
               double factor = (speed - minImpactSpeed) / (maxImpactSpeedRef - minImpactSpeed);
               factor = Mth.clamp(factor, 0.0, 1.0);
               float finalPct = (float)Mth.lerp(factor, 0.05F, 0.35F);
               float damage = maxHealth * finalPct;
               serverPlayer.hurt(serverPlayer.damageSources().flyIntoWall(), damage);
               serverPlayer.level()
                  .playSound(
                     null,
                     serverPlayer.getX(),
                     serverPlayer.getY(),
                     serverPlayer.getZ(),
                     SoundEvents.PLAYER_HURT,
                     SoundSource.PLAYERS,
                     1.0F,
                     (float)(0.5 + factor * 0.5)
                  );
            }
         }
      }
   }

   @Override
   public void onPlayerSecond(ServerPlayer serverPlayer, StatsData data) {
      handleFlightKiDrain(serverPlayer, data);
   }

   private static void handleFlightKiDrain(ServerPlayer player, StatsData data) {
      if (data.getSkills().isSkillActive("fly")) {
         if (!player.isCreative() && !player.isSpectator()) {
            int flyLevel = data.getSkills().getSkillLevel("fly");
            float baseCost = (float)ConfigManager.getCombatConfig().getBaselineFormDrain().intValue() / 4.0F;
            double basePercent = 0.03;
            double energyCostPercent = Math.max(0.002, basePercent - (double)flyLevel * 0.005);
            energyCostPercent *= (double)getFlyCostMultiplier(flyLevel);
            boolean isSprintFlight = player.isSprinting() && player.getDeltaMovement().length() > 0.65F;
            if (isSprintFlight) {
               energyCostPercent *= 2.0;
            }

            if (data.getStatus().getFlightMode() == 1) {
               energyCostPercent *= ConfigManager.getCombatConfig().getCombatFlyDrainMultiplier();
            }

            int energyCost = (int)Math.ceil((double)baseCost * energyCostPercent);
            float currentEnergy = data.getResources().getCurrentEnergy();
            if (currentEnergy >= (float)energyCost) {
               data.getResources().removeEnergy((float)energyCost);
            } else {
               data.getSkills().setSkillActive("fly", false);
               if (!player.isCreative() && !player.isSpectator()) {
                  player.getAbilities().mayfly = false;
                  player.getAbilities().flying = false;
                  player.onUpdateAbilities();
               }

               NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
            }
         }
      }
   }

   private static float getFlyCostMultiplier(int flyLevel) {
      int clampedLevel = Mth.clamp(flyLevel, 1, 10);
      float t = (float)(clampedLevel - 1) / 9.0F;
      return Mth.lerp(t, 4.0F, 1.0F);
   }
}
