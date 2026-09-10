package com.dragonminez.common.stats.character;

import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.server.dynamicgrowth.DynamicGrowthService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

public class Resources {
   private float currentEnergy = 0.0F;
   private float currentStamina = 0.0F;
   private float currentPoise = 0.0F;
   private int release = 5;
   private int releaseLimit = 0;
   private int actionCharge = 0;
   private int alignment = 100;
   private float trainingPoints = 0.0F;
   private int pendingAttributePoints = 0;
   private int racialSkillCount = 0;
   private Player player;
   private transient StatsData statsData;
   private static final float LARGE_POOL_THRESHOLD = 1000000.0F;

   public void reset() {
      this.currentEnergy = 0.0F;
      this.currentStamina = 0.0F;
      this.currentPoise = 0.0F;
      this.release = 5;
      this.releaseLimit = 0;
      this.actionCharge = 0;
      this.alignment = 100;
      this.racialSkillCount = 0;
   }

   private static float roundToQuarter(float value) {
      return (float)Math.round(value * 4.0F) / 4.0F;
   }

   private static float truncateToInt(float value) {
      return (float)Math.floor((double)value);
   }

   private static float clampPool(double value, float max) {
      if (max > 0.0F && Double.isFinite(value)) {
         double v = Math.min(Math.max(0.0, value), (double)max);
         if (v >= (double)max - Math.max(4.0 * (double)Math.ulp(max), 1.0)) {
            return max;
         } else {
            return v < 1000000.0 ? roundToQuarter((float)v) : (float)v;
         }
      } else {
         return 0.0F;
      }
   }

   public int getPowerRelease() {
      return this.release;
   }

   public void setCurrentEnergy(float energy) {
      if (energy <= 1.0F) {
         this.setPowerRelease(0);
      }

      float max = this.statsData != null ? this.statsData.getMaxEnergy() : 0.0F;
      this.currentEnergy = clampPool((double)energy, max);
   }

   public void setCurrentStamina(float stamina) {
      float max = this.statsData != null ? Math.max(0.0F, this.statsData.getMaxStamina()) : 0.0F;
      this.currentStamina = clampPool((double)stamina, max);
   }

   public void setCurrentPoise(float poise) {
      float max = this.statsData != null ? this.statsData.getMaxPoise() : 0.0F;
      this.currentPoise = roundToQuarter((float)Math.min(Math.max(0.0, (double)poise), Math.max(0.0, (double)max)));
   }

   public void setPowerRelease(int release) {
      this.release = Math.max(0, release);
   }

   public void setReleaseLimit(int releaseLimit) {
      this.releaseLimit = Math.max(0, releaseLimit);
   }

   public void setActionCharge(int actionCharge) {
      this.actionCharge = Math.max(0, Math.min(100, actionCharge));
   }

   public void setAlignment(int alignment) {
      if (this.statsData != null && this.statsData.getEffects().hasEffect("majin")) {
         this.alignment = 0;
      } else {
         this.alignment = Math.max(0, Math.min(100, alignment));
      }
   }

   public void setTrainingPoints(float points) {
      float clamped = Math.max(0.0F, Math.min(Float.MAX_VALUE, points));
      this.trainingPoints = truncateToInt(clamped);
   }

   public void setPendingAttributePoints(int points) {
      this.pendingAttributePoints = Math.max(0, points);
   }

   public void addPendingAttributePoints(int amount) {
      this.setPendingAttributePoints(this.pendingAttributePoints + amount);
   }

   public void removePendingAttributePoints(int amount) {
      this.setPendingAttributePoints(this.pendingAttributePoints - amount);
   }

   public void setRacialSkillCount(int count) {
      this.racialSkillCount = Math.max(0, count);
   }

   public void addEnergy(float amount) {
      this.setCurrentEnergy((float)((double)this.currentEnergy + (double)amount));
   }

   public void addStamina(float amount) {
      this.setCurrentStamina((float)((double)this.currentStamina + (double)amount));
   }

   public void addPoise(float amount) {
      this.setCurrentPoise(this.currentPoise + amount);
   }

   public void grantMaxPoolIncrease(float oldMax, float newMax, boolean energy) {
      if (newMax > oldMax) {
         double cur = energy ? (double)this.currentEnergy : (double)this.currentStamina;
         boolean wasFull = oldMax <= 0.0F || cur >= (double)oldMax - Math.max(4.0 * (double)Math.ulp(oldMax), 1.0);
         if (wasFull) {
            if (energy) {
               this.setCurrentEnergy(newMax);
            } else {
               this.setCurrentStamina(newMax);
            }
         } else {
            double next = cur + ((double)newMax - (double)oldMax);
            if (energy) {
               this.setCurrentEnergy((float)Math.min((double)newMax, next));
            } else {
               this.setCurrentStamina((float)Math.min((double)newMax, next));
            }
         }
      }
   }

   public void reclampToCurrentMax() {
      if (this.statsData != null) {
         float maxE = this.statsData.getMaxEnergy();
         float maxS = this.statsData.getMaxStamina();
         float maxP = this.statsData.getMaxPoise();
         if (maxE > 0.0F) {
            this.currentEnergy = clampPool((double)this.currentEnergy, maxE);
         }

         if (maxS > 0.0F) {
            this.currentStamina = clampPool((double)this.currentStamina, maxS);
         }

         if (maxP > 0.0F) {
            this.currentPoise = roundToQuarter((float)Math.min(Math.max(0.0, (double)this.currentPoise), (double)maxP));
         }
      }
   }

   public void addAlignment(int amount) {
      this.setAlignment(this.alignment + amount);
   }

   public void addTrainingPoints(float amount) {
      this.addTrainingPoints(amount, true);
   }

   public void addTrainingPoints(float amount, boolean shareWithParty) {
      if (!(amount <= 0.0F) && this.player != null) {
         float oldValue = this.trainingPoints;
         DMZEvent.TPGainEvent event = new DMZEvent.TPGainEvent(this.player, (int)oldValue, (int)amount, shareWithParty);
         if (!((DMZEvent.TPGainEvent)NeoForge.EVENT_BUS.post(event)).isCanceled()) {
            this.setTrainingPoints(oldValue + (float)event.getTpGain());
         }
      } else {
         this.setTrainingPoints(this.trainingPoints + amount);
      }
   }

   public void addRacialSkillCount(int amount) {
      this.setRacialSkillCount(this.racialSkillCount + amount);
   }

   public void removeEnergy(float amount) {
      float before = this.currentEnergy;
      this.setCurrentEnergy(this.currentEnergy - amount);
      this.awardDynamicGrowthEnergy(before - this.currentEnergy);
   }

   public void removeStamina(float amount) {
      float before = this.currentStamina;
      this.setCurrentStamina(this.currentStamina - amount);
      this.awardDynamicGrowthStamina(before - this.currentStamina);
   }

   private void awardDynamicGrowthStamina(float spent) {
      if (spent > 0.0F && this.statsData != null && this.player instanceof ServerPlayer serverPlayer) {
         DynamicGrowthService.awardStaminaSpent(serverPlayer, this.statsData, (double)spent);
      }
   }

   private void awardDynamicGrowthEnergy(float spent) {
      if (spent > 0.0F && this.statsData != null && this.player instanceof ServerPlayer serverPlayer) {
         DynamicGrowthService.awardEnergySpent(serverPlayer, this.statsData, (double)spent);
      }
   }

   public void removePoise(float amount) {
      this.setCurrentPoise(this.currentPoise - amount);
   }

   public void removeAlignment(int amount) {
      this.setAlignment(this.alignment - amount);
   }

   public void removeTrainingPoints(float amount) {
      this.setTrainingPoints(this.trainingPoints - amount);
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putFloat("CurrentEnergy", this.currentEnergy);
      tag.putFloat("CurrentStamina", this.currentStamina);
      tag.putFloat("CurrentPoise", this.currentPoise);
      tag.putInt("Release", this.release);
      tag.putInt("ReleaseLimit", this.releaseLimit);
      tag.putInt("FormRelease", this.actionCharge);
      tag.putInt("Alignment", this.alignment);
      tag.putFloat("TrainingPointsF", this.trainingPoints);
      tag.putInt("PendingAttributePoints", this.pendingAttributePoints);
      tag.putInt("ZenkaiCount", this.racialSkillCount);
      return tag;
   }

   public void load(CompoundTag tag) {
      if (tag.contains("CurrentEnergy", 5)) {
         this.currentEnergy = tag.getFloat("CurrentEnergy");
      } else {
         this.currentEnergy = (float)tag.getInt("CurrentEnergy");
      }

      if (tag.contains("CurrentStamina", 5)) {
         this.currentStamina = tag.getFloat("CurrentStamina");
      } else {
         this.currentStamina = (float)tag.getInt("CurrentStamina");
      }

      if (tag.contains("CurrentPoise", 5)) {
         this.currentPoise = tag.getFloat("CurrentPoise");
      } else {
         this.currentPoise = (float)tag.getInt("CurrentPoise");
      }

      this.release = tag.getInt("Release");
      this.releaseLimit = tag.getInt("ReleaseLimit");
      this.actionCharge = tag.getInt("FormRelease");
      this.alignment = tag.getInt("Alignment");
      if (tag.contains("TrainingPointsF", 5)) {
         this.trainingPoints = tag.getFloat("TrainingPointsF");
      } else {
         this.trainingPoints = (float)tag.getInt("TrainingPoints");
      }

      this.pendingAttributePoints = tag.getInt("PendingAttributePoints");
      this.racialSkillCount = tag.getInt("ZenkaiCount");
   }

   public void copyFrom(Resources other) {
      this.currentEnergy = other.currentEnergy;
      this.currentStamina = other.currentStamina;
      this.currentPoise = other.currentPoise;
      this.release = other.release;
      this.releaseLimit = other.releaseLimit;
      this.actionCharge = other.actionCharge;
      this.alignment = other.alignment;
      this.trainingPoints = other.trainingPoints;
      this.pendingAttributePoints = other.pendingAttributePoints;
      this.racialSkillCount = other.racialSkillCount;
   }

   public float getCurrentEnergy() {
      return this.currentEnergy;
   }

   public float getCurrentStamina() {
      return this.currentStamina;
   }

   public float getCurrentPoise() {
      return this.currentPoise;
   }

   public int getRelease() {
      return this.release;
   }

   public int getReleaseLimit() {
      return this.releaseLimit;
   }

   public int getActionCharge() {
      return this.actionCharge;
   }

   public int getAlignment() {
      return this.alignment;
   }

   public float getTrainingPoints() {
      return this.trainingPoints;
   }

   public int getPendingAttributePoints() {
      return this.pendingAttributePoints;
   }

   public int getRacialSkillCount() {
      return this.racialSkillCount;
   }

   public Player getPlayer() {
      return this.player;
   }

   public StatsData getStatsData() {
      return this.statsData;
   }

   public void setRelease(int release) {
      this.release = release;
   }

   public void setPlayer(Player player) {
      this.player = player;
   }

   public void setStatsData(StatsData statsData) {
      this.statsData = statsData;
   }
}
