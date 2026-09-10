package com.dragonminez.common.combat.clash;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import lombok.Generated;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public class ClashParticipant {
   private final AbstractKiProjectile beam;
   private final LivingEntity owner;
   private final boolean npc;
   private final boolean wasNoAi;
   private final double statPower;
   private final float npcAccuracy;
   private static final int TIMING_SAMPLE_COUNT = 6;
   private static final float BOT_SPREAD_THRESHOLD = 0.02F;
   private static final float BOT_DAMPEN = 0.4F;
   private final float[] recentPressPhases;
   private int pressSampleCount;
   private int pressWriteIndex;
   private float meterPhase;
   private float prevMeterPhase;
   private float momentum;
   private int idleTicks;

   public ClashParticipant(AbstractKiProjectile beam, LivingEntity owner) {
      boolean var10001;
      label16: {
         super();
         this.recentPressPhases = new float[6];
         this.beam = beam;
         this.owner = owner;
         this.npc = !(owner instanceof ServerPlayer);
         if (owner instanceof Mob mob && mob.isNoAi()) {
            var10001 = true;
            break label16;
         }

         var10001 = false;
      }

      this.wasNoAi = var10001;
      this.statPower = Math.max(1.0, (double)beam.getKiDamage());
      this.npcAccuracy = resolveNpcAccuracy(this.statPower, this.npc);
      this.meterPhase = owner.getRandom().nextFloat();
      this.prevMeterPhase = this.meterPhase;
   }

   private static float resolveNpcAccuracy(double attackPower, boolean npc) {
      if (!npc) {
         return 0.0F;
      } else {
         double t = Math.log10(attackPower + 1.0) / 3.0;
         return (float)Math.min(0.92, 0.45 + t * 0.45);
      }
   }

   public AbstractKiProjectile beam() {
      return this.beam;
   }

   public LivingEntity owner() {
      return this.owner;
   }

   public double statPower() {
      return this.statPower;
   }

   public float meterPhase() {
      return this.meterPhase;
   }

   public float momentum() {
      return this.momentum;
   }

   public int idleTicks() {
      return this.idleTicks;
   }

   public Vec3 origin() {
      return this.beam.position();
   }

   public Vec3 direction() {
      return Vec3.directionFromRotation(this.beam.getClashPitch(), this.beam.getClashYaw());
   }

   public boolean isStillFiring() {
      return this.owner.isAlive() && !this.beam.isRemoved() && this.beam.isClashableBeam();
   }

   public void freezeOwner() {
      if (this.owner instanceof Mob mob) {
         if (!mob.isNoAi()) {
            mob.setNoAi(true);
         }

         mob.getNavigation().stop();
         mob.setDeltaMovement(0.0, 0.0, 0.0);
         mob.hasImpulse = true;
      }
   }

   public void unfreezeOwner() {
      if (this.owner instanceof Mob mob) {
         mob.setNoAi(this.wasNoAi);
      }
   }

   public boolean tickMeter() {
      this.prevMeterPhase = this.meterPhase;
      this.meterPhase += 0.01F;
      this.momentum *= 0.96F;
      this.idleTicks++;
      boolean wrapped = false;
      if (this.meterPhase >= 1.0F) {
         this.meterPhase--;
         wrapped = true;
      }

      if (this.npc) {
         this.tickNpcPress();
      }

      return wrapped;
   }

   private void tickNpcPress() {
      float center = 0.87F;
      boolean crossedCenter = this.prevMeterPhase < center && this.meterPhase >= center && this.meterPhase >= this.prevMeterPhase;
      if (crossedCenter) {
         float jitter = 0.7F + this.owner.getRandom().nextFloat() * 0.3F;
         this.addBurst(this.npcAccuracy * jitter);
         this.idleTicks = 0;
      }
   }

   public void registerPlayerPress() {
      float phaseAtPress = this.meterPhase;
      float efficiency = scoreEfficiency(phaseAtPress) * this.botConsistencyPenalty(phaseAtPress);
      this.addBurst(efficiency);
      this.idleTicks = 0;
      this.meterPhase = 0.0F;
      this.prevMeterPhase = 0.0F;
   }

   private float botConsistencyPenalty(float phaseAtPress) {
      this.recentPressPhases[this.pressWriteIndex] = phaseAtPress;
      this.pressWriteIndex = (this.pressWriteIndex + 1) % 6;
      if (this.pressSampleCount < 6) {
         this.pressSampleCount++;
         return 1.0F;
      } else {
         float min = Float.MAX_VALUE;
         float max = -Float.MAX_VALUE;

         for (float p : this.recentPressPhases) {
            if (p < 0.78F || p > 0.96F) {
               return 1.0F;
            }

            min = Math.min(min, p);
            max = Math.max(max, p);
         }

         return max - min < 0.02F ? 0.4F : 1.0F;
      }
   }

   public static float scoreEfficiency(float phase) {
      if (!(phase < 0.78F) && !(phase > 0.96F)) {
         float center = 0.87F;
         float half = 0.09F;
         float closeness = 1.0F - Math.abs(phase - center) / half;
         return 0.18F + 0.82F * Math.max(0.0F, closeness);
      } else {
         return 0.18F;
      }
   }

   private void addBurst(float efficiency) {
      this.momentum += efficiency * 0.6F;
   }

   @Generated
   public boolean isNpc() {
      return this.npc;
   }
}
