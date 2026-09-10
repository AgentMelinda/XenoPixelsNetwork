package com.dragonminez.common.stats.skills;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.SkillsConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;

public class Skills {
   private static final double NAME_SIMILARITY_THRESHOLD = 0.8;
   private final Map<String, Skill> skillMap = new HashMap<>();

   public void registerDefaultSkill(String skillName, int maxLevel) {
      String lowerName = skillName.toLowerCase();
      if (this.skillMap.containsKey(lowerName)) {
         this.skillMap.get(lowerName).setMaxLevel(maxLevel);
      } else {
         this.skillMap.put(lowerName, new Skill(skillName, maxLevel));
      }
   }

   public Skill getSkill(String name) {
      return this.skillMap.get(name.toLowerCase());
   }

   public boolean hasSkill(String name) {
      return this.skillMap.containsKey(name.toLowerCase());
   }

   public boolean isUnlockedAtLevel(String name, int requiredLevel) {
      Skill skill = this.skillMap.get(name.toLowerCase());
      return skill != null && skill.isUnlockedAt(requiredLevel);
   }

   public int getSkillLevel(String name) {
      Skill skill = this.skillMap.get(name.toLowerCase());
      return skill != null ? skill.getLevel() : 0;
   }

   public int getMaxSkillLevel(String name) {
      Skill skill = this.skillMap.get(name.toLowerCase());
      return skill != null ? skill.getMaxLevel() : 0;
   }

   private int calculateMaxLevel(String skillName) {
      int costBasedMaxLevel = 0;

      try {
         SkillsConfig config = ConfigManager.getSkillsConfig();
         if (config != null) {
            SkillsConfig.SkillCosts skillCosts = config.getSkillCosts(skillName);
            if (skillCosts != null && skillCosts.getCosts() != null) {
               costBasedMaxLevel = skillCosts.getCosts().size();
            }
         }
      } catch (Exception var5) {
      }

      return skillName.equalsIgnoreCase("potentialunlock") ? Math.min(costBasedMaxLevel, 30) : Math.min(costBasedMaxLevel, 50);
   }

   public void refreshNonFormSkillMaxLevels() {
      List<String> formSkills = ConfigManager.getSkillsConfig().getFormSkills();

      for (Skill skill : this.skillMap.values()) {
         String skillName = skill.getName().toLowerCase();
         if (!formSkills.contains(skillName)) {
            int newMax = this.calculateMaxLevel(skillName);
            if (newMax > 0) {
               skill.setMaxLevel(newMax);
            }
         }
      }
   }

   public void setSkillLevel(String name, int level) {
      String lowerName = name.toLowerCase();
      if (!this.skillMap.containsKey(lowerName)) {
         int finalMaxLevel = this.calculateMaxLevel(lowerName);
         this.skillMap.put(lowerName, new Skill(name, 0, false, finalMaxLevel));
      }

      this.skillMap.get(lowerName).setLevel(level);
   }

   public void removeSkill(String name) {
      this.skillMap.remove(name.toLowerCase());
   }

   public void removeAllSkills() {
      this.skillMap.clear();
   }

   public Map<String, String> repairSkillNames() {
      Map<String, String> renamed = new LinkedHashMap<>();
      SkillsConfig config = ConfigManager.getSkillsConfig();
      if (config == null) {
         return renamed;
      } else {
         Set<String> validNames = new HashSet<>();
         validNames.addAll(config.getSkills().keySet());
         validNames.addAll(config.getFormSkills());
         validNames.addAll(config.getStackSkills());
         validNames.addAll(config.getKiSkills());
         validNames.addAll(config.getStrikeSkills());
         if (validNames.isEmpty()) {
            return renamed;
         } else {
            List<String> invalidKeys = new ArrayList<>();

            for (String key : this.skillMap.keySet()) {
               if (!validNames.contains(key)) {
                  invalidKeys.add(key);
               }
            }

            for (String badKey : invalidKeys) {
               String canonical = resolveCanonicalAlias(badKey, validNames);
               if (canonical == null) {
                  canonical = findClosestSkill(badKey, validNames);
               }

               if (canonical != null && !canonical.equals(badKey)) {
                  Skill legacy = this.skillMap.remove(badKey);
                  if (legacy != null) {
                     int maxLevel = config.getFormSkills().contains(canonical) ? legacy.getMaxLevel() : this.calculateMaxLevel(canonical);
                     Skill target = this.skillMap.get(canonical);
                     if (target != null) {
                        target.setMaxLevel(Math.max(target.getMaxLevel(), maxLevel));
                        target.setLevel(Math.max(target.getLevel(), legacy.getLevel()));
                        target.setActive(target.isActive() || legacy.isActive());
                     } else {
                        Skill migrated = new Skill(canonical, maxLevel);
                        migrated.setLevel(legacy.getLevel());
                        migrated.setActive(legacy.isActive());
                        this.skillMap.put(canonical, migrated);
                     }

                     renamed.put(badKey, canonical);
                  }
               }
            }

            return renamed;
         }
      }
   }

   private static String resolveCanonicalAlias(String input, Set<String> candidates) {
      if (input == null || input.isEmpty()) {
         return null;
      } else if (candidates.contains(input + "s")) {
         return input + "s";
      } else {
         return input.endsWith("s") && candidates.contains(input.substring(0, input.length() - 1)) ? input.substring(0, input.length() - 1) : null;
      }
   }

   private static String findClosestSkill(String input, Set<String> candidates) {
      String best = null;
      double bestSimilarity = 0.0;

      for (String candidate : candidates) {
         int maxLen = Math.max(input.length(), candidate.length());
         if (maxLen != 0) {
            double similarity = 1.0 - (double)levenshtein(input, candidate) / (double)maxLen;
            if (similarity > bestSimilarity) {
               bestSimilarity = similarity;
               best = candidate;
            }
         }
      }

      return bestSimilarity >= 0.8 ? best : null;
   }

   private static int levenshtein(String a, String b) {
      int[] prev = new int[b.length() + 1];
      int[] curr = new int[b.length() + 1];
      int j = 0;

      while (j <= b.length()) {
         prev[j] = j++;
      }

      for (int i = 1; i <= a.length(); i++) {
         curr[0] = i;

         for (int jx = 1; jx <= b.length(); jx++) {
            int cost = a.charAt(i - 1) == b.charAt(jx - 1) ? 0 : 1;
            curr[jx] = Math.min(Math.min(curr[jx - 1] + 1, prev[jx] + 1), prev[jx - 1] + cost);
         }

         int[] tmp = prev;
         prev = curr;
         curr = tmp;
      }

      return prev[b.length()];
   }

   public void addSkillLevel(String name, int amount) {
      Skill skill = this.skillMap.get(name.toLowerCase());
      if (skill != null) {
         skill.addLevel(amount);
      }
   }

   public boolean isSkillActive(String name) {
      Skill skill = this.skillMap.get(name.toLowerCase());
      return skill != null && skill.isActive();
   }

   public void setSkillActive(String name, boolean active) {
      Skill skill = this.skillMap.get(name.toLowerCase());
      if (skill != null) {
         skill.setActive(active);
      }
   }

   public void toggleSkillActive(String name) {
      Skill skill = this.skillMap.get(name.toLowerCase());
      if (skill != null) {
         skill.setActive(!skill.isActive());
      }
   }

   public Map<String, Skill> getAllSkills() {
      return new HashMap<>(this.skillMap);
   }

   public CompoundTag save() {
      CompoundTag nbt = new CompoundTag();
      ListTag skillsList = new ListTag();

      for (Skill skill : this.skillMap.values()) {
         skillsList.add(skill.save());
      }

      nbt.put("SkillsList", skillsList);
      return nbt;
   }

   public void load(CompoundTag nbt) {
      if (nbt.contains("SkillsList", 9)) {
         ListTag skillsList = nbt.getList("SkillsList", 10);
         this.skillMap.clear();

         for (int i = 0; i < skillsList.size(); i++) {
            CompoundTag skillTag = skillsList.getCompound(i);
            Skill skill = Skill.load(skillTag);
            String skillName = skill.getName().toLowerCase();
            if (!ConfigManager.getSkillsConfig().getFormSkills().contains(skillName)) {
               int newMax = this.calculateMaxLevel(skillName);
               if (newMax > 0) {
                  skill.setMaxLevel(newMax);
               }
            }

            this.skillMap.put(skillName, skill);
         }
      }
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeInt(this.skillMap.size());

      for (Skill skill : this.skillMap.values()) {
         skill.toBytes(buf);
      }
   }

   public void fromBytes(FriendlyByteBuf buf) {
      int size = buf.readInt();
      this.skillMap.clear();

      for (int i = 0; i < size; i++) {
         Skill skill = Skill.fromBytes(buf);
         this.skillMap.put(skill.getName().toLowerCase(), skill);
      }
   }

   public void copyFrom(Skills other) {
      this.skillMap.clear();

      for (Entry<String, Skill> entry : other.skillMap.entrySet()) {
         Skill newSkill = new Skill(entry.getValue().getName(), entry.getValue().getLevel(), entry.getValue().isActive(), entry.getValue().getMaxLevel());
         this.skillMap.put(entry.getKey(), newSkill);
      }
   }
}
