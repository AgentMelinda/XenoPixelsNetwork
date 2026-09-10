package com.dragonminez.common.stats.character;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public class BonusStats {
   private final Map<String, List<BonusStats.StatBonus>> bonuses = new HashMap<>();

   public BonusStats() {
      this.initializeStat("STR");
      this.initializeStat("SKP");
      this.initializeStat("DEF");
      this.initializeStat("STM");
      this.initializeStat("VIT");
      this.initializeStat("PWR");
      this.initializeStat("ENE");
   }

   private void initializeStat(String stat) {
      this.bonuses.put(stat, new ArrayList<>());
   }

   public void addBonusSplit(String stat, String bonusName, String operation, double value, boolean applyMultipliers) {
      if (stat.equalsIgnoreCase("RES")) {
         this.addBonus("DEF", bonusName, operation, value, applyMultipliers);
         this.addBonus("STM", bonusName, operation, value, applyMultipliers);
      } else {
         this.addBonus(stat, bonusName, operation, value, applyMultipliers);
      }
   }

   public void removeBonusSplit(String stat, String bonusName) {
      if (stat.equalsIgnoreCase("RES")) {
         this.removeBonus("DEF", bonusName);
         this.removeBonus("STM", bonusName);
      } else {
         this.removeBonus(stat, bonusName);
      }
   }

   public void clearBonusSplit(String stat, String bonusName) {
      if (stat.equalsIgnoreCase("RES")) {
         this.clearBonus("DEF", bonusName);
         this.clearBonus("STM", bonusName);
      } else {
         this.clearBonus(stat, bonusName);
      }
   }

   public void clearAllSplit(String stat) {
      if (stat.equalsIgnoreCase("RES")) {
         this.clearAll("DEF");
         this.clearAll("STM");
      } else {
         this.clearAll(stat);
      }
   }

   public void addBonus(String stat, String bonusName, String operation, double value) {
      this.addBonus(stat, bonusName, operation, value, false);
   }

   public void addBonus(String stat, String bonusName, String operation, double value, boolean applyMultipliers) {
      stat = stat.toUpperCase();
      if (this.bonuses.containsKey(stat)) {
         List<BonusStats.StatBonus> statBonuses = this.bonuses.get(stat);
         statBonuses.removeIf(bonus -> bonus.name.equals(bonusName));
         statBonuses.add(new BonusStats.StatBonus(bonusName, operation, value, applyMultipliers));
      }
   }

   public void removeBonus(String stat, String bonusName) {
      stat = stat.toUpperCase();
      if (this.bonuses.containsKey(stat)) {
         List<BonusStats.StatBonus> statBonuses = this.bonuses.get(stat);
         statBonuses.removeIf(bonus -> bonus.name.equals(bonusName));
      }
   }

   public void removeAllBonuses(String bonusName) {
      for (List<BonusStats.StatBonus> statBonuses : this.bonuses.values()) {
         statBonuses.removeIf(bonus -> bonus.name.equals(bonusName));
      }
   }

   public void clearBonus(String stat, String bonusName) {
      stat = stat.toUpperCase();
      if (this.bonuses.containsKey(stat)) {
         List<BonusStats.StatBonus> statBonuses = this.bonuses.get(stat);
         statBonuses.removeIf(bonus -> bonus.name.contains(bonusName));
      }
   }

   public void clearAll(String stat) {
      stat = stat.toUpperCase();
      if (this.bonuses.containsKey(stat)) {
         this.bonuses.get(stat).clear();
      }
   }

   public void clearAllStats() {
      for (List<BonusStats.StatBonus> bonusList : this.bonuses.values()) {
         bonusList.clear();
      }
   }

   public double calculateBonus(String stat, int baseStat, boolean getMultiplicable) {
      stat = stat.toUpperCase();
      if (!this.bonuses.containsKey(stat)) {
         return 0.0;
      } else {
         double flatResult = 0.0;
         double multiplierProduct = 1.0;

         for (BonusStats.StatBonus bonus : this.bonuses.get(stat)) {
            if (bonus.applyMultipliers == getMultiplicable) {
               String var11 = bonus.operation;
               switch (var11) {
                  case "+":
                     flatResult += bonus.value;
                     break;
                  case "-":
                     flatResult -= bonus.value;
                     break;
                  case "*":
                     multiplierProduct *= bonus.value;
               }
            }
         }

         return (double)baseStat * multiplierProduct - (double)baseStat + flatResult;
      }
   }

   public List<BonusStats.StatBonus> getBonuses(String stat) {
      stat = stat.toUpperCase();
      return !this.bonuses.containsKey(stat) ? new ArrayList<>() : new ArrayList<>(this.bonuses.get(stat));
   }

   public boolean hasBonus(String stat, String bonusName) {
      stat = stat.toUpperCase();
      return !this.bonuses.containsKey(stat) ? false : this.bonuses.get(stat).stream().anyMatch(bonus -> bonus.name.equals(bonusName));
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();

      for (Entry<String, List<BonusStats.StatBonus>> entry : this.bonuses.entrySet()) {
         ListTag bonusList = new ListTag();

         for (BonusStats.StatBonus bonus : entry.getValue()) {
            CompoundTag bonusTag = new CompoundTag();
            bonusTag.putString("Name", bonus.name);
            bonusTag.putString("Operation", bonus.operation);
            bonusTag.putDouble("Value", bonus.value);
            bonusTag.putBoolean("ApplyMultipliers", bonus.applyMultipliers);
            bonusList.add(bonusTag);
         }

         tag.put(entry.getKey(), bonusList);
      }

      return tag;
   }

   public void load(CompoundTag tag) {
      for (String stat : this.bonuses.keySet()) {
         if (tag.contains(stat)) {
            List<BonusStats.StatBonus> statBonuses = this.bonuses.get(stat);
            statBonuses.clear();
            ListTag bonusList = tag.getList(stat, 10);

            for (int i = 0; i < bonusList.size(); i++) {
               CompoundTag bonusTag = bonusList.getCompound(i);
               String name = bonusTag.getString("Name");
               String operation = bonusTag.getString("Operation");
               double value = bonusTag.getDouble("Value");
               boolean applyMultipliers = bonusTag.getBoolean("ApplyMultipliers");
               statBonuses.add(new BonusStats.StatBonus(name, operation, value, applyMultipliers));
            }
         }
      }
   }

   public void copyFrom(BonusStats other) {
      for (Entry<String, List<BonusStats.StatBonus>> entry : other.bonuses.entrySet()) {
         List<BonusStats.StatBonus> thisList = this.bonuses.get(entry.getKey());
         thisList.clear();

         for (BonusStats.StatBonus bonus : entry.getValue()) {
            thisList.add(new BonusStats.StatBonus(bonus.name, bonus.operation, bonus.value, bonus.applyMultipliers));
         }
      }
   }

   public static class StatBonus {
      public final String name;
      public final String operation;
      public final double value;
      public final boolean applyMultipliers;

      public StatBonus(String name, String operation, double value, boolean applyMultipliers) {
         this.name = name;
         this.operation = operation;
         this.value = value;
         this.applyMultipliers = applyMultipliers;
      }
   }
}
