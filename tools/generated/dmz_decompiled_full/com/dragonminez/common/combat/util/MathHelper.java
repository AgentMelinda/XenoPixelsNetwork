package com.dragonminez.common.combat.util;

public class MathHelper {
   public static float clamp(float value, float min, float max) {
      return Math.max(Math.min(value, max), min);
   }

   public static double easeOutCubic(double number) {
      return 1.0 - Math.pow(1.0 - number, 3.0);
   }

   public double easeInExpo(double x) {
      return x == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * x - 10.0);
   }

   public double easeOutExpo(double x) {
      return x == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * x);
   }
}
