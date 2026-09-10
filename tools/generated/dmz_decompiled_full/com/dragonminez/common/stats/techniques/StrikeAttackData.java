package com.dragonminez.common.stats.techniques;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TechniqueConfig;
import com.dragonminez.common.stats.StatsData;
import lombok.Generated;
import net.minecraft.nbt.CompoundTag;

public class StrikeAttackData extends TechniqueData {
   public static final float LEVEL_MODIFIER_STEP = 0.025F;
   public static final float MAX_MODIFIER_LIMIT = 0.5F;
   private float damageMultiplier;
   private int damageLevel;
   private int cooldownLevel;
   private String animationId;
   private int durationTicks;

   public void applyConfigDefaults() {
      TechniqueConfig.StrikeAttackConfig cfg = ConfigManager.getTechniqueConfig().getStrikeConfig(this.id);
      this.castTime = Math.max(0, cfg.getCastTimeTicks());
      this.cooldown = Math.max(0, cfg.getCooldownTicks());
   }

   public float getDamageLevelMultiplier() {
      return 1.0F + Math.min(0.5F, (float)Math.max(0, this.damageLevel) * 0.025F);
   }

   public float getReductionLevelMultiplier(int level) {
      return Math.max(0.5F, 1.0F - (float)Math.max(0, level) * 0.025F);
   }

   public float getActualDamageMultiplier() {
      return this.damageMultiplier * this.getDamageLevelMultiplier();
   }

   public int getActualCastTime() {
      return Math.max(0, this.castTime);
   }

   public int getActualCooldown() {
      TechniqueConfig.StrikeAttackConfig cfg = ConfigManager.getTechniqueConfig().getStrikeConfig(this.id);
      int base = Math.max(0, cfg.getCooldownTicks());
      return Math.max(1, Math.round((float)base * this.getReductionLevelMultiplier(this.cooldownLevel)));
   }

   public int getUpgradeXpCost(String statName) {
      TechniqueConfig.StrikeAttackConfig cfg = ConfigManager.getTechniqueConfig().getStrikeConfig(this.id);
      int baseMin = Math.max(0, cfg.getMinXPCost());
      double multiplier = Math.max(0.0, cfg.getXpCostMultiplier());
      int totalUpgrades = Math.max(0, this.damageLevel) + Math.max(0, this.cooldownLevel);
      int scaledBase = (int)Math.round((double)baseMin * multiplier);
      int upgradeExtra = (int)Math.round((double)totalUpgrades * Math.max(0.0, (double)baseMin * (multiplier - 1.0)));
      int computed = Math.max(0, scaledBase + upgradeExtra);
      int max = cfg.getMaxXPCost();
      if (max >= 0) {
         computed = Math.min(computed, max);
      }

      return Math.max(0, computed);
   }

   public int getXpGainPerHit() {
      TechniqueConfig.StrikeAttackConfig cfg = ConfigManager.getTechniqueConfig().getStrikeConfig(this.id);
      double gain = Math.max(0.0, (double)cfg.getXpGainPerHit() * cfg.getXpGainMultiplier());
      return Math.max(0, (int)Math.round(gain));
   }

   public int getXpGainPerKill() {
      TechniqueConfig.StrikeAttackConfig cfg = ConfigManager.getTechniqueConfig().getStrikeConfig(this.id);
      double gain = Math.max(0.0, (double)cfg.getXpGainPerKill() * cfg.getXpGainMultiplier());
      return Math.max(0, (int)Math.round(gain));
   }

   public boolean canUpgradeStat(String statName) {
      return "damage".equals(statName) || "cooldown".equals(statName);
   }

   @Override
   public TechniqueType getType() {
      return TechniqueType.STRIKE_ATTACK;
   }

   @Override
   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("Id", this.id);
      tag.putString("Name", this.name);
      tag.putString("Author", this.author);
      tag.putInt("Experience", this.experience);
      tag.putDouble("BaseCost", this.baseCost);
      tag.putFloat("TpCost", this.tpCost);
      tag.putFloat("DamageMultiplier", this.damageMultiplier);
      tag.putInt("DamageLevel", this.damageLevel);
      tag.putInt("CooldownLevel", this.cooldownLevel);
      tag.putString("AnimationId", this.animationId != null ? this.animationId : "");
      tag.putInt("DurationTicks", this.durationTicks);
      tag.putInt("CastTime", this.castTime);
      tag.putInt("Cooldown", this.cooldown);
      return tag;
   }

   @Override
   public void load(CompoundTag tag) {
      this.id = tag.getString("Id");
      this.name = tag.getString("Name");
      this.author = tag.getString("Author");
      this.experience = tag.getInt("Experience");
      this.baseCost = tag.getDouble("BaseCost");
      this.tpCost = tag.contains("TpCost") ? tag.getFloat("TpCost") : 0.0F;
      this.damageMultiplier = tag.getFloat("DamageMultiplier");
      this.damageLevel = tag.getInt("DamageLevel");
      this.cooldownLevel = tag.getInt("CooldownLevel");
      this.animationId = tag.getString("AnimationId");
      this.durationTicks = tag.contains("DurationTicks") ? tag.getInt("DurationTicks") : 60;
      if (this.durationTicks <= 0) {
         this.durationTicks = 60;
      }

      this.castTime = tag.getInt("CastTime");
      this.cooldown = tag.getInt("Cooldown");
   }

   @Override
   public double getCalculatedCost(StatsData statsData) {
      TechniqueConfig.StrikeAttackConfig cfg = ConfigManager.getTechniqueConfig().getStrikeConfig(this.id);
      double baseDamage = statsData.getStrikeDamageNoForms() * (double)this.getActualDamageMultiplier() * Math.max(0.0, cfg.getDamageMultiplier());
      double costMult = Math.max(0.0, cfg.getKiCostMultiplier());
      return Math.max(5.0, baseDamage * 0.35 * costMult / 2.0);
   }

   @Generated
   public float getDamageMultiplier() {
      return this.damageMultiplier;
   }

   @Generated
   public int getDamageLevel() {
      return this.damageLevel;
   }

   @Generated
   public int getCooldownLevel() {
      return this.cooldownLevel;
   }

   @Generated
   public String getAnimationId() {
      return this.animationId;
   }

   @Generated
   public int getDurationTicks() {
      return this.durationTicks;
   }

   @Generated
   public void setDamageMultiplier(float damageMultiplier) {
      this.damageMultiplier = damageMultiplier;
   }

   @Generated
   public void setDamageLevel(int damageLevel) {
      this.damageLevel = damageLevel;
   }

   @Generated
   public void setCooldownLevel(int cooldownLevel) {
      this.cooldownLevel = cooldownLevel;
   }

   @Generated
   public void setAnimationId(String animationId) {
      this.animationId = animationId;
   }

   @Generated
   public void setDurationTicks(int durationTicks) {
      this.durationTicks = durationTicks;
   }
}
