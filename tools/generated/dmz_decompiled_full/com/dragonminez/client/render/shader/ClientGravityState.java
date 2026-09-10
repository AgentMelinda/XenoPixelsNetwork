package com.dragonminez.client.render.shader;

import com.dragonminez.common.config.ConfigManager;
import lombok.Generated;

public final class ClientGravityState {
   private static volatile float machineGravity = 0.0F;
   private static volatile float environmentalGravity = 1.0F;
   private static volatile float netGravity = 0.0F;
   private static volatile float statMult = 1.0F;
   private static volatile float tpGravityMult = 1.0F;
   private static volatile int idealWeight = 0;
   private static volatile int totalWeight = 0;
   private static volatile int effectiveWeight = 0;
   private static volatile float loadRatio = 0.0F;
   private static volatile float weightTpMult = 1.0F;
   private static volatile int zone = 0;

   private ClientGravityState() {
   }

   public static void update(
      float machineGravity,
      float environmentalGravity,
      float netGravity,
      float statMult,
      float tpGravityMult,
      int idealWeight,
      int totalWeight,
      int effectiveWeight,
      float loadRatio,
      float weightTpMult,
      int zone
   ) {
      ClientGravityState.machineGravity = Math.max(0.0F, machineGravity);
      ClientGravityState.environmentalGravity = environmentalGravity;
      ClientGravityState.netGravity = netGravity;
      ClientGravityState.statMult = statMult;
      ClientGravityState.tpGravityMult = tpGravityMult;
      ClientGravityState.idealWeight = idealWeight;
      ClientGravityState.totalWeight = totalWeight;
      ClientGravityState.effectiveWeight = effectiveWeight;
      ClientGravityState.loadRatio = loadRatio;
      ClientGravityState.weightTpMult = weightTpMult;
      ClientGravityState.zone = zone;
   }

   public static float getShaderIntensity() {
      if (machineGravity <= 0.0F) {
         return 0.0F;
      } else {
         double forMax = ConfigManager.getServerConfig().getGravity().getDeviceShaderGravityForMax();
         double curved = Math.sqrt(Math.min(1.0, (double)machineGravity / forMax));
         return (float)Math.min(1.0, Math.max(0.35, curved));
      }
   }

   @Generated
   public static float getEnvironmentalGravity() {
      return environmentalGravity;
   }

   @Generated
   public static float getNetGravity() {
      return netGravity;
   }

   @Generated
   public static float getStatMult() {
      return statMult;
   }

   @Generated
   public static float getTpGravityMult() {
      return tpGravityMult;
   }

   @Generated
   public static int getIdealWeight() {
      return idealWeight;
   }

   @Generated
   public static int getTotalWeight() {
      return totalWeight;
   }

   @Generated
   public static int getEffectiveWeight() {
      return effectiveWeight;
   }

   @Generated
   public static float getLoadRatio() {
      return loadRatio;
   }

   @Generated
   public static float getWeightTpMult() {
      return weightTpMult;
   }

   @Generated
   public static int getZone() {
      return zone;
   }
}
