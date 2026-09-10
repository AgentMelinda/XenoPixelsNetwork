package com.dragonminez.common.stats.techniques;

import com.dragonminez.common.stats.StatsData;
import java.util.Locale;
import java.util.UUID;
import lombok.Generated;
import net.minecraft.nbt.CompoundTag;

public abstract class TechniqueData {
   protected String id = UUID.randomUUID().toString();
   protected String name;
   protected String author;
   protected int experience = 0;
   protected double baseCost;
   protected int castTime = 0;
   protected int cooldown = 0;
   protected float tpCost = 0.0F;

   public abstract CompoundTag save();

   public abstract void load(CompoundTag var1);

   public abstract TechniqueType getType();

   public abstract double getCalculatedCost(StatsData var1);

   public void addExperience(int amount) {
      this.experience += amount;
   }

   public static String generateId(String author, String name) {
      String a = sanitizeIdPart(author);
      String n = sanitizeIdPart(name);
      if (a.isEmpty()) {
         a = "player";
      }

      if (n.isEmpty()) {
         n = "skill";
      }

      return a + "_" + n;
   }

   private static String sanitizeIdPart(String value) {
      return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
   }

   @Generated
   public String getId() {
      return this.id;
   }

   @Generated
   public String getName() {
      return this.name;
   }

   @Generated
   public String getAuthor() {
      return this.author;
   }

   @Generated
   public int getExperience() {
      return this.experience;
   }

   @Generated
   public double getBaseCost() {
      return this.baseCost;
   }

   @Generated
   public int getCastTime() {
      return this.castTime;
   }

   @Generated
   public int getCooldown() {
      return this.cooldown;
   }

   @Generated
   public float getTpCost() {
      return this.tpCost;
   }

   @Generated
   public void setId(String id) {
      this.id = id;
   }

   @Generated
   public void setName(String name) {
      this.name = name;
   }

   @Generated
   public void setAuthor(String author) {
      this.author = author;
   }

   @Generated
   public void setExperience(int experience) {
      this.experience = experience;
   }

   @Generated
   public void setBaseCost(double baseCost) {
      this.baseCost = baseCost;
   }

   @Generated
   public void setCastTime(int castTime) {
      this.castTime = castTime;
   }

   @Generated
   public void setCooldown(int cooldown) {
      this.cooldown = cooldown;
   }

   @Generated
   public void setTpCost(float tpCost) {
      this.tpCost = tpCost;
   }
}
