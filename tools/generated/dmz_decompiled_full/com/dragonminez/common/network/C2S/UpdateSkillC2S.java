package com.dragonminez.common.network.C2S;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.config.SkillsConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.skills.Skill;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class UpdateSkillC2S {
   private final String skillName;
   private final UpdateSkillC2S.SkillAction action;
   private final int cost;

   public UpdateSkillC2S(UpdateSkillC2S.SkillAction action, String skillName, int cost) {
      this.skillName = skillName;
      this.action = action;
      this.cost = cost;
   }

   public UpdateSkillC2S(FriendlyByteBuf buf) {
      this.skillName = buf.readUtf();
      this.action = (UpdateSkillC2S.SkillAction)buf.readEnum(UpdateSkillC2S.SkillAction.class);
      this.cost = buf.readInt();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeUtf(this.skillName);
      buf.writeEnum(this.action);
      buf.writeInt(this.cost);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get()
         .enqueueWork(
            () -> {
               ServerPlayer player = ctx.get().getSender();
               if (player != null) {
                  StatsProvider.get(StatsCapability.INSTANCE, player)
                     .ifPresent(
                        data -> {
                           Skill skill = data.getSkills().getSkill(this.skillName);
                           boolean raceAllowed = isSkillAllowedForPlayerRace(data, this.skillName);
                           switch (this.action) {
                              case TOGGLE:
                                 if (skill != null && skill.getLevel() > 0) {
                                    skill.setActive(!skill.isActive());
                                 }
                                 break;
                              case UPGRADE:
                                 if (skill != null && (skill.getLevel() > 0 || raceAllowed)) {
                                    boolean isStackSkill = ConfigManager.getSkillsConfig().getStackSkills().contains(this.skillName.toLowerCase());
                                    if ((!isStackSkill || skill.getLevel() > 0) && (skill.getLevel() > 0 || !isMasterOnlyFormSkill(data, this.skillName))) {
                                       refreshRuntimeMaxLevel(data, this.skillName, skill);
                                       int upgradeCost = computeTpCost(data, this.skillName, skill.getLevel());
                                       if (!skill.isMaxLevel()
                                          && upgradeCost >= 0
                                          && data.getResources().getTrainingPoints() >= (float)upgradeCost
                                          && (!this.skillName.equals("potentialunlock") || skill.getLevel() != 10)) {
                                          data.getResources().removeTrainingPoints((float)upgradeCost);
                                          boolean wasLevelZero = skill.getLevel() == 0;
                                          skill.addLevel(1);
                                          if (wasLevelZero) {
                                             this.unlockTechniqueIfPresent(data, this.skillName);
                                          }
                                       }
                                    }
                                 }
                                 break;
                              case PURCHASE:
                                 label90:
                                 if (raceAllowed) {
                                    boolean isFormSkillPurchase = ConfigManager.getSkillsConfig().getFormSkills().contains(this.skillName.toLowerCase());
                                    int effectiveCost;
                                    if (isFormSkillPurchase) {
                                       RaceCharacterConfig charConfig = ConfigManager.getRaceCharacter(data.getCharacter().getRaceName());
                                       if (charConfig == null || !charConfig.hasFormSkill(this.skillName)) {
                                          break label90;
                                       }

                                       Integer[] prices = charConfig.getFormSkillTpCosts(this.skillName);
                                       effectiveCost = prices.length > 0 && prices[0] != null ? prices[0] : -1;
                                    } else {
                                       effectiveCost = computeTpCost(data, this.skillName, 0);
                                    }

                                    boolean notOwned = !data.getSkills().hasSkill(this.skillName)
                                       || isFormSkillPurchase && data.getSkills().getSkillLevel(this.skillName) == 0;
                                    if (notOwned && effectiveCost >= 0 && data.getResources().getTrainingPoints() >= (float)effectiveCost) {
                                       data.getResources().removeTrainingPoints((float)effectiveCost);
                                       data.getSkills().setSkillLevel(this.skillName, 1);
                                       Skill purchased = data.getSkills().getSkill(this.skillName);
                                       if (purchased != null) {
                                          refreshRuntimeMaxLevel(data, this.skillName, purchased);
                                       }

                                       this.unlockTechniqueIfPresent(data, this.skillName);
                                    }
                                 }
                           }

                           NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                        }
                     );
               }
            }
         );
      ctx.get().setPacketHandled(true);
   }

   private static int computeTpCost(StatsData data, String skillName, int currentLevel) {
      if (skillName != null && currentLevel >= 0) {
         SkillsConfig skillsConfig = ConfigManager.getSkillsConfig();
         if (skillsConfig.getFormSkills().contains(skillName.toLowerCase())) {
            String raceName = data.getCharacter() != null ? data.getCharacter().getRaceName() : "";
            RaceCharacterConfig charConfig = ConfigManager.getRaceCharacter(raceName);
            if (charConfig == null) {
               return -1;
            } else {
               Integer[] prices = charConfig.getFormSkillTpCosts(skillName);
               return prices != null && currentLevel < prices.length && prices[currentLevel] != null ? Math.max(0, prices[currentLevel]) : -1;
            }
         } else {
            SkillsConfig.SkillCosts skillCosts = skillsConfig.getSkillCosts(skillName);
            if (skillCosts != null && skillCosts.getCosts() != null) {
               List<Integer> costs = skillCosts.getCosts();
               return currentLevel < costs.size() && costs.get(currentLevel) != null ? Math.max(0, costs.get(currentLevel)) : -1;
            } else {
               return -1;
            }
         }
      } else {
         return -1;
      }
   }

   private static boolean isMasterOnlyFormSkill(StatsData data, String skillName) {
      if (data != null && skillName != null) {
         if (!ConfigManager.getSkillsConfig().getFormSkills().contains(skillName.toLowerCase())) {
            return false;
         } else {
            String raceName = data.getCharacter() != null ? data.getCharacter().getRaceName() : "";
            RaceCharacterConfig charConfig = ConfigManager.getRaceCharacter(raceName);
            return charConfig != null && charConfig.isFormSkillBuyFromMaster(skillName);
         }
      } else {
         return false;
      }
   }

   private static boolean isSkillAllowedForPlayerRace(StatsData data, String skillName) {
      if (data != null && skillName != null && !skillName.isEmpty()) {
         String raceName = data.getCharacter() != null ? data.getCharacter().getRaceName() : "";
         return ConfigManager.getSkillsConfig().isSkillAllowedForRace(skillName, raceName);
      } else {
         return false;
      }
   }

   private void unlockTechniqueIfPresent(StatsData data, String techId) {
      if (PredefinedTechniques.REGISTRY.containsKey(techId)) {
         KiAttackData template = PredefinedTechniques.REGISTRY.get(techId);
         KiAttackData clone = new KiAttackData();
         clone.load(template.save());
         data.getTechniques().unlockTechnique(clone);
      } else if (PredefinedTechniques.STRIKE_REGISTRY.containsKey(techId)) {
         StrikeAttackData template = PredefinedTechniques.STRIKE_REGISTRY.get(techId);
         StrikeAttackData clone = new StrikeAttackData();
         clone.load(template.save());
         data.getTechniques().unlockTechnique(clone);
      }
   }

   private static void refreshRuntimeMaxLevel(StatsData data, String skillName, Skill skill) {
      String normalizedSkill = skillName.toLowerCase();
      SkillsConfig skillsConfig = ConfigManager.getSkillsConfig();
      if (skillsConfig.getFormSkills().contains(normalizedSkill)) {
         String raceName = data.getCharacter().getRaceName();
         if (raceName != null && !raceName.isEmpty()) {
            RaceCharacterConfig charConfig = ConfigManager.getRaceCharacter(raceName);
            int maxLevel = charConfig.getFormSkillTpCosts(normalizedSkill).length;
            skill.setMaxLevel(maxLevel);
         }
      } else {
         int maxLevel = 0;
         SkillsConfig.SkillCosts skillCosts = skillsConfig.getSkillCosts(normalizedSkill);
         if (skillCosts != null && skillCosts.getCosts() != null) {
            maxLevel = skillCosts.getCosts().size();
         }

         if ("potentialunlock".equalsIgnoreCase(normalizedSkill)) {
            maxLevel = Math.min(maxLevel, 30);
         } else {
            maxLevel = Math.min(maxLevel, 50);
         }

         skill.setMaxLevel(maxLevel);
      }
   }

   public static enum SkillAction {
      TOGGLE,
      UPGRADE,
      PURCHASE;
   }
}
