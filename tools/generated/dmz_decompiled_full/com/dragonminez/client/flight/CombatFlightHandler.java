package com.dragonminez.client.flight;

import com.dragonminez.common.config.CombatConfig;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.EntityAttributes;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.CombatFlyImpulseC2S;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.server.util.GravityLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class CombatFlightHandler {
   private static final int DIR_FORWARD = 0;
   private static final int DIR_BACK = 1;
   private static final int DIR_LEFT = 2;
   private static final int DIR_RIGHT = 3;
   private static final int DIR_UP = 4;
   private static final int DIR_DOWN = 5;
   private static final int DIR_COUNT = 6;
   private static final long DOUBLE_TAP_WINDOW_MS = 250L;
   private static final float BASE_ATTRIBUTE_FLY_SPEED = 0.35F;
   private static final float SMOOTHING = 0.35F;
   private static final float BURST_DECAY = 0.88F;
   private static final double PASSIVE_DESCENT = -0.05;
   private static final double VERTICAL_RATE = 0.55;
   private static final double MIN_GROUND_CLEARANCE = 0.25;
   private static Vec3 velocity = Vec3.ZERO;
   private static Vec3 burstVelocity = Vec3.ZERO;
   private static final boolean[] wasDown = new boolean[6];
   private static final long[] lastTapTime = new long[6];
   private static int sustainedDir = -1;
   private static long lastImpulseTime = 0L;

   public static void handle(LocalPlayer player, StatsData data, boolean movementRestricted) {
      if (data.getStatus().isStunned()) {
         velocity = Vec3.ZERO;
         burstVelocity = Vec3.ZERO;
         sustainedDir = -1;
         player.setDeltaMovement(0.0, -1.5, 0.0);
         player.fallDistance = 0.0F;
      } else {
         CombatConfig config = ConfigManager.getCombatConfig();
         int flyLevel = data.getSkills().getSkillLevel("fly");
         float speedScale = getFlySpeedScale(player);
         float levelMultiplier = 1.0F + 0.2F * (float)flyLevel;
         double baseSpeed = config.getCombatFlyBaseSpeed() * (double)levelMultiplier * (double)speedScale;
         double sprintSpeed = config.getCombatFlySprintSpeed() * (double)levelMultiplier * (double)speedScale;
         Minecraft mc = Minecraft.getInstance();
         boolean forward = !movementRestricted && mc.options.keyUp.isDown();
         boolean back = !movementRestricted && mc.options.keyDown.isDown();
         boolean left = !movementRestricted && mc.options.keyLeft.isDown();
         boolean right = !movementRestricted && mc.options.keyRight.isDown();
         boolean jump = !movementRestricted && mc.options.keyJump.isDown();
         boolean descend = !movementRestricted && mc.options.keyShift.isDown();
         boolean sprint = !movementRestricted && mc.options.keySprint.isDown();
         float yaw = player.getYRot();
         Vec3 fwdDir = Vec3.directionFromRotation(0.0F, yaw).normalize();
         Vec3 rightDir = fwdDir.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
         boolean[] pressed = new boolean[]{forward, back, left, right, jump, descend};
         handleDoubleTaps(player, pressed, fwdDir, rightDir, config, speedScale, flyLevel);
         Vec3 horizontal = Vec3.ZERO;
         if (forward) {
            horizontal = horizontal.add(fwdDir);
         }

         if (back) {
            horizontal = horizontal.add(fwdDir.scale(-1.0));
         }

         if (left) {
            horizontal = horizontal.add(rightDir.scale(-1.0));
         }

         if (right) {
            horizontal = horizontal.add(rightDir);
         }

         boolean hasHorizontal = horizontal.lengthSqr() > 1.0E-4;
         double maxHorizontal = sprint ? sprintSpeed : baseSpeed;
         if (isSustainedHorizontal() && pressed[sustainedDir]) {
            maxHorizontal *= config.getCombatFlyHoldSpeedMultiplier();
         }

         Vec3 targetHorizontal = hasHorizontal ? horizontal.normalize().scale(maxHorizontal) : Vec3.ZERO;
         double maxVertical = 0.55 * (double)speedScale;
         if ((sustainedDir == 4 || sustainedDir == 5) && pressed[sustainedDir]) {
            maxVertical *= config.getCombatFlyHoldSpeedMultiplier();
         }

         double targetY;
         if (jump) {
            targetY = maxVertical;
         } else if (descend) {
            targetY = -maxVertical;
         } else {
            targetY = -0.05;
         }

         Vec3 target = new Vec3(targetHorizontal.x, targetY, targetHorizontal.z);
         velocity = new Vec3(Mth.lerp(0.35F, velocity.x, target.x), Mth.lerp(0.35F, velocity.y, target.y), Mth.lerp(0.35F, velocity.z, target.z));
         burstVelocity = burstVelocity.scale(0.88F);
         if (burstVelocity.lengthSqr() < 1.0E-6) {
            burstVelocity = Vec3.ZERO;
         }

         Vec3 combined = velocity.add(burstVelocity);
         if (!jump && combined.y < 0.0 && getGroundDistance(player) <= 0.25) {
            velocity = new Vec3(velocity.x, Math.max(0.0, velocity.y), velocity.z);
            burstVelocity = new Vec3(burstVelocity.x, 0.0, burstVelocity.z);
            combined = velocity.add(burstVelocity);
         }

         if (GravityLogic.isFlightHardStopped(player)) {
            velocity = Vec3.ZERO;
            burstVelocity = Vec3.ZERO;
            player.setDeltaMovement(0.0, -1.5, 0.0);
         } else {
            double flyFactor = GravityLogic.getFlyFactor(player);
            if (flyFactor < 1.0) {
               velocity = velocity.scale(flyFactor);
               burstVelocity = burstVelocity.scale(flyFactor);
               combined = velocity.add(burstVelocity);
            }

            player.setDeltaMovement(combined);
            player.fallDistance = 0.0F;
         }
      }
   }

   private static void handleDoubleTaps(LocalPlayer player, boolean[] pressed, Vec3 fwdDir, Vec3 rightDir, CombatConfig config, float speedScale, int flyLevel) {
      long now = System.currentTimeMillis();
      long impulseCooldownMs = (long)config.getCombatFlyImpulseCooldownTicks().intValue() * 50L;
      if (sustainedDir != -1 && !pressed[sustainedDir]) {
         sustainedDir = -1;
      }

      for (int dir = 0; dir < 6; dir++) {
         boolean down = pressed[dir];
         if (down && !wasDown[dir]) {
            boolean doubleTap = now - lastTapTime[dir] <= 250L;
            lastTapTime[dir] = now;
            if (doubleTap && now - lastImpulseTime >= impulseCooldownMs) {
               applyImpulse(player, dir, fwdDir, rightDir, config, speedScale, flyLevel);
               lastImpulseTime = now;
               sustainedDir = dir;
            }
         }

         wasDown[dir] = down;
      }
   }

   private static void applyImpulse(LocalPlayer player, int dir, Vec3 fwdDir, Vec3 rightDir, CombatConfig config, float speedScale, int flyLevel) {
      Vec3 impulseDir = switch (dir) {
         case 0 -> fwdDir;
         case 1 -> fwdDir.scale(-1.0);
         case 2 -> rightDir.scale(-1.0);
         case 3 -> rightDir;
         case 4 -> new Vec3(0.0, 1.0, 0.0);
         case 5 -> new Vec3(0.0, -1.0, 0.0);
         default -> Vec3.ZERO;
      };
      double levelScale = 1.0 + 0.2 * (double)flyLevel;
      double strength = 1.0 * (double)speedScale * levelScale;
      burstVelocity = burstVelocity.add(impulseDir.scale(strength));
      player.fallDistance = 0.0F;
      NetworkHandler.sendToServer(new CombatFlyImpulseC2S(dir));
   }

   private static boolean isSustainedHorizontal() {
      return sustainedDir == 0 || sustainedDir == 1 || sustainedDir == 2 || sustainedDir == 3;
   }

   private static float getFlySpeedScale(LocalPlayer player) {
      double attrValue = player.getAttributes().hasAttribute(EntityAttributes.FLY_SPEED) ? player.getAttributeValue(EntityAttributes.FLY_SPEED) : 0.0;
      if (attrValue <= 0.0) {
         return 1.0F;
      } else {
         double scale = attrValue / 0.35F;
         return (float)Mth.clamp(scale, 0.25, 4.0);
      }
   }

   private static double getGroundDistance(LocalPlayer player) {
      Vec3 start = new Vec3(player.getX(), player.getBoundingBox().minY, player.getZ());
      Vec3 end = start.add(0.0, -1.5, 0.0);
      HitResult hit = player.level().clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, player));
      return hit.getType() == Type.MISS ? Double.MAX_VALUE : start.y - hit.getLocation().y;
   }

   public static void initFromMotion(LocalPlayer player) {
      velocity = player.getDeltaMovement();
      burstVelocity = Vec3.ZERO;
   }

   public static void injectKnockback(Vec3 knockback) {
      burstVelocity = burstVelocity.add(knockback);
   }

   public static void reset() {
      velocity = Vec3.ZERO;
      burstVelocity = Vec3.ZERO;
      sustainedDir = -1;
      lastImpulseTime = 0L;

      for (int i = 0; i < 6; i++) {
         wasDown[i] = false;
         lastTapTime[i] = 0L;
      }
   }
}
