package com.dragonminez.common.stats.character;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.MainAttributes;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

public class Stats {
   private Player player;
   private int strength;
   private int strikePower;
   private int resistance;
   private int vitality;
   private int kiPower;
   private int energy;

   private int clampStatValue(int value) {
      int min = 0;
      int capped = Math.max(min, value);
      if (ConfigManager.getServerConfig() == null || ConfigManager.getServerConfig().getGameplay() == null) {
         return capped;
      } else if (ConfigManager.getServerConfig().getGameplay().getMaxLevelValueInsteadOfStats()) {
         return capped;
      } else {
         int max = ConfigManager.getServerConfig().getGameplay().getMaxValue();
         return Math.min(capped, max);
      }
   }

   private int safeAdd(int base, int delta) {
      long result = (long)base + (long)delta;
      if (result > 2147483647L) {
         return Integer.MAX_VALUE;
      } else {
         return result < -2147483648L ? Integer.MIN_VALUE : (int)result;
      }
   }

   private boolean attributesReady() {
      return this.player != null && this.player.getAttributes() != null;
   }

   private void setAttributeBaseValue(Holder<Attribute> attribute, int value) {
      if (this.attributesReady()) {
         AttributeInstance instance = this.player.getAttribute(attribute);
         if (instance != null) {
            if (!(Math.abs(instance.getBaseValue() - (double)value) < 0.5)) {
               instance.setBaseValue((double)value);
            }
         }
      }
   }

   public void applyToAttributes() {
      if (this.attributesReady()) {
         this.setAttributeBaseValue(MainAttributes.STRENGTH, this.strength);
         this.setAttributeBaseValue(MainAttributes.STRIKE_POWER, this.strikePower);
         this.setAttributeBaseValue(MainAttributes.RESISTANCE, this.resistance);
         this.setAttributeBaseValue(MainAttributes.VITALITY, this.vitality);
         this.setAttributeBaseValue(MainAttributes.KI_POWER, this.kiPower);
         this.setAttributeBaseValue(MainAttributes.ENERGY, this.energy);
      }
   }

   public void pullFromAttributesIfFieldsEmpty() {
      if (this.player != null) {
         if (this.strength == 0 && this.strikePower == 0 && this.resistance == 0 && this.vitality == 0 && this.kiPower == 0 && this.energy == 0) {
            this.strength = this.readAttributeBase(MainAttributes.STRENGTH, 0);
            this.strikePower = this.readAttributeBase(MainAttributes.STRIKE_POWER, 0);
            this.resistance = this.readAttributeBase(MainAttributes.RESISTANCE, 0);
            this.vitality = this.readAttributeBase(MainAttributes.VITALITY, 0);
            this.kiPower = this.readAttributeBase(MainAttributes.KI_POWER, 0);
            this.energy = this.readAttributeBase(MainAttributes.ENERGY, 0);
         }
      }
   }

   private int readAttributeBase(Holder<Attribute> attribute, int fallback) {
      if (!this.attributesReady()) {
         return fallback;
      } else {
         AttributeInstance instance = this.player.getAttribute(attribute);
         return instance == null ? fallback : (int)Math.round(instance.getBaseValue());
      }
   }

   public int getStrength() {
      return this.strength;
   }

   public int getStrikePower() {
      return this.strikePower;
   }

   public int getResistance() {
      return this.resistance;
   }

   public int getVitality() {
      return this.vitality;
   }

   public int getKiPower() {
      return this.kiPower;
   }

   public int getEnergy() {
      return this.energy;
   }

   public void setStrength(int value) {
      int oldValue = this.strength;
      int newValue = this.clampStatValue(value);
      if (oldValue != newValue && this.player != null) {
         DMZEvent.StatChangeEvent event = new DMZEvent.StatChangeEvent(this.player, DMZEvent.StatChangeEvent.StatType.STRENGTH, oldValue, newValue);
         if (!((DMZEvent.StatChangeEvent)NeoForge.EVENT_BUS.post(event)).isCanceled()) {
            this.strength = newValue;
            this.setAttributeBaseValue(MainAttributes.STRENGTH, newValue);
         }
      } else {
         this.strength = newValue;
         this.setAttributeBaseValue(MainAttributes.STRENGTH, newValue);
      }
   }

   public void setStrikePower(int value) {
      int oldValue = this.strikePower;
      int newValue = this.clampStatValue(value);
      if (oldValue != newValue && this.player != null) {
         DMZEvent.StatChangeEvent event = new DMZEvent.StatChangeEvent(this.player, DMZEvent.StatChangeEvent.StatType.STRIKE_POWER, oldValue, newValue);
         if (!((DMZEvent.StatChangeEvent)NeoForge.EVENT_BUS.post(event)).isCanceled()) {
            this.strikePower = newValue;
            this.setAttributeBaseValue(MainAttributes.STRIKE_POWER, newValue);
         }
      } else {
         this.strikePower = newValue;
         this.setAttributeBaseValue(MainAttributes.STRIKE_POWER, newValue);
      }
   }

   public void setResistance(int value) {
      int oldValue = this.resistance;
      int newValue = this.clampStatValue(value);
      if (oldValue != newValue && this.player != null) {
         DMZEvent.StatChangeEvent event = new DMZEvent.StatChangeEvent(this.player, DMZEvent.StatChangeEvent.StatType.RESISTANCE, oldValue, newValue);
         if (!((DMZEvent.StatChangeEvent)NeoForge.EVENT_BUS.post(event)).isCanceled()) {
            this.resistance = newValue;
            this.setAttributeBaseValue(MainAttributes.RESISTANCE, newValue);
         }
      } else {
         this.resistance = newValue;
         this.setAttributeBaseValue(MainAttributes.RESISTANCE, newValue);
      }
   }

   public void setVitality(int value) {
      int oldValue = this.vitality;
      int newValue = this.clampStatValue(value);
      if (oldValue != newValue && this.player != null) {
         DMZEvent.StatChangeEvent event = new DMZEvent.StatChangeEvent(this.player, DMZEvent.StatChangeEvent.StatType.VITALITY, oldValue, newValue);
         if (!((DMZEvent.StatChangeEvent)NeoForge.EVENT_BUS.post(event)).isCanceled()) {
            this.vitality = newValue;
            this.setAttributeBaseValue(MainAttributes.VITALITY, newValue);
         }
      } else {
         this.vitality = newValue;
         this.setAttributeBaseValue(MainAttributes.VITALITY, newValue);
      }
   }

   public void setKiPower(int value) {
      int oldValue = this.kiPower;
      int newValue = this.clampStatValue(value);
      if (oldValue != newValue && this.player != null) {
         DMZEvent.StatChangeEvent event = new DMZEvent.StatChangeEvent(this.player, DMZEvent.StatChangeEvent.StatType.KI_POWER, oldValue, newValue);
         if (!((DMZEvent.StatChangeEvent)NeoForge.EVENT_BUS.post(event)).isCanceled()) {
            this.kiPower = newValue;
            this.setAttributeBaseValue(MainAttributes.KI_POWER, newValue);
         }
      } else {
         this.kiPower = newValue;
         this.setAttributeBaseValue(MainAttributes.KI_POWER, newValue);
      }
   }

   public void setEnergy(int value) {
      int oldValue = this.energy;
      int newValue = this.clampStatValue(value);
      if (oldValue != newValue && this.player != null) {
         DMZEvent.StatChangeEvent event = new DMZEvent.StatChangeEvent(this.player, DMZEvent.StatChangeEvent.StatType.ENERGY, oldValue, newValue);
         if (!((DMZEvent.StatChangeEvent)NeoForge.EVENT_BUS.post(event)).isCanceled()) {
            this.energy = newValue;
            this.setAttributeBaseValue(MainAttributes.ENERGY, newValue);
         }
      } else {
         this.energy = newValue;
         this.setAttributeBaseValue(MainAttributes.ENERGY, newValue);
      }
   }

   public void addStrength(int amount) {
      this.setStrength(this.safeAdd(this.getStrength(), amount));
   }

   public void addStrikePower(int amount) {
      this.setStrikePower(this.safeAdd(this.getStrikePower(), amount));
   }

   public void addResistance(int amount) {
      this.setResistance(this.safeAdd(this.getResistance(), amount));
   }

   public void addVitality(int amount) {
      this.setVitality(this.safeAdd(this.getVitality(), amount));
   }

   public void addKiPower(int amount) {
      this.setKiPower(this.safeAdd(this.getKiPower(), amount));
   }

   public void addEnergy(int amount) {
      this.setEnergy(this.safeAdd(this.getEnergy(), amount));
   }

   public void setStat(String statName, int value) {
      String var3 = statName.toLowerCase();
      switch (var3) {
         case "str":
            this.setStrength(value);
            break;
         case "skp":
            this.setStrikePower(value);
            break;
         case "res":
            this.setResistance(value);
            break;
         case "vit":
            this.setVitality(value);
            break;
         case "pwr":
            this.setKiPower(value);
            break;
         case "ene":
            this.setEnergy(value);
            break;
         default:
            throw new IllegalArgumentException("Unknown stat: " + statName);
      }
   }

   public void addStat(String statName, int amount) {
      String var3 = statName.toLowerCase();
      switch (var3) {
         case "str":
            this.addStrength(amount);
            break;
         case "skp":
            this.addStrikePower(amount);
            break;
         case "res":
            this.addResistance(amount);
            break;
         case "vit":
            this.addVitality(amount);
            break;
         case "pwr":
            this.addKiPower(amount);
            break;
         case "ene":
            this.addEnergy(amount);
            break;
         default:
            throw new IllegalArgumentException("Unknown stat: " + statName);
      }
   }

   public void removeStat(String statName, int amount) {
      this.addStat(statName, -amount);
   }

   public int getTotalStats() {
      long total = (long)this.getStrength()
         + (long)this.getStrikePower()
         + (long)this.getResistance()
         + (long)this.getVitality()
         + (long)this.getKiPower()
         + (long)this.getEnergy();
      return total > 2147483647L ? Integer.MAX_VALUE : (int)total;
   }

   public CompoundTag save() {
      this.applyToAttributes();
      CompoundTag tag = new CompoundTag();
      tag.putInt("STR", this.strength);
      tag.putInt("SKP", this.strikePower);
      tag.putInt("RES", this.resistance);
      tag.putInt("VIT", this.vitality);
      tag.putInt("PWR", this.kiPower);
      tag.putInt("ENE", this.energy);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.strength = this.clampStatValue(tag.contains("STR") ? tag.getInt("STR") : 0);
      this.strikePower = this.clampStatValue(tag.contains("SKP") ? tag.getInt("SKP") : 0);
      this.resistance = this.clampStatValue(tag.contains("RES") ? tag.getInt("RES") : 0);
      this.vitality = this.clampStatValue(tag.contains("VIT") ? tag.getInt("VIT") : 0);
      this.kiPower = this.clampStatValue(tag.contains("PWR") ? tag.getInt("PWR") : 0);
      this.energy = this.clampStatValue(tag.contains("ENE") ? tag.getInt("ENE") : 0);
      this.applyToAttributes();
   }

   public void copyFrom(Stats other) {
      this.strength = other.strength;
      this.strikePower = other.strikePower;
      this.resistance = other.resistance;
      this.vitality = other.vitality;
      this.kiPower = other.kiPower;
      this.energy = other.energy;
      this.applyToAttributes();
   }

   public void setPlayer(Player player) {
      this.player = player;
      this.applyToAttributes();
   }

   public Player getPlayer() {
      return this.player;
   }
}
