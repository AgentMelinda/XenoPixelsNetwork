package com.dragonminez.server.world.raid;

import java.util.ArrayList;
import java.util.List;
import lombok.Generated;
import net.minecraft.network.chat.Component;

public class RaidType {
   private final String id;
   private final Component displayName;
   private final List<RaidWave> waves;
   private final double activationRadius;
   private final double leashDistance;
   private final int interWaveDelayTicks;
   private final RaidReward reward;

   private RaidType(RaidType.Builder builder) {
      this.id = builder.id;
      this.displayName = builder.displayName;
      this.waves = builder.waves;
      this.activationRadius = builder.activationRadius;
      this.leashDistance = builder.leashDistance;
      this.interWaveDelayTicks = builder.interWaveDelayTicks;
      this.reward = builder.reward;
   }

   public int waveCount() {
      return this.waves.size();
   }

   public RaidWave wave(int index) {
      return this.waves.get(index);
   }

   public boolean isFinalWave(int index) {
      return index == this.waves.size() - 1;
   }

   public static RaidType.Builder builder(String id) {
      return new RaidType.Builder(id);
   }

   @Generated
   public String getId() {
      return this.id;
   }

   @Generated
   public Component getDisplayName() {
      return this.displayName;
   }

   @Generated
   public List<RaidWave> getWaves() {
      return this.waves;
   }

   @Generated
   public double getActivationRadius() {
      return this.activationRadius;
   }

   @Generated
   public double getLeashDistance() {
      return this.leashDistance;
   }

   @Generated
   public int getInterWaveDelayTicks() {
      return this.interWaveDelayTicks;
   }

   @Generated
   public RaidReward getReward() {
      return this.reward;
   }

   public static class Builder {
      private final String id;
      private Component displayName;
      private final List<RaidWave> waves = new ArrayList<>();
      private double activationRadius = 48.0;
      private double leashDistance = 64.0;
      private int interWaveDelayTicks = 100;
      private RaidReward reward = RaidReward.builder().build();

      private Builder(String id) {
         this.id = id;
         this.displayName = Component.literal(id);
      }

      public RaidType.Builder name(Component displayName) {
         this.displayName = displayName;
         return this;
      }

      public RaidType.Builder wave(RaidWave wave) {
         this.waves.add(wave);
         return this;
      }

      public RaidType.Builder activationRadius(double radius) {
         this.activationRadius = radius;
         return this;
      }

      public RaidType.Builder leashDistance(double distance) {
         this.leashDistance = distance;
         return this;
      }

      public RaidType.Builder interWaveDelay(int ticks) {
         this.interWaveDelayTicks = ticks;
         return this;
      }

      public RaidType.Builder reward(RaidReward reward) {
         this.reward = reward;
         return this;
      }

      public RaidType build() {
         if (this.waves.isEmpty()) {
            throw new IllegalStateException("Raid type '" + this.id + "' must have at least one wave");
         } else {
            return new RaidType(this);
         }
      }
   }
}
