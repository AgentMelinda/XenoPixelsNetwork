package com.dragonminez.common.config;

import com.dragonminez.common.init.item.consumables.CapsuleType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Generated;

public class GeneralServerConfig {
   public static final String CURRENT_VERSION = "2.1.3";
   private String configVersion;
   private GeneralServerConfig.WorldGenConfig worldGen = new GeneralServerConfig.WorldGenConfig();
   private GeneralServerConfig.GameplayConfig gameplay = new GeneralServerConfig.GameplayConfig();
   private GeneralServerConfig.RacialSkillsConfig racialSkills = new GeneralServerConfig.RacialSkillsConfig();
   private GeneralServerConfig.DynamicGrowthConfig dynamicGrowth = new GeneralServerConfig.DynamicGrowthConfig();
   private GeneralServerConfig.GravityConfig gravity = new GeneralServerConfig.GravityConfig();
   private GeneralServerConfig.MutantConfig mutant = new GeneralServerConfig.MutantConfig();
   private GeneralServerConfig.CraftingConfig crafting = new GeneralServerConfig.CraftingConfig();
   private GeneralServerConfig.StorageConfig storage = new GeneralServerConfig.StorageConfig();
   private GeneralServerConfig.DeveloperConfig developer = new GeneralServerConfig.DeveloperConfig();

   @Generated
   public String getConfigVersion() {
      return this.configVersion;
   }

   @Generated
   public GeneralServerConfig.WorldGenConfig getWorldGen() {
      return this.worldGen;
   }

   @Generated
   public GeneralServerConfig.GameplayConfig getGameplay() {
      return this.gameplay;
   }

   @Generated
   public GeneralServerConfig.RacialSkillsConfig getRacialSkills() {
      return this.racialSkills;
   }

   @Generated
   public GeneralServerConfig.DynamicGrowthConfig getDynamicGrowth() {
      return this.dynamicGrowth;
   }

   @Generated
   public GeneralServerConfig.GravityConfig getGravity() {
      return this.gravity;
   }

   @Generated
   public GeneralServerConfig.MutantConfig getMutant() {
      return this.mutant;
   }

   @Generated
   public GeneralServerConfig.CraftingConfig getCrafting() {
      return this.crafting;
   }

   @Generated
   public GeneralServerConfig.StorageConfig getStorage() {
      return this.storage;
   }

   @Generated
   public GeneralServerConfig.DeveloperConfig getDeveloper() {
      return this.developer;
   }

   @Generated
   public void setConfigVersion(String configVersion) {
      this.configVersion = configVersion;
   }

   public static class CapsuleValues {
      private String stats;
      private Integer points;

      @Generated
      public String getStats() {
         return this.stats;
      }

      @Generated
      public Integer getPoints() {
         return this.points;
      }

      @Generated
      public CapsuleValues(String stats, Integer points) {
         this.stats = stats;
         this.points = points;
      }

      @Generated
      public CapsuleValues() {
      }
   }

   public static class CapsulesConfig {
      private String statSeparator = ", ";
      private Map<CapsuleType, GeneralServerConfig.CapsuleValues> values = new HashMap<>();

      public CapsulesConfig() {
         Arrays.stream(CapsuleType.values()).forEach(type -> this.values.put(type, new GeneralServerConfig.CapsuleValues(type.getStatName(), 5)));
      }

      public GeneralServerConfig.CapsuleValues getCapsuleValues(CapsuleType type) {
         return this.values.getOrDefault(type, new GeneralServerConfig.CapsuleValues(type.getStatName(), type.getStatPoints()));
      }

      @Generated
      public String getStatSeparator() {
         return this.statSeparator;
      }

      @Generated
      public Map<CapsuleType, GeneralServerConfig.CapsuleValues> getValues() {
         return this.values;
      }
   }

   public static class CraftingConfig {
      private Boolean copyEnchantmentsFromTemplate = false;
      private Boolean copyWeaponLevelFromTemplate = false;
      private Boolean copyWeaponLevelProgressFromTemplate = false;
      private Boolean copyApotheosisRarityFromTemplate = false;
      private Boolean copyApotheosisAffixesFromTemplate = false;
      private Boolean copyApotheosisSocketsFromTemplate = false;
      private Boolean copyApotheosisGemsFromTemplate = false;

      @Generated
      public Boolean getCopyEnchantmentsFromTemplate() {
         return this.copyEnchantmentsFromTemplate;
      }

      @Generated
      public Boolean getCopyWeaponLevelFromTemplate() {
         return this.copyWeaponLevelFromTemplate;
      }

      @Generated
      public Boolean getCopyWeaponLevelProgressFromTemplate() {
         return this.copyWeaponLevelProgressFromTemplate;
      }

      @Generated
      public Boolean getCopyApotheosisRarityFromTemplate() {
         return this.copyApotheosisRarityFromTemplate;
      }

      @Generated
      public Boolean getCopyApotheosisAffixesFromTemplate() {
         return this.copyApotheosisAffixesFromTemplate;
      }

      @Generated
      public Boolean getCopyApotheosisSocketsFromTemplate() {
         return this.copyApotheosisSocketsFromTemplate;
      }

      @Generated
      public Boolean getCopyApotheosisGemsFromTemplate() {
         return this.copyApotheosisGemsFromTemplate;
      }
   }

   public static class DeveloperConfig {
      private Boolean reportJsonProblemsInChat = true;

      public Boolean isReportJsonProblemsInChat() {
         return this.reportJsonProblemsInChat == null || this.reportJsonProblemsInChat;
      }

      @Generated
      public Boolean getReportJsonProblemsInChat() {
         return this.reportJsonProblemsInChat;
      }
   }

   public static class DynamicGrowthConfig {
      private Boolean enabled = true;
      private Boolean debugChat = false;
      private Boolean practiceCurveEnabled = true;
      private Double practiceXpMultiplier = 2.0;
      private Double strPracticeMultiplier = 1.0;
      private Double skpPracticeMultiplier = 1.0;
      private Double resPracticeMultiplier = 1.5;
      private Double vitPracticeMultiplier = 0.5;
      private Double pwrPracticeMultiplier = 1.0;
      private Double enePracticeMultiplier = 1.5;
      private Double staminaSpentXpRatio = 0.1;
      private Double energySpentXpRatio = 0.1;
      private Double kiWeaponMeleePwrShare = 0.25;
      private Double naturalCombatTpMultiplier = 1.0;
      private Boolean manualTpPurchasesEnabled = true;
      private Double attributeTpCostMultiplier = 1.0;
      private Double passiveAnimalPracticeMultiplier = 0.5;
      private Double villagerPracticeMultiplier = 0.1;
      private Double lowDamagePracticeMultiplier = 0.2;
      private Double shadowDummyPracticeMultiplier = 0.35;
      private Double noRiskPracticeMultiplier = 0.5;
      private Integer repeatTargetWindowSeconds = 30;
      private Integer repeatTargetSoftCap = 8;
      private Integer repeatTargetHardCap = 24;
      private Double repeatTargetSoftMultiplier = 0.5;
      private Double repeatTargetHardMultiplier = 0.15;

      public Boolean isEnabled() {
         return this.enabled != null ? this.enabled : true;
      }

      public Boolean isPracticeCurveEnabled() {
         return this.practiceCurveEnabled == null || this.practiceCurveEnabled;
      }

      public Double getPracticeXpMultiplier() {
         return clampNonNeg(this.practiceXpMultiplier, 2.0);
      }

      public Double getStatPracticeMultiplier(String statName) {
         String var2 = statName.toUpperCase();

         return switch (var2) {
            case "STR" -> clampNonNeg(this.strPracticeMultiplier, 1.0);
            case "SKP" -> clampNonNeg(this.skpPracticeMultiplier, 1.0);
            case "RES" -> clampNonNeg(this.resPracticeMultiplier, 1.5);
            case "VIT" -> clampNonNeg(this.vitPracticeMultiplier, 0.5);
            case "PWR" -> clampNonNeg(this.pwrPracticeMultiplier, 1.0);
            case "ENE" -> clampNonNeg(this.enePracticeMultiplier, 1.5);
            default -> 1.0;
         };
      }

      public Double getStaminaSpentXpRatio() {
         return clampNonNeg(this.staminaSpentXpRatio, 0.1);
      }

      public Double getEnergySpentXpRatio() {
         return clampNonNeg(this.energySpentXpRatio, 0.1);
      }

      public Double getKiWeaponMeleePwrShare() {
         return clampNonNeg(this.kiWeaponMeleePwrShare, 0.25);
      }

      public Double getNaturalCombatTpMultiplier() {
         return clampNonNeg(this.naturalCombatTpMultiplier, 1.0);
      }

      public Boolean isManualTpPurchasesEnabled() {
         return this.manualTpPurchasesEnabled == null || this.manualTpPurchasesEnabled;
      }

      public Double getAttributeTpCostMultiplier() {
         return Math.max(1.0, this.attributeTpCostMultiplier != null ? this.attributeTpCostMultiplier : 1.0);
      }

      public Double getPassiveAnimalPracticeMultiplier() {
         return clampNonNeg(this.passiveAnimalPracticeMultiplier, 0.5);
      }

      public Double getVillagerPracticeMultiplier() {
         return clampNonNeg(this.villagerPracticeMultiplier, 0.1);
      }

      public Double getLowDamagePracticeMultiplier() {
         return clampNonNeg(this.lowDamagePracticeMultiplier, 0.2);
      }

      public Double getShadowDummyPracticeMultiplier() {
         return clampNonNeg(this.shadowDummyPracticeMultiplier, 0.35);
      }

      public Double getNoRiskPracticeMultiplier() {
         return clampNonNeg(this.noRiskPracticeMultiplier, 0.5);
      }

      public Integer getRepeatTargetWindowSeconds() {
         return Math.max(1, this.repeatTargetWindowSeconds != null ? this.repeatTargetWindowSeconds : 30);
      }

      public Integer getRepeatTargetSoftCap() {
         return Math.max(0, this.repeatTargetSoftCap != null ? this.repeatTargetSoftCap : 8);
      }

      public Integer getRepeatTargetHardCap() {
         return Math.max(0, this.repeatTargetHardCap != null ? this.repeatTargetHardCap : 24);
      }

      public Double getRepeatTargetSoftMultiplier() {
         return clampNonNeg(this.repeatTargetSoftMultiplier, 0.5);
      }

      public Double getRepeatTargetHardMultiplier() {
         return clampNonNeg(this.repeatTargetHardMultiplier, 0.15);
      }

      private static double clampNonNeg(Double value, double fallback) {
         return value == null ? fallback : Math.max(0.0, value);
      }

      @Generated
      public Boolean getEnabled() {
         return this.enabled;
      }

      @Generated
      public Boolean getDebugChat() {
         return this.debugChat;
      }

      @Generated
      public Boolean getPracticeCurveEnabled() {
         return this.practiceCurveEnabled;
      }

      @Generated
      public Double getStrPracticeMultiplier() {
         return this.strPracticeMultiplier;
      }

      @Generated
      public Double getSkpPracticeMultiplier() {
         return this.skpPracticeMultiplier;
      }

      @Generated
      public Double getResPracticeMultiplier() {
         return this.resPracticeMultiplier;
      }

      @Generated
      public Double getVitPracticeMultiplier() {
         return this.vitPracticeMultiplier;
      }

      @Generated
      public Double getPwrPracticeMultiplier() {
         return this.pwrPracticeMultiplier;
      }

      @Generated
      public Double getEnePracticeMultiplier() {
         return this.enePracticeMultiplier;
      }

      @Generated
      public Boolean getManualTpPurchasesEnabled() {
         return this.manualTpPurchasesEnabled;
      }
   }

   public static class FoodConfig {
      private Integer minHungerPoints = 2;
      private Integer maxHungerPoints = 20;
      private Float minSaturationPoints = 0.4F;
      private Float maxSaturationPoints = 2.0F;
      private Float healthPercentageRecoveredPerHungerPoint = 0.01F;
      private Float kiPercentageRecoveredPerHungerPoint = 0.01F;
      private Float staminaPercentageRecoveredPerHungerPoint = 0.02F;
      private Float healthPercentageRecoveredPerSaturationPoint = 0.001F;
      private Float kiPercentageRecoveredPerSaturationPoint = 0.001F;
      private Float staminaPercentageRecoveredPerSaturationPoint = 0.004F;
      private List<String> blacklistedNamespaces = new ArrayList<>();
      private List<String> blacklistedItems = new ArrayList<>();

      @Generated
      public Integer getMinHungerPoints() {
         return this.minHungerPoints;
      }

      @Generated
      public Integer getMaxHungerPoints() {
         return this.maxHungerPoints;
      }

      @Generated
      public Float getMinSaturationPoints() {
         return this.minSaturationPoints;
      }

      @Generated
      public Float getMaxSaturationPoints() {
         return this.maxSaturationPoints;
      }

      @Generated
      public Float getHealthPercentageRecoveredPerHungerPoint() {
         return this.healthPercentageRecoveredPerHungerPoint;
      }

      @Generated
      public Float getKiPercentageRecoveredPerHungerPoint() {
         return this.kiPercentageRecoveredPerHungerPoint;
      }

      @Generated
      public Float getStaminaPercentageRecoveredPerHungerPoint() {
         return this.staminaPercentageRecoveredPerHungerPoint;
      }

      @Generated
      public Float getHealthPercentageRecoveredPerSaturationPoint() {
         return this.healthPercentageRecoveredPerSaturationPoint;
      }

      @Generated
      public Float getKiPercentageRecoveredPerSaturationPoint() {
         return this.kiPercentageRecoveredPerSaturationPoint;
      }

      @Generated
      public Float getStaminaPercentageRecoveredPerSaturationPoint() {
         return this.staminaPercentageRecoveredPerSaturationPoint;
      }

      @Generated
      public List<String> getBlacklistedNamespaces() {
         return this.blacklistedNamespaces;
      }

      @Generated
      public List<String> getBlacklistedItems() {
         return this.blacklistedItems;
      }
   }

   public static class GameplayConfig {
      private Boolean forceCharacterCreation = true;
      private Boolean commandOutputOnConsole = true;
      private Integer reviveCooldownSeconds = 180;
      private Double tpGainMultiplier = 1.0;
      private Double increaseTPGainRelativeToTPCost = 0.5;
      private Double globalTPCostMultiplier = 1.0;
      private Integer minTPCost = 16;
      private Integer maxTPDiscount = 140;
      private Double tpHealthRatio = 0.25;
      private Integer tpPerHit = 2;
      private Integer passiveTpGain = 1;
      private Integer tpPer20BlocksTraveled = 1;
      private Integer tpPerBlockMined = 1;
      private Integer tpPerItemCrafted = 1;
      private Boolean gravityBonusEnabled = true;
      private Double HTCTpMultiplier = 1.75;
      private Boolean maxLevelValueInsteadOfStats = true;
      private Long maxValue = 10000L;
      private GeneralServerConfig.CapsulesConfig capsules = new GeneralServerConfig.CapsulesConfig();
      private Boolean storyModeEnabled = true;
      private Boolean createDefaultSagas = true;
      private Boolean sideQuestsEnabled = true;
      private Boolean createDefaultSideQuests = true;
      private Boolean autoUpdateQuests = true;
      private Double defaultQuestPartyMultiplier = 1.45;
      private Integer senzuCooldownTicks = 240;
      private Integer senzuGiftCooldownTicks = 18000;
      private Integer senzuGiftAmount = 5;
      private GeneralServerConfig.FoodConfig food = new GeneralServerConfig.FoodConfig();
      private Double mightFruitPower = 1.2;
      private Double majinPower = 1.3;
      private Double metamoruFusionThreshold = 0.5;
      private String[] fusionBoosts = new String[]{"STR", "SKP", "PWR"};
      private Integer fusionDurationSeconds = 900;
      private Integer fusionCooldownSeconds = 1800;
      private Boolean multiplicationInsteadOfAdditionForMultipliers = false;
      private Boolean ultimateFormFixedValue = false;
      private Integer partyMaxMembers = -1;
      private Integer partyMaxLevelGap = 500;
      private Double partyTpShareRatio = 0.5;
      private Double enemyHealthPerPartyPlayer = 1.25;
      private Double enemyDamagePerPartyPlayer = 1.1;
      private Integer instantTransmissionPlayerRangePerLevel = 200;
      private Double easyModeHPMultiplier = 0.75;
      private Double easyModeDamageMultiplier = 0.5;
      private Double easyModeTPMultiplier = 1.25;
      private Double easyModeQuestRewardMultiplier = 1.0;
      private Double hardModeHPMultiplier = 2.0;
      private Double hardModeDamageMultiplier = 1.5;
      private Double hardModeTPMultiplier = 1.25;
      private Double hardModeQuestRewardMultiplier = 1.25;
      private Double storyResetTPMultiplier = 0.5;
      private List<String> helmetsThatKeepHair = new ArrayList<>(
         Arrays.asList("dragonminez:invencible_armor_helmet", "dragonminez:invencible_blue_armor_helmet")
      );
      private Map<TpSource, List<TpBoost>> tpGainBoosts = defaultTpGainBoosts();

      private static Map<TpSource, List<TpBoost>> defaultTpGainBoosts() {
         Map<TpSource, List<TpBoost>> map = new LinkedHashMap<>();

         for (TpSource source : TpSource.values()) {
            List<TpBoost> boosts = new ArrayList<>(
               Arrays.asList(TpBoost.CLASS, TpBoost.RACIALSKILL, TpBoost.HTC, TpBoost.GRAVITY, TpBoost.WEIGHTS, TpBoost.GLOBAL, TpBoost.POTION, TpBoost.MUTANT)
            );
            if (source == TpSource.STORY) {
               boosts.add(TpBoost.DIFFICULTY);
            }

            map.put(source, boosts);
         }

         return map;
      }

      public List<TpBoost> getTpGainBoosts(TpSource source) {
         if (this.tpGainBoosts == null) {
            return Arrays.asList(TpBoost.values());
         } else {
            List<TpBoost> boosts = this.tpGainBoosts.get(source);
            return boosts == null ? List.of() : boosts.stream().filter(Objects::nonNull).toList();
         }
      }

      public Integer getReviveCooldownSeconds() {
         return Math.max(0, Math.min(this.reviveCooldownSeconds, Integer.MAX_VALUE));
      }

      public Boolean getForceCharacterCreation() {
         return this.forceCharacterCreation != null ? this.forceCharacterCreation : true;
      }

      public Double getTpsGainMultiplier() {
         return Math.max(0.0, Math.min(this.tpGainMultiplier, Double.MAX_VALUE));
      }

      public Double getIncreaseTPGainRelativeToTPCost() {
         return Math.max(0.0, this.increaseTPGainRelativeToTPCost != null ? this.increaseTPGainRelativeToTPCost : 0.5);
      }

      public Double getGlobalTpCostMultiplier() {
         return Math.max(0.01, Math.min(this.globalTPCostMultiplier, Double.MAX_VALUE));
      }

      public Double getTpHealthRatio() {
         return Math.max(0.0, Math.min(this.tpHealthRatio, Double.MAX_VALUE));
      }

      public Integer getTpPerHit() {
         return Math.max(0, Math.min(this.tpPerHit, Integer.MAX_VALUE));
      }

      public Integer getPassiveTpGain() {
         return Math.max(0, Math.min(this.passiveTpGain, Integer.MAX_VALUE));
      }

      public Integer getTpPer20BlocksTraveled() {
         return Math.max(0, Math.min(this.tpPer20BlocksTraveled, Integer.MAX_VALUE));
      }

      public Integer getTpPerBlockMined() {
         return Math.max(0, Math.min(this.tpPerBlockMined, Integer.MAX_VALUE));
      }

      public Integer getTpPerItemCrafted() {
         return Math.max(0, Math.min(this.tpPerItemCrafted, Integer.MAX_VALUE));
      }

      public Boolean getGravityBonusEnabled() {
         return this.gravityBonusEnabled != null ? this.gravityBonusEnabled : true;
      }

      public Double getHTCTpMultiplier() {
         return Math.max(1.0, Math.min(this.HTCTpMultiplier, Double.MAX_VALUE));
      }

      public Integer getMaxValue() {
         return (int)Math.max(1000L, Math.min(this.maxValue != null ? this.maxValue : 10000L, 2147483647L));
      }

      public Boolean getMaxLevelValueInsteadOfStats() {
         return this.maxLevelValueInsteadOfStats != null ? this.maxLevelValueInsteadOfStats : true;
      }

      public Integer getSenzuCooldownTicks() {
         return Math.max(0, Math.min(this.senzuCooldownTicks, Integer.MAX_VALUE));
      }

      public Double getDefaultQuestPartyMultiplier() {
         return Math.max(1.0, Math.min(this.defaultQuestPartyMultiplier, 5.0));
      }

      public Double getMightFruitPower() {
         return Math.max(0.0, Math.min(this.mightFruitPower, Double.MAX_VALUE));
      }

      public Double getMajinPower() {
         return Math.max(0.0, Math.min(this.majinPower, Double.MAX_VALUE));
      }

      public Double getMetamoruFusionThreshold() {
         return Math.max(0.0, Math.min(this.metamoruFusionThreshold, Double.MAX_VALUE));
      }

      public Integer getFusionDurationSeconds() {
         return Math.max(0, Math.min(this.fusionDurationSeconds, Integer.MAX_VALUE));
      }

      public Integer getFusionCooldownSeconds() {
         return Math.max(0, Math.min(this.fusionCooldownSeconds, Integer.MAX_VALUE));
      }

      public Integer getPartyMaxMembers() {
         return this.partyMaxMembers == null ? -1 : this.partyMaxMembers < -1 ? -1 : this.partyMaxMembers;
      }

      public Integer getPartyMaxLevelGap() {
         return this.partyMaxLevelGap == null ? 500 : this.partyMaxLevelGap < -1 ? -1 : this.partyMaxLevelGap;
      }

      public Double getPartyTpShareRatio() {
         return this.partyTpShareRatio == null ? 0.0 : Math.max(0.0, Math.min(this.partyTpShareRatio, 10.0));
      }

      public Double getEnemyHealthPerPartyPlayer() {
         return Math.max(1.0, this.enemyHealthPerPartyPlayer != null ? this.enemyHealthPerPartyPlayer : 1.25);
      }

      public Double getEnemyDamagePerPartyPlayer() {
         return Math.max(1.0, this.enemyDamagePerPartyPlayer != null ? this.enemyDamagePerPartyPlayer : 1.1);
      }

      public Integer getInstantTransmissionPlayerRangePerLevel() {
         return this.instantTransmissionPlayerRangePerLevel == null
            ? 200
            : Math.max(0, Math.min(this.instantTransmissionPlayerRangePerLevel, Integer.MAX_VALUE));
      }

      public Boolean getUltimateFormFixedValue() {
         return this.ultimateFormFixedValue != null && this.ultimateFormFixedValue;
      }

      public Double getEasyModeHPMultiplier() {
         return Math.max(0.0, this.easyModeHPMultiplier != null ? this.easyModeHPMultiplier : 0.75);
      }

      public Double getEasyModeDamageMultiplier() {
         return Math.max(0.0, this.easyModeDamageMultiplier != null ? this.easyModeDamageMultiplier : 0.5);
      }

      public Double getEasyModeTPMultiplier() {
         return Math.max(0.0, this.easyModeTPMultiplier != null ? this.easyModeTPMultiplier : 1.25);
      }

      public Double getEasyModeQuestRewardMultiplier() {
         return Math.max(0.0, this.easyModeQuestRewardMultiplier != null ? this.easyModeQuestRewardMultiplier : 1.0);
      }

      public Double getHardModeHPMultiplier() {
         return Math.max(0.0, this.hardModeHPMultiplier != null ? this.hardModeHPMultiplier : 2.0);
      }

      public Double getHardModeDamageMultiplier() {
         return Math.max(0.0, this.hardModeDamageMultiplier != null ? this.hardModeDamageMultiplier : 1.5);
      }

      public Double getHardModeTPMultiplier() {
         return Math.max(0.0, this.hardModeTPMultiplier != null ? this.hardModeTPMultiplier : 1.25);
      }

      public Double getHardModeQuestRewardMultiplier() {
         return Math.max(0.0, this.hardModeQuestRewardMultiplier != null ? this.hardModeQuestRewardMultiplier : 1.25);
      }

      public Double getStoryResetTPMultiplier() {
         return Math.min(1.0, Math.max(0.0, this.storyResetTPMultiplier != null ? this.storyResetTPMultiplier : 0.5));
      }

      @Generated
      public Boolean getCommandOutputOnConsole() {
         return this.commandOutputOnConsole;
      }

      @Generated
      public Double getTpGainMultiplier() {
         return this.tpGainMultiplier;
      }

      @Generated
      public Integer getMinTPCost() {
         return this.minTPCost;
      }

      @Generated
      public Integer getMaxTPDiscount() {
         return this.maxTPDiscount;
      }

      @Generated
      public GeneralServerConfig.CapsulesConfig getCapsules() {
         return this.capsules;
      }

      @Generated
      public Boolean getStoryModeEnabled() {
         return this.storyModeEnabled;
      }

      @Generated
      public Boolean getCreateDefaultSagas() {
         return this.createDefaultSagas;
      }

      @Generated
      public Boolean getSideQuestsEnabled() {
         return this.sideQuestsEnabled;
      }

      @Generated
      public Boolean getCreateDefaultSideQuests() {
         return this.createDefaultSideQuests;
      }

      @Generated
      public Boolean getAutoUpdateQuests() {
         return this.autoUpdateQuests;
      }

      @Generated
      public Integer getSenzuGiftCooldownTicks() {
         return this.senzuGiftCooldownTicks;
      }

      @Generated
      public Integer getSenzuGiftAmount() {
         return this.senzuGiftAmount;
      }

      @Generated
      public GeneralServerConfig.FoodConfig getFood() {
         return this.food;
      }

      @Generated
      public String[] getFusionBoosts() {
         return this.fusionBoosts;
      }

      @Generated
      public Boolean getMultiplicationInsteadOfAdditionForMultipliers() {
         return this.multiplicationInsteadOfAdditionForMultipliers;
      }

      @Generated
      public List<String> getHelmetsThatKeepHair() {
         return this.helmetsThatKeepHair;
      }

      @Generated
      public Map<TpSource, List<TpBoost>> getTpGainBoosts() {
         return this.tpGainBoosts;
      }
   }

   public static class GravityConfig {
      private Boolean enabled = true;
      private Map<String, Double> gravityPerWorld = defaultGravityPerWorld();
      private Double defaultWorldGravity = 1.0;
      private Double npcGravityValue = 10.0;
      private Double npcGravityRange = 100.0;
      private Boolean machineGravityEnabled = true;
      private Double resistanceStatDivisorRatio = 0.9;
      private Double resistanceScale = 100.0;
      private Boolean statReductionEnabled = true;
      private String[] affectedStats = new String[]{"STR", "SKP", "PWR", "DEF", "STM"};
      private Double statReductionPerGravity = 0.01;
      private Double minStatReduction = 0.0;
      private Double maxStatReduction = 0.9;
      private Double hardStopThreshold = 75.0;
      private Double maxMovementPenalty = 0.95;
      private Double maxAttackPenalty = 0.9;
      private Double penaltyCurveFactor = 1.6;
      private Boolean physicalEnabled = true;
      private Double maxJumpPenalty = 0.95;
      private Double extraFallPerGravity = 0.02;
      private Double maxExtraFall = 0.6;
      private Double maxFlyPenalty = 0.95;
      private Boolean tpEnabled = true;
      private Double tpPeakMultiplier = 2.0;
      private Double tpCurveWidth = 7.0;
      private Double tpGravityBonusPerGravity = 0.025;
      private Double masteryBonusPerGravity = 0.0025;
      private Double tpIdealBaseDivisor = 2.0;
      private Double gravitySensitivity = 1.0;
      private Double tpComfortRatioLow = 0.5;
      private Double tpIdealRatioLow = 0.75;
      private Double tpIdealRatioHigh = 1.25;
      private Double tpOverloadRatio = 2.0;
      private Double tpOverloadHardRatio = 2.5;
      private Double tpComfortMultiplier = 1.5;
      private Double tpHeavyMultiplier = 2.5;
      private Double maxWeightPenalty = 0.6;
      private Double gravityRoomMk2Relief = 0.5;
      private Double gravityRoomMk2Falloff = 3.0;
      private Double gravityRoomMk3Relief = 0.8;
      private Double gravityRoomMk3Falloff = 1.5;
      private Double loadDrainComfort = 1.15;
      private Double loadDrainIdeal = 1.3;
      private Double loadDrainHeavy = 1.5;
      private Double loadDrainOverload = 2.0;
      private Double consumptionPerGravity = 0.04;
      private Integer deviceMinRoomSize = 5;
      private Integer deviceMaxRoomSize = 25;
      private Integer deviceMaxGravity = 1000;
      private Integer deviceEnergyCapacity = 20000;
      private Double deviceEnergyPerGravityPerSecond = 1.0;
      private Double deviceShaderGravityForMax = 60.0;

      private static Map<String, Double> defaultGravityPerWorld() {
         Map<String, Double> map = new LinkedHashMap<>();
         map.put("minecraft:overworld", 1.0);
         map.put("minecraft:the_nether", 8.0);
         map.put("minecraft:the_end", 20.0);
         map.put("dragonminez:time_chamber", 10.0);
         map.put("dragonminez:otherworld", 1.0);
         map.put("dragonminez:namek", 1.0);
         return map;
      }

      public Boolean isEnabled() {
         return this.enabled == null || this.enabled;
      }

      public Double getWorldGravity(String dimensionId) {
         double fallback = this.getDefaultWorldGravity();
         if (this.gravityPerWorld == null) {
            return fallback;
         } else {
            Double value = this.gravityPerWorld.get(dimensionId);
            return value != null ? Math.max(0.0, value) : fallback;
         }
      }

      public Double getDefaultWorldGravity() {
         return clampNonNeg(this.defaultWorldGravity, 1.0);
      }

      public Double getNpcGravityValue() {
         return clampNonNeg(this.npcGravityValue, 10.0);
      }

      public Double getNpcGravityRange() {
         return clampNonNeg(this.npcGravityRange, 100.0);
      }

      public Boolean getMachineGravityEnabled() {
         return this.machineGravityEnabled == null || this.machineGravityEnabled;
      }

      public Double getResistanceStatDivisorRatio() {
         Double value = this.resistanceStatDivisorRatio != null ? this.resistanceStatDivisorRatio : 0.9;
         return Math.max(0.01, Math.min(value, 1.0));
      }

      public Double getResistanceScale() {
         return clampNonNeg(this.resistanceScale, 100.0);
      }

      public Boolean getStatReductionEnabled() {
         return this.statReductionEnabled == null || this.statReductionEnabled;
      }

      public String[] getAffectedStats() {
         return this.affectedStats != null ? this.affectedStats : new String[]{"STR", "SKP", "PWR", "DEF", "STM"};
      }

      public Double getStatReductionPerGravity() {
         return clampNonNeg(this.statReductionPerGravity, 0.01);
      }

      public Double getMinStatReduction() {
         Double value = this.minStatReduction != null ? this.minStatReduction : 0.0;
         return Math.max(0.0, Math.min(value, 1.0));
      }

      public Double getMaxStatReduction() {
         Double value = this.maxStatReduction != null ? this.maxStatReduction : 0.9;
         return Math.max(0.0, Math.min(value, 1.0));
      }

      public Double getHardStopThreshold() {
         return clampNonNeg(this.hardStopThreshold, 50.0);
      }

      public Double getMaxMovementPenalty() {
         Double value = this.maxMovementPenalty != null ? this.maxMovementPenalty : 0.95;
         return Math.max(0.0, Math.min(value, 1.0));
      }

      public Double getMaxAttackPenalty() {
         Double value = this.maxAttackPenalty != null ? this.maxAttackPenalty : 0.9;
         return Math.max(0.0, Math.min(value, 1.0));
      }

      public Double getPenaltyCurveFactor() {
         return clampNonNeg(this.penaltyCurveFactor, 1.6);
      }

      public Boolean getPhysicalEnabled() {
         return this.physicalEnabled == null || this.physicalEnabled;
      }

      public Double getMaxJumpPenalty() {
         Double value = this.maxJumpPenalty != null ? this.maxJumpPenalty : 0.95;
         return Math.max(0.0, Math.min(value, 1.0));
      }

      public Double getExtraFallPerGravity() {
         return clampNonNeg(this.extraFallPerGravity, 0.02);
      }

      public Double getMaxExtraFall() {
         return clampNonNeg(this.maxExtraFall, 0.6);
      }

      public Double getMaxFlyPenalty() {
         Double value = this.maxFlyPenalty != null ? this.maxFlyPenalty : 0.95;
         return Math.max(0.0, Math.min(value, 1.0));
      }

      public Boolean getTpEnabled() {
         return this.tpEnabled == null || this.tpEnabled;
      }

      public Double getTpPeakMultiplier() {
         return clampNonNeg(this.tpPeakMultiplier, 2.0);
      }

      public Double getTpCurveWidth() {
         return Math.max(1.0E-4, this.tpCurveWidth != null ? this.tpCurveWidth : 7.0);
      }

      public Double getTpIdealBaseDivisor() {
         return Math.max(1.0E-4, this.tpIdealBaseDivisor != null ? this.tpIdealBaseDivisor : 2.0);
      }

      public Double getGravitySensitivity() {
         Double value = this.gravitySensitivity != null ? this.gravitySensitivity : 1.0;
         return Math.max(0.0, Math.min(value, 1.0));
      }

      public Double getTpComfortRatioLow() {
         Double value = this.tpComfortRatioLow != null ? this.tpComfortRatioLow : 0.5;
         return Math.max(0.0, value);
      }

      public Double getTpIdealRatioLow() {
         Double value = this.tpIdealRatioLow != null ? this.tpIdealRatioLow : 0.75;
         return Math.max(this.getTpComfortRatioLow(), value);
      }

      public Double getTpIdealRatioHigh() {
         Double value = this.tpIdealRatioHigh != null ? this.tpIdealRatioHigh : 1.25;
         return Math.max(this.getTpIdealRatioLow(), value);
      }

      public Double getTpOverloadRatio() {
         Double value = this.tpOverloadRatio != null ? this.tpOverloadRatio : 2.0;
         return Math.max(this.getTpIdealRatioHigh(), value);
      }

      public Double getTpOverloadHardRatio() {
         Double value = this.tpOverloadHardRatio != null ? this.tpOverloadHardRatio : 2.5;
         return Math.max(this.getTpOverloadRatio() + 1.0E-4, value);
      }

      public Double getTpComfortMultiplier() {
         return Math.max(1.0, this.tpComfortMultiplier != null ? this.tpComfortMultiplier : 1.5);
      }

      public Double getTpHeavyMultiplier() {
         return Math.max(this.getTpPeakMultiplier(), this.tpHeavyMultiplier != null ? this.tpHeavyMultiplier : 2.5);
      }

      public Double getMaxWeightPenalty() {
         Double value = this.maxWeightPenalty != null ? this.maxWeightPenalty : 0.6;
         return Math.max(0.0, Math.min(value, 1.0));
      }

      public Double getGravityRoomMk2Relief() {
         Double value = this.gravityRoomMk2Relief != null ? this.gravityRoomMk2Relief : 0.5;
         return Math.max(0.0, Math.min(value, 1.0));
      }

      public Double getGravityRoomMk2Falloff() {
         return clampNonNeg(this.gravityRoomMk2Falloff, 3.0);
      }

      public Double getGravityRoomMk3Relief() {
         Double value = this.gravityRoomMk3Relief != null ? this.gravityRoomMk3Relief : 0.8;
         return Math.max(0.0, Math.min(value, 1.0));
      }

      public Double getGravityRoomMk3Falloff() {
         return clampNonNeg(this.gravityRoomMk3Falloff, 1.5);
      }

      public Double getLoadDrainComfort() {
         return Math.max(0.0, this.loadDrainComfort != null ? this.loadDrainComfort : 1.15);
      }

      public Double getLoadDrainIdeal() {
         return Math.max(0.0, this.loadDrainIdeal != null ? this.loadDrainIdeal : 1.3);
      }

      public Double getLoadDrainHeavy() {
         return Math.max(0.0, this.loadDrainHeavy != null ? this.loadDrainHeavy : 1.5);
      }

      public Double getLoadDrainOverload() {
         return Math.max(0.0, this.loadDrainOverload != null ? this.loadDrainOverload : 2.0);
      }

      public Double getTpGravityBonusPerGravity() {
         return clampNonNeg(this.tpGravityBonusPerGravity, 0.05);
      }

      public Double getMasteryBonusPerGravity() {
         return clampNonNeg(this.masteryBonusPerGravity, 0.1);
      }

      public Double getConsumptionPerGravity() {
         return clampNonNeg(this.consumptionPerGravity, 0.04);
      }

      public Integer getDeviceMinRoomSize() {
         return Math.max(1, this.deviceMinRoomSize != null ? this.deviceMinRoomSize : 5);
      }

      public Integer getDeviceMaxRoomSize() {
         int min = this.getDeviceMinRoomSize();
         int max = this.deviceMaxRoomSize != null ? this.deviceMaxRoomSize : 25;
         return Math.max(min, max);
      }

      public Integer getDeviceMaxGravity() {
         return Math.max(1, this.deviceMaxGravity != null ? this.deviceMaxGravity : 1000);
      }

      public Integer getDeviceEnergyCapacity() {
         return Math.max(1, this.deviceEnergyCapacity != null ? this.deviceEnergyCapacity : 20000);
      }

      public Double getDeviceEnergyPerGravityPerSecond() {
         return clampNonNeg(this.deviceEnergyPerGravityPerSecond, 1.0);
      }

      public Double getDeviceShaderGravityForMax() {
         return Math.max(1.0, this.deviceShaderGravityForMax != null ? this.deviceShaderGravityForMax : 60.0);
      }

      private static double clampNonNeg(Double value, double fallback) {
         return value == null ? fallback : Math.max(0.0, value);
      }

      @Generated
      public Boolean getEnabled() {
         return this.enabled;
      }

      @Generated
      public Map<String, Double> getGravityPerWorld() {
         return this.gravityPerWorld;
      }
   }

   public static class MutantConfig {
      private Boolean enabled = true;
      private Integer rollIntervalMinutes = 30;
      private Integer playersPerRoll = 1;
      private Double chance = 0.2;
      private Integer maxHolders = 1;
      private String legendaryGroupName = "legendaryforms";
      private Double tpGainMultiplier = 1.25;
      private Double masteryGainMultiplier = 1.5;
      private Double powerBonusReductionNoSkill = 0.67;
      private Double powerBonusBoostWithSkill = 0.33;
      private Boolean keepMutantOnDeath = false;

      public Boolean getEnabled() {
         return this.enabled == null || this.enabled;
      }

      public Integer getRollIntervalMinutes() {
         return Math.max(1, this.rollIntervalMinutes != null ? this.rollIntervalMinutes : 30);
      }

      public Integer getPlayersPerRoll() {
         return Math.max(1, this.playersPerRoll != null ? this.playersPerRoll : 1);
      }

      public Double getChance() {
         Double value = this.chance != null ? this.chance : 0.2;
         return Math.max(0.0, Math.min(value, 1.0));
      }

      public Integer getMaxHolders() {
         return Math.max(0, this.maxHolders != null ? this.maxHolders : 1);
      }

      public String getLegendaryGroupName() {
         return this.legendaryGroupName != null && !this.legendaryGroupName.isEmpty() ? this.legendaryGroupName : "legendaryforms";
      }

      public Double getTpGainMultiplier() {
         return Math.max(0.0, this.tpGainMultiplier != null ? this.tpGainMultiplier : 1.25);
      }

      public Double getMasteryGainMultiplier() {
         return Math.max(0.0, this.masteryGainMultiplier != null ? this.masteryGainMultiplier : 1.5);
      }

      public Double getPowerBonusReductionNoSkill() {
         Double value = this.powerBonusReductionNoSkill != null ? this.powerBonusReductionNoSkill : 0.33;
         return Math.max(0.0, Math.min(value, 1.0));
      }

      public Double getPowerBonusBoostWithSkill() {
         return Math.max(0.0, this.powerBonusBoostWithSkill != null ? this.powerBonusBoostWithSkill : 0.33);
      }

      public Boolean getKeepMutantOnDeath() {
         return this.keepMutantOnDeath != null && this.keepMutantOnDeath;
      }
   }

   public static class RacialSkillsConfig {
      private Boolean enableRacialSkills = true;
      private Boolean humanRacialSkill = true;
      private Double humanKiRegenBoost = 1.4;
      private Boolean saiyanRacialSkill = true;
      private Integer saiyanZenkaiMinLevel = 100;
      private Integer saiyanZenkaiAmount = 3;
      private Double saiyanZenkaiHealthRegen = 0.2;
      private Double saiyanZenkaiStatBoost = 0.075;
      private String[] saiyanZenkaiBoosts = new String[]{"STR", "SKP", "PWR"};
      private Integer saiyanZenkaiCooldownSeconds = 900;
      private Boolean namekianRacialSkill = true;
      private Integer namekianAssimilationAmount = 4;
      private Double namekianAssimilationHealthRegen = 0.35;
      private Double namekianAssimilationStatBoost = 0.075;
      private String[] namekianAssimilationBoosts = new String[]{"STR", "SKP", "PWR"};
      private Boolean namekianAssimilationOnNamekNpcs = true;
      private Boolean frostDemonRacialSkill = true;
      private Double frostDemonTPBoost = 1.25;
      private Boolean bioAndroidRacialSkill = true;
      private Integer bioAndroidCooldownSeconds = 180;
      private Double bioAndroidDrainRatio = 0.25;
      private Boolean majinAbsoprtionSkill = true;
      private Boolean majinReviveSkill = true;
      private Integer majinAbsorptionAmount = 3;
      private Double majinAbsorptionHealthRegen = 0.3;
      private Double majinAbsorptionStatsCopy = 0.04;
      private String[] majinAbsorptionBoosts = new String[]{"STR", "SKP", "PWR"};
      private Boolean majinAbsorptionOnMobs = true;
      private Integer majinReviveCooldownSeconds = 3600;
      private Double majinReviveHealthRatioPerBlop = 0.25;

      public Double getHumanKiRegenBoost() {
         return Math.max(0.0, Math.min(this.humanKiRegenBoost, Double.MAX_VALUE));
      }

      public Integer getSaiyanZenkaiMinLevel() {
         return Math.max(0, Math.min(this.saiyanZenkaiMinLevel != null ? this.saiyanZenkaiMinLevel : 100, Integer.MAX_VALUE));
      }

      public Integer getSaiyanZenkaiAmount() {
         return Math.max(0, Math.min(this.saiyanZenkaiAmount, Integer.MAX_VALUE));
      }

      public Double getSaiyanZenkaiHealthRegen() {
         return Math.max(0.0, Math.min(this.saiyanZenkaiHealthRegen, Double.MAX_VALUE));
      }

      public Double getSaiyanZenkaiStatBoost() {
         return Math.max(0.0, Math.min(this.saiyanZenkaiStatBoost, Double.MAX_VALUE));
      }

      public Integer getSaiyanZenkaiCooldownSeconds() {
         return Math.max(0, Math.min(this.saiyanZenkaiCooldownSeconds, Integer.MAX_VALUE));
      }

      public Integer getNamekianAssimilationAmount() {
         return Math.max(0, Math.min(this.namekianAssimilationAmount, Integer.MAX_VALUE));
      }

      public Double getNamekianAssimilationHealthRegen() {
         return Math.max(0.0, Math.min(this.namekianAssimilationHealthRegen, Double.MAX_VALUE));
      }

      public Double getNamekianAssimilationStatBoost() {
         return Math.max(0.0, Math.min(this.namekianAssimilationStatBoost, Double.MAX_VALUE));
      }

      public Double getFrostDemonTPBoost() {
         return Math.max(1.0, Math.min(this.frostDemonTPBoost, Double.MAX_VALUE));
      }

      public Integer getBioAndroidCooldownSeconds() {
         return Math.max(0, Math.min(this.bioAndroidCooldownSeconds, Integer.MAX_VALUE));
      }

      public Double getBioAndroidDrainRatio() {
         return Math.max(0.0, Math.min(this.bioAndroidDrainRatio, Double.MAX_VALUE));
      }

      public Integer getMajinAbsorptionAmount() {
         return Math.max(0, Math.min(this.majinAbsorptionAmount, Integer.MAX_VALUE));
      }

      public Double getMajinAbsorptionHealthRegen() {
         return Math.max(0.0, Math.min(this.majinAbsorptionHealthRegen, Double.MAX_VALUE));
      }

      public Double getMajinAbsorptionStatCopy() {
         return Math.max(0.0, Math.min(this.majinAbsorptionStatsCopy, Double.MAX_VALUE));
      }

      public Integer getMajinReviveCooldownSeconds() {
         return Math.max(0, Math.min(this.majinReviveCooldownSeconds, Integer.MAX_VALUE));
      }

      public Double getMajinReviveHealthRatioPerBlop() {
         return Math.max(0.0, Math.min(this.majinReviveHealthRatioPerBlop, Double.MAX_VALUE));
      }

      @Generated
      public Boolean getEnableRacialSkills() {
         return this.enableRacialSkills;
      }

      @Generated
      public Boolean getHumanRacialSkill() {
         return this.humanRacialSkill;
      }

      @Generated
      public Boolean getSaiyanRacialSkill() {
         return this.saiyanRacialSkill;
      }

      @Generated
      public String[] getSaiyanZenkaiBoosts() {
         return this.saiyanZenkaiBoosts;
      }

      @Generated
      public Boolean getNamekianRacialSkill() {
         return this.namekianRacialSkill;
      }

      @Generated
      public String[] getNamekianAssimilationBoosts() {
         return this.namekianAssimilationBoosts;
      }

      @Generated
      public Boolean getNamekianAssimilationOnNamekNpcs() {
         return this.namekianAssimilationOnNamekNpcs;
      }

      @Generated
      public Boolean getFrostDemonRacialSkill() {
         return this.frostDemonRacialSkill;
      }

      @Generated
      public Boolean getBioAndroidRacialSkill() {
         return this.bioAndroidRacialSkill;
      }

      @Generated
      public Boolean getMajinAbsoprtionSkill() {
         return this.majinAbsoprtionSkill;
      }

      @Generated
      public Boolean getMajinReviveSkill() {
         return this.majinReviveSkill;
      }

      @Generated
      public Double getMajinAbsorptionStatsCopy() {
         return this.majinAbsorptionStatsCopy;
      }

      @Generated
      public String[] getMajinAbsorptionBoosts() {
         return this.majinAbsorptionBoosts;
      }

      @Generated
      public Boolean getMajinAbsorptionOnMobs() {
         return this.majinAbsorptionOnMobs;
      }
   }

   public static class StorageConfig {
      private GeneralServerConfig.StorageConfig.StorageType storageType = GeneralServerConfig.StorageConfig.StorageType.NBT;
      private String host = "localhost";
      private Integer port = 3306;
      private String database = "dragonminez";
      private String table = "player_data";
      private String username = "root";
      private String password = "password";
      private Integer poolSize = 10;
      private Integer threadPoolSize = 4;

      public Integer getPoolSize() {
         return Math.max(1, this.poolSize);
      }

      public Integer getThreadPoolSize() {
         return Math.max(1, this.threadPoolSize);
      }

      @Generated
      public GeneralServerConfig.StorageConfig.StorageType getStorageType() {
         return this.storageType;
      }

      @Generated
      public String getHost() {
         return this.host;
      }

      @Generated
      public Integer getPort() {
         return this.port;
      }

      @Generated
      public String getDatabase() {
         return this.database;
      }

      @Generated
      public String getTable() {
         return this.table;
      }

      @Generated
      public String getUsername() {
         return this.username;
      }

      @Generated
      public String getPassword() {
         return this.password;
      }

      public static enum StorageType {
         NBT,
         JSON,
         DATABASE;
      }
   }

   public static class WorldGenConfig {
      private Boolean generateCustomStructures = true;
      private Boolean generateDragonBalls = true;
      private Boolean otherworldActive = true;
      private Integer dbSpawnRange = 1000;
      private Integer dragonBallSets = 1;
      private Integer structureMinDistanceFromSpawn = 0;
      private Integer structureMaxDistanceFromSpawn = 4000;
      private Integer structureMinDistanceBetween = 250;
      private Integer structureSpacing = 6000;
      private Integer structureSeparation = 2000;

      public Integer getDBSpawnRange() {
         return Math.max(100, Math.min(this.dbSpawnRange, 6000));
      }

      public Integer getDragonBallSets() {
         return Math.max(0, Math.min(this.dragonBallSets, 10));
      }

      public Integer getStructureMaxDistanceFromSpawn() {
         int value = this.structureMaxDistanceFromSpawn != null ? this.structureMaxDistanceFromSpawn : 8000;
         return Math.max(3000, value);
      }

      public Integer getStructureMinDistanceFromSpawn() {
         int value = this.structureMinDistanceFromSpawn != null ? this.structureMinDistanceFromSpawn : 0;
         return Math.max(0, Math.min(value, this.getStructureMaxDistanceFromSpawn() - 16));
      }

      public Integer getStructureMinDistanceBetween() {
         int value = this.structureMinDistanceBetween != null ? this.structureMinDistanceBetween : 250;
         return Math.max(0, value);
      }

      public Integer getStructureSpacingBlocks() {
         int value = this.structureSpacing != null ? this.structureSpacing : 6000;
         return Math.max(256, value);
      }

      public Integer getStructureSeparationBlocks() {
         int value = this.structureSeparation != null ? this.structureSeparation : 2000;
         return Math.max(0, Math.min(value, this.getStructureSpacingBlocks() - 16));
      }

      @Generated
      public Boolean getGenerateCustomStructures() {
         return this.generateCustomStructures;
      }

      @Generated
      public Boolean getGenerateDragonBalls() {
         return this.generateDragonBalls;
      }

      @Generated
      public Boolean getOtherworldActive() {
         return this.otherworldActive;
      }

      @Generated
      public Integer getStructureSpacing() {
         return this.structureSpacing;
      }

      @Generated
      public Integer getStructureSeparation() {
         return this.structureSeparation;
      }
   }
}
