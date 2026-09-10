package com.dragonminez.common.config;

import lombok.Generated;

public class TrainingConfig {
   public static final String CURRENT_VERSION = "2.1.3";
   private String configVersion;
   private double rewardBaseCoefficient = 3.4;
   private double rewardCostExponent = 0.6;
   private TrainingConfig.RhythmConfig rhythm = new TrainingConfig.RhythmConfig();
   private TrainingConfig.ControlConfig control = new TrainingConfig.ControlConfig();
   private TrainingConfig.MemoryConfig memory = new TrainingConfig.MemoryConfig();
   private TrainingConfig.PrecisionConfig precision = new TrainingConfig.PrecisionConfig();
   private TrainingConfig.GravityConfig gravity = new TrainingConfig.GravityConfig();

   public float computeTpsPerLevel(int singleStatCost, TrainingConfig.MinigameSettings settings) {
      double tpc = Math.max(1.0, (double)singleStatCost);
      double perLevel = this.rewardBaseCoefficient * Math.pow(tpc, this.rewardCostExponent);
      double multiplier = settings != null ? settings.getRewardMultiplier() : 1.0;
      return (float)Math.max(1.0, perLevel * multiplier);
   }

   public TrainingConfig.MinigameSettings getSettings(String minigameId) {
      if (minigameId == null) {
         return this.rhythm;
      } else {
         String var2 = minigameId.toLowerCase();

         return (TrainingConfig.MinigameSettings)(switch (var2) {
            case "control" -> this.control;
            case "memory" -> this.memory;
            case "precision" -> this.precision;
            case "gravity" -> this.gravity;
            default -> this.rhythm;
         });
      }
   }

   @Generated
   public String getConfigVersion() {
      return this.configVersion;
   }

   @Generated
   public double getRewardBaseCoefficient() {
      return this.rewardBaseCoefficient;
   }

   @Generated
   public double getRewardCostExponent() {
      return this.rewardCostExponent;
   }

   @Generated
   public TrainingConfig.RhythmConfig getRhythm() {
      return this.rhythm;
   }

   @Generated
   public TrainingConfig.ControlConfig getControl() {
      return this.control;
   }

   @Generated
   public TrainingConfig.MemoryConfig getMemory() {
      return this.memory;
   }

   @Generated
   public TrainingConfig.PrecisionConfig getPrecision() {
      return this.precision;
   }

   @Generated
   public TrainingConfig.GravityConfig getGravity() {
      return this.gravity;
   }

   @Generated
   public void setConfigVersion(String configVersion) {
      this.configVersion = configVersion;
   }

   public static class ControlConfig extends TrainingConfig.MinigameSettings {
      private int holdDurationTicks;
      private int levelTimeLimitTicks;
      private int barWidth;
      private int baseZoneWidth;
      private int zoneWidthDecreasePerLevel;
      private int minZoneWidth;
      private double baseZoneSpeed;
      private double zoneSpeedPerLevel;
      private double markerSpeed;
      private double baseProgressLossPerTick;
      private double progressLossPerLevel;

      @Generated
      public int getHoldDurationTicks() {
         return this.holdDurationTicks;
      }

      @Generated
      public int getLevelTimeLimitTicks() {
         return this.levelTimeLimitTicks;
      }

      @Generated
      public int getBarWidth() {
         return this.barWidth;
      }

      @Generated
      public int getBaseZoneWidth() {
         return this.baseZoneWidth;
      }

      @Generated
      public int getZoneWidthDecreasePerLevel() {
         return this.zoneWidthDecreasePerLevel;
      }

      @Generated
      public int getMinZoneWidth() {
         return this.minZoneWidth;
      }

      @Generated
      public double getBaseZoneSpeed() {
         return this.baseZoneSpeed;
      }

      @Generated
      public double getZoneSpeedPerLevel() {
         return this.zoneSpeedPerLevel;
      }

      @Generated
      public double getMarkerSpeed() {
         return this.markerSpeed;
      }

      @Generated
      public double getBaseProgressLossPerTick() {
         return this.baseProgressLossPerTick;
      }

      @Generated
      public double getProgressLossPerLevel() {
         return this.progressLossPerLevel;
      }

      @Generated
      public ControlConfig() {
         this.masterName = "krillin";
         this.holdDurationTicks = 100;
         this.levelTimeLimitTicks = 300;
         this.barWidth = 200;
         this.baseZoneWidth = 60;
         this.zoneWidthDecreasePerLevel = 6;
         this.minZoneWidth = 24;
         this.baseZoneSpeed = 1.2;
         this.zoneSpeedPerLevel = 0.15;
         this.markerSpeed = 2.75;
         this.baseProgressLossPerTick = 0.12;
         this.progressLossPerLevel = 0.12;
      }
   }

   public static class GravityConfig extends TrainingConfig.MinigameSettings {
      private int holdDurationTicks;
      private int barHeight;
      private double controlLineFraction;
      private double baseGravity;
      private double gravityPerLevel;
      private double risePerTap;
      private double progressLossPerTick;
      private double wrongPressDescentMultiplier;

      @Generated
      public int getHoldDurationTicks() {
         return this.holdDurationTicks;
      }

      @Generated
      public int getBarHeight() {
         return this.barHeight;
      }

      @Generated
      public double getControlLineFraction() {
         return this.controlLineFraction;
      }

      @Generated
      public double getBaseGravity() {
         return this.baseGravity;
      }

      @Generated
      public double getGravityPerLevel() {
         return this.gravityPerLevel;
      }

      @Generated
      public double getRisePerTap() {
         return this.risePerTap;
      }

      @Generated
      public double getProgressLossPerTick() {
         return this.progressLossPerTick;
      }

      @Generated
      public double getWrongPressDescentMultiplier() {
         return this.wrongPressDescentMultiplier;
      }

      @Generated
      public GravityConfig() {
         this.masterName = "vegeta";
         this.holdDurationTicks = 200;
         this.barHeight = 180;
         this.controlLineFraction = 0.45;
         this.baseGravity = 1.1;
         this.gravityPerLevel = 0.13;
         this.risePerTap = 9.0;
         this.progressLossPerTick = 2.5;
         this.wrongPressDescentMultiplier = 3.0;
      }
   }

   public static class MemoryConfig extends TrainingConfig.MinigameSettings {
      private int baseSequenceLength;
      private int sequenceLengthPerLevel;
      private int baseShowTicks;
      private int showTicksDecreasePerLevel;
      private int minShowTicks;

      @Generated
      public int getBaseSequenceLength() {
         return this.baseSequenceLength;
      }

      @Generated
      public int getSequenceLengthPerLevel() {
         return this.sequenceLengthPerLevel;
      }

      @Generated
      public int getBaseShowTicks() {
         return this.baseShowTicks;
      }

      @Generated
      public int getShowTicksDecreasePerLevel() {
         return this.showTicksDecreasePerLevel;
      }

      @Generated
      public int getMinShowTicks() {
         return this.minShowTicks;
      }

      @Generated
      public MemoryConfig() {
         this.masterName = "gohan";
         this.baseSequenceLength = 3;
         this.sequenceLengthPerLevel = 1;
         this.baseShowTicks = 42;
         this.showTicksDecreasePerLevel = 4;
         this.minShowTicks = 16;
      }
   }

   public static class MinigameSettings {
      protected double rewardMultiplier = 1.0;
      protected float tpsLimitPerGame = 50000.0F;
      protected boolean unlockedByDefault = false;
      protected String masterName = "a master";

      @Generated
      public double getRewardMultiplier() {
         return this.rewardMultiplier;
      }

      @Generated
      public float getTpsLimitPerGame() {
         return this.tpsLimitPerGame;
      }

      @Generated
      public boolean isUnlockedByDefault() {
         return this.unlockedByDefault;
      }

      @Generated
      public String getMasterName() {
         return this.masterName;
      }
   }

   public static class PrecisionConfig extends TrainingConfig.MinigameSettings {
      private int outerRingRadius;
      private int targetRadius;
      private double baseRingSpeed;
      private double ringSpeedPerLevel;
      private int spawnIntervalTicks;
      private int maxCircles;
      private int perfectWindow;
      private int goodWindow;
      private int perfectPoints;
      private int goodPoints;
      private int missPenalty;
      private int fadeOutTicks;
      private int startingScore;
      private double burstChance;
      private int levelUpScoreBase;
      private int levelUpScorePerLevel;
      private int loseMissThreshold;
      private int loseMissWindow;

      @Generated
      public int getOuterRingRadius() {
         return this.outerRingRadius;
      }

      @Generated
      public int getTargetRadius() {
         return this.targetRadius;
      }

      @Generated
      public double getBaseRingSpeed() {
         return this.baseRingSpeed;
      }

      @Generated
      public double getRingSpeedPerLevel() {
         return this.ringSpeedPerLevel;
      }

      @Generated
      public int getSpawnIntervalTicks() {
         return this.spawnIntervalTicks;
      }

      @Generated
      public int getMaxCircles() {
         return this.maxCircles;
      }

      @Generated
      public int getPerfectWindow() {
         return this.perfectWindow;
      }

      @Generated
      public int getGoodWindow() {
         return this.goodWindow;
      }

      @Generated
      public int getPerfectPoints() {
         return this.perfectPoints;
      }

      @Generated
      public int getGoodPoints() {
         return this.goodPoints;
      }

      @Generated
      public int getMissPenalty() {
         return this.missPenalty;
      }

      @Generated
      public int getFadeOutTicks() {
         return this.fadeOutTicks;
      }

      @Generated
      public int getStartingScore() {
         return this.startingScore;
      }

      @Generated
      public double getBurstChance() {
         return this.burstChance;
      }

      @Generated
      public int getLevelUpScoreBase() {
         return this.levelUpScoreBase;
      }

      @Generated
      public int getLevelUpScorePerLevel() {
         return this.levelUpScorePerLevel;
      }

      @Generated
      public int getLoseMissThreshold() {
         return this.loseMissThreshold;
      }

      @Generated
      public int getLoseMissWindow() {
         return this.loseMissWindow;
      }

      @Generated
      public PrecisionConfig() {
         this.masterName = "trunks";
         this.outerRingRadius = 40;
         this.targetRadius = 14;
         this.baseRingSpeed = 0.7;
         this.ringSpeedPerLevel = 0.18;
         this.spawnIntervalTicks = 12;
         this.maxCircles = 3;
         this.perfectWindow = 4;
         this.goodWindow = 11;
         this.perfectPoints = 2;
         this.goodPoints = 1;
         this.missPenalty = 2;
         this.fadeOutTicks = 20;
         this.startingScore = 6;
         this.burstChance = 0.5;
         this.levelUpScoreBase = 10;
         this.levelUpScorePerLevel = 8;
         this.loseMissThreshold = 5;
         this.loseMissWindow = 10;
      }
   }

   public static class RhythmConfig extends TrainingConfig.MinigameSettings {
      private double baseNoteSpeed;
      private double noteSpeedPerLevel;
      private int noteTravelDistance;
      private float arrowScale;
      private int baseSpawnIntervalTicks;
      private int minSpawnIntervalTicks;
      private int spawnIntervalDecreasePerLevel;
      private int perfectWindow;
      private int goodWindow;
      private double progressMax;
      private double progressOnLevelUp;
      private double progressGainPerfect;
      private double progressGainGood;
      private double progressGainHold;
      private double progressDecayPerTick;
      private double progressLossOnMiss;
      private int loseMissThreshold;
      private int loseMissWindow;
      private double holdNoteChance;
      private int holdDurationTicks;
      private double doubleNoteChance;
      private int doubleNoteGap;

      @Generated
      public double getBaseNoteSpeed() {
         return this.baseNoteSpeed;
      }

      @Generated
      public double getNoteSpeedPerLevel() {
         return this.noteSpeedPerLevel;
      }

      @Generated
      public int getNoteTravelDistance() {
         return this.noteTravelDistance;
      }

      @Generated
      public float getArrowScale() {
         return this.arrowScale;
      }

      @Generated
      public int getBaseSpawnIntervalTicks() {
         return this.baseSpawnIntervalTicks;
      }

      @Generated
      public int getMinSpawnIntervalTicks() {
         return this.minSpawnIntervalTicks;
      }

      @Generated
      public int getSpawnIntervalDecreasePerLevel() {
         return this.spawnIntervalDecreasePerLevel;
      }

      @Generated
      public int getPerfectWindow() {
         return this.perfectWindow;
      }

      @Generated
      public int getGoodWindow() {
         return this.goodWindow;
      }

      @Generated
      public double getProgressMax() {
         return this.progressMax;
      }

      @Generated
      public double getProgressOnLevelUp() {
         return this.progressOnLevelUp;
      }

      @Generated
      public double getProgressGainPerfect() {
         return this.progressGainPerfect;
      }

      @Generated
      public double getProgressGainGood() {
         return this.progressGainGood;
      }

      @Generated
      public double getProgressGainHold() {
         return this.progressGainHold;
      }

      @Generated
      public double getProgressDecayPerTick() {
         return this.progressDecayPerTick;
      }

      @Generated
      public double getProgressLossOnMiss() {
         return this.progressLossOnMiss;
      }

      @Generated
      public int getLoseMissThreshold() {
         return this.loseMissThreshold;
      }

      @Generated
      public int getLoseMissWindow() {
         return this.loseMissWindow;
      }

      @Generated
      public double getHoldNoteChance() {
         return this.holdNoteChance;
      }

      @Generated
      public int getHoldDurationTicks() {
         return this.holdDurationTicks;
      }

      @Generated
      public double getDoubleNoteChance() {
         return this.doubleNoteChance;
      }

      @Generated
      public int getDoubleNoteGap() {
         return this.doubleNoteGap;
      }

      @Generated
      public RhythmConfig() {
         this.masterName = "popo";
         this.baseNoteSpeed = 4.0;
         this.noteSpeedPerLevel = 0.5;
         this.noteTravelDistance = 150;
         this.arrowScale = 2.0F;
         this.baseSpawnIntervalTicks = 20;
         this.minSpawnIntervalTicks = 8;
         this.spawnIntervalDecreasePerLevel = 2;
         this.perfectWindow = 14;
         this.goodWindow = 30;
         this.progressMax = 100.0;
         this.progressOnLevelUp = 30.0;
         this.progressGainPerfect = 18.0;
         this.progressGainGood = 12.0;
         this.progressGainHold = 22.0;
         this.progressDecayPerTick = 0.28;
         this.progressLossOnMiss = 10.0;
         this.loseMissThreshold = 5;
         this.loseMissWindow = 10;
         this.holdNoteChance = 0.18;
         this.holdDurationTicks = 24;
         this.doubleNoteChance = 0.12;
         this.doubleNoteGap = 26;
      }
   }
}
