package com.dragonminez.common.util;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

public final class ITTeleportHelper {
   private ITTeleportHelper() {
   }

   public static int extraKiCostForDistance(double distance) {
      return distance <= 0.0 ? 0 : (int)(distance / 25.0) * 5;
   }

   public static void applyTeleportCooldown(ServerPlayer player, StatsData data) {
      int ticks = Math.max(0, ConfigManager.getCombatConfig().getTeleportCooldownSeconds()) * 20;
      if (ticks > 0) {
         data.getCooldowns().setCooldown("TeleportCooldown", ticks);
         player.addEffect(new MobEffectInstance(MainEffects.TELEPORT_CD, ticks, 0, false, false, true));
      }
   }

   public static BlockPos findSafeTeleportPos(ServerLevel level, BlockPos center) {
      for (int r = 3; r <= 4; r++) {
         for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
               double dist = Math.sqrt((double)(x * x + z * z));
               if (dist >= 3.0 && dist <= 4.5) {
                  for (int y = 3; y >= -3; y--) {
                     BlockPos testPos = center.offset(x, y, z);
                     if (isSafe(level, testPos)) {
                        return testPos;
                     }
                  }
               }
            }
         }
      }

      return center.above(1);
   }

   public static boolean isSafe(ServerLevel level, BlockPos pos) {
      boolean hasFloor = !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
      boolean bodyClear = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
      boolean headClear = level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty() && level.getFluidState(pos.above()).isEmpty();
      return hasFloor && bodyClear && headClear;
   }
}
