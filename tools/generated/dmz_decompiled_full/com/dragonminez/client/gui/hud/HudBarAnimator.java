package com.dragonminez.client.gui.hud;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.util.Mth;

public class HudBarAnimator {
   private static final float FRONT_TAU = 0.55F;
   private static final float GHOST_TAU = 0.32F;
   private static final float DAMAGE_HOLD_SECONDS = 1.5F;
   private static final float HEAL_HOLD_SECONDS = 0.25F;
   private static final float BURST_HEAL_THRESHOLD = 0.12F;
   private static final float DAMAGE_THRESHOLD = 0.01F;
   private static final float MIN_GAP = 0.0015F;
   private static final float MAX_FRAME_SECONDS = 0.25F;
   private final List<HudBarAnimator.Segment> segments = new ArrayList<>();
   private boolean initialized;
   private float target;
   private float front;
   private float healGhost;
   private float healHold;
   private boolean healing;
   private long lastNanos;

   public void reset(float fraction) {
      fraction = Mth.clamp(fraction, 0.0F, 1.0F);
      this.initialized = true;
      this.target = this.front = fraction;
      this.healGhost = fraction;
      this.healHold = 0.0F;
      this.healing = false;
      this.segments.clear();
      this.lastNanos = System.nanoTime();
   }

   public void update(float fraction) {
      fraction = Mth.clamp(fraction, 0.0F, 1.0F);
      if (!this.initialized) {
         this.reset(fraction);
      } else {
         long now = System.nanoTime();
         float dt = (float)(now - this.lastNanos) / 1.0E9F;
         this.lastNanos = now;
         if (!(dt <= 0.0F)) {
            if (dt > 0.25F) {
               dt = 0.25F;
            }

            float previous = this.target;
            if (previous - fraction >= 0.01F) {
               this.segments.add(new HudBarAnimator.Segment(previous - fraction));
               this.front = fraction;
               this.healing = false;
            } else if (fraction - previous >= 0.12F) {
               this.segments.clear();
               this.healing = true;
               this.healGhost = fraction;
               this.healHold = 0.25F;
            }

            this.target = fraction;
            float frontBlend = 1.0F - (float)Math.exp((double)(-dt / 0.55F));
            float ghostBlend = 1.0F - (float)Math.exp((double)(-dt / 0.32F));
            float previousFront = this.front;
            boolean frontFrozen = this.healing && this.healHold > 0.0F;
            if (!frontFrozen) {
               this.front = this.front + (this.target - this.front) * frontBlend;
            }

            if (this.healing) {
               if (this.healHold > 0.0F) {
                  this.healHold -= dt;
               }

               this.healGhost = this.target;
               if (this.front >= this.healGhost - 0.0015F) {
                  this.front = this.healGhost;
                  this.healing = false;
               }
            } else if (!this.segments.isEmpty()) {
               float frontRise = this.front - previousFront;
               if (frontRise > 0.0F) {
                  this.consumeFromBottom(frontRise);
               }

               Iterator<HudBarAnimator.Segment> it = this.segments.iterator();

               while (it.hasNext()) {
                  HudBarAnimator.Segment segment = it.next();
                  segment.age += dt;
                  if (segment.age >= 1.5F) {
                     segment.amount = segment.amount - segment.amount * ghostBlend;
                     if (segment.amount <= 0.0015F) {
                        it.remove();
                     }
                  }
               }
            }
         }
      }
   }

   private void consumeFromBottom(float amount) {
      for (int i = this.segments.size() - 1; i >= 0 && amount > 0.0F; i--) {
         HudBarAnimator.Segment segment = this.segments.get(i);
         float take = Math.min(segment.amount, amount);
         segment.amount -= take;
         amount -= take;
         if (segment.amount <= 0.0015F) {
            this.segments.remove(i);
         }
      }
   }

   public float frontFraction() {
      return this.front;
   }

   public float ghostFraction() {
      if (this.healing) {
         return this.healGhost;
      } else {
         float total = 0.0F;

         for (HudBarAnimator.Segment segment : this.segments) {
            total += segment.amount;
         }

         return Mth.clamp(this.front + total, 0.0F, 1.0F);
      }
   }

   public HudBarAnimator.GapType gapType() {
      if (this.healing) {
         return HudBarAnimator.GapType.HEAL;
      } else {
         return this.segments.isEmpty() ? HudBarAnimator.GapType.NONE : HudBarAnimator.GapType.DAMAGE;
      }
   }

   public static enum GapType {
      NONE,
      DAMAGE,
      HEAL;
   }

   private static final class Segment {
      private float amount;
      private float age;

      private Segment(float amount) {
         this.amount = amount;
      }
   }
}
