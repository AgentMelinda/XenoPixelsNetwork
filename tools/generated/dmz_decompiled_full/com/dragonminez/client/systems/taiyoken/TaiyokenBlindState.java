package com.dragonminez.client.systems.taiyoken;

public final class TaiyokenBlindState {
   private static int ticksRemaining = 0;
   private static int totalTicks = 0;

   private TaiyokenBlindState() {
   }

   public static void startBlind(int durationTicks) {
      if (durationTicks <= 0) {
         clear();
      } else {
         ticksRemaining = durationTicks;
         totalTicks = durationTicks;
      }
   }

   public static void tick() {
      if (ticksRemaining > 0) {
         ticksRemaining--;
      }
   }

   public static boolean isActive() {
      return ticksRemaining > 0;
   }

   public static int getTicksRemaining() {
      return ticksRemaining;
   }

   public static float getProgress() {
      return totalTicks <= 0 ? 0.0F : (float)ticksRemaining / (float)totalTicks;
   }

   public static void clear() {
      ticksRemaining = 0;
      totalTicks = 0;
   }
}
