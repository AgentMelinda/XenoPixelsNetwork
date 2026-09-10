package com.dragonminez.common.stats.character;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

public class Cooldowns {
   private final Map<String, Integer> cooldowns = new HashMap<>();
   public static final String SENZU_KARIN = "SenzuKarin";
   public static final String REVIVE_BABA = "Revive";
   public static final String ZENKAI = "Zenkai";
   public static final String DRAIN = "Drain";
   public static final String COMBAT = "CombatTimer";
   public static final String POISE_CD = "PoiseCooldown";
   public static final String FUSION_CD = "FusionCooldown";
   public static final String DRAIN_ACTIVE = "DrainActive";
   public static final String DASH_CD = "DashCooldown";
   public static final String DOUBLEDASH_CD = "DoubleDashCooldown";
   public static final String TELEPORT_CD = "TeleportCooldown";
   public static final String DASH_ACTIVE = "DashActive";
   public static final String KI_BLAST_CD = "KiBlastCooldown";
   public static final String MAJIN_REVIVE_ACTIVE = "MajinReviveActive";
   public static final String MAJIN_REVIVE_CD = "MajinReviveCooldown";
   public static final String KNOCKDOWN_DURATION = "KnockdownDuration";
   public static final String STAMINA_PAUSE = "StaminaPause";
   public static final String COMBAT_FLY_LOCK = "CombatFlyLock";
   public static final String COMBATFLY_IMPULSE_CD = "CombatFlyImpulseCd";

   public int getCooldown(String key) {
      return this.cooldowns.getOrDefault(key, 0);
   }

   public void setCooldown(String key, int value) {
      if (value <= 0) {
         this.cooldowns.remove(key);
      } else {
         this.cooldowns.put(key, value);
      }
   }

   public void addCooldown(String key, int amount) {
      int current = this.getCooldown(key);
      this.setCooldown(key, current + amount);
   }

   public void reduceCooldown(String key, int amount) {
      int current = this.getCooldown(key);
      this.setCooldown(key, Math.max(0, current - amount));
   }

   public boolean hasCooldown(String key) {
      return this.getCooldown(key) > 0;
   }

   public void removeCooldown(String key) {
      this.cooldowns.remove(key);
   }

   public void clearCooldowns() {
      this.cooldowns.clear();
   }

   public void tick() {
      this.cooldowns.replaceAll((key, value) -> Math.max(0, value - 1));
      this.cooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      this.cooldowns.forEach(tag::putInt);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.cooldowns.clear();

      for (String key : tag.getAllKeys()) {
         this.cooldowns.put(key, tag.getInt(key));
      }
   }

   public void copyFrom(Cooldowns other) {
      this.cooldowns.clear();
      this.cooldowns.putAll(other.cooldowns);
   }

   public Map<String, Integer> getAllCooldowns() {
      return new HashMap<>(this.cooldowns);
   }
}
