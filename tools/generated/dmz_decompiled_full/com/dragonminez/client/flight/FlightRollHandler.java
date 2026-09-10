package com.dragonminez.client.flight;

import com.dragonminez.client.events.FlySkillEvent;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.skills.Skill;
import com.dragonminez.compat.util.LazyOptional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public class FlightRollHandler {
   private static final float ROLL_ACCELERATION = 1.5F;
   private static final float MAX_ROLL_SPEED = 4.0F;
   private static final float ROLL_FRICTION = 0.9F;
   private static final float STABILIZE_SPEED = 0.5F;
   private static final float MOUSE_SENSITIVITY = 0.05F;
   private static final float ROLL_FORCE_MULTIPLIER = 0.5F;
   private static float currentRoll = 0.0F;
   private static float prevRoll = 0.0F;
   private static float rollVelocity = 0.0F;
   private static float lastYaw = 0.0F;

   public static void tick() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null) {
         reset();
      } else {
         prevRoll = currentRoll;
         float currentYaw = player.getYRot();
         float deltaYaw = currentYaw - lastYaw;

         while (deltaYaw >= 180.0F) {
            deltaYaw -= 360.0F;
         }

         while (deltaYaw < -180.0F) {
            deltaYaw += 360.0F;
         }

         if (isPlayerFlying(player) && FlySkillEvent.getInstance().isFlyingFast(player)) {
            float input = 0.0F;
            if (player.input.left) {
               input--;
            }

            if (player.input.right) {
               input++;
            }

            float keyForce = input * 1.5F;
            float mouseForce = deltaYaw * 0.05F;
            float totalForce = (keyForce - mouseForce) * 0.5F;
            if (Math.abs(totalForce) > 0.01F) {
               rollVelocity += totalForce;
            } else {
               float normalizedRoll = Mth.wrapDegrees(currentRoll);
               if (Math.abs(normalizedRoll) < 90.0F) {
                  float distTo0 = -normalizedRoll;
                  if (Math.abs(distTo0) > 0.1F) {
                     float stabilizeForce = Mth.clamp(distTo0 * 0.1F, -0.5F, 0.5F);
                     rollVelocity += stabilizeForce;
                  }
               }
            }

            rollVelocity *= 0.9F;
            rollVelocity = Mth.clamp(rollVelocity, -4.0F, 4.0F);
            currentRoll = currentRoll + rollVelocity;
            rebaseRoll();
         } else {
            float target = (float)Math.round(currentRoll / 360.0F) * 360.0F;
            currentRoll = Mth.lerp(0.1F, currentRoll, target);
            rollVelocity = 0.0F;
         }

         lastYaw = currentYaw;
      }
   }

   private static void rebaseRoll() {
      float wrapped = Mth.wrapDegrees(currentRoll);
      float offset = currentRoll - wrapped;
      if (offset != 0.0F) {
         currentRoll = wrapped;
         prevRoll -= offset;
      }
   }

   private static boolean isPlayerFlying(LocalPlayer player) {
      LazyOptional<StatsData> statsOpt = StatsProvider.get(StatsCapability.INSTANCE, player);
      if (statsOpt.isPresent()) {
         StatsData data = statsOpt.resolve().orElse(null);
         if (data != null) {
            Skill flySkill = data.getSkills().getSkill("fly");
            return flySkill != null && flySkill.isActive();
         }
      }

      return false;
   }

   public static void reset() {
      currentRoll = 0.0F;
      prevRoll = 0.0F;
      rollVelocity = 0.0F;
      lastYaw = 0.0F;
   }

   public static float getRoll(float partialTicks) {
      return Mth.lerp(partialTicks, prevRoll, currentRoll);
   }

   public static boolean hasActiveRoll() {
      return Math.abs(currentRoll) > 0.1F || Math.abs(rollVelocity) > 0.01F;
   }
}
