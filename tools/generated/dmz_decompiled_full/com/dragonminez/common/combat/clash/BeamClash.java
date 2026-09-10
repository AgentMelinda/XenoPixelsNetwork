package com.dragonminez.common.combat.clash;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import java.util.UUID;
import lombok.Generated;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class BeamClash {
   public static final float SWEEP_RATE = 0.01F;
   public static final float SWEET_LOW = 0.78F;
   public static final float SWEET_HIGH = 0.96F;
   public static final float OFF_SPOT_EFFICIENCY = 0.18F;
   public static final float BURST_PER_PERFECT_PRESS = 0.6F;
   public static final float MOMENTUM_DECAY = 0.96F;
   private static final float DRIFT_PER_TICK = 0.005F;
   private static final float STR_FLOOR = 0.6F;
   private static final float STR_SPAN = 0.95F;
   private static final float MIN_TRACTION = 0.35F;
   private static final float WIN_THRESHOLD = 0.8F;
   private static final int IDLE_DISSOLVE_TICKS = 100;
   private static final int MAX_DURATION = 600;
   private static final int WINNER_BREAKTHROUGH_TICKS = 60;
   private final ClashParticipant a;
   private final ClashParticipant b;
   private float biasT;
   private int age = 0;
   private boolean ended = false;

   public BeamClash(ClashParticipant a, ClashParticipant b) {
      this.a = a;
      this.b = b;
      this.biasT = 0.5F;
   }

   public ClashParticipant a() {
      return this.a;
   }

   public ClashParticipant b() {
      return this.b;
   }

   public boolean involves(AbstractKiProjectile beam) {
      return this.a.beam() == beam || this.b.beam() == beam;
   }

   public boolean involvesOwner(UUID ownerId) {
      return this.a.owner().getUUID().equals(ownerId) || this.b.owner().getUUID().equals(ownerId);
   }

   public ClashParticipant participantFor(UUID ownerId) {
      if (this.a.owner().getUUID().equals(ownerId)) {
         return this.a;
      } else {
         return this.b.owner().getUUID().equals(ownerId) ? this.b : null;
      }
   }

   public BeamClash.Result tick() {
      if (this.ended) {
         return BeamClash.Result.DISSOLVED;
      } else if (this.a.isStillFiring() && this.b.isStillFiring()) {
         this.age++;
         this.a.freezeOwner();
         this.b.freezeOwner();
         this.a.tickMeter();
         this.b.tickMeter();
         double pa = this.a.statPower();
         double pb = this.b.statPower();
         float strengthA = (float)(pa / (pa + pb));
         float strengthB = 1.0F - strengthA;
         float tracA = Mth.clamp(2.0F * this.biasT, 0.35F, 1.0F);
         float tracB = Mth.clamp(2.0F * (1.0F - this.biasT), 0.35F, 1.0F);
         float pushA = this.a.momentum() * (0.6F + 0.95F * strengthA) * tracA;
         float pushB = this.b.momentum() * (0.6F + 0.95F * strengthB) * tracB;
         this.biasT += 0.005F * (pushA - pushB);
         this.biasT = Mth.clamp(this.biasT, 0.0F, 1.0F);
         this.applyLock();
         if (this.biasT >= 0.8F) {
            return BeamClash.Result.A_WINS;
         } else if (this.biasT <= 0.19999999F) {
            return BeamClash.Result.B_WINS;
         } else if (Math.min(this.a.idleTicks(), this.b.idleTicks()) >= 100) {
            return BeamClash.Result.DISSOLVED;
         } else if (this.age < 600) {
            return BeamClash.Result.ONGOING;
         } else if (this.biasT > 0.5F) {
            return BeamClash.Result.A_WINS;
         } else {
            return this.biasT < 0.5F ? BeamClash.Result.B_WINS : BeamClash.Result.DISSOLVED;
         }
      } else {
         return BeamClash.Result.DISSOLVED;
      }
   }

   private void applyLock() {
      Vec3 originA = this.a.origin();
      Vec3 originB = this.b.origin();
      double gap = originA.distanceTo(originB);
      float lockA = (float)(gap * (double)this.biasT);
      float lockB = (float)(gap * (double)(1.0F - this.biasT));
      this.a.beam().setClashLock(lockA, this.b.owner().getUUID());
      this.b.beam().setClashLock(lockB, this.a.owner().getUUID());
      keepAlive(this.a.beam());
      keepAlive(this.b.beam());
   }

   private static void keepAlive(AbstractKiProjectile beam) {
      beam.setMaxLife(beam.tickCount + 40);
   }

   public void resolve(BeamClash.Result result) {
      if (!this.ended) {
         this.ended = true;
         ClashParticipant winner = result == BeamClash.Result.A_WINS ? this.a : this.b;
         ClashParticipant loser = result == BeamClash.Result.A_WINS ? this.b : this.a;
         winner.beam().clearClashLock();
         winner.beam().setMaxLife(winner.beam().tickCount + 60);
         loser.beam().clearClashLock();
         if (!loser.beam().isRemoved()) {
            loser.beam().discard();
         }

         winner.unfreezeOwner();
         loser.unfreezeOwner();
      }
   }

   public void dissolve() {
      if (!this.ended) {
         this.ended = true;
         this.a.beam().clearClashLock();
         this.b.beam().clearClashLock();
         this.a.unfreezeOwner();
         this.b.unfreezeOwner();
      }
   }

   public float advantageFor(LivingEntity owner) {
      return owner.getUUID().equals(this.a.owner().getUUID()) ? this.biasT : 1.0F - this.biasT;
   }

   @Generated
   public boolean isEnded() {
      return this.ended;
   }

   public static enum Result {
      ONGOING,
      A_WINS,
      B_WINS,
      DISSOLVED;
   }
}
