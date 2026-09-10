package com.dragonminez.common.config;

import com.dragonminez.common.combat.logic.player.TargetHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Generated;

public class CombatConfig {
   public static final String CURRENT_VERSION = "2.1.3";
   private String configVersion;
   private Double staminaConsumptionRatio = 0.083;
   private Double blockStaminaCost = 0.25;
   private Integer knockdownDurationSeconds = 30;
   private Integer baselineFormDrain = 80;
   private Boolean killPlayersOnCombatLogout = true;
   private Double kiProtectionMitigationPerLevel = 0.025;
   private Double kiProtectionCostRatio = 0.15;
   private Double kiInfusionDamagePerLevel = 0.025;
   private Double kiInfusionBaseCostPct = 2.5;
   private Double kiInfusionMaxCostPct = 7.5;
   private Double baseDamageReductionCap = 0.75;
   private Double enchantmentDamageReductionCap = 0.85;
   private Double defenseDecayOnGuardBreak = 0.66;
   private Double flatMitigationFactor = 0.1;
   private Double flatMitigationMaxAbsorbFraction = 0.82;
   private Double defenseReductionScale = 0.11;
   private Boolean enableAdaptativeDefenseMitigation = true;
   private Double adaptativeMitigationParityRatio = 1.0;
   private Double adaptativeMitigationParityValue = 0.25;
   private Double adaptativeMitigationZeroRatio = 5.0;
   private Double adaptativeDefenseMitigationCap = 0.65;
   private Boolean cancelDamageEventIfMitigationTooHigh = true;
   private Double cancelDamageMitigationThreshold = 2.5;
   private Boolean accurateMobBattlePower = true;
   private Boolean enableBlocking = true;
   private Boolean enableParrying = true;
   private Integer parryWindowMs = 150;
   private Double parryStaminaCostPenalty = 2.0;
   private Integer blockBreakStunDurationTicks = 60;
   private Double poiseDamageMultiplier = 0.25;
   private Double blockDamageReductionCap = 0.65;
   private Double blockDamageReductionMin = 0.05;
   private Integer poiseRegenCooldown = 100;
   private Boolean enablePerfectEvasion = true;
   private Integer perfectEvasionWindowMs = 200;
   private Integer dashCooldownSeconds = 4;
   private Integer doubleDashCooldownSeconds = 12;
   private Integer teleportCooldownSeconds = 30;
   private Boolean combatFlyAutoSwitchOnDamage = true;
   private Integer combatFlyLockSeconds = 8;
   private Double combatFlyBaseSpeed = 0.3;
   private Double combatFlySprintSpeed = 0.5;
   private Double combatFlyHoldSpeedMultiplier = 1.6;
   private Double combatFlyDrainMultiplier = 0.5;
   private Double combatFlyImpulseKiCostPct = 0.05;
   private Integer combatFlyImpulseCooldownTicks = 25;
   private Map<String, CombatConfig.KiWeaponConfig> kiWeaponsConfig = new HashMap<String, CombatConfig.KiWeaponConfig>() {
      {
         this.put("blade", new CombatConfig.KiWeaponConfig(0.0, 0.25, 0.0, 0.075, -2.4, "#FFFFFF", "dragonminez:sword"));
         this.put("scythe", new CombatConfig.KiWeaponConfig(0.0, 0.45, 0.0, 0.105, -2.8, "#FFFFFF", "dragonminez:scythe"));
         this.put("clawlance", new CombatConfig.KiWeaponConfig(0.0, 0.65, 0.0, 0.175, -2.6, "#FFFFFF", "dragonminez:trident"));
      }
   };
   private Float upswingMultiplier = 0.5F;
   private Boolean allowAttackingMount = false;
   private Integer attackIntervalCap = 2;
   private Boolean weaponRegistryLogging = false;
   private Boolean weaponRegistryCompression = true;
   private Map<String, TargetHelper.Relation> playerRelations = new HashMap<String, TargetHelper.Relation>() {
      {
         this.put("minecraft:player", TargetHelper.Relation.HOSTILE);
         this.put("minecraft:villager", TargetHelper.Relation.NEUTRAL);
         this.put("minecraft:iron_golem", TargetHelper.Relation.NEUTRAL);
         this.put("guardvillagers:guard", TargetHelper.Relation.NEUTRAL);
      }
   };
   private List<String> masteryBlacklistEntities = new ArrayList<>(Arrays.asList("minecraft:silverfish", "dummmmmmy:target_dummy"));
   private TargetHelper.Relation playerRelationToPassives = TargetHelper.Relation.HOSTILE;
   private TargetHelper.Relation playerRelationToHostiles = TargetHelper.Relation.HOSTILE;
   private TargetHelper.Relation playerRelationToOther = TargetHelper.Relation.HOSTILE;
   private Boolean fallbackCompatibilityEnabled = true;
   private String blacklistItemIdRegex = "pickaxe";
   private List<CombatConfig.CompatibilitySpecifier> fallbackCompatibility = new ArrayList<>(
      Arrays.asList(
         new CombatConfig.CompatibilitySpecifier("claymore|great_sword|greatsword", "dragonminez:claymore"),
         new CombatConfig.CompatibilitySpecifier("great_hammer|greathammer|war_hammer|warhammer|maul", "dragonminez:hammer"),
         new CombatConfig.CompatibilitySpecifier("double_axe|doubleaxe|war_axe|waraxe|great_axe|greataxe", "dragonminez:double_axe"),
         new CombatConfig.CompatibilitySpecifier("scythe", "dragonminez:scythe"),
         new CombatConfig.CompatibilitySpecifier("halberd|glaive|pike|lance|naginata", "dragonminez:halberd"),
         new CombatConfig.CompatibilitySpecifier("spear|trident|pitchfork|javelin", "dragonminez:spear"),
         new CombatConfig.CompatibilitySpecifier("battlestaff|staff|quarterstaff|pole", "dragonminez:battlestaff"),
         new CombatConfig.CompatibilitySpecifier("katana|uchigatana|nodachi|tachi", "dragonminez:katana"),
         new CombatConfig.CompatibilitySpecifier("rapier|foil", "dragonminez:rapier"),
         new CombatConfig.CompatibilitySpecifier("dagger|knife|shiv|dirk|kunai|karambit|wakizashi|tanto", "dragonminez:dagger"),
         new CombatConfig.CompatibilitySpecifier("sickle|kama", "dragonminez:sickle"),
         new CombatConfig.CompatibilitySpecifier("soul_knife", "dragonminez:soul_knife"),
         new CombatConfig.CompatibilitySpecifier("claw|katar", "dragonminez:claw"),
         new CombatConfig.CompatibilitySpecifier("wand", "dragonminez:wand"),
         new CombatConfig.CompatibilitySpecifier("mace|hammer|flail", "dragonminez:mace"),
         new CombatConfig.CompatibilitySpecifier("axe", "dragonminez:axe"),
         new CombatConfig.CompatibilitySpecifier("coral_blade", "dragonminez:coral_blade"),
         new CombatConfig.CompatibilitySpecifier("twin_blade|twinblade", "dragonminez:twin_blade"),
         new CombatConfig.CompatibilitySpecifier("cutlass|scimitar|machete", "dragonminez:cutlass"),
         new CombatConfig.CompatibilitySpecifier("sword|blade", "dragonminez:sword")
      )
   );

   public float getUpswingMultiplier() {
      return Math.max(0.2F, Math.min(1.0F, this.upswingMultiplier));
   }

   public double getFlatMitigationFactor() {
      return this.flatMitigationFactor != null ? Math.max(0.0, this.flatMitigationFactor) : 0.1;
   }

   public double getFlatMitigationMaxAbsorbFraction() {
      return this.flatMitigationMaxAbsorbFraction != null ? Math.max(0.0, Math.min(1.0, this.flatMitigationMaxAbsorbFraction)) : 0.82;
   }

   public double getDefenseReductionScale() {
      return this.defenseReductionScale != null ? Math.max(0.01, this.defenseReductionScale) : 0.11;
   }

   public boolean getEnableAdaptativeDefenseMitigation() {
      return this.enableAdaptativeDefenseMitigation == null || this.enableAdaptativeDefenseMitigation;
   }

   public double getAdaptativeMitigationParityRatio() {
      return this.adaptativeMitigationParityRatio != null ? Math.max(1.0E-4, this.adaptativeMitigationParityRatio) : 1.0;
   }

   public double getAdaptativeMitigationParityValue() {
      return this.adaptativeMitigationParityValue != null ? Math.max(0.0, Math.min(1.0, this.adaptativeMitigationParityValue)) : 0.25;
   }

   public double getAdaptativeMitigationZeroRatio() {
      return this.adaptativeMitigationZeroRatio != null
         ? Math.max(this.getAdaptativeMitigationParityRatio() + 1.0E-4, this.adaptativeMitigationZeroRatio)
         : 5.0;
   }

   public double getAdaptativeDefenseMitigationCap() {
      return this.adaptativeDefenseMitigationCap != null ? Math.max(0.0, Math.min(1.0, this.adaptativeDefenseMitigationCap)) : 0.7;
   }

   public boolean getCancelDamageEventIfMitigationTooHigh() {
      return this.cancelDamageEventIfMitigationTooHigh == null || this.cancelDamageEventIfMitigationTooHigh;
   }

   public double getCancelDamageMitigationThreshold() {
      return this.cancelDamageMitigationThreshold != null ? Math.max(1.0, this.cancelDamageMitigationThreshold) : 3.0;
   }

   public boolean getAccurateMobBattlePower() {
      return this.accurateMobBattlePower == null || this.accurateMobBattlePower;
   }

   public CombatConfig.KiWeaponConfig getKiWeaponConfig(String type) {
      return type != null && this.kiWeaponsConfig != null ? this.kiWeaponsConfig.get(type.toLowerCase()) : null;
   }

   public List<String> getKiWeaponTypes() {
      if (this.kiWeaponsConfig == null) {
         return new ArrayList<>();
      } else {
         List<String> keys = new ArrayList<>(this.kiWeaponsConfig.keySet());
         Collections.sort(keys);
         return keys;
      }
   }

   @Generated
   public String getConfigVersion() {
      return this.configVersion;
   }

   @Generated
   public Double getStaminaConsumptionRatio() {
      return this.staminaConsumptionRatio;
   }

   @Generated
   public Double getBlockStaminaCost() {
      return this.blockStaminaCost;
   }

   @Generated
   public Integer getKnockdownDurationSeconds() {
      return this.knockdownDurationSeconds;
   }

   @Generated
   public Integer getBaselineFormDrain() {
      return this.baselineFormDrain;
   }

   @Generated
   public Boolean getKillPlayersOnCombatLogout() {
      return this.killPlayersOnCombatLogout;
   }

   @Generated
   public Double getKiProtectionMitigationPerLevel() {
      return this.kiProtectionMitigationPerLevel;
   }

   @Generated
   public Double getKiProtectionCostRatio() {
      return this.kiProtectionCostRatio;
   }

   @Generated
   public Double getKiInfusionDamagePerLevel() {
      return this.kiInfusionDamagePerLevel;
   }

   @Generated
   public Double getKiInfusionBaseCostPct() {
      return this.kiInfusionBaseCostPct;
   }

   @Generated
   public Double getKiInfusionMaxCostPct() {
      return this.kiInfusionMaxCostPct;
   }

   @Generated
   public Double getBaseDamageReductionCap() {
      return this.baseDamageReductionCap;
   }

   @Generated
   public Double getEnchantmentDamageReductionCap() {
      return this.enchantmentDamageReductionCap;
   }

   @Generated
   public Double getDefenseDecayOnGuardBreak() {
      return this.defenseDecayOnGuardBreak;
   }

   @Generated
   public Boolean getEnableBlocking() {
      return this.enableBlocking;
   }

   @Generated
   public Boolean getEnableParrying() {
      return this.enableParrying;
   }

   @Generated
   public Integer getParryWindowMs() {
      return this.parryWindowMs;
   }

   @Generated
   public Double getParryStaminaCostPenalty() {
      return this.parryStaminaCostPenalty;
   }

   @Generated
   public Integer getBlockBreakStunDurationTicks() {
      return this.blockBreakStunDurationTicks;
   }

   @Generated
   public Double getPoiseDamageMultiplier() {
      return this.poiseDamageMultiplier;
   }

   @Generated
   public Double getBlockDamageReductionCap() {
      return this.blockDamageReductionCap;
   }

   @Generated
   public Double getBlockDamageReductionMin() {
      return this.blockDamageReductionMin;
   }

   @Generated
   public Integer getPoiseRegenCooldown() {
      return this.poiseRegenCooldown;
   }

   @Generated
   public Boolean getEnablePerfectEvasion() {
      return this.enablePerfectEvasion;
   }

   @Generated
   public Integer getPerfectEvasionWindowMs() {
      return this.perfectEvasionWindowMs;
   }

   @Generated
   public Integer getDashCooldownSeconds() {
      return this.dashCooldownSeconds;
   }

   @Generated
   public Integer getDoubleDashCooldownSeconds() {
      return this.doubleDashCooldownSeconds;
   }

   @Generated
   public Integer getTeleportCooldownSeconds() {
      return this.teleportCooldownSeconds;
   }

   @Generated
   public Boolean getCombatFlyAutoSwitchOnDamage() {
      return this.combatFlyAutoSwitchOnDamage;
   }

   @Generated
   public Integer getCombatFlyLockSeconds() {
      return this.combatFlyLockSeconds;
   }

   @Generated
   public Double getCombatFlyBaseSpeed() {
      return this.combatFlyBaseSpeed;
   }

   @Generated
   public Double getCombatFlySprintSpeed() {
      return this.combatFlySprintSpeed;
   }

   @Generated
   public Double getCombatFlyHoldSpeedMultiplier() {
      return this.combatFlyHoldSpeedMultiplier;
   }

   @Generated
   public Double getCombatFlyDrainMultiplier() {
      return this.combatFlyDrainMultiplier;
   }

   @Generated
   public Double getCombatFlyImpulseKiCostPct() {
      return this.combatFlyImpulseKiCostPct;
   }

   @Generated
   public Integer getCombatFlyImpulseCooldownTicks() {
      return this.combatFlyImpulseCooldownTicks;
   }

   @Generated
   public Map<String, CombatConfig.KiWeaponConfig> getKiWeaponsConfig() {
      return this.kiWeaponsConfig;
   }

   @Generated
   public Boolean getAllowAttackingMount() {
      return this.allowAttackingMount;
   }

   @Generated
   public Integer getAttackIntervalCap() {
      return this.attackIntervalCap;
   }

   @Generated
   public Boolean getWeaponRegistryLogging() {
      return this.weaponRegistryLogging;
   }

   @Generated
   public Boolean getWeaponRegistryCompression() {
      return this.weaponRegistryCompression;
   }

   @Generated
   public Map<String, TargetHelper.Relation> getPlayerRelations() {
      return this.playerRelations;
   }

   @Generated
   public List<String> getMasteryBlacklistEntities() {
      return this.masteryBlacklistEntities;
   }

   @Generated
   public TargetHelper.Relation getPlayerRelationToPassives() {
      return this.playerRelationToPassives;
   }

   @Generated
   public TargetHelper.Relation getPlayerRelationToHostiles() {
      return this.playerRelationToHostiles;
   }

   @Generated
   public TargetHelper.Relation getPlayerRelationToOther() {
      return this.playerRelationToOther;
   }

   @Generated
   public Boolean getFallbackCompatibilityEnabled() {
      return this.fallbackCompatibilityEnabled;
   }

   @Generated
   public String getBlacklistItemIdRegex() {
      return this.blacklistItemIdRegex;
   }

   @Generated
   public List<CombatConfig.CompatibilitySpecifier> getFallbackCompatibility() {
      return this.fallbackCompatibility;
   }

   @Generated
   public void setConfigVersion(String configVersion) {
      this.configVersion = configVersion;
   }

   public static class CompatibilitySpecifier {
      private String item_id_regex;
      private String weapon_attributes;

      @Generated
      public CompatibilitySpecifier(String item_id_regex, String weapon_attributes) {
         this.item_id_regex = item_id_regex;
         this.weapon_attributes = weapon_attributes;
      }

      @Generated
      public CompatibilitySpecifier() {
      }

      @Generated
      public String getItem_id_regex() {
         return this.item_id_regex;
      }

      @Generated
      public String getWeapon_attributes() {
         return this.weapon_attributes;
      }
   }

   public static class KiWeaponConfig {
      private Double baseDamage = 0.0;
      private Double kiScalingDamage = 1.0;
      private Double baseKiCost = 0.0;
      private Double kiScalingCost = 0.05;
      private Double attackSpeed = -2.4;
      private String forcedColor = "#FFFFFF";
      private String weaponCombo = "";

      public double getBaseDamage() {
         return this.baseDamage != null ? Math.max(0.0, this.baseDamage) : 0.0;
      }

      public double getKiScalingDamage() {
         return this.kiScalingDamage != null ? Math.max(0.0, this.kiScalingDamage) : 0.0;
      }

      public double getBaseKiCost() {
         return this.baseKiCost != null ? Math.max(0.0, this.baseKiCost) : 0.0;
      }

      public double getKiScalingCost() {
         return this.kiScalingCost != null ? Math.max(0.0, this.kiScalingCost) : 0.0;
      }

      public double getAttackSpeed() {
         return this.attackSpeed != null ? this.attackSpeed : 0.0;
      }

      @Generated
      public KiWeaponConfig(
         Double baseDamage, Double kiScalingDamage, Double baseKiCost, Double kiScalingCost, Double attackSpeed, String forcedColor, String weaponCombo
      ) {
         this.baseDamage = baseDamage;
         this.kiScalingDamage = kiScalingDamage;
         this.baseKiCost = baseKiCost;
         this.kiScalingCost = kiScalingCost;
         this.attackSpeed = attackSpeed;
         this.forcedColor = forcedColor;
         this.weaponCombo = weaponCombo;
      }

      @Generated
      public KiWeaponConfig() {
      }

      @Generated
      public String getForcedColor() {
         return this.forcedColor;
      }

      @Generated
      public String getWeaponCombo() {
         return this.weaponCombo;
      }
   }
}
