package com.dragonminez.client.systems.impactframes;

public class ImpactFrame {
   private float threshold;
   private float thresholdLerp;
   private int duration;
   private boolean invert;

   public ImpactFrame(float threshold, float thresholdLerp, int duration, boolean invert) {
      this.threshold = threshold;
      this.thresholdLerp = thresholdLerp;
      this.duration = duration;
      this.invert = invert;
   }

   public ImpactFrame(ImpactFrame other) {
      this(other.threshold, other.thresholdLerp, other.duration, other.invert);
   }

   public ImpactFrame() {
      this(0.6F, 0.05F, 15, false);
   }

   public float getThreshold() {
      return this.threshold;
   }

   public float getThresholdLerp() {
      return this.thresholdLerp;
   }

   public int getDuration() {
      return this.duration;
   }

   public boolean isInverted() {
      return this.invert;
   }

   public void setThreshold(float threshold) {
      this.threshold = threshold;
   }

   public void setThresholdLerp(float thresholdLerp) {
      this.thresholdLerp = thresholdLerp;
   }

   public void setDuration(int duration) {
      this.duration = duration;
   }

   public void setInverted(boolean invert) {
      this.invert = invert;
   }
}
