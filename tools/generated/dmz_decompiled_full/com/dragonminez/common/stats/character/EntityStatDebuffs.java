package com.dragonminez.common.stats.character;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

public final class EntityStatDebuffs {
   public static final Set<String> SUPPORTED = Set.of("STR", "PWR", "DEF", "HP_REGEN");

   private EntityStatDebuffs() {
   }

   public static boolean isSupported(String stat) {
      return stat != null && SUPPORTED.contains(stat.toUpperCase());
   }

   private static String factorKey(String stat) {
      return "dmz_debuff_" + stat + "_factor";
   }

   private static String untilKey(String stat) {
      return "dmz_debuff_" + stat + "_until";
   }

   public static void applyDebuff(LivingEntity entity, String stat, double factor, int durationTicks) {
      if (entity != null && !entity.level().isClientSide) {
         if (stat != null && !(factor >= 0.0) && durationTicks > 0) {
            String key = stat.toUpperCase();
            if (SUPPORTED.contains(key)) {
               CompoundTag data = entity.getPersistentData();
               long now = entity.level().getGameTime();
               boolean active = data.getLong(untilKey(key)) > now;
               if (!active || Math.abs(factor) >= Math.abs(data.getDouble(factorKey(key)))) {
                  data.putDouble(factorKey(key), factor);
               }

               long newUntil = now + (long)durationTicks;
               data.putLong(untilKey(key), active ? Math.max(data.getLong(untilKey(key)), newUntil) : newUntil);
            }
         }
      }
   }

   public static double getMultiplier(LivingEntity entity, String stat) {
      if (entity != null && stat != null) {
         String key = stat.toUpperCase();
         CompoundTag data = entity.getPersistentData();
         return data.getLong(untilKey(key)) <= entity.level().getGameTime() ? 1.0 : Math.max(0.0, 1.0 + data.getDouble(factorKey(key)));
      } else {
         return 1.0;
      }
   }

   public static boolean hasDebuff(LivingEntity entity, String stat) {
      return entity != null && stat != null ? entity.getPersistentData().getLong(untilKey(stat.toUpperCase())) > entity.level().getGameTime() : false;
   }
}
