package com.dragonminez.common.stats;

import com.dragonminez.common.config.CombatConfig;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.config.RaceStatsConfig;
import com.dragonminez.common.config.TpBoost;
import com.dragonminez.common.config.TpSource;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.init.MainAttributes;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainEnchants;
import com.dragonminez.common.quest.Difficulty;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.stats.character.BonusStats;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Cooldowns;
import com.dragonminez.common.stats.character.Effects;
import com.dragonminez.common.stats.character.Resources;
import com.dragonminez.common.stats.character.SecondaryStatEffects;
import com.dragonminez.common.stats.character.Stats;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.extras.DynamicGrowthData;
import com.dragonminez.common.stats.skills.Skills;
import com.dragonminez.common.stats.techniques.Techniques;
import com.dragonminez.common.util.AttributeMods;
import com.dragonminez.common.util.TransformationsHelper;
import com.dragonminez.server.events.players.StatsEvents;
import com.dragonminez.server.events.players.TickHandler;
import com.dragonminez.server.events.players.statuseffect.TransformStatusHandler;
import com.dragonminez.server.util.FusionLogic;
import com.dragonminez.server.util.GravityLogic;
import com.dragonminez.server.util.PotionEffectHelper;
import com.dragonminez.server.world.dimension.HTCDimension;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantments;

public class StatsData {
   private static final double DEFENSE_FLAT_FOLD = 0.12;
   private static final double STAT_COST_PER_POINT = 1.25;
   private static final double STAT_COST_LATE_KNEE_FRACTION = 0.05;
   private static final double STAT_COST_LATE_EXPONENT = 0.7;
   private final Player player;
   private final Stats stats;
   private final Status status;
   private final Cooldowns cooldowns;
   private final Character character;
   private final Resources resources;
   private final Skills skills;
   private final Effects effects;
   private final SecondaryStatEffects secondaryStatEffects;
   private final PlayerQuestData playerQuestData;
   private final BonusStats bonusStats;
   private final Techniques techniques;
   private final DynamicGrowthData dynamicGrowth;
   private boolean hasInitializedHealth = false;
   private boolean isDataLoaded = false;
   private static final double K = 100.0;
   private static final double BP_REF_VALUE = 1200.0;
   private static final double BP_CURVE_EXPONENT = 1.2;
   private static final double SUPPORT_STAT_BP_WEIGHT = 0.5;

   public StatsData(Player player) {
      this.player = player;
      this.stats = new Stats();
      this.stats.setPlayer(player);
      this.status = new Status();
      this.cooldowns = new Cooldowns();
      this.character = new Character();
      this.resources = new Resources();
      this.resources.setPlayer(player);
      this.resources.setStatsData(this);
      this.skills = new Skills();
      this.effects = new Effects();
      this.secondaryStatEffects = new SecondaryStatEffects();
      this.playerQuestData = new PlayerQuestData();
      this.bonusStats = new BonusStats();
      this.techniques = new Techniques();
      this.dynamicGrowth = new DynamicGrowthData();
   }

   public boolean hasInitializedHealth() {
      return this.hasInitializedHealth;
   }

   public void setInitializedHealth(boolean initialized) {
      this.hasInitializedHealth = initialized;
   }

   public int getLevel() {
      int maxLevel = this.getConfiguredMaxValue();
      if (maxLevel <= 1) {
         return 1;
      } else {
         int initialStats = this.getInitialTotalStats();
         int totalStats = Math.max(initialStats, this.stats.getTotalStats());
         long maxTotalStatsForLevel = Math.max((long)initialStats, this.getConfiguredMaxTotalStatsRaw());
         double denominator = Math.max(1.0, (double)maxTotalStatsForLevel - (double)initialStats);
         double progress = (double)(totalStats - initialStats) / denominator;
         progress = Math.max(0.0, Math.min(1.0, progress));
         int computedLevel = 1 + (int)Math.floor(progress * (double)(maxLevel - 1));
         return Math.max(1, Math.min(maxLevel, computedLevel));
      }
   }

   public int getConfiguredMaxValue() {
      return ConfigManager.getServerConfig().getGameplay().getMaxValue();
   }

   public boolean isMaxLevelValueInsteadOfStats() {
      return ConfigManager.getServerConfig().getGameplay().getMaxLevelValueInsteadOfStats();
   }

   public long getConfiguredMaxTotalStatsLong() {
      long raw = this.getConfiguredMaxTotalStatsRaw();
      return raw < 0L ? 1152921504606846975L : raw;
   }

   public int getConfiguredMaxTotalStats() {
      long rawMax = this.getConfiguredMaxTotalStatsLong();
      return rawMax > 2147483647L ? Integer.MAX_VALUE : (int)rawMax;
   }

   public long getRemainingAssignableStatsLong() {
      long remaining = this.getConfiguredMaxTotalStatsLong() - (long)this.stats.getTotalStats();
      return Math.max(0L, remaining);
   }

   public int getRemainingAssignableStats() {
      long remaining = this.getRemainingAssignableStatsLong();
      return remaining <= 0L ? 0 : (int)Math.min(2147483647L, remaining);
   }

   public int getCurrentStatValue(String statName) {
      String var2 = statName.toUpperCase();

      return switch (var2) {
         case "STR" -> this.stats.getStrength();
         case "SKP" -> this.stats.getStrikePower();
         case "RES" -> this.stats.getResistance();
         case "VIT" -> this.stats.getVitality();
         case "PWR" -> this.stats.getKiPower();
         case "ENE" -> this.stats.getEnergy();
         default -> 0;
      };
   }

   public int getMaxAllowedIncreaseForStat(String statName, int requestedAmount) {
      int safeRequested = Math.max(0, requestedAmount);
      if (safeRequested <= 0) {
         return 0;
      } else {
         long remainingTotal = this.getRemainingAssignableStatsLong();
         if (remainingTotal <= 0L) {
            return 0;
         } else {
            long allowedByTotal = Math.min((long)safeRequested, remainingTotal);
            if (!this.isMaxLevelValueInsteadOfStats()) {
               long remainingStat = Math.max(0L, (long)this.getConfiguredMaxValue() - (long)this.getCurrentStatValue(statName));
               allowedByTotal = Math.min(allowedByTotal, remainingStat);
            } else {
               long remainingStat = Math.max(0L, (long)this.getConfiguredMaxValue() - (long)this.getCurrentStatValue(statName));
               allowedByTotal = Math.min(allowedByTotal, remainingStat);
            }

            return (int)Math.min(2147483647L, allowedByTotal);
         }
      }
   }

   public int clampStatToConfiguredMax(int value) {
      int max = this.getConfiguredMaxValue();
      return value < 0 ? 0 : Math.min(value, max);
   }

   public float getBattlePower() {
      double exact = this.getBattlePowerExact();
      return exact >= Float.MAX_VALUE ? Float.MAX_VALUE : (float)exact;
   }

   public double getBattlePowerExact() {
      if (this.status.isAndroidUpgraded()) {
         return Float.MAX_VALUE;
      } else {
         double str = (double)this.stats.getStrength();
         double skp = (double)this.stats.getStrikePower();
         double res = (double)this.stats.getResistance();
         double vit = (double)this.stats.getVitality();
         double pwr = (double)this.stats.getKiPower();
         double ene = (double)this.stats.getEnergy();
         double multBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(str), true);
         double flatBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(str), false);
         double multBonusSkp = this.bonusStats.calculateBonus("SKP", (int)Math.round(skp), true);
         double flatBonusSkp = this.bonusStats.calculateBonus("SKP", (int)Math.round(skp), false);
         double multBonusDef = this.bonusStats.calculateBonus("DEF", (int)Math.round(res), true);
         double flatBonusDef = this.bonusStats.calculateBonus("DEF", (int)Math.round(res), false);
         double multBonusVit = this.bonusStats.calculateBonus("VIT", (int)Math.round(vit), true);
         double flatBonusVit = this.bonusStats.calculateBonus("VIT", (int)Math.round(vit), false);
         double multBonusPwr = this.bonusStats.calculateBonus("PWR", (int)Math.round(pwr), true);
         double flatBonusPwr = this.bonusStats.calculateBonus("PWR", (int)Math.round(pwr), false);
         double multBonusEne = this.bonusStats.calculateBonus("ENE", (int)Math.round(ene), true);
         double flatBonusEne = this.bonusStats.calculateBonus("ENE", (int)Math.round(ene), false);
         double rawPower = (str + multBonusStr) * this.getStatScaling("STR") * this.getTotalMultiplier("STR")
            + flatBonusStr * this.getStatScaling("STR")
            + (skp + multBonusSkp) * this.getStatScaling("SKP") * this.getTotalMultiplier("SKP")
            + flatBonusSkp * this.getStatScaling("SKP")
            + (res + multBonusDef) * this.getStatScaling("DEF") * this.getTotalMultiplier("RES")
            + flatBonusDef * this.getStatScaling("DEF")
            + (pwr + multBonusPwr) * this.getStatScaling("PWR") * this.getTotalMultiplier("PWR")
            + flatBonusPwr * this.getStatScaling("PWR");
         rawPower += 0.5
            * (
               (vit + multBonusVit) * this.getStatScaling("VIT") * this.getTotalMultiplier("VIT")
                  + flatBonusVit * this.getStatScaling("VIT")
                  + (ene + multBonusEne) * this.getStatScaling("ENE") * this.getTotalMultiplier("ENE")
                  + flatBonusEne * this.getStatScaling("ENE")
            );
         if (!Double.isNaN(rawPower) && !(rawPower <= 0.0)) {
            double releaseMultiplier = (double)this.resources.getPowerRelease() / 100.0;
            double bp = 1200.0 * Math.pow(rawPower / 100.0, 1.2) * releaseMultiplier;
            return !Double.isNaN(bp) && !(bp <= 0.0) ? bp : 0.0;
         } else {
            return 0.0;
         }
      }
   }

   private double getSecondaryAttributeValue(Holder<Attribute> attribute, double fallback) {
      if (this.player == null) {
         return fallback;
      } else {
         AttributeInstance instance = this.player.getAttribute(attribute);
         return instance != null ? instance.getValue() : fallback;
      }
   }

   private double getSecondaryAttributeBaseValue(Holder<Attribute> attribute, double fallback) {
      if (this.player == null) {
         return fallback;
      } else {
         AttributeInstance instance = this.player.getAttribute(attribute);
         return instance != null ? instance.getBaseValue() : fallback;
      }
   }

   private double getArmorToughnessValue() {
      if (this.player == null) {
         return 0.0;
      } else {
         AttributeInstance toughness = this.player.getAttribute(Attributes.ARMOR_TOUGHNESS);
         return toughness != null ? toughness.getValue() : 0.0;
      }
   }

   public float getHealthBonus() {
      double vitality = (double)this.stats.getVitality();
      double vitScaling = this.getStatScaling("VIT");
      double vitMult = this.getTotalMultiplier("VIT");
      double flatBonusVit = this.bonusStats.calculateBonus("VIT", (int)Math.round(vitality), false);
      double multBonusVit = this.bonusStats.calculateBonus("VIT", (int)Math.round(vitality), true);
      return (float)Math.min((vitality + multBonusVit) * vitScaling * vitMult + flatBonusVit * vitScaling, Float.MAX_VALUE);
   }

   public float getMaxHealth() {
      return (float)this.getSecondaryAttributeValue(Attributes.MAX_HEALTH, 20.0);
   }

   public float getMaxEnergy() {
      double energy = (double)this.stats.getEnergy();
      double eneScaling = this.getStatScaling("ENE");
      double eneMult = this.getTotalMultiplier("ENE");
      double flatBonusEne = this.bonusStats.calculateBonus("ENE", (int)Math.round(energy), false);
      double multBonusEne = this.bonusStats.calculateBonus("ENE", (int)Math.round(energy), true);
      double secondaryMaxEnergy = this.getSecondaryAttributeValue(MainAttributes.MAX_ENERGY, 20.0);
      return Math.min((float)(secondaryMaxEnergy + (energy + multBonusEne) * eneScaling * eneMult + flatBonusEne * eneScaling), Float.MAX_VALUE);
   }

   public float getMaxStamina() {
      double resistance = (double)this.stats.getResistance();
      double stmScaling = this.getStatScaling("STM");
      double stmMult = this.getTotalMultiplier("STM");
      double flatBonusStm = this.bonusStats.calculateBonus("STM", (int)Math.round(resistance), false);
      double multBonusStm = this.bonusStats.calculateBonus("STM", (int)Math.round(resistance), true);
      double secondaryMaxStamina = this.getSecondaryAttributeValue(MainAttributes.MAX_STAMINA, 20.0);
      double maxStamina = secondaryMaxStamina + (resistance + multBonusStm) * stmScaling * stmMult + flatBonusStm * stmScaling;
      return Math.min((float)Math.max(0.0, maxStamina), Float.MAX_VALUE);
   }

   public double getStaminaRegenPerSecond() {
      RaceStatsConfig raceConfig = ConfigManager.getRaceStats(this.character.getRaceName());
      RaceStatsConfig.ClassStats classStats = this.getClassStats(raceConfig, this.character.getCharacterClass());
      int baseVit = this.stats.getVitality();
      double flatBonusVit = this.bonusStats.calculateBonus("VIT", baseVit, false);
      double multBonusVit = this.bonusStats.calculateBonus("VIT", baseVit, true);
      double vitMult = this.getTotalMultiplier("VIT");
      double effectiveVit = ((double)baseVit + multBonusVit) * vitMult + flatBonusVit;
      double sp5 = classStats.getBaseSp5() + effectiveVit * classStats.getSp5StmScaling();
      int totalEnchLvl = TickHandler.getTotalArmorEnchantmentLevel(MainEnchants.RESISTANCE_RECOVERY, this.player);
      double enchMult = TickHandler.getRecoveryMultiplier(totalEnchLvl);
      int meditationLevel = this.skills.getSkillLevel("meditation");
      double meditationBonus = meditationLevel > 0 ? 1.0 + (double)meditationLevel * 0.05 : 1.0;
      double adjustedStaminaDrain = this.getAdjustedStaminaDrain();
      double regenMultiplier = 1.0;
      if (adjustedStaminaDrain > 0.0) {
         regenMultiplier = Math.max(0.0, 1.0 - adjustedStaminaDrain / 50.0);
      } else if (adjustedStaminaDrain < 0.0) {
         regenMultiplier = 1.0 + Math.abs(adjustedStaminaDrain);
      }

      double actionMod = this.player != null && this.player.getPersistentData().contains("dmz_stamina_regen_mod")
         ? this.player.getPersistentData().getDouble("dmz_stamina_regen_mod")
         : 1.0;
      double regenPerSecond = sp5 / 5.0 * meditationBonus * enchMult * regenMultiplier * actionMod * this.secondaryStatEffects.getMultiplier("STM_REGEN");
      return PotionEffectHelper.applyStaminaRegenMultiplier(this.player, regenPerSecond);
   }

   public double getHealthRegenPerSecond() {
      RaceStatsConfig raceConfig = ConfigManager.getRaceStats(this.character.getRaceName());
      RaceStatsConfig.ClassStats classStats = this.getClassStats(raceConfig, this.character.getCharacterClass());
      int baseVit = this.stats.getVitality();
      double flatBonusVit = this.bonusStats.calculateBonus("VIT", baseVit, false);
      double multBonusVit = this.bonusStats.calculateBonus("VIT", baseVit, true);
      double vitMult = this.getTotalMultiplier("VIT");
      double effectiveVit = ((double)baseVit + multBonusVit) * vitMult + flatBonusVit;
      double hp5 = classStats.getBaseHp5() + effectiveVit * classStats.getHp5VitScaling();
      int totalEnchLvl = TickHandler.getTotalArmorEnchantmentLevel(MainEnchants.VITALITY_RECOVERY, this.player);
      double enchMult = TickHandler.getRecoveryMultiplier(totalEnchLvl);
      double adjustedHealthDrain = this.getAdjustedHealthDrain();
      double regenMultiplier = 1.0;
      if (adjustedHealthDrain > 0.0) {
         regenMultiplier = Math.max(0.0, 1.0 - adjustedHealthDrain / 10.0);
      } else if (adjustedHealthDrain < 0.0) {
         regenMultiplier = 1.0 + Math.abs(adjustedHealthDrain);
      }

      return hp5 / 5.0 * enchMult * regenMultiplier * this.secondaryStatEffects.getMultiplier("HP_REGEN");
   }

   public double getEnergyRegenPerSecond(boolean activeCharging) {
      RaceStatsConfig raceConfig = ConfigManager.getRaceStats(this.character.getRaceName());
      RaceStatsConfig.ClassStats classStats = this.getClassStats(raceConfig, this.character.getCharacterClass());
      float currentEnergy = this.resources.getCurrentEnergy();
      float maxEnergy = this.getMaxEnergy();
      int baseEne = this.stats.getEnergy();
      double flatBonusEne = this.bonusStats.calculateBonus("ENE", baseEne, false);
      double multBonusEne = this.bonusStats.calculateBonus("ENE", baseEne, true);
      double eneMult = this.getTotalMultiplier("ENE");
      double effectiveEne = ((double)baseEne + multBonusEne) * eneMult + flatBonusEne;
      double ep5 = classStats.getBaseEp5() + effectiveEne * classStats.getEp5EneScaling();
      int totalEnchLvl = TickHandler.getTotalArmorEnchantmentLevel(MainEnchants.ENERGY_RECOVERY, this.player);
      double enchMult = TickHandler.getRecoveryMultiplier(totalEnchLvl);
      int meditationLevel = this.skills.getSkillLevel("meditation");
      double meditationBonus = meditationLevel > 0 ? 1.0 + (double)meditationLevel * 0.05 : 1.0;
      double kiConductivityMult = TickHandler.getRecoveryMultiplier(TickHandler.getTotalArmorEnchantmentLevel(MainEnchants.KI_CONDUCTIVITY, this.player));
      double baseRegenPerSecond = ep5 / 5.0 * meditationBonus * enchMult * kiConductivityMult;
      double androidRegenMult = this.isAndroidRacialActive() ? 2.0 : 1.0;
      double energyChange = 0.0;
      if (activeCharging) {
         int kiBoostLevel = this.skills.getSkillLevel("kiboost");
         double kiBoostMult = 1.0 + (double)kiBoostLevel * 0.25;
         double regenAmount = PotionEffectHelper.applyKiRegenMultiplier(this.player, baseRegenPerSecond * 1.5) * androidRegenMult * kiBoostMult;
         if (regenAmount < 1.0) {
            regenAmount = 1.0;
         }

         energyChange += regenAmount;
      } else if (currentEnergy < maxEnergy) {
         energyChange += PotionEffectHelper.applyKiRegenMultiplier(this.player, baseRegenPerSecond) * androidRegenMult;
      }

      return energyChange * this.secondaryStatEffects.getMultiplier("ENE_REGEN");
   }

   public float getMaxPoise() {
      double secondaryMaxPoise = this.getSecondaryAttributeValue(MainAttributes.MAX_POISE, 25.0);
      return Math.min((float)(secondaryMaxPoise + this.getDefenseLegacyUnits()), Float.MAX_VALUE);
   }

   public double getMaxMeleeDamage() {
      double strength = (double)this.stats.getStrength();
      double strScaling = this.getStatScaling("STR");
      double strMult = this.getTotalMultiplier("STR");
      double flatBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(strength), false);
      double multBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(strength), true);
      double secondaryMeleeDamage = this.getSecondaryAttributeValue(MainAttributes.MELEE_DAMAGE, 1.0);
      return secondaryMeleeDamage + (strength + multBonusStr) * strScaling * strMult + flatBonusStr * strScaling;
   }

   public double getMeleeDamage() {
      double strength = (double)this.stats.getStrength();
      double strScaling = this.getStatScaling("STR");
      double strMult = this.getTotalMultiplier("STR");
      double releaseMultiplier = (double)this.resources.getPowerRelease() / 100.0;
      double flatBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(strength), false);
      double multBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(strength), true);
      double secondaryMeleeDamage = this.getSecondaryAttributeValue(MainAttributes.MELEE_DAMAGE, 1.0);
      return secondaryMeleeDamage + ((strength + multBonusStr) * strScaling * strMult + flatBonusStr * strScaling) * releaseMultiplier;
   }

   public double getMeleeDamageNoMultipliers() {
      double strength = (double)this.stats.getStrength();
      double strScaling = this.getStatScaling("STR");
      double releaseMultiplier = (double)this.resources.getPowerRelease() / 100.0;
      double flatBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(strength), false);
      double multBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(strength), true);
      double secondaryMeleeDamage = this.getSecondaryAttributeValue(MainAttributes.MELEE_DAMAGE, 1.0);
      return secondaryMeleeDamage + ((strength + multBonusStr) * strScaling + flatBonusStr * strScaling) * releaseMultiplier;
   }

   public double getMaxStrikeDamage() {
      double strikePower = (double)this.stats.getStrikePower();
      double strength = (double)this.stats.getStrength();
      double skpScaling = this.getStatScaling("SKP");
      double strScaling = this.getStatScaling("STR");
      double skpMult = this.getTotalMultiplier("SKP");
      double strMult = this.getTotalMultiplier("STR");
      double flatBonusSkp = this.bonusStats.calculateBonus("SKP", (int)Math.round(strikePower), false);
      double multBonusSkp = this.bonusStats.calculateBonus("SKP", (int)Math.round(strikePower), true);
      double flatBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(strength), false);
      double multBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(strength), true);
      double secondaryStrikeDamage = this.getSecondaryAttributeValue(MainAttributes.STRIKE_DAMAGE, 1.0);
      return secondaryStrikeDamage
         + (strikePower + multBonusSkp) * skpScaling * skpMult
         + flatBonusSkp * skpScaling
         + ((strength + multBonusStr) * strScaling * strMult + flatBonusStr * strScaling) * 0.25;
   }

   public double getStrikeDamage() {
      double strikePower = (double)this.stats.getStrikePower();
      double strength = (double)this.stats.getStrength();
      double skpScaling = this.getStatScaling("SKP");
      double strScaling = this.getStatScaling("STR");
      double skpMult = this.getTotalMultiplier("SKP");
      double strMult = this.getTotalMultiplier("STR");
      double releaseMultiplier = (double)this.resources.getPowerRelease() / 100.0;
      double flatBonusSkp = this.bonusStats.calculateBonus("SKP", (int)Math.round(strikePower), false);
      double multBonusSkp = this.bonusStats.calculateBonus("SKP", (int)Math.round(strikePower), true);
      double flatBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(strength), false);
      double multBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(strength), true);
      double secondaryStrikeDamage = this.getSecondaryAttributeValue(MainAttributes.STRIKE_DAMAGE, 1.0);
      double baseDamage = (strikePower + multBonusSkp) * skpScaling * skpMult
         + flatBonusSkp * skpScaling
         + ((strength + multBonusStr) * strScaling * strMult + flatBonusStr * strScaling) * 0.25;
      return secondaryStrikeDamage + baseDamage * releaseMultiplier;
   }

   public double getStrikeDamageNoForms() {
      double strikePower = (double)this.stats.getStrikePower();
      double strength = (double)this.stats.getStrength();
      double skpScaling = this.getStatScaling("SKP");
      double strScaling = this.getStatScaling("STR");
      double releaseMultiplier = (double)this.resources.getPowerRelease() / 100.0;
      double flatBonusSkp = this.bonusStats.calculateBonus("SKP", (int)Math.round(strikePower), false);
      double multBonusSkp = this.bonusStats.calculateBonus("SKP", (int)Math.round(strikePower), true);
      double flatBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(strength), false);
      double multBonusStr = this.bonusStats.calculateBonus("STR", (int)Math.round(strength), true);
      double secondaryStrikeDamage = this.getSecondaryAttributeValue(MainAttributes.STRIKE_DAMAGE, 1.0);
      double baseDamage = (strikePower + multBonusSkp) * skpScaling
         + flatBonusSkp * skpScaling
         + ((strength + multBonusStr) * strScaling + flatBonusStr * strScaling) * 0.25;
      return secondaryStrikeDamage + baseDamage * releaseMultiplier;
   }

   public double getMaxKiDamage() {
      double kiPower = (double)this.stats.getKiPower();
      double pwrScaling = this.getStatScaling("PWR");
      double pwrMult = this.getTotalMultiplier("PWR");
      double flatBonusPwr = this.bonusStats.calculateBonus("PWR", (int)Math.round(kiPower), false);
      double multBonusPwr = this.bonusStats.calculateBonus("PWR", (int)Math.round(kiPower), true);
      double secondaryKiDamage = this.getSecondaryAttributeValue(MainAttributes.KI_DAMAGE, 0.0);
      return secondaryKiDamage + (kiPower + multBonusPwr) * pwrScaling * pwrMult + flatBonusPwr * pwrScaling;
   }

   public double getKiDamage() {
      double kiPower = (double)this.stats.getKiPower();
      double pwrScaling = this.getStatScaling("PWR");
      double pwrMult = this.getTotalMultiplier("PWR");
      double releaseMultiplier = (double)this.resources.getPowerRelease() / 100.0;
      double flatBonusPwr = this.bonusStats.calculateBonus("PWR", (int)Math.round(kiPower), false);
      double multBonusPwr = this.bonusStats.calculateBonus("PWR", (int)Math.round(kiPower), true);
      double secondaryKiDamage = this.getSecondaryAttributeValue(MainAttributes.KI_DAMAGE, 0.0);
      return secondaryKiDamage + ((kiPower + multBonusPwr) * pwrScaling * pwrMult + flatBonusPwr * pwrScaling) * releaseMultiplier;
   }

   public double getKiDamageNoForms() {
      double kiPower = (double)this.stats.getKiPower();
      double pwrScaling = this.getStatScaling("PWR");
      double releaseMultiplier = (double)this.resources.getPowerRelease() / 100.0;
      double flatBonusPwr = this.bonusStats.calculateBonus("PWR", (int)Math.round(kiPower), false);
      double multBonusPwr = this.bonusStats.calculateBonus("PWR", (int)Math.round(kiPower), true);
      double secondaryKiDamage = this.getSecondaryAttributeValue(MainAttributes.KI_DAMAGE, 0.0);
      return secondaryKiDamage + ((kiPower + multBonusPwr) * pwrScaling + flatBonusPwr * pwrScaling) * releaseMultiplier;
   }

   public double getMaxDefense() {
      double resistance = (double)this.stats.getResistance();
      double defScaling = this.getStatScaling("DEF");
      double flatBonusDef = this.bonusStats.calculateBonus("DEF", (int)Math.round(resistance), false);
      double multBonusDef = this.bonusStats.calculateBonus("DEF", (int)Math.round(resistance), true);
      double armor = (double)this.player.getArmorValue();
      double toughness = this.getArmorToughnessValue();
      double secondaryDefense = this.getSecondaryAttributeValue(MainAttributes.DEFENSE, 0.0);
      double statDef = (resistance + multBonusDef) * defScaling + flatBonusDef * defScaling;
      double armorComponent = armor * 0.5 + toughness * 0.7;
      return (secondaryDefense + statDef + armorComponent) * this.secondaryStatEffects.getMultiplier("DEF");
   }

   public double getDefense() {
      double resistance = (double)this.stats.getResistance();
      double defScaling = this.getStatScaling("DEF");
      double releaseMultiplier = (double)this.resources.getPowerRelease() / 100.0;
      double flatBonusDef = this.bonusStats.calculateBonus("DEF", (int)Math.round(resistance), false);
      double multBonusDef = this.bonusStats.calculateBonus("DEF", (int)Math.round(resistance), true);
      double armor = (double)this.player.getArmorValue();
      double toughness = this.getArmorToughnessValue();
      double secondaryDefense = this.getSecondaryAttributeValue(MainAttributes.DEFENSE, 0.0);
      double statDef = (resistance + multBonusDef) * defScaling + flatBonusDef * defScaling;
      double armorComponent = armor * 0.5 + toughness * 0.7;
      return (secondaryDefense + statDef + armorComponent) * releaseMultiplier * this.secondaryStatEffects.getMultiplier("DEF");
   }

   public double calculatePostMitigationDamage(double incomingDamage, boolean isGuardBroken, double armorPenetration) {
      double defMult = this.getTotalMultiplier("DEF");
      double baseDefense = this.getDefense() * Math.max(1.0, defMult);
      if (isGuardBroken) {
         baseDefense *= 1.0 - ConfigManager.getCombatConfig().getDefenseDecayOnGuardBreak();
      }

      if (baseDefense > 0.0) {
         baseDefense *= 1.0 - armorPenetration;
      }

      if (ConfigManager.getCombatConfig().getCancelDamageEventIfMitigationTooHigh()
         && incomingDamage > 0.0
         && baseDefense >= incomingDamage * ConfigManager.getCombatConfig().getCancelDamageMitigationThreshold()) {
         return 0.0;
      } else {
         double flatAbsorbCap = incomingDamage * ConfigManager.getCombatConfig().getFlatMitigationMaxAbsorbFraction();
         double flatMitigation = Math.min(baseDefense, flatAbsorbCap);
         double postFlatDamage = Math.max(0.0, incomingDamage - flatMitigation);
         int maxValue = this.getConfiguredMaxValue();
         double expectedMaxStats = this.isMaxLevelValueInsteadOfStats() ? (double)maxValue * 6.0 / 2.0 : (double)maxValue;
         double expectedMaxDef = expectedMaxStats * this.getStatScaling("DEF");
         double k_factor = Math.max(12.0, expectedMaxDef * ConfigManager.getCombatConfig().getDefenseReductionScale());
         double baseReduction;
         if (baseDefense >= 0.0) {
            baseReduction = baseDefense / (k_factor + baseDefense);
         } else {
            baseReduction = baseDefense / (k_factor - baseDefense);
         }

         double baseCap = ConfigManager.getCombatConfig().getBaseDamageReductionCap();
         baseReduction = Math.min(baseReduction, baseCap);
         double remainingDamage = postFlatDamage * (1.0 - baseReduction);
         int totalProtection = 0;
         if (this.player != null) {
            totalProtection = TickHandler.getTotalArmorEnchantmentLevel(Enchantments.PROTECTION, this.player);
         }

         double enchReduction = 0.0;
         if (totalProtection > 0) {
            double rawEffective = 0.0;
            int remaining = totalProtection;

            for (double mult = 1.0; remaining > 0; mult *= 0.5) {
               int chunk = Math.min(remaining, 4);
               rawEffective += (double)chunk * mult;
               remaining -= chunk;
            }

            double effectiveProtection = rawEffective * (1.0 - armorPenetration);
            double k_ench = 20.0;
            enchReduction = effectiveProtection / (k_ench + effectiveProtection);
            double totalCap = ConfigManager.getCombatConfig().getEnchantmentDamageReductionCap();
            double maxEnchReductionAllowed = (totalCap - baseReduction) / (1.0 - baseReduction);
            enchReduction = Math.min(enchReduction, Math.max(0.0, maxEnchReductionAllowed));
         }

         double afterEnchant = remainingDamage * (1.0 - enchReduction);
         if (ConfigManager.getCombatConfig().getEnableAdaptativeDefenseMitigation() && baseDefense > 0.0 && incomingDamage > 0.0) {
            double ratio = incomingDamage / baseDefense;
            afterEnchant *= 1.0 - this.computeAdaptativeDefenseMitigation(ratio);
         }

         return afterEnchant;
      }
   }

   private double computeAdaptativeDefenseMitigation(double ratio) {
      if (Double.isFinite(ratio) && !(ratio <= 0.0)) {
         CombatConfig cfg = ConfigManager.getCombatConfig();
         double parityRatio = cfg.getAdaptativeMitigationParityRatio();
         double parityValue = cfg.getAdaptativeMitigationParityValue();
         double zeroRatio = cfg.getAdaptativeMitigationZeroRatio();
         double cap = cfg.getAdaptativeDefenseMitigationCap();
         double slope = parityValue / (zeroRatio - parityRatio);
         double mitigation = parityValue + slope * (parityRatio - ratio);
         return Double.isFinite(mitigation) && !(mitigation <= 0.0) ? Math.min(mitigation, cap) : 0.0;
      } else {
         return 0.0;
      }
   }

   public double getFlatMitigation() {
      double defMult = this.getTotalMultiplier("DEF");
      return this.getDefense() * Math.max(1.0, defMult);
   }

   public double getMaxFlatMitigation() {
      double defMult = this.getTotalMultiplier("DEF");
      return this.getMaxDefense() * Math.max(1.0, defMult);
   }

   public double getDefenseLegacyUnits() {
      return this.getDefense() / 0.12;
   }

   public double getStaminaPerHit() {
      double staminaDamage = this.getMeleeDamageNoMultipliers();
      int baseStaminaRequired = (int)Math.ceil(staminaDamage * ConfigManager.getCombatConfig().getStaminaConsumptionRatio());
      return (double)baseStaminaRequired * this.getAdjustedStaminaDrainMultiplier();
   }

   private double getFormOffenseCostFactor() {
      boolean hasFormMult = this.character.hasActiveForm() && this.character.getActiveFormData() != null;
      boolean hasStackMult = this.character.hasActiveStackForm() && this.character.getActiveStackFormData() != null;
      double formCostMultiplier;
      if (hasFormMult && hasStackMult) {
         formCostMultiplier = (this.character.getActiveFormData().getMaxCostMultiplier() + this.character.getActiveStackFormData().getMaxCostMultiplier())
            / 2.0;
      } else if (hasFormMult) {
         formCostMultiplier = this.character.getActiveFormData().getMaxCostMultiplier();
      } else if (hasStackMult) {
         formCostMultiplier = this.character.getActiveStackFormData().getMaxCostMultiplier();
      } else {
         formCostMultiplier = 1.0;
      }

      return Math.min(1.0, formCostMultiplier);
   }

   private double getReducedOffense() {
      double totalOffense = this.getMeleeDamageNoBonus() + this.getStrikeDamageNoBonus() + this.getKiDamageNoBonus();
      return totalOffense * this.getFormOffenseCostFactor();
   }

   private double getMeleeDamageNoBonus() {
      double strength = (double)this.stats.getStrength();
      double strScaling = this.getStatScaling("STR");
      double strMult = this.getTotalMultiplier("STR");
      double releaseMultiplier = (double)this.resources.getPowerRelease() / 100.0;
      double secondaryMeleeDamage = this.getSecondaryAttributeValue(MainAttributes.MELEE_DAMAGE, 1.0);
      return secondaryMeleeDamage + strength * strScaling * strMult * releaseMultiplier;
   }

   private double getStrikeDamageNoBonus() {
      double strikePower = (double)this.stats.getStrikePower();
      double strength = (double)this.stats.getStrength();
      double skpScaling = this.getStatScaling("SKP");
      double strScaling = this.getStatScaling("STR");
      double skpMult = this.getTotalMultiplier("SKP");
      double strMult = this.getTotalMultiplier("STR");
      double releaseMultiplier = (double)this.resources.getPowerRelease() / 100.0;
      double secondaryStrikeDamage = this.getSecondaryAttributeValue(MainAttributes.STRIKE_DAMAGE, 1.0);
      double baseDamage = strikePower * skpScaling * skpMult + strength * strScaling * strMult * 0.25;
      return secondaryStrikeDamage + baseDamage * releaseMultiplier;
   }

   private double getKiDamageNoBonus() {
      double kiPower = (double)this.stats.getKiPower();
      double pwrScaling = this.getStatScaling("PWR");
      double pwrMult = this.getTotalMultiplier("PWR");
      double releaseMultiplier = (double)this.resources.getPowerRelease() / 100.0;
      double secondaryKiDamage = this.getSecondaryAttributeValue(MainAttributes.KI_DAMAGE, 0.0);
      return secondaryKiDamage + kiPower * pwrScaling * pwrMult * releaseMultiplier;
   }

   public double getEffectiveEnergyDrain() {
      double base = this.getAdjustedEnergyDrain();
      if (base <= 0.0) {
         return base;
      } else {
         double maxEnergy = (double)this.getMaxEnergy();
         double rawEnergyRatio = this.getReducedOffense() / Math.max(1.0, maxEnergy * 1.5);
         double energyRatio = Math.max(1.0, Math.sqrt(rawEnergyRatio));
         double formRawEneDrain = 0.0;
         if (this.character.hasActiveForm() && this.character.getActiveFormData() != null) {
            formRawEneDrain += Math.max(0.0, this.character.getActiveFormData().getEnergyDrain());
         }

         if (this.character.hasActiveStackForm() && this.character.getActiveStackFormData() != null) {
            formRawEneDrain += Math.max(0.0, this.character.getActiveStackFormData().getEnergyDrain());
         }

         double percentageEnergy = maxEnergy * formRawEneDrain * 0.01 * 0.75;
         return base * energyRatio + percentageEnergy;
      }
   }

   public double getEffectiveStaminaDrain() {
      double base = this.getAdjustedStaminaDrain();
      if (base <= 0.0) {
         return base;
      } else {
         double maxStamina = (double)this.getMaxStamina();
         double staminaRatio = Math.max(1.0, this.getReducedOffense() / Math.max(1.0, maxStamina * 1.5));
         double percentageStamina = maxStamina * 0.005;
         return base * staminaRatio + percentageStamina;
      }
   }

   public double getEffectiveHealthDrain() {
      double base = this.getAdjustedHealthDrain();
      if (base <= 0.0) {
         return base;
      } else {
         double maxHealth = (double)this.getMaxHealth();
         double healthRatio = Math.max(1.0, this.getReducedOffense() / Math.max(1.0, maxHealth * 1.5));
         double percentageHealth = maxHealth * 0.005;
         return base * healthRatio + percentageHealth;
      }
   }

   public double getTotalMultiplier(String statName) {
      double form = this.getFormMultiplier(statName);
      double stack = this.getStackFormMultiplier(statName);
      double effect = this.getEffectsMultiplier(statName);
      double secondary = statName.equalsIgnoreCase("DEF") ? 1.0 : this.secondaryStatEffects.getMultiplier(statName);
      return ConfigManager.getServerConfig().getGameplay().getMultiplicationInsteadOfAdditionForMultipliers()
         ? form * stack * effect * secondary
         : 1.0 + (form - 1.0) + (stack - 1.0) + (effect - 1.0) + (secondary - 1.0);
   }

   public double getFormMultiplier(String statName) {
      String currentForm = this.character.getActiveForm();
      String currentFormGroup = this.character.getActiveFormGroup();
      if (currentForm == null || currentForm.isEmpty() || currentForm.equals("base")) {
         return 1.0;
      } else if (currentFormGroup != null && !currentFormGroup.isEmpty()) {
         FormConfig formConfig = ConfigManager.getFormGroup(this.character.getRaceName(), currentFormGroup);
         if (formConfig == null) {
            return 1.0;
         } else {
            FormConfig.FormData formData = formConfig.getForm(currentForm);
            if (formData == null) {
               return 1.0;
            } else {
               String mastery = statName.toUpperCase();

               double baseMult = switch (mastery) {
                  case "STR" -> formData.getStrMultiplier();
                  case "SKP" -> formData.getSkpMultiplier();
                  case "STM" -> formData.getStmMultiplier();
                  case "DEF" -> formData.getDefMultiplier();
                  case "RES" -> (formData.getDefMultiplier() + formData.getStmMultiplier()) / 2.0;
                  case "VIT" -> formData.getVitMultiplier();
                  case "PWR" -> formData.getPwrMultiplier();
                  case "ENE" -> formData.getEneMultiplier();
                  default -> 1.0;
               };
               double mastery = this.character.getFormMasteries().getMastery(currentFormGroup, currentForm);
               double result = this.applyMasteryStatBonus(formData, baseMult, mastery);
               return this.applyMutantFormPowerModifier(currentFormGroup, result);
            }
         }
      } else {
         return 1.0;
      }
   }

   private double applyMutantFormPowerModifier(String groupName, double multiplier) {
      if (multiplier <= 1.0) {
         return multiplier;
      } else if (!this.effects.hasEffect("mutant")) {
         return multiplier;
      } else {
         GeneralServerConfig.MutantConfig mutantConfig = ConfigManager.getServerConfig() != null ? ConfigManager.getServerConfig().getMutant() : null;
         if (mutantConfig == null) {
            return multiplier;
         } else {
            String legendaryGroup = mutantConfig.getLegendaryGroupName();
            if (groupName != null && groupName.equalsIgnoreCase(legendaryGroup)) {
               boolean hasSkill = this.skills.getSkillLevel("legendaryforms") > 0;
               double factor = hasSkill ? 1.0 + mutantConfig.getPowerBonusBoostWithSkill() : 1.0 - mutantConfig.getPowerBonusReductionNoSkill();
               return 1.0 + (multiplier - 1.0) * factor;
            } else {
               return multiplier;
            }
         }
      }
   }

   private double getBaseFormMultiplier(FormConfig.FormData formData, String statName) {
      String var3 = statName.toUpperCase();

      return switch (var3) {
         case "STR" -> formData.getStrMultiplier();
         case "SKP" -> formData.getSkpMultiplier();
         case "STM" -> formData.getStmMultiplier();
         case "DEF" -> formData.getDefMultiplier();
         case "RES" -> (formData.getDefMultiplier() + formData.getStmMultiplier()) / 2.0;
         case "VIT" -> formData.getVitMultiplier();
         case "PWR" -> formData.getPwrMultiplier();
         case "ENE" -> formData.getEneMultiplier();
         default -> 1.0;
      };
   }

   private double getMasteryAdjustedMultiplier(FormConfig.FormData formData, String statName, double mastery) {
      double baseMult = this.getBaseFormMultiplier(formData, statName);
      return this.applyMasteryStatBonus(formData, baseMult, mastery);
   }

   private double applyMasteryStatBonus(FormConfig.FormData formData, double baseMult, double mastery) {
      if (baseMult <= 1.0) {
         return baseMult;
      } else {
         double maxMastery = formData.getMaxMastery();
         if (maxMastery <= 0.0) {
            return baseMult;
         } else {
            double ratio = Math.min(1.0, Math.max(0.0, mastery) / maxMastery);
            double factor = 1.0 + ratio * (formData.getMaxStatsMultiplier() - 1.0);
            return baseMult * factor;
         }
      }
   }

   private double getMasteryCostFactor(FormConfig.FormData formData, double mastery) {
      double maxMastery = formData.getMaxMastery();
      if (maxMastery <= 0.0) {
         return 1.0;
      } else {
         double ratio = Math.min(1.0, Math.max(0.0, mastery) / maxMastery);
         return 1.0 + ratio * (formData.getMaxCostMultiplier() - 1.0);
      }
   }

   private FormConfig.FormData getBestUltimateBaseForm() {
      String raceName = this.character.getRaceName();
      Map<String, FormConfig> groups = ConfigManager.getAllFormsForRace(raceName);
      if (groups != null && !groups.isEmpty()) {
         FormConfig.FormData best = null;
         double bestAverage = -1.0;

         for (Entry<String, FormConfig> entry : groups.entrySet()) {
            String groupName = entry.getKey();
            FormConfig group = entry.getValue();
            if (group != null) {
               for (FormConfig.FormData formData : TransformationsHelper.getUnlockedForms(this, raceName, groupName)) {
                  if (formData != null && !formData.isIncompatibleWith("ultimate", "ultimate")) {
                     double mastery = this.character.getFormMasteries().getMastery(groupName, formData.getName());
                     double average = (
                           this.getMasteryAdjustedMultiplier(formData, "STR", mastery)
                              + this.getMasteryAdjustedMultiplier(formData, "SKP", mastery)
                              + this.getMasteryAdjustedMultiplier(formData, "DEF", mastery)
                              + this.getMasteryAdjustedMultiplier(formData, "VIT", mastery)
                              + this.getMasteryAdjustedMultiplier(formData, "PWR", mastery)
                              + this.getMasteryAdjustedMultiplier(formData, "ENE", mastery)
                        )
                        / 6.0;
                     if (average > bestAverage) {
                        bestAverage = average;
                        best = formData;
                     }
                  }
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private Object[] getBestUltimateBaseFormWithGroup() {
      String raceName = this.character.getRaceName();
      Map<String, FormConfig> groups = ConfigManager.getAllFormsForRace(raceName);
      if (groups != null && !groups.isEmpty()) {
         FormConfig.FormData best = null;
         String bestGroup = null;
         double bestAverage = -1.0;

         for (Entry<String, FormConfig> entry : groups.entrySet()) {
            String groupName = entry.getKey();
            FormConfig group = entry.getValue();
            if (group != null) {
               for (FormConfig.FormData formData : TransformationsHelper.getUnlockedForms(this, raceName, groupName)) {
                  if (formData != null && !formData.isIncompatibleWith("ultimate", "ultimate")) {
                     double mastery = this.character.getFormMasteries().getMastery(groupName, formData.getName());
                     double average = (
                           this.getMasteryAdjustedMultiplier(formData, "STR", mastery)
                              + this.getMasteryAdjustedMultiplier(formData, "SKP", mastery)
                              + this.getMasteryAdjustedMultiplier(formData, "DEF", mastery)
                              + this.getMasteryAdjustedMultiplier(formData, "VIT", mastery)
                              + this.getMasteryAdjustedMultiplier(formData, "PWR", mastery)
                              + this.getMasteryAdjustedMultiplier(formData, "ENE", mastery)
                        )
                        / 6.0;
                     if (average > bestAverage) {
                        bestAverage = average;
                        best = formData;
                        bestGroup = groupName;
                     }
                  }
               }
            }
         }

         return new Object[]{bestGroup, best};
      } else {
         return new Object[]{null, null};
      }
   }

   private boolean isUltimateStackFormActive() {
      String group = this.character.getActiveStackFormGroup();
      return group != null && "ultimate".equalsIgnoreCase(group);
   }

   public double getStackFormMultiplier(String statName) {
      String currentForm = this.character.getActiveStackForm();
      String currentFormGroup = this.character.getActiveStackFormGroup();
      if (currentForm == null || currentForm.isEmpty()) {
         return 1.0;
      } else if (currentFormGroup != null && !currentFormGroup.isEmpty()) {
         FormConfig formConfig = ConfigManager.getStackFormGroup(currentFormGroup);
         if (formConfig == null) {
            return 1.0;
         } else {
            FormConfig.FormData formData = formConfig.getForm(currentForm);
            if (formData == null) {
               return 1.0;
            } else if ("ultimate".equalsIgnoreCase(currentFormGroup) && !ConfigManager.getServerConfig().getGameplay().getUltimateFormFixedValue()) {
               Object[] bestResult = this.getBestUltimateBaseFormWithGroup();
               String bestGroup = (String)bestResult[0];
               FormConfig.FormData bestForm = (FormConfig.FormData)bestResult[1];
               double bestMult;
               if (bestForm != null) {
                  double bestMastery = this.character.getFormMasteries().getMastery(bestGroup, bestForm.getName());
                  bestMult = this.getMasteryAdjustedMultiplier(bestForm, statName, bestMastery);
               } else {
                  bestMult = 1.0;
               }

               double ultimateMult = this.getBaseFormMultiplier(formData, statName);
               return bestMult + ultimateMult - 1.0;
            } else {
               double mastery = this.character.getStackFormMasteries().getMastery(currentFormGroup, currentForm);
               return this.getMasteryAdjustedMultiplier(formData, statName, mastery);
            }
         }
      } else {
         return 1.0;
      }
   }

   public double getEffectsMultiplier(String statName) {
      double rawEffect = this.effects.getTotalEffectMultiplier();
      String var4 = statName.toUpperCase();

      return switch (var4) {
         case "STR", "SKP", "PWR" -> rawEffect;
         case "DEF" -> 1.0 + (rawEffect - 1.0) * 0.5;
         default -> 1.0;
      };
   }

   public double getLoadDrainMultiplier() {
      GeneralServerConfig.GravityConfig g = ConfigManager.getServerConfig().getGravity();

      return switch (GravityLogic.getTrainingZone(this.player)) {
         case 1 -> g.getLoadDrainComfort();
         case 2 -> g.getLoadDrainIdeal();
         case 3 -> g.getLoadDrainHeavy();
         case 4 -> g.getLoadDrainOverload();
         default -> 1.0;
      };
   }

   public double getAdjustedStaminaDrainMultiplier() {
      double baseDrainMult = 1.0;
      double stackDrainMult = 1.0;
      if (this.character.hasActiveForm() || this.character.hasActiveStackForm()) {
         FormConfig.FormData formData = this.character.getActiveFormData();
         FormConfig.FormData stackFormData = this.character.getActiveStackFormData();
         if (this.character.hasActiveForm() && formData != null) {
            baseDrainMult = formData.getStaminaDrainMultiplier();
         }

         if (this.character.hasActiveStackForm() && stackFormData != null) {
            stackDrainMult = stackFormData.getStaminaDrainMultiplier();
         }
      }

      return Math.max(0.001, baseDrainMult * stackDrainMult * this.getLoadDrainMultiplier());
   }

   public double getAdjustedEnergyDrain() {
      if (!this.character.hasActiveForm() && !this.character.hasActiveStackForm()) {
         return 0.0;
      } else {
         FormConfig.FormData formData = this.character.getActiveFormData();
         FormConfig.FormData stackFormData = this.character.getActiveStackFormData();
         double powerRelease = (double)this.resources.getPowerRelease() / 100.0;
         if (formData == null && stackFormData == null) {
            return 0.0;
         } else {
            double adjustedBaseDrain = 0.0;
            if (this.character.hasActiveForm() && formData != null) {
               double baseDrain = formData.getEnergyDrain();
               if (this.character.hasActiveStackForm() && stackFormData != null) {
                  baseDrain *= formData.getStackDrainMultiplier() * stackFormData.getStackDrainMultiplier();
               }

               double mastery = this.character.getFormMasteries().getMastery(this.character.getActiveFormGroup(), this.character.getActiveForm());
               double costFactor = this.getMasteryCostFactor(formData, mastery);
               if (baseDrain < 0.0) {
                  adjustedBaseDrain = baseDrain / costFactor * powerRelease;
               } else {
                  adjustedBaseDrain = baseDrain * costFactor * powerRelease;
               }
            }

            double adjustedStackDrain = 0.0;
            if (this.character.hasActiveStackForm() && stackFormData != null) {
               double stackDrain = stackFormData.getEnergyDrain();
               if (this.character.hasActiveForm() && formData != null) {
                  stackDrain *= formData.getStackDrainMultiplier() * stackFormData.getStackDrainMultiplier();
               }

               double stackMastery = this.isUltimateStackFormActive()
                  ? 0.0
                  : this.character.getStackFormMasteries().getMastery(this.character.getActiveStackFormGroup(), this.character.getActiveStackForm());
               double stackCostFactor = this.getMasteryCostFactor(stackFormData, stackMastery);
               if (stackDrain < 0.0) {
                  adjustedStackDrain = stackDrain / stackCostFactor * powerRelease;
               } else {
                  adjustedStackDrain = stackDrain * stackCostFactor * powerRelease;
               }
            }

            double drainAmount = adjustedBaseDrain + adjustedStackDrain;
            if (drainAmount == 0.0) {
               return 0.0;
            } else {
               double scaledDrain = drainAmount * (double)ConfigManager.getCombatConfig().getBaselineFormDrain().intValue() * this.getLoadDrainMultiplier();
               if (scaledDrain == 0.0) {
                  return 0.0;
               } else {
                  return drainAmount < 0.0 ? Math.min(-1.0, scaledDrain) : Math.max(1.0, scaledDrain);
               }
            }
         }
      }
   }

   public double getAdjustedStaminaDrain() {
      if (!this.character.hasActiveForm() && !this.character.hasActiveStackForm()) {
         return 0.0;
      } else {
         FormConfig.FormData formData = this.character.getActiveFormData();
         FormConfig.FormData stackFormData = this.character.getActiveStackFormData();
         double powerRelease = (double)this.resources.getPowerRelease() / 100.0;
         if (formData == null && stackFormData == null) {
            return 0.0;
         } else {
            double adjustedBaseDrain = 0.0;
            if (this.character.hasActiveForm() && formData != null) {
               double baseDrain = formData.getStaminaDrain();
               if (this.character.hasActiveStackForm() && stackFormData != null) {
                  baseDrain *= formData.getStackDrainMultiplier() * stackFormData.getStackDrainMultiplier();
               }

               double mastery = this.character.getFormMasteries().getMastery(this.character.getActiveFormGroup(), this.character.getActiveForm());
               double costFactor = this.getMasteryCostFactor(formData, mastery);
               if (baseDrain < 0.0) {
                  adjustedBaseDrain = baseDrain / costFactor * powerRelease;
               } else {
                  adjustedBaseDrain = baseDrain * costFactor * powerRelease;
               }
            }

            double adjustedStackDrain = 0.0;
            if (this.character.hasActiveStackForm() && stackFormData != null) {
               double stackDrain = stackFormData.getStaminaDrain();
               if (this.character.hasActiveForm() && formData != null) {
                  stackDrain *= formData.getStackDrainMultiplier() * stackFormData.getStackDrainMultiplier();
               }

               double stackMastery = this.isUltimateStackFormActive()
                  ? 0.0
                  : this.character.getStackFormMasteries().getMastery(this.character.getActiveStackFormGroup(), this.character.getActiveStackForm());
               double stackCostFactor = this.getMasteryCostFactor(stackFormData, stackMastery);
               if (stackDrain < 0.0) {
                  adjustedStackDrain = stackDrain / stackCostFactor * powerRelease;
               } else {
                  adjustedStackDrain = stackDrain * stackCostFactor * powerRelease;
               }
            }

            double drainAmount = adjustedBaseDrain + adjustedStackDrain;
            if (drainAmount == 0.0) {
               return 0.0;
            } else {
               double scaledDrain = drainAmount * (double)ConfigManager.getCombatConfig().getBaselineFormDrain().intValue() * this.getLoadDrainMultiplier();
               if (scaledDrain == 0.0) {
                  return 0.0;
               } else {
                  return drainAmount < 0.0 ? Math.min(-1.0, scaledDrain) : Math.max(1.0, scaledDrain);
               }
            }
         }
      }
   }

   public double getAdjustedHealthDrain() {
      if (!this.character.hasActiveForm() && !this.character.hasActiveStackForm()) {
         return 0.0;
      } else {
         FormConfig.FormData formData = this.character.getActiveFormData();
         FormConfig.FormData stackFormData = this.character.getActiveStackFormData();
         double powerRelease = (double)this.resources.getPowerRelease() / 100.0;
         if (formData == null && stackFormData == null) {
            return 0.0;
         } else {
            double adjustedBaseDrain = 0.0;
            if (this.character.hasActiveForm() && formData != null) {
               double baseDrain = formData.getHealthDrain();
               if (this.character.hasActiveStackForm() && stackFormData != null) {
                  baseDrain *= formData.getStackDrainMultiplier() * stackFormData.getStackDrainMultiplier();
               }

               double mastery = this.character.getFormMasteries().getMastery(this.character.getActiveFormGroup(), this.character.getActiveForm());
               double costFactor = this.getMasteryCostFactor(formData, mastery);
               if (baseDrain < 0.0) {
                  adjustedBaseDrain = baseDrain / costFactor * powerRelease;
               } else {
                  adjustedBaseDrain = baseDrain * costFactor * powerRelease;
               }
            }

            double adjustedStackDrain = 0.0;
            if (this.character.hasActiveStackForm() && stackFormData != null) {
               double stackDrain = stackFormData.getHealthDrain();
               if (this.character.hasActiveForm() && formData != null) {
                  stackDrain *= formData.getStackDrainMultiplier() * stackFormData.getStackDrainMultiplier();
               }

               double stackMastery = this.isUltimateStackFormActive()
                  ? 0.0
                  : this.character.getStackFormMasteries().getMastery(this.character.getActiveStackFormGroup(), this.character.getActiveStackForm());
               double stackCostFactor = this.getMasteryCostFactor(stackFormData, stackMastery);
               if (stackDrain < 0.0) {
                  adjustedStackDrain = stackDrain / stackCostFactor * powerRelease;
               } else {
                  adjustedStackDrain = stackDrain * stackCostFactor * powerRelease;
               }
            }

            double drainAmount = adjustedBaseDrain + adjustedStackDrain;
            if (drainAmount == 0.0) {
               return 0.0;
            } else {
               double scaledDrain = drainAmount * (double)ConfigManager.getCombatConfig().getBaselineFormDrain().intValue() * this.getLoadDrainMultiplier();
               if (scaledDrain == 0.0) {
                  return 0.0;
               } else {
                  return drainAmount < 0.0 ? Math.min(-1.0, scaledDrain) : Math.max(1.0, scaledDrain);
               }
            }
         }
      }
   }

   public float[] snapshotMultiplierResources() {
      return new float[]{this.getMaxHealth(), this.getMaxEnergy(), this.getMaxStamina()};
   }

   public void restoreMultiplierGains(ServerPlayer player, float[] snapshot) {
      if (snapshot != null && snapshot.length >= 3) {
         StatsEvents.applyHealthBonus(player);
         float newMaxHealth = this.getMaxHealth();
         float healthDelta = newMaxHealth - snapshot[0];
         if (healthDelta > 0.0F) {
            player.setHealth(Math.min(newMaxHealth, player.getHealth() + healthDelta));
         }

         float newMaxEnergy = this.getMaxEnergy();
         float energyDelta = newMaxEnergy - snapshot[1];
         if (energyDelta > 0.0F) {
            this.resources.setCurrentEnergy(Math.min(newMaxEnergy, this.resources.getCurrentEnergy() + energyDelta));
         }

         float newMaxStamina = this.getMaxStamina();
         float staminaDelta = newMaxStamina - snapshot[2];
         if (staminaDelta > 0.0F) {
            this.resources.setCurrentStamina(Math.min(newMaxStamina, this.resources.getCurrentStamina() + staminaDelta));
         }
      }
   }

   public void initializeWithRaceAndClass(
      String raceName,
      String characterClass,
      String gender,
      int hairId,
      CustomHair customHair,
      int bodyType,
      int eyesType,
      int noseType,
      int mouthType,
      int tattooType,
      float boobScale,
      String activeHeadBone,
      String hairColor,
      String bodyColor,
      String bodyColor2,
      String bodyColor3,
      String eye1Color,
      String eye2Color,
      String auraColor
   ) {
      this.character.setRace(raceName);
      this.character.setGender(gender);
      this.character.setCharacterClass(characterClass);
      this.character.setHairId(hairId);
      if (customHair != null) {
         this.character.setHairBase(customHair);
      }

      this.character.setBodyType(bodyType);
      this.character.setEyesType(eyesType);
      this.character.setNoseType(noseType);
      this.character.setMouthType(mouthType);
      this.character.setTattooType(tattooType);
      this.character.setBoobScale(boobScale);
      this.character.setActiveHeadBone(activeHeadBone);
      this.character.setHairColor(hairColor);
      this.character.setBodyColor(bodyColor);
      this.character.setBodyColor2(bodyColor2);
      this.character.setBodyColor3(bodyColor3);
      this.character.setEye1Color(eye1Color);
      this.character.setEye2Color(eye2Color);
      this.character.setAuraColor(auraColor);
      this.status.setHasCreatedCharacter(true);
      RaceStatsConfig raceConfig = ConfigManager.getRaceStats(raceName);
      RaceStatsConfig.ClassStats classStats = this.getClassStats(raceConfig, characterClass);
      RaceStatsConfig.BaseStats baseStats = classStats.getBaseStats();
      if (baseStats == null) {
         baseStats = new RaceStatsConfig().getClassStats(characterClass).getBaseStats();
      }

      boolean hasDefaultStats = this.stats.getStrength() == 0
         && this.stats.getStrikePower() == 0
         && this.stats.getResistance() == 0
         && this.stats.getVitality() == 0
         && this.stats.getKiPower() == 0
         && this.stats.getEnergy() == 0;
      if (hasDefaultStats) {
         this.stats.setStrength(baseStats.getStrength());
         this.stats.setStrikePower(baseStats.getStrikePower());
         this.stats.setResistance(baseStats.getResistance());
         this.stats.setVitality(baseStats.getVitality());
         this.stats.setKiPower(baseStats.getKiPower());
         this.stats.setEnergy(baseStats.getEnergy());
      }

      this.resources.setCurrentEnergy(this.getMaxEnergy());
      this.resources.setCurrentStamina(this.getMaxStamina());
      this.resources.setCurrentPoise(this.getMaxPoise());
      this.resources.setPowerRelease(0);
      this.resources.setAlignment(100);
      this.character.setSelectedFormGroup(TransformationsHelper.getGroupWithFirstAvailableForm(this));
      this.updateTransformationSkillLimits(raceName);
   }

   public void updateTransformationSkillLimits(String raceName) {
      this.skills.refreshNonFormSkillMaxLevels();
      this.status.validateKiWeaponType();
      RaceCharacterConfig charConfig = ConfigManager.getRaceCharacter(raceName);
      if (charConfig != null) {
         Collection<String> formSkills = charConfig.getFormSkills();
         List<String> androidBlacklistedForms = ConfigManager.getSkillsConfig().getAndroidBlacklistedForms();

         for (String skillName : formSkills) {
            if ((!this.status.isAndroidUpgraded() || !androidBlacklistedForms.contains(skillName))
               && (this.status.isAndroidUpgraded() || !"androidforms".equalsIgnoreCase(skillName))) {
               Integer[] tpCosts = charConfig.getFormSkillTpCosts(skillName);
               int maxLevel = tpCosts != null ? tpCosts.length : 0;
               if (charConfig.isFormSkillBuyFromMaster(skillName)) {
                  if (this.skills.hasSkill(skillName)) {
                     this.skills.registerDefaultSkill(skillName, maxLevel);
                  }
               } else {
                  this.skills.registerDefaultSkill(skillName, maxLevel);
               }
            }
         }
      }
   }

   public double getStatScaling(String statName) {
      String raceName = this.character.getRaceName();
      String characterClass = this.character.getCharacterClass();
      RaceStatsConfig raceConfig = ConfigManager.getRaceStats(raceName);
      RaceStatsConfig.ClassStats classStats = this.getClassStats(raceConfig, characterClass);
      RaceStatsConfig.StatScaling scaling = classStats.getStatScaling();
      if (scaling == null) {
         String var9 = statName.toUpperCase();

         return switch (var9) {
            case "STR" -> new RaceStatsConfig().getClassStats(characterClass).getStatScaling().getStrengthScaling();
            case "SKP" -> new RaceStatsConfig().getClassStats(characterClass).getStatScaling().getStrikePowerScaling();
            case "STM" -> new RaceStatsConfig().getClassStats(characterClass).getStatScaling().getStaminaScaling();
            case "DEF" -> new RaceStatsConfig().getClassStats(characterClass).getStatScaling().getDefenseScaling();
            case "VIT" -> new RaceStatsConfig().getClassStats(characterClass).getStatScaling().getVitalityScaling();
            case "PWR" -> new RaceStatsConfig().getClassStats(characterClass).getStatScaling().getKiPowerScaling();
            case "ENE" -> new RaceStatsConfig().getClassStats(characterClass).getStatScaling().getEnergyScaling();
            default -> 1.0;
         };
      } else {
         String var7 = statName.toUpperCase();

         return switch (var7) {
            case "STR" -> scaling.getStrengthScaling();
            case "SKP" -> scaling.getStrikePowerScaling();
            case "STM" -> scaling.getStaminaScaling();
            case "DEF" -> scaling.getDefenseScaling();
            case "VIT" -> scaling.getVitalityScaling();
            case "PWR" -> scaling.getKiPowerScaling();
            case "ENE" -> scaling.getEnergyScaling();
            default -> 1.0;
         };
      }
   }

   private RaceStatsConfig.ClassStats getClassStats(RaceStatsConfig config, String characterClass) {
      return config == null ? new RaceStatsConfig().getClassStats(characterClass) : config.getClassStats(characterClass);
   }

   private int getInitialTotalStats() {
      RaceStatsConfig.BaseStats baseStats = this.getInitialBaseStats();
      return baseStats.getStrength()
         + baseStats.getStrikePower()
         + baseStats.getResistance()
         + baseStats.getVitality()
         + baseStats.getKiPower()
         + baseStats.getEnergy();
   }

   private RaceStatsConfig.BaseStats getInitialBaseStats() {
      String raceName = this.character.getRaceName();
      String characterClass = this.character.getCharacterClass();
      RaceStatsConfig raceConfig = ConfigManager.getRaceStats(raceName);
      RaceStatsConfig.ClassStats classStats = this.getClassStats(raceConfig, characterClass);
      RaceStatsConfig.BaseStats baseStats = classStats.getBaseStats();
      if (baseStats == null) {
         baseStats = new RaceStatsConfig().getClassStats(characterClass).getBaseStats();
      }

      return baseStats;
   }

   public int getPendingAttributePoints() {
      return this.resources.getPendingAttributePoints();
   }

   public int relocateStats(ServerPlayer serverPlayer) {
      RaceStatsConfig.BaseStats baseStats = this.getInitialBaseStats();
      int gained = 0;
      gained += Math.max(0, this.stats.getStrength() - baseStats.getStrength());
      gained += Math.max(0, this.stats.getStrikePower() - baseStats.getStrikePower());
      gained += Math.max(0, this.stats.getResistance() - baseStats.getResistance());
      gained += Math.max(0, this.stats.getVitality() - baseStats.getVitality());
      gained += Math.max(0, this.stats.getKiPower() - baseStats.getKiPower());
      gained += Math.max(0, this.stats.getEnergy() - baseStats.getEnergy());
      if (gained <= 0) {
         return 0;
      } else {
         float oldHealthBonus = this.getHealthBonus();
         this.stats.setStrength(baseStats.getStrength());
         this.stats.setStrikePower(baseStats.getStrikePower());
         this.stats.setResistance(baseStats.getResistance());
         this.stats.setVitality(baseStats.getVitality());
         this.stats.setKiPower(baseStats.getKiPower());
         this.stats.setEnergy(baseStats.getEnergy());
         float newHealthBonus = this.getHealthBonus();
         if (newHealthBonus != oldHealthBonus) {
            StatsEvents.applyHealthBonus(serverPlayer);
            if (serverPlayer.getHealth() > serverPlayer.getMaxHealth()) {
               serverPlayer.setHealth(serverPlayer.getMaxHealth());
            }
         }

         this.resources.setCurrentEnergy(Math.min(this.resources.getCurrentEnergy(), this.getMaxEnergy()));
         this.resources.setCurrentStamina(Math.min(this.resources.getCurrentStamina(), this.getMaxStamina()));
         this.resources.addPendingAttributePoints(gained);
         return gained;
      }
   }

   private long getConfiguredMaxTotalStatsRaw() {
      return (long)this.getConfiguredMaxValue() * 6L;
   }

   public boolean isHumanRacialActive() {
      return ConfigManager.getServerConfig().getRacialSkills().getEnableRacialSkills()
         && ConfigManager.getServerConfig().getRacialSkills().getHumanRacialSkill()
         && ConfigManager.getRaceCharacter(this.character.getRace()).getRacialSkill().equals("human");
   }

   public boolean isAndroidRacialActive() {
      return this.isHumanRacialActive() && this.status.isAndroidUpgraded();
   }

   public double getKiAttackCostModifier() {
      if (this.isAndroidRacialActive()) {
         return 0.5;
      } else {
         return this.isHumanRacialActive() ? 0.75 : 1.0;
      }
   }

   public double getKiAttackDamageModifier() {
      return this.isAndroidRacialActive() ? 0.85 : 1.0;
   }

   public double getRaceTpCostMultiplier() {
      String raceName = this.character.getRaceName();
      String characterClass = this.character.getCharacterClass();
      RaceStatsConfig raceConfig = ConfigManager.getRaceStats(raceName);
      if (raceConfig == null) {
         return 1.0;
      } else {
         RaceStatsConfig.ClassStats classStats = raceConfig.getClassStats(characterClass);
         if (classStats == null) {
            return 1.0;
         } else {
            Double classMult = classStats.getTpCostMultiplier();
            return classMult != null ? classMult : 1.0;
         }
      }
   }

   public double getTpAdditiveMultiplier() {
      double total = 1.0;
      total += this.getTpClassMultiplier() - 1.0;
      total += this.getTpFrostDemonMultiplier() - 1.0;
      total += this.getTpHTCMultiplier() - 1.0;
      total += this.getMutantTpMultiplier() - 1.0;
      total += this.getTpGlobalMultiplier() - 1.0;
      total += this.getTpPotionEffectMultiplier() - 1.0;
      total += this.getDifficultyTpMultiplier() - 1.0;
      total += this.getTpWeightBellMultiplier() - 1.0;
      if (ConfigManager.getServerConfig().getGameplay() != null && ConfigManager.getServerConfig().getGameplay().getGravityBonusEnabled()) {
         total += this.getTpGravityMultiplier() - 1.0;
      }

      return Math.max(0.0, total);
   }

   public double getTpGlobalMultiplier() {
      return ConfigManager.getServerConfig().getGameplay().getTpsGainMultiplier();
   }

   public double getTpClassMultiplier() {
      String race = this.character.getRace();
      RaceStatsConfig raceStats = ConfigManager.getRaceStats(race);
      if (raceStats == null) {
         return 1.0;
      } else {
         RaceStatsConfig.ClassStats classStats = raceStats.getClassStats(this.character.getCharacterClass());
         if (classStats == null) {
            return 1.0;
         } else {
            Double classMult = classStats.getTpGainMultiplier();
            return classMult != null ? classMult : 1.0;
         }
      }
   }

   public boolean isFrostDemonTpPassiveActive() {
      return ConfigManager.getServerConfig().getRacialSkills().getEnableRacialSkills()
         && ConfigManager.getServerConfig().getRacialSkills().getFrostDemonRacialSkill()
         && "frostdemon".equals(ConfigManager.getRaceCharacter(this.character.getRace()).getRacialSkill());
   }

   public double getTpFrostDemonMultiplier() {
      return !this.isFrostDemonTpPassiveActive() ? 1.0 : ConfigManager.getServerConfig().getRacialSkills().getFrostDemonTPBoost();
   }

   public double getTpHTCMultiplier() {
      return !this.player.level().dimension().equals(HTCDimension.HTC_KEY) ? 1.0 : ConfigManager.getServerConfig().getGameplay().getHTCTpMultiplier();
   }

   public double getTpGravityMultiplier() {
      GeneralServerConfig.GravityConfig gravityConfig = ConfigManager.getServerConfig().getGravity();
      if (!gravityConfig.getTpEnabled()) {
         return 1.0;
      } else {
         double bonusGravity = GravityLogic.getTrainingBonusGravity(this.player);
         return bonusGravity <= 0.0 ? 1.0 : 1.0 + bonusGravity * gravityConfig.getTpGravityBonusPerGravity();
      }
   }

   public double getGravityPenalizationGravity() {
      return GravityLogic.getPenalizationGravity(this.player);
   }

   public double getGravityEnvironmentalMultiplier() {
      return GravityLogic.getGravityMultiplier(this.player);
   }

   public double getGravityStatMultiplier() {
      return 1.0 - GravityLogic.getStatReduction(this.player);
   }

   public double getTpWeightBellMultiplier() {
      return GravityLogic.getWeightTpMultiplier(this.player);
   }

   public int getTpIdealWeight() {
      return GravityLogic.getIdealWeight(this.player);
   }

   public int getGravityTotalWeight() {
      return GravityLogic.getTotalWeight(this.player);
   }

   public double getTpPotionEffectMultiplier() {
      return this.player == null ? 1.0 : PotionEffectHelper.getMultiplierFromEffect(this.player, MainEffects.TP_GAIN, "tp_gain");
   }

   public double getMutantTpMultiplier() {
      if (!this.effects.hasEffect("mutant")) {
         return 1.0;
      } else {
         GeneralServerConfig.MutantConfig mutantConfig = ConfigManager.getServerConfig() != null ? ConfigManager.getServerConfig().getMutant() : null;
         return mutantConfig != null ? mutantConfig.getTpGainMultiplier() : 1.0;
      }
   }

   public double getTpTotalMultiplier() {
      double finalTotal = this.getTpAdditiveMultiplier();
      finalTotal += this.getProgressionTpGainMultiplier() - 1.0;
      return Math.max(0.0, finalTotal);
   }

   public double getTpSourceMultiplier(TpSource source) {
      List<TpBoost> boosts = ConfigManager.getServerConfig().getGameplay().getTpGainBoosts(source);
      if (boosts.isEmpty()) {
         return 1.0;
      } else {
         double total = 1.0;
         boolean gravityEnabled = ConfigManager.getServerConfig().getGameplay() == null
            || ConfigManager.getServerConfig().getGameplay().getGravityBonusEnabled();

         for (TpBoost boost : boosts) {
            switch (boost) {
               case CLASS:
                  total += this.getTpClassMultiplier() - 1.0;
                  break;
               case RACIALSKILL:
                  total += this.getTpFrostDemonMultiplier() - 1.0;
                  break;
               case HTC:
                  total += this.getTpHTCMultiplier() - 1.0;
                  break;
               case GRAVITY:
                  if (gravityEnabled) {
                     total += this.getTpGravityMultiplier() - 1.0;
                  }
                  break;
               case WEIGHTS:
                  total += this.getTpWeightBellMultiplier() - 1.0;
                  break;
               case GLOBAL:
                  total += this.getTpGlobalMultiplier() - 1.0;
                  break;
               case POTION:
                  total += this.getTpPotionEffectMultiplier() - 1.0;
                  break;
               case MUTANT:
                  total += this.getMutantTpMultiplier() - 1.0;
                  break;
               case DIFFICULTY:
                  total += this.getDifficultyTpMultiplier() - 1.0;
            }
         }

         total += this.getProgressionTpGainMultiplier() - 1.0;
         return Math.max(0.0, total);
      }
   }

   public double getDifficultyTpMultiplier() {
      if (this.getPlayerQuestData() == null) {
         return 1.0;
      } else {
         Difficulty difficulty = this.getPlayerQuestData().getDifficulty();
         return difficulty != null ? difficulty.tpMultiplier() : 1.0;
      }
   }

   public int applyTpBoosts(TpSource source, int baseTp) {
      if (baseTp <= 0) {
         return baseTp;
      } else {
         double mult = this.getTpSourceMultiplier(source);
         int result = (int)Math.max(0.0, (double)baseTp * mult);
         return result == 0 && mult > 0.0 ? 1 : result;
      }
   }

   public int calculateTPGain(int baseTP) {
      return this.calculateTPGain(baseTP, TpSource.STORY);
   }

   public int calculateTPGain(int baseTP, TpSource source) {
      if (baseTP <= 0) {
         return 0;
      } else {
         double total = (double)baseTP * this.getTpSourceMultiplier(source);
         return (int)Math.max(0.0, total);
      }
   }

   public double getProgressionTpGainMultiplier() {
      double strength = ConfigManager.getServerConfig().getGameplay().getIncreaseTPGainRelativeToTPCost();
      if (strength <= 0.0) {
         return 1.0;
      } else if (!ConfigManager.getServerConfig().getDynamicGrowth().isManualTpPurchasesEnabled()) {
         return 1.0;
      } else {
         int maxCost = this.getSingleStatCost(this.getConfiguredMaxTotalStats());
         if (maxCost <= 0) {
            return 1.0;
         } else {
            int currentCost = this.getSingleStatCost(this.stats.getTotalStats());
            double factor = Math.max(0.0, Math.min(1.0, (double)currentCost / (double)maxCost));
            return 1.0 + strength * factor;
         }
      }
   }

   private double statCostVariableComponent(int simulatedTotalStats) {
      double totalStats = Math.max(0.0, (double)simulatedTotalStats);
      double knee = (double)this.getConfiguredMaxTotalStats() * 0.05;
      if (!(knee <= 0.0) && !(totalStats <= knee)) {
         double kneeCost = knee * 1.25;
         double ratio = totalStats / knee;
         return kneeCost + kneeCost / 0.7 * (Math.pow(ratio, 0.7) - 1.0);
      } else {
         return totalStats * 1.25;
      }
   }

   public int getSingleStatCost(int simulatedTotalStats) {
      GeneralServerConfig.DynamicGrowthConfig dynamicGrowthConfig = ConfigManager.getServerConfig().getDynamicGrowth();
      if (!dynamicGrowthConfig.isManualTpPurchasesEnabled()) {
         return Integer.MAX_VALUE;
      } else {
         double globalMult = ConfigManager.getServerConfig().getGameplay().getGlobalTpCostMultiplier();
         double raceMult = this.getRaceTpCostMultiplier();
         double totalMult = globalMult * raceMult;
         int minCost = ConfigManager.getServerConfig().getGameplay().getMinTPCost();
         int discountThreshold = ConfigManager.getServerConfig().getGameplay().getMaxTPDiscount();
         double baseCost = (double)minCost + this.statCostVariableComponent(simulatedTotalStats);
         int earlyGameDiscount = 0;
         if (simulatedTotalStats < discountThreshold) {
            earlyGameDiscount = discountThreshold - simulatedTotalStats;
         }

         int finalCost = (int)(baseCost * totalMult) - earlyGameDiscount;
         finalCost = Math.max(minCost, finalCost);
         double tpCostMultiplier = dynamicGrowthConfig.getAttributeTpCostMultiplier();
         if (tpCostMultiplier > 1.0) {
            double scaled = Math.ceil((double)finalCost * tpCostMultiplier);
            finalCost = scaled >= 2.147483647E9 ? Integer.MAX_VALUE : (int)scaled;
         }

         return finalCost;
      }
   }

   public int calculateRecursiveCost(int statsToAdd, int maxStats) {
      if (!ConfigManager.getServerConfig().getDynamicGrowth().isManualTpPurchasesEnabled()) {
         return statsToAdd <= 0 ? 0 : Integer.MAX_VALUE;
      } else {
         int totalCost = 0;
         int currentTotalStats = this.stats.getTotalStats();
         int totalCap = this.getConfiguredMaxTotalStats();

         for (int i = 0; i < statsToAdd && currentTotalStats + i < totalCap; i++) {
            totalCost += this.getSingleStatCost(currentTotalStats + i);
         }

         return totalCost;
      }
   }

   public int calculateStatIncrease(int maxStatsToAdd, float availableTPs, int maxStats) {
      if (!ConfigManager.getServerConfig().getDynamicGrowth().isManualTpPurchasesEnabled()) {
         return 0;
      } else {
         int statsIncreased = 0;
         int costAccumulated = 0;
         int currentTotalStats = this.stats.getTotalStats();

         for (int totalCap = this.getConfiguredMaxTotalStats();
            statsIncreased < maxStatsToAdd && currentTotalStats + statsIncreased < totalCap;
            statsIncreased++
         ) {
            int costForNext = this.getSingleStatCost(currentTotalStats + statsIncreased);
            if ((float)(costAccumulated + costForNext) > availableTPs) {
               break;
            }

            costAccumulated += costForNext;
         }

         return statsIncreased;
      }
   }

   public void resetPlayerProgress(ServerPlayer player, Integer keepPercentage, boolean keepSkills, boolean forceSaiyanTail) {
      Stats currentStats = this.getStats();
      if (keepPercentage != null) {
         int newStr = currentStats.getStrength() * keepPercentage / 100;
         int newSkp = currentStats.getStrikePower() * keepPercentage / 100;
         int newRes = currentStats.getResistance() * keepPercentage / 100;
         int newVit = currentStats.getVitality() * keepPercentage / 100;
         int newPwr = currentStats.getKiPower() * keepPercentage / 100;
         int newEne = currentStats.getEnergy() * keepPercentage / 100;
         float currentTPs = this.getResources().getTrainingPoints();
         float newTPs = currentTPs * (float)keepPercentage.intValue() / 100.0F;
         currentStats.setStrength(Math.max(0, newStr));
         currentStats.setStrikePower(Math.max(0, newSkp));
         currentStats.setResistance(Math.max(0, newRes));
         currentStats.setVitality(Math.max(0, newVit));
         currentStats.setKiPower(Math.max(0, newPwr));
         currentStats.setEnergy(Math.max(0, newEne));
         this.getResources().setTrainingPoints(newTPs);
      } else {
         currentStats.setStrength(0);
         currentStats.setStrikePower(0);
         currentStats.setResistance(0);
         currentStats.setVitality(0);
         currentStats.setKiPower(0);
         currentStats.setEnergy(0);
         this.getResources().setTrainingPoints(0.0F);
      }

      if (this.getStatus().isFused()) {
         FusionLogic.endFusion(player, this, false);
      }

      this.getCharacter().clearActiveForm(player);
      this.getCharacter().clearActiveStackForm(player);
      TransformStatusHandler.clearAllPersistentFormEffects(player);
      this.getStatus().reset();
      this.getResources().reset();
      this.getResources().setPendingAttributePoints(0);
      this.getResources().setPowerRelease(0);
      this.getSkills().setSkillActive("kisense", false);
      this.getPlayerQuestData().resetAll();
      this.getPlayerQuestData().clearStoryResets();
      this.getCharacter().clearInteractedMasters();
      this.getDynamicGrowth().clear();
      if (!keepSkills) {
         this.getSkills().removeAllSkills();
         this.getEffects().removeAllEffects();
         this.getSecondaryStatEffects().clear();
         this.getTechniques().clearAllTechniques();
      }

      this.getCooldowns().clearCooldowns();
      this.getBonusStats().clearAllStats();
      if (forceSaiyanTail) {
         this.getCharacter().setHasSaiyanTail(true);
      }

      player.refreshDimensions();
      player.setHealth(20.0F);
      AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
      if (attribute != null) {
         attribute.removeModifier(AttributeMods.id(StatsEvents.DMZ_HEALTH_MODIFIER_UUID));
      }

      player.setHealth(20.0F);
   }

   public void tick() {
      this.cooldowns.tick();
   }

   public CompoundTag save() {
      CompoundTag nbt = new CompoundTag();
      nbt.put("Stats", this.stats.save());
      nbt.put("Status", this.status.save());
      nbt.put("Cooldowns", this.cooldowns.save());
      nbt.put("Character", this.character.save());
      nbt.put("Resources", this.resources.save());
      nbt.put("Skills", this.skills.save());
      nbt.put("Effects", this.effects.save());
      nbt.put("SecondaryStatEffects", this.secondaryStatEffects.save());
      nbt.put("PlayerQuestData", this.playerQuestData.serializeNBT());
      nbt.put("BonusStats", this.bonusStats.save());
      nbt.put("Techniques", this.techniques.save());
      nbt.put("DynamicGrowth", this.dynamicGrowth.save());
      nbt.putBoolean("HasInitializedHealth", this.hasInitializedHealth);
      return nbt;
   }

   public void load(CompoundTag nbt) throws ClassNotFoundException {
      if (nbt.contains("Stats")) {
         this.stats.load(nbt.getCompound("Stats"));
      }

      if (nbt.contains("Status")) {
         this.status.load(nbt.getCompound("Status"));
      }

      if (nbt.contains("Cooldowns")) {
         this.cooldowns.load(nbt.getCompound("Cooldowns"));
      }

      if (nbt.contains("Character")) {
         this.character.load(nbt.getCompound("Character"));
      }

      if (nbt.contains("Resources")) {
         this.resources.load(nbt.getCompound("Resources"));
      }

      if (nbt.contains("Skills")) {
         this.skills.load(nbt.getCompound("Skills"));
      }

      if (nbt.contains("Effects")) {
         this.effects.load(nbt.getCompound("Effects"));
      }

      if (nbt.contains("SecondaryStatEffects")) {
         this.secondaryStatEffects.load(nbt.getCompound("SecondaryStatEffects"));
      } else {
         this.secondaryStatEffects.clear();
      }

      if (nbt.contains("PlayerQuestData")) {
         this.playerQuestData.deserializeNBT(nbt.getCompound("PlayerQuestData"));
         if (nbt.contains("BonusStats")) {
            this.bonusStats.load(nbt.getCompound("BonusStats"));
         }

         if (nbt.contains("Techniques")) {
            this.techniques.load(nbt.getCompound("Techniques"));
         }

         if (nbt.contains("DynamicGrowth")) {
            this.dynamicGrowth.load(nbt.getCompound("DynamicGrowth"));
         }

         if (nbt.contains("HasInitializedHealth")) {
            this.hasInitializedHealth = nbt.getBoolean("HasInitializedHealth");
         }

         if (this.character.getRaceName() != null && !this.character.getRaceName().isEmpty()) {
            this.updateTransformationSkillLimits(this.character.getRaceName());
         }

         this.stats.applyToAttributes();
         this.isDataLoaded = true;
      } else {
         throw new ClassNotFoundException(
            "PlayerQuestData not found in NBT. This is required for quest progression to work correctly. Please update the mod or re-generate your config files."
         );
      }
   }

   public void copyFrom(StatsData other) {
      this.stats.copyFrom(other.stats);
      this.status.copyFrom(other.status);
      this.cooldowns.copyFrom(other.cooldowns);
      this.character.copyFrom(other.character);
      this.resources.copyFrom(other.resources);
      this.skills.copyFrom(other.skills);
      this.effects.copyFrom(other.effects);
      this.secondaryStatEffects.copyFrom(other.secondaryStatEffects);
      this.playerQuestData.deserializeNBT(other.playerQuestData.serializeNBT());
      this.bonusStats.copyFrom(other.bonusStats);
      this.techniques.copyFrom(other.techniques);
      this.dynamicGrowth.copyFrom(other.dynamicGrowth);
      this.hasInitializedHealth = other.hasInitializedHealth;
      if (this.character.getRaceName() != null && !this.character.getRaceName().isEmpty()) {
         this.updateTransformationSkillLimits(this.character.getRaceName());
      }

      this.stats.applyToAttributes();
      this.isDataLoaded = true;
   }

   public void reapplyStatAttributes() {
      this.stats.applyToAttributes();
   }

   public Player getPlayer() {
      return this.player;
   }

   public Stats getStats() {
      return this.stats;
   }

   public Status getStatus() {
      return this.status;
   }

   public Cooldowns getCooldowns() {
      return this.cooldowns;
   }

   public Character getCharacter() {
      return this.character;
   }

   public Resources getResources() {
      return this.resources;
   }

   public Skills getSkills() {
      return this.skills;
   }

   public Effects getEffects() {
      return this.effects;
   }

   public SecondaryStatEffects getSecondaryStatEffects() {
      return this.secondaryStatEffects;
   }

   public PlayerQuestData getPlayerQuestData() {
      return this.playerQuestData;
   }

   public BonusStats getBonusStats() {
      return this.bonusStats;
   }

   public Techniques getTechniques() {
      return this.techniques;
   }

   public DynamicGrowthData getDynamicGrowth() {
      return this.dynamicGrowth;
   }

   public boolean isHasInitializedHealth() {
      return this.hasInitializedHealth;
   }

   public boolean isDataLoaded() {
      return this.isDataLoaded;
   }
}
