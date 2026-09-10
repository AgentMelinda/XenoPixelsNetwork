package com.dragonminez.server.world.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.Generated;
import net.minecraft.world.entity.EntityType;

public class RaidWave {
   private final List<RaidWave.SpawnEntry> spawns;
   private final double healthMultiplier;
   private final double damageMultiplier;
   private final boolean bossWave;

   private RaidWave(List<RaidWave.SpawnEntry> spawns, double healthMultiplier, double damageMultiplier, boolean bossWave) {
      this.spawns = spawns;
      this.healthMultiplier = healthMultiplier;
      this.damageMultiplier = damageMultiplier;
      this.bossWave = bossWave;
   }

   public int totalMobCount() {
      int total = 0;

      for (RaidWave.SpawnEntry entry : this.spawns) {
         total += entry.count();
      }

      return total;
   }

   public static RaidWave.Builder builder() {
      return new RaidWave.Builder();
   }

   @Generated
   public List<RaidWave.SpawnEntry> getSpawns() {
      return this.spawns;
   }

   @Generated
   public double getHealthMultiplier() {
      return this.healthMultiplier;
   }

   @Generated
   public double getDamageMultiplier() {
      return this.damageMultiplier;
   }

   @Generated
   public boolean isBossWave() {
      return this.bossWave;
   }

   public static class Builder {
      private final List<RaidWave.SpawnEntry> spawns = new ArrayList<>();
      private double healthMultiplier = 1.0;
      private double damageMultiplier = 1.0;
      private boolean bossWave = false;

      public RaidWave.Builder add(Supplier<? extends EntityType<?>> type, int count) {
         this.spawns.add(new RaidWave.SpawnEntry(type, count));
         return this;
      }

      public RaidWave.Builder health(double multiplier) {
         this.healthMultiplier = multiplier;
         return this;
      }

      public RaidWave.Builder damage(double multiplier) {
         this.damageMultiplier = multiplier;
         return this;
      }

      public RaidWave.Builder boss(boolean bossWave) {
         this.bossWave = bossWave;
         return this;
      }

      public RaidWave build() {
         if (this.spawns.isEmpty()) {
            throw new IllegalStateException("A raid wave must contain at least one spawn entry");
         } else {
            return new RaidWave(List.copyOf(this.spawns), this.healthMultiplier, this.damageMultiplier, this.bossWave);
         }
      }
   }

   public static record SpawnEntry(Supplier<? extends EntityType<?>> type, int count) {
   }
}
