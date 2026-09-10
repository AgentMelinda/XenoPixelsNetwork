package com.dragonminez.common.stats.extras;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;

public class DynamicGrowthData {
   private static final int MAX_TRACKED_TARGETS = 128;
   private final Map<String, Double> practiceXp = new HashMap<>();
   private final Map<String, DynamicGrowthData.TargetHistory> targetHistory = new HashMap<>();
   private final Set<String> disabledStats = new HashSet<>();
   private long lastCombatMs;

   public boolean isGrowthEnabled(DynamicGrowthStat stat) {
      return !this.disabledStats.contains(stat.key());
   }

   public void setGrowthEnabled(DynamicGrowthStat stat, boolean enabled) {
      if (enabled) {
         this.disabledStats.remove(stat.key());
      } else {
         this.disabledStats.add(stat.key());
      }
   }

   public double getPracticeXp(DynamicGrowthStat stat) {
      double value = this.practiceXp.getOrDefault(stat.key(), 0.0);
      return Double.isFinite(value) && value > 0.0 ? value : 0.0;
   }

   public void resetPracticeXp(DynamicGrowthStat stat) {
      this.practiceXp.put(stat.key(), 0.0);
   }

   public void addPracticeXp(DynamicGrowthStat stat, double amount) {
      if (Double.isFinite(amount) && !(amount <= 0.0)) {
         double updated = this.getPracticeXp(stat) + amount;
         this.practiceXp.put(stat.key(), Double.isFinite(updated) ? Math.max(0.0, updated) : 0.0);
      }
   }

   public void consumePracticeXp(DynamicGrowthStat stat, double amount) {
      this.practiceXp.put(stat.key(), Math.max(0.0, this.getPracticeXp(stat) - amount));
   }

   public double recordTargetAndGetMultiplier(
      String targetKey, long nowMs, int windowSeconds, int softCap, int hardCap, double softMultiplier, double hardMultiplier
   ) {
      if (targetKey != null && !targetKey.isEmpty()) {
         long windowMs = (long)Math.max(1, windowSeconds) * 1000L;
         DynamicGrowthData.TargetHistory history = this.targetHistory.computeIfAbsent(targetKey, key -> new DynamicGrowthData.TargetHistory(nowMs));
         if (nowMs - history.windowStartMs > windowMs) {
            history.windowStartMs = nowMs;
            history.count = 0;
         }

         history.count++;
         history.lastSeenMs = nowMs;
         this.pruneTargets();
         if (hardCap > 0 && history.count > hardCap) {
            return hardMultiplier;
         } else {
            return softCap > 0 && history.count > softCap ? softMultiplier : 1.0;
         }
      } else {
         return 1.0;
      }
   }

   public void markCombat(long nowMs) {
      this.lastCombatMs = nowMs;
   }

   public boolean isRecentlyInCombat(long nowMs, long windowMs) {
      return this.lastCombatMs > 0L && nowMs - this.lastCombatMs <= windowMs;
   }

   public void clearCombatMemory() {
      this.targetHistory.clear();
      this.lastCombatMs = 0L;
   }

   public boolean hasProgress() {
      for (double xp : this.practiceXp.values()) {
         if (xp > 0.0) {
            return true;
         }
      }

      return false;
   }

   private void pruneTargets() {
      if (this.targetHistory.size() > 128) {
         long oldestSeen = Long.MAX_VALUE;
         String oldestKey = null;

         for (Entry<String, DynamicGrowthData.TargetHistory> entry : this.targetHistory.entrySet()) {
            if (entry.getValue().lastSeenMs < oldestSeen) {
               oldestSeen = entry.getValue().lastSeenMs;
               oldestKey = entry.getKey();
            }
         }

         if (oldestKey != null) {
            this.targetHistory.remove(oldestKey);
         }

         if (this.targetHistory.size() > 128) {
            Iterator<String> iterator = this.targetHistory.keySet().iterator();

            while (this.targetHistory.size() > 128 && iterator.hasNext()) {
               iterator.next();
               iterator.remove();
            }
         }
      }
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      CompoundTag xpTag = new CompoundTag();

      for (Entry<String, Double> entry : this.practiceXp.entrySet()) {
         xpTag.putDouble(entry.getKey(), entry.getValue());
      }

      tag.put("PracticeXp", xpTag);
      if (!this.disabledStats.isEmpty()) {
         ListTag disabledTag = new ListTag();

         for (String key : this.disabledStats) {
            disabledTag.add(StringTag.valueOf(key));
         }

         tag.put("DisabledStats", disabledTag);
      }

      return tag;
   }

   public void load(CompoundTag tag) {
      this.practiceXp.clear();
      CompoundTag xpTag = tag.getCompound("PracticeXp");

      for (String key : xpTag.getAllKeys()) {
         double value = xpTag.getDouble(key);
         this.practiceXp.put(key, Double.isFinite(value) && value > 0.0 ? value : 0.0);
      }

      this.disabledStats.clear();
      ListTag disabledTag = tag.getList("DisabledStats", 8);

      for (int i = 0; i < disabledTag.size(); i++) {
         this.disabledStats.add(disabledTag.getString(i));
      }
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeInt(this.practiceXp.size());

      for (Entry<String, Double> entry : this.practiceXp.entrySet()) {
         buf.writeUtf(entry.getKey());
         buf.writeDouble(entry.getValue());
      }

      buf.writeInt(this.disabledStats.size());

      for (String key : this.disabledStats) {
         buf.writeUtf(key);
      }
   }

   public void fromBytes(FriendlyByteBuf buf) {
      this.practiceXp.clear();
      int size = buf.readInt();

      for (int i = 0; i < size; i++) {
         String key = buf.readUtf();
         double value = buf.readDouble();
         this.practiceXp.put(key, Double.isFinite(value) && value > 0.0 ? value : 0.0);
      }

      this.disabledStats.clear();
      int disabledSize = buf.readInt();

      for (int i = 0; i < disabledSize; i++) {
         this.disabledStats.add(buf.readUtf());
      }
   }

   public void copyFrom(DynamicGrowthData other) {
      if (other != this) {
         this.practiceXp.clear();
         this.practiceXp.putAll(other.practiceXp);
         this.disabledStats.clear();
         this.disabledStats.addAll(other.disabledStats);
         this.targetHistory.clear();

         for (Entry<String, DynamicGrowthData.TargetHistory> entry : other.targetHistory.entrySet()) {
            this.targetHistory.put(entry.getKey(), entry.getValue().copy());
         }

         this.lastCombatMs = other.lastCombatMs;
      }
   }

   public void clear() {
      this.practiceXp.clear();
      this.targetHistory.clear();
      this.lastCombatMs = 0L;
   }

   private static class TargetHistory {
      private long windowStartMs;
      private long lastSeenMs;
      private int count;

      private TargetHistory(long windowStartMs) {
         this.windowStartMs = windowStartMs;
         this.lastSeenMs = windowStartMs;
      }

      private DynamicGrowthData.TargetHistory copy() {
         DynamicGrowthData.TargetHistory copy = new DynamicGrowthData.TargetHistory(this.windowStartMs);
         copy.lastSeenMs = this.lastSeenMs;
         copy.count = this.count;
         return copy;
      }
   }
}
