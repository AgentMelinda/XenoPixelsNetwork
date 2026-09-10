package com.dragonminez.client.events;

import com.dragonminez.client.flight.CombatFlightHandler;
import com.dragonminez.client.flight.FlightOrientationHandler;
import com.dragonminez.client.flight.FlightRollHandler;
import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.EntityAttributes;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.FlightModeC2S;
import com.dragonminez.common.network.C2S.FlyToggleC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.skills.Skill;
import com.dragonminez.common.stats.techniques.TechniqueDispatcher;
import com.dragonminez.server.util.GravityLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.InputEvent.Key;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME,
   value = {Dist.CLIENT}
)
public class FlySkillEvent {
   private static final FlySkillEvent INSTANCE = new FlySkillEvent();
   private static boolean pendingFlightActivation = false;
   private static Vec3 flightVector = Vec3.ZERO;
   private static int verticalHover = 0;
   private static float hovering = 0.0F;
   private static final float NORMAL_MAX_SPEED = 0.6F;
   private static final float SPRINT_MAX_SPEED = 1.05F;
   private static final float ACCELERATION = 0.055F;
   private static final float DECELERATION = 0.035F;
   private static final float EXIT_DECELERATION = 0.08F;
   private static final float SLOW_DESCENT_RATE = -0.02F;
   private static final float TURN_SPEED_NORMAL = 0.15F;
   private static final float TURN_SPEED_FAST = 0.3F;
   private static final float BASE_ATTRIBUTE_FLY_SPEED = 0.35F;
   private static final float FAST_FLYING_THRESHOLD = 0.55F;
   private static final double MIN_GROUND_CLEARANCE = 0.25;
   private static int kiConsumptionTicks = 0;
   private static final int KI_CONSUMPTION_INTERVAL = 20;
   private static long pendingGroundActivationStartTime = 0L;
   private static boolean pendingGroundActivation = false;
   private static final long DOUBLE_TAP_WINDOW_MS = 250L;
   private static boolean wasFlyingSkillActive = false;
   private static boolean pendingFlightDisable = false;
   private static boolean wasSprintingInAir = false;
   private static int lastFlightMode = 0;

   public static FlySkillEvent getInstance() {
      return INSTANCE;
   }

   @SubscribeEvent
   public static void onKeyPress(Key event) {
      if (KeyBinds.FLY_KEY.consumeClick()) {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         if (player != null && mc.screen == null) {
            if (KeyBinds.isSecondFunctionDown()) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
                  if (data.getStatus().isHasCreatedCharacter() && !data.getStatus().isStunned()) {
                     Skill flySkill = data.getSkills().getSkill("fly");
                     Skill kiControlSkill = data.getSkills().getSkill("kicontrol");
                     if (kiControlSkill == null || kiControlSkill.getLevel() <= 0) {
                        player.displayClientMessage(Component.translatable("message.dragonminez.flight.no_kicontrol"), true);
                     } else if (flySkill != null && flySkill.getLevel() > 0) {
                        NetworkHandler.sendToServer(new FlightModeC2S());
                        player.playSound((SoundEvent)MainSounds.UI_MENU_SWITCH.get(), 0.7F, 1.0F);
                     } else {
                        player.displayClientMessage(Component.translatable("message.dragonminez.flight.no_fly"), true);
                     }
                  }
               });
               return;
            }

            long currentTime = System.currentTimeMillis();
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               if (data.getStatus().isHasCreatedCharacter() && !data.getStatus().isStunned()) {
                  Skill flySkill = data.getSkills().getSkill("fly");
                  Skill kiControlSkill = data.getSkills().getSkill("kicontrol");
                  boolean flyActive = flySkill != null && flySkill.isActive();
                  if (kiControlSkill != null && kiControlSkill.getLevel() > 0) {
                     if (flySkill != null && flySkill.getLevel() > 0) {
                        if (!flyActive && data.getResources().getPowerRelease() < 5) {
                           player.displayClientMessage(Component.translatable("message.dragonminez.flight.low_power_release"), true);
                        } else {
                           int flyLevel = flySkill.getLevel();
                           double energyCostPercent = getActivationEnergyPercent(flyLevel);
                           int energyCost = (int)Math.ceil((double)ConfigManager.getCombatConfig().getBaselineFormDrain().intValue() * energyCostPercent);
                           if (!flySkill.isActive()) {
                              if (data.getResources().getCurrentEnergy() < (float)energyCost) {
                                 return;
                              }

                              if (player.onGround()) {
                                 pendingGroundActivation = true;
                                 pendingGroundActivationStartTime = currentTime;
                                 return;
                              }

                              NetworkHandler.sendToServer(new FlyToggleC2S(true));
                           } else {
                              pendingFlightDisable = !pendingFlightDisable;
                           }
                        }
                     } else {
                        if (!flyActive) {
                           player.displayClientMessage(Component.translatable("message.dragonminez.flight.no_fly"), true);
                        }
                     }
                  } else {
                     if (!flyActive) {
                        player.displayClientMessage(Component.translatable("message.dragonminez.flight.no_kicontrol"), true);
                     }
                  }
               }
            });
         }
      }
   }

   public static void toggleFlightFromMenu() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> performStandardToggle(player, data));
      }
   }

   public static void performStandardToggle(LocalPlayer player, StatsData data) {
      if (data.getStatus().isHasCreatedCharacter() && !data.getStatus().isStunned()) {
         Skill flySkill = data.getSkills().getSkill("fly");
         Skill kiControlSkill = data.getSkills().getSkill("kicontrol");
         boolean flyActive = flySkill != null && flySkill.isActive();
         if (kiControlSkill != null && kiControlSkill.getLevel() > 0) {
            if (flySkill != null && flySkill.getLevel() > 0) {
               if (!flyActive && data.getResources().getPowerRelease() < 5) {
                  player.displayClientMessage(Component.translatable("message.dragonminez.flight.low_power_release"), true);
               } else {
                  if (!flySkill.isActive()) {
                     int flyLevel = flySkill.getLevel();
                     double energyCostPercent = getActivationEnergyPercent(flyLevel);
                     int energyCost = (int)Math.ceil((double)ConfigManager.getCombatConfig().getBaselineFormDrain().intValue() * energyCostPercent);
                     if (data.getResources().getCurrentEnergy() < (float)energyCost) {
                        return;
                     }

                     if (player.onGround()) {
                        pendingGroundActivation = true;
                        pendingGroundActivationStartTime = System.currentTimeMillis();
                     } else {
                        NetworkHandler.sendToServer(new FlyToggleC2S(true));
                     }
                  } else {
                     pendingFlightDisable = !pendingFlightDisable;
                  }
               }
            } else {
               if (!flyActive) {
                  player.displayClientMessage(Component.translatable("message.dragonminez.flight.no_fly"), true);
               }
            }
         } else {
            if (!flyActive) {
               player.displayClientMessage(Component.translatable("message.dragonminez.flight.no_kicontrol"), true);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onClientTick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null) {
         boolean startedGroundActivation = false;
         if (pendingGroundActivation) {
            long elapsed = System.currentTimeMillis() - pendingGroundActivationStartTime;
            if (elapsed >= 250L) {
               player.jumpFromGround();
               Vec3 motion = player.getDeltaMovement();
               player.setDeltaMovement(motion.x, 0.42, motion.z);
               pendingFlightActivation = true;
               pendingGroundActivation = false;
               startedGroundActivation = true;
            } else if (!player.onGround()) {
               pendingGroundActivation = false;
            }
         }

         if (pendingFlightActivation) {
            if (!startedGroundActivation && player.getDeltaMovement().y < 0.0 && !player.onGround()) {
               NetworkHandler.sendToServer(new FlyToggleC2S(true));
               pendingFlightActivation = false;
            } else if (!startedGroundActivation && player.onGround()) {
               pendingFlightActivation = false;
            }
         }

         StatsProvider.get(StatsCapability.INSTANCE, player)
            .ifPresent(
               data -> {
                  if (data.getStatus().isHasCreatedCharacter()) {
                     Skill flySkill = data.getSkills().getSkill("fly");
                     if (flySkill != null) {
                        boolean isFlying = flySkill.isActive();
                        int flightMode = data.getStatus().getFlightMode();
                        boolean isCombatFly = flightMode == 1;
                        if (isFlying && !wasFlyingSkillActive) {
                           if (isCombatFly) {
                              CombatFlightHandler.initFromMotion(player);
                           } else {
                              initializeFlightVectorFromCurrentMotion(player, data.getSkills().getSkillLevel("fly"));
                           }

                           lastFlightMode = flightMode;
                        }

                        if (isFlying && flightMode != lastFlightMode) {
                           if (isCombatFly) {
                              resetFlightState();
                              CombatFlightHandler.initFromMotion(player);
                           } else {
                              CombatFlightHandler.reset();
                              initializeFlightVectorFromCurrentMotion(player, data.getSkills().getSkillLevel("fly"));
                           }

                           Component switchHint = Component.empty()
                              .append(KeyBinds.SECOND_FUNCTION_KEY.getTranslatedKeyMessage())
                              .append(" + ")
                              .append(KeyBinds.FLY_KEY.getTranslatedKeyMessage());
                           player.displayClientMessage(
                              Component.translatable(
                                 isCombatFly ? "dragonminez.flight.mode.switched.combat" : "dragonminez.flight.mode.switched.search", new Object[]{switchHint}
                              ),
                              true
                           );
                           lastFlightMode = flightMode;
                        }

                        boolean movementRestricted = TechniqueDispatcher.isMovementRestrictedKiAttack(player, data)
                           || data.getStatus().isStunned()
                           || data.getStatus().isActionCharging();
                        if (isFlying) {
                           if (TechniqueDispatcher.isMovementRestrictedKiAttack(player, data)) {
                              flightVector = Vec3.ZERO;
                              player.setDeltaMovement(0.0, 0.0, 0.0);
                              player.fallDistance = 0.0F;
                           } else if (isCombatFly) {
                              if (pendingFlightDisable) {
                                 pendingFlightDisable = false;
                                 NetworkHandler.sendToServer(new FlyToggleC2S(false));
                                 CombatFlightHandler.reset();
                                 resetFlightState();
                                 return;
                              }

                              CombatFlightHandler.handle(player, data, movementRestricted);
                           } else {
                              handleFlightMovement(player, data.getSkills().getSkillLevel("fly"), movementRestricted);
                           }

                           handleKiConsumption(player, data, flySkill);
                        } else if (!pendingFlightDisable) {
                           resetFlightState();
                           CombatFlightHandler.reset();
                           lastFlightMode = 0;
                        }

                        wasFlyingSkillActive = isFlying;
                     }
                  }
               }
            );
      }
   }

   private static void handleFlightMovement(LocalPlayer player, int flyLevel, boolean movementRestricted) {
      float flySpeedScale = getFlySpeedScale(player);
      float levelMultiplier = 1.0F + 0.2F * (float)flyLevel;
      float maxNormalSpeed = 0.6F * levelMultiplier * flySpeedScale;
      float maxSprintSpeed = 1.05F * levelMultiplier * flySpeedScale;
      Minecraft mc = Minecraft.getInstance();
      boolean isForward = !movementRestricted && mc.options.keyUp.isDown();
      boolean isBack = !movementRestricted && mc.options.keyDown.isDown();
      boolean isLeft = !movementRestricted && mc.options.keyLeft.isDown();
      boolean isRight = !movementRestricted && mc.options.keyRight.isDown();
      boolean isJump = !movementRestricted && mc.options.keyJump.isDown();
      boolean isCrouch = !movementRestricted && mc.options.keyShift.isDown();
      boolean isSprintingInput = !movementRestricted && player.isSprinting();
      boolean hasInput = isForward || isBack || isLeft || isRight;
      boolean isFastFlight = INSTANCE.isFlyingFast(player);
      boolean canSprint = isSprintingInput && isFastFlight;
      float currentMaxSpeed = canSprint ? maxSprintSpeed : maxNormalSpeed;
      float currentAccel = 0.055F * flySpeedScale;
      if (canSprint && !wasSprintingInAir) {
         player.playSound((SoundEvent)MainSounds.TRANSFORM_ON.get(), 0.7F, 1.2F);
      }

      wasSprintingInAir = canSprint;
      if (!isFastFlight) {
         FlightOrientationHandler.reset();
      }

      Vec3 lookDir = isFastFlight ? FlightOrientationHandler.getForwardVector(player) : player.getLookAngle();
      Vec3 targetDirection = Vec3.ZERO;
      if (isForward) {
         targetDirection = targetDirection.add(lookDir);
      }

      if (isBack) {
         targetDirection = targetDirection.add(lookDir.scale(-0.5));
      }

      if (isLeft) {
         Vec3 leftDir = lookDir.yRot((float)Math.toRadians(90.0)).normalize();
         targetDirection = targetDirection.add(new Vec3(leftDir.x, 0.0, leftDir.z).scale(0.7));
      }

      if (isRight) {
         Vec3 rightDir = lookDir.yRot((float)Math.toRadians(-90.0)).normalize();
         targetDirection = targetDirection.add(new Vec3(rightDir.x, 0.0, rightDir.z).scale(0.7));
      }

      double currentSpeed = flightVector.length();
      if (pendingFlightDisable) {
         double newSpeed = Math.max(0.0, currentSpeed - (double)(0.08F * flySpeedScale));
         Vec3 normalized = currentSpeed > 1.0E-4 ? flightVector.normalize() : Vec3.ZERO;
         flightVector = normalized.scale(newSpeed);
         if (flightVector.lengthSqr() < 0.03) {
            pendingFlightDisable = false;
            NetworkHandler.sendToServer(new FlyToggleC2S(false));
            resetFlightState();
            return;
         }
      } else if (hasInput && targetDirection.length() > 0.001) {
         targetDirection = targetDirection.normalize();
         if (canSprint) {
            Vec3 targetVelocity = targetDirection.scale((double)maxSprintSpeed);
            flightVector = targetVelocity;
         } else {
            double minSpeed = ConfigManager.getCombatConfig().getCombatFlyBaseSpeed() * (double)levelMultiplier * (double)flySpeedScale;
            double targetSpeed = Math.max(minSpeed, Math.min(currentSpeed + (double)currentAccel, (double)currentMaxSpeed));
            Vec3 targetVelocity = targetDirection.scale(targetSpeed);
            float turnSpeed = isFastFlight ? 0.3F : 0.15F;
            flightVector = new Vec3(
               Mth.lerp((double)turnSpeed, flightVector.x, targetVelocity.x),
               Mth.lerp((double)turnSpeed, flightVector.y, targetVelocity.y),
               Mth.lerp((double)turnSpeed, flightVector.z, targetVelocity.z)
            );
         }

         hovering = Math.min(1.0F, hovering + 0.1F);
      } else if (currentSpeed > 0.01) {
         double newSpeed = Math.max(0.0, currentSpeed - 0.035F);
         if (currentSpeed > 0.001) {
            flightVector = flightVector.normalize().scale(newSpeed);
         } else {
            flightVector = Vec3.ZERO;
         }
      } else {
         flightVector = Vec3.ZERO;
      }

      FlightRollHandler.tick();
      if (GravityLogic.isFlightHardStopped(player)) {
         flightVector = Vec3.ZERO;
         player.setDeltaMovement(0.0, -1.5, 0.0);
      } else {
         double flyFactor = GravityLogic.getFlyFactor(player);
         if (flyFactor < 1.0) {
            flightVector = flightVector.scale(flyFactor);
         }
      }

      if (!(flightVector.length() > 0.01) && !pendingFlightDisable) {
         handleHovering(player, isJump, isCrouch);
      } else {
         player.setDeltaMovement(flightVector);
         player.fallDistance = 0.0F;
         verticalHover = 0;
      }

      if (player.onGround() && !pendingFlightActivation) {
         pendingFlightDisable = false;
         NetworkHandler.sendToServer(new FlyToggleC2S(false));
         resetFlightState();
      }
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

   private static void initializeFlightVectorFromCurrentMotion(LocalPlayer player, int flyLevel) {
      float speedScale = getFlySpeedScale(player);
      float levelMultiplier = 1.0F + 0.2F * (float)flyLevel;
      float maxNormalSpeed = 0.6F * levelMultiplier * speedScale;
      float maxSprintSpeed = 1.05F * levelMultiplier * speedScale;
      float capSpeed = Math.max(maxNormalSpeed, maxSprintSpeed);
      Vec3 currentMotion = player.getDeltaMovement();
      if (currentMotion.lengthSqr() < 1.0E-5) {
         flightVector = player.getLookAngle().scale((double)(maxNormalSpeed * 0.35F));
      } else {
         double clamped = Math.min(currentMotion.length(), (double)capSpeed);
         flightVector = currentMotion.normalize().scale(clamped);
      }
   }

   private static void handleHovering(LocalPlayer player, boolean isJump, boolean isCrouch) {
      if (isJump) {
         if (verticalHover < 20) {
            verticalHover = Mth.clamp(verticalHover + 1, -20, 20);
         }
      } else if (isCrouch) {
         if (verticalHover > -20) {
            verticalHover = Mth.clamp(verticalHover - 1, -20, 20);
         }
      } else if (verticalHover > -3) {
         verticalHover = Mth.clamp(verticalHover - 1, -3, 20);
      } else if (verticalHover < -3) {
         verticalHover = Mth.clamp(verticalHover + 1, -20, -3);
      }

      double yMovement;
      if (verticalHover >= -3 && verticalHover <= 0 && !isJump && !isCrouch) {
         yMovement = -0.02F + Math.sin((double)((float)player.tickCount / 10.0F)) / 200.0;
      } else if (verticalHover == 0) {
         yMovement = Math.sin((double)((float)player.tickCount / 10.0F)) / 100.0;
      } else {
         yMovement = (double)verticalHover / 60.0;
      }

      if (!isJump && !isCrouch && getGroundDistance(player) <= 0.25) {
         yMovement = Math.max(0.0, yMovement);
      }

      player.setDeltaMovement(new Vec3(player.getDeltaMovement().x * 0.9, yMovement, player.getDeltaMovement().z * 0.9));
      player.fallDistance = 0.0F;
      if (hovering < 1.0F) {
         hovering = Math.min(1.0F, hovering + 0.1F);
      }
   }

   private static void handleKiConsumption(LocalPlayer player, StatsData data, Skill flySkill) {
      kiConsumptionTicks++;
      if (kiConsumptionTicks >= 20) {
         kiConsumptionTicks = 0;
         int flyLevel = flySkill.getLevel();
         float maxEnergy = data.getMaxEnergy();
         boolean isFastFlight = INSTANCE.isFlyingFast(player);
         boolean isSprintFlight = player.isSprinting() && isFastFlight;
         double basePercent = 0.03;
         double energyCostPercent = Math.max(0.002, basePercent - (double)flyLevel * 0.005);
         energyCostPercent *= (double)getFlyCostMultiplier(flyLevel);
         if (isSprintFlight) {
            energyCostPercent *= 2.0;
         }

         int energyCost = (int)Math.ceil((double)maxEnergy * energyCostPercent);
         if (data.getResources().getCurrentEnergy() <= (float)energyCost) {
            NetworkHandler.sendToServer(new FlyToggleC2S(false));
            resetFlightState();
         }
      }
   }

   public static void injectKnockback(Vec3 knockback) {
      flightVector = flightVector.add(knockback);
   }

   private static void resetFlightState() {
      flightVector = Vec3.ZERO;
      verticalHover = 0;
      hovering = 0.0F;
      kiConsumptionTicks = 0;
      pendingFlightDisable = false;
      wasFlyingSkillActive = false;
      wasSprintingInAir = false;
      FlightRollHandler.reset();
      FlightOrientationHandler.reset();
   }

   private boolean isFlyingFast() {
      return flightVector.length() > 0.55F;
   }

   public boolean isFlyingFast(AbstractClientPlayer player) {
      if (player == null) {
         return false;
      } else {
         LocalPlayer localPlayer = Minecraft.getInstance().player;
         if (localPlayer != null && player == localPlayer) {
            return this.isFlyingFast();
         } else {
            boolean flyActive = StatsProvider.get(StatsCapability.INSTANCE, player).map(data -> {
               Skill flySkill = data.getSkills().getSkill("fly");
               return flySkill != null && flySkill.isActive() && data.getStatus().getFlightMode() != 1;
            }).orElse(false);
            return flyActive && player.getDeltaMovement().lengthSqr() > 0.3025F;
         }
      }
   }

   private static double getActivationEnergyPercent(int flyLevel) {
      double basePercent = Math.max(0.01, 0.04 - (double)flyLevel * 0.003);
      return basePercent * (double)getFlyCostMultiplier(flyLevel);
   }

   private static float getFlyCostMultiplier(int flyLevel) {
      int clampedLevel = Mth.clamp(flyLevel, 1, 10);
      float t = (float)(clampedLevel - 1) / 9.0F;
      return Mth.lerp(t, 4.0F, 1.0F);
   }

   private static double getGroundDistance(LocalPlayer player) {
      Vec3 start = new Vec3(player.getX(), player.getBoundingBox().minY, player.getZ());
      Vec3 end = start.add(0.0, -1.5, 0.0);
      HitResult hit = player.level().clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, player));
      return hit.getType() == Type.MISS ? Double.MAX_VALUE : start.y - hit.getLocation().y;
   }
}
