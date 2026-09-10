package com.dragonminez.client.gui.hud;

import com.dragonminez.common.config.ConfigManager;

public class HudStatNumberAnimator {
   public static final int WHITE_RGB = 16777215;
   private static final int INCREASE_RGB = 5635925;
   private static final int DAMAGE_RGB = 16733525;
   private static final float VISIBLE_HOLD_TICKS = 50.0F;
   private static final float FADE_OUT_TICKS = 20.0F;
   private static final float FADE_IN_TICKS = 5.0F;
   private static final float PULSE_TICKS = 14.0F;
   private static final float MAX_ELAPSED_TICKS = 5.0F;
   private static final float CHANGE_EPSILON = 0.001F;
   private static final float MIN_CHANGE_ALPHA = 0.35F;
   private final HudStatNumberAnimator.StatKind statKind;
   private boolean initialized;
   private String lastText = "";
   private float lastValue;
   private float lastChangeTick;
   private float lastUpdateTick;
   private float pulseStartTick = -14.0F;
   private HudStatNumberAnimator.ChangeDirection pulseDirection = HudStatNumberAnimator.ChangeDirection.NONE;
   private float alpha = 1.0F;

   public HudStatNumberAnimator(HudStatNumberAnimator.StatKind statKind) {
      this.statKind = statKind;
   }

   public HudStatNumberAnimator.RenderState update(String text, float value, float tickTime) {
      if (this.statKind != HudStatNumberAnimator.StatKind.KISENSE_HEALTH && ConfigManager.getUserConfig().getHideHudNumbers()) {
         return new HudStatNumberAnimator.RenderState(0.0F, 16777215, 0.0F, 0.0F);
      } else if (!this.initialized) {
         this.initialized = true;
         this.lastText = text;
         this.lastValue = value;
         this.lastChangeTick = tickTime;
         this.lastUpdateTick = tickTime;
         this.alpha = 1.0F;
         return this.renderState(tickTime);
      } else {
         if (tickTime < this.lastUpdateTick) {
            this.lastChangeTick = tickTime;
            this.lastUpdateTick = tickTime;
            this.pulseDirection = HudStatNumberAnimator.ChangeDirection.NONE;
            this.alpha = 1.0F;
         }

         float elapsed = clamp(tickTime - this.lastUpdateTick, 0.0F, 5.0F);
         this.lastUpdateTick = Math.max(this.lastUpdateTick, tickTime);
         HudStatNumberAnimator.ChangeDirection direction = this.getChangeDirection(text, value);
         if (direction != HudStatNumberAnimator.ChangeDirection.NONE || !text.equals(this.lastText)) {
            this.lastText = text;
            this.lastValue = value;
            this.lastChangeTick = tickTime;
            this.alpha = Math.max(this.alpha, 0.35F);
            if (direction != HudStatNumberAnimator.ChangeDirection.UP && !this.shouldPulseDamage(direction)) {
               this.pulseDirection = HudStatNumberAnimator.ChangeDirection.NONE;
            } else {
               this.pulseDirection = direction;
               this.pulseStartTick = tickTime;
            }
         }

         float targetAlpha = 1.0F;
         if (this.statKind != HudStatNumberAnimator.StatKind.KISENSE_HEALTH && !ConfigManager.getUserConfig().getAlwaysVisibleHudValues()) {
            targetAlpha = tickTime - this.lastChangeTick <= 50.0F ? 1.0F : 0.0F;
         }

         float rate = targetAlpha > this.alpha ? elapsed / 5.0F : elapsed / 20.0F;
         this.alpha = approach(this.alpha, targetAlpha, rate);
         return this.renderState(tickTime);
      }
   }

   private boolean shouldPulseDamage(HudStatNumberAnimator.ChangeDirection direction) {
      return (this.statKind == HudStatNumberAnimator.StatKind.HEALTH || this.statKind == HudStatNumberAnimator.StatKind.KISENSE_HEALTH)
         && direction == HudStatNumberAnimator.ChangeDirection.DOWN;
   }

   private HudStatNumberAnimator.ChangeDirection getChangeDirection(String text, float value) {
      if (Math.abs(value - this.lastValue) <= 0.001F) {
         return HudStatNumberAnimator.ChangeDirection.NONE;
      } else {
         return value > this.lastValue ? HudStatNumberAnimator.ChangeDirection.UP : HudStatNumberAnimator.ChangeDirection.DOWN;
      }
   }

   private HudStatNumberAnimator.RenderState renderState(float tickTime) {
      float pulseAge = tickTime - this.pulseStartTick;
      float pulse = pulseAge >= 0.0F && pulseAge <= 14.0F ? 1.0F - smoothStep(pulseAge / 14.0F) : 0.0F;
      int rgbColor = this.pulseColor(pulse);
      float offsetX = this.damageShakeOffsetX(pulseAge, pulse);
      float offsetY = this.damageShakeOffsetY(pulseAge, pulse);
      return new HudStatNumberAnimator.RenderState(clamp(this.alpha, 0.0F, 1.0F), rgbColor, offsetX, offsetY);
   }

   private int pulseColor(float pulse) {
      if (pulse <= 0.0F) {
         return 16777215;
      } else if (this.pulseDirection == HudStatNumberAnimator.ChangeDirection.UP) {
         return lerpColor(16777215, 5635925, pulse);
      } else {
         return this.shouldPulseDamage(this.pulseDirection) ? lerpColor(16777215, 16733525, pulse) : 16777215;
      }
   }

   private float damageShakeOffsetX(float pulseAge, float pulse) {
      return this.shouldPulseDamage(this.pulseDirection) && !(pulse <= 0.0F) ? (float)Math.sin((double)(pulseAge * 3.2F)) * 1.25F * pulse : 0.0F;
   }

   private float damageShakeOffsetY(float pulseAge, float pulse) {
      return this.shouldPulseDamage(this.pulseDirection) && !(pulse <= 0.0F) ? (float)Math.cos((double)(pulseAge * 4.1F)) * 0.45F * pulse : 0.0F;
   }

   private static float approach(float current, float target, float amount) {
      if (current < target) {
         return Math.min(target, current + amount);
      } else {
         return current > target ? Math.max(target, current - amount) : current;
      }
   }

   private static float smoothStep(float value) {
      float clamped = clamp(value, 0.0F, 1.0F);
      return clamped * clamped * (3.0F - 2.0F * clamped);
   }

   private static int lerpColor(int from, int to, float amount) {
      float clamped = clamp(amount, 0.0F, 1.0F);
      int r = lerpChannel(from >> 16 & 0xFF, to >> 16 & 0xFF, clamped);
      int g = lerpChannel(from >> 8 & 0xFF, to >> 8 & 0xFF, clamped);
      int b = lerpChannel(from & 0xFF, to & 0xFF, clamped);
      return r << 16 | g << 8 | b;
   }

   private static int lerpChannel(int from, int to, float amount) {
      return Math.round((float)from + (float)(to - from) * amount);
   }

   private static float clamp(float value, float min, float max) {
      if (value < min) {
         return min;
      } else {
         return value > max ? max : value;
      }
   }

   private static enum ChangeDirection {
      NONE,
      UP,
      DOWN;
   }

   public static record RenderState(float alpha, int rgbColor, float offsetX, float offsetY) {
      public boolean isHidden() {
         return this.alpha <= 0.01F;
      }
   }

   public static enum StatKind {
      HEALTH,
      KI,
      STAMINA,
      KISENSE_HEALTH;
   }
}
