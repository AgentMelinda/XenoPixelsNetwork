package com.dragonminez.client.clash;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientBeamClashState {
   private static volatile boolean active = false;
   private static volatile float meterPhase = 0.0F;
   private static volatile float sweetLow = 0.0F;
   private static volatile float sweetHigh = 0.0F;
   private static volatile float advantage = 0.5F;
   private static volatile int beamColor = 16777215;
   private static volatile int opponentId = -1;

   private ClientBeamClashState() {
   }

   public static void update(boolean active, float meterPhase, float sweetLow, float sweetHigh, float advantage, int beamColor, int opponentId) {
      ClientBeamClashState.active = active;
      ClientBeamClashState.meterPhase = meterPhase;
      ClientBeamClashState.sweetLow = sweetLow;
      ClientBeamClashState.sweetHigh = sweetHigh;
      ClientBeamClashState.advantage = advantage;
      ClientBeamClashState.beamColor = beamColor;
      ClientBeamClashState.opponentId = opponentId;
   }

   public static void clear() {
      active = false;
   }

   public static boolean isActive() {
      return active;
   }

   public static float meterPhase() {
      return meterPhase;
   }

   public static float sweetLow() {
      return sweetLow;
   }

   public static float sweetHigh() {
      return sweetHigh;
   }

   public static float advantage() {
      return advantage;
   }

   public static int beamColor() {
      return beamColor;
   }

   public static int opponentId() {
      return opponentId;
   }
}
