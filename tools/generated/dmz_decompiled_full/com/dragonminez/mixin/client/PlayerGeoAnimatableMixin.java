package com.dragonminez.mixin.client;

import com.dragonminez.client.animation.AnimationCache;
import com.dragonminez.client.animation.BaseAnimations;
import com.dragonminez.client.animation.CombatAnimationResolver;
import com.dragonminez.client.animation.IPlayerAnimatable;
import com.dragonminez.client.events.FlySkillEvent;
import com.dragonminez.client.render.firstperson.dto.FirstPersonManager;
import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.combat.logic.player.PlayerAttackProperties;
import com.dragonminez.common.combat.player.AttackHand;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.common.util.TransformationsHelper;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.AnimationController.State;
import software.bernie.geckolib.util.GeckoLibUtil;

@Mixin({AbstractClientPlayer.class})
public abstract class PlayerGeoAnimatableMixin implements GeoAnimatable, IPlayerAnimatable {
   private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
   @Unique
   private double dragonminez$lastPosX = Double.NaN;
   @Unique
   private double dragonminez$lastPosZ = Double.NaN;
   @Unique
   private int dragonminez$stoppedTicks = 0;
   @Unique
   private boolean dragonminez$isMovingState = false;
   @Unique
   private int dragonminez$lastTickCount = -1;
   @Unique
   private static final int STOPPED_THRESHOLD_TICKS = 3;
   @Unique
   private double dragonminez$horizSpeed = 0.0;
   @Unique
   private boolean dragonminez$wasAirborne = false;
   @Unique
   private float dragonminez$maxFallDistance = 0.0F;
   @Unique
   private boolean dragonminez$landingTriggered = false;
   @Unique
   private int dragonminez$landingTicks = 0;
   @Unique
   private boolean dragonminez$wasEating = false;
   @Unique
   private static final double WALK_SPEED_BASELINE = 0.2158;
   @Unique
   private static final double RUN_SPEED_BASELINE = 0.2806;
   @Unique
   private static final int LANDING_ANIM_TICKS = 13;
   @Unique
   private static final float FALL_TRIGGER_DISTANCE = 4.5F;
   @Unique
   private static final double LANDING_LEAD_TICKS = 7.0;
   @Unique
   private static final double LANDING_RAY_DISTANCE = 7.0;
   @Unique
   private int dragonminez$lastDashTickRun = -1;
   @Unique
   private int dragonminez$dashAnimTicks = 0;
   @Unique
   private int dragonminez$lastAttackTickRun = -1;
   @Unique
   private boolean dragonminez$isFlying = false;
   @Unique
   private int dragonminez$dashDirection = 0;
   @Unique
   private boolean dragonminez$isEvading = false;
   @Unique
   private int dragonminez$evasionVariant = 0;
   @Unique
   private int dragonminez$attackAnimTicks = 0;
   @Unique
   private int dragonminez$combatGraceFrames = 0;
   @Unique
   private boolean dragonminez$isOffhandAttack = false;
   @Unique
   private int dragonminez$miningAnimTicks = 0;
   @Unique
   private int dragonminez$lastMiningTickRun = -1;
   @Unique
   private boolean dragonminez$isShootingKi = false;
   @Unique
   private String dragonminez$currentMeleeAnim = null;
   @Unique
   private String dragonminez$currentPoseAnim = null;
   @Unique
   private boolean dragonminez$poseInstantResume = false;
   @Unique
   private float dragonminez$currentMeleeSpeed = 1.0F;
   @Unique
   private static final int POSE_TRANSITION_TICKS = 4;
   @Unique
   private String dragonminez$currentKiAnim = null;
   @Unique
   private String dragonminez$lastKiAnim = null;
   @Unique
   private boolean dragonminez$kiAnimHold = true;
   @Unique
   private int dragonminez$kiAnimTicks = 0;
   @Unique
   private int dragonminez$lastKiTickRun = -1;
   @Unique
   private String dragonminez$lastKiCtlAnim = null;
   @Unique
   private static final int KI_FIRE_ONESHOT_TICKS = 14;
   @Unique
   private static final double FUSION_PAIR_RANGE_SQ = 64.0;

   @Unique
   private boolean dragonminez$isActuallyMoving(AbstractClientPlayer player) {
      int currentTick = player.tickCount;
      if (currentTick == this.dragonminez$lastTickCount) {
         return this.dragonminez$isMovingState;
      } else {
         this.dragonminez$lastTickCount = currentTick;
         if (Double.isNaN(this.dragonminez$lastPosX)) {
            this.dragonminez$lastPosX = player.getX();
            this.dragonminez$lastPosZ = player.getZ();
            this.dragonminez$wasAirborne = !player.onGround();
            return false;
         } else {
            double deltaX = player.getX() - this.dragonminez$lastPosX;
            double deltaZ = player.getZ() - this.dragonminez$lastPosZ;
            double distanceSq = deltaX * deltaX + deltaZ * deltaZ;
            this.dragonminez$lastPosX = player.getX();
            this.dragonminez$lastPosZ = player.getZ();
            this.dragonminez$horizSpeed = Math.sqrt(distanceSq);
            this.dragonminez$updateLanding(player);
            boolean isCurrentlyMoving = distanceSq > 1.0E-4;
            if (isCurrentlyMoving) {
               this.dragonminez$isMovingState = true;
               this.dragonminez$stoppedTicks = 0;
            } else {
               this.dragonminez$stoppedTicks++;
               if (this.dragonminez$stoppedTicks >= 3) {
                  this.dragonminez$isMovingState = false;
               }
            }

            return this.dragonminez$isMovingState;
         }
      }
   }

   @Unique
   private void dragonminez$updateLanding(AbstractClientPlayer player) {
      boolean onGround = player.onGround();
      if (!onGround) {
         this.dragonminez$maxFallDistance = Math.max(this.dragonminez$maxFallDistance, player.fallDistance);
         double descentSpeed = -player.getDeltaMovement().y;
         if (!this.dragonminez$landingTriggered
            && this.dragonminez$maxFallDistance >= 4.5F
            && descentSpeed > 0.1
            && this.dragonminez$groundClearance(player) / descentSpeed <= 7.0) {
            this.dragonminez$landingTicks = 13;
            this.dragonminez$landingTriggered = true;
         }
      } else {
         this.dragonminez$maxFallDistance = 0.0F;
         this.dragonminez$landingTriggered = false;
      }

      this.dragonminez$wasAirborne = !onGround;
      if (this.dragonminez$landingTicks > 0) {
         this.dragonminez$landingTicks--;
      }
   }

   @Unique
   private double dragonminez$groundClearance(AbstractClientPlayer player) {
      Vec3 start = new Vec3(player.getX(), player.getBoundingBox().minY, player.getZ());
      Vec3 end = start.add(0.0, -7.0, 0.0);
      HitResult hit = player.level().clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, player));
      return hit.getType() == Type.MISS ? Double.MAX_VALUE : start.y - hit.getLocation().y;
   }

   @Unique
   private float dragonminez$movementSpeedFactor(double baseline) {
      return (float)Mth.clamp(this.dragonminez$horizSpeed / baseline, 0.6, 1.8);
   }

   @Unique
   private AbstractClientPlayer dragonminez$findFusionPartner(AbstractClientPlayer player) {
      AbstractClientPlayer partner = null;
      double bestSq = Double.MAX_VALUE;

      for (Player other : player.level().players()) {
         if (other != player && other instanceof AbstractClientPlayer) {
            AbstractClientPlayer candidate = (AbstractClientPlayer)other;
            boolean otherFusing = StatsProvider.get(StatsCapability.INSTANCE, other)
               .map(d -> d.getStatus().isActionCharging() && d.getStatus().getSelectedAction() == ActionMode.FUSION)
               .orElse(false);
            if (otherFusing) {
               double dSq = player.distanceToSqr(other);
               if (dSq <= 64.0 && dSq < bestSq) {
                  bestSq = dSq;
                  partner = candidate;
               }
            }
         }
      }

      return partner;
   }

   @Unique
   private AbstractClientPlayer dragonminez$findPotaraPartner(AbstractClientPlayer player, UUID partnerUUID) {
      if (partnerUUID == null) {
         return null;
      } else {
         return player.level().getPlayerByUUID(partnerUUID) instanceof AbstractClientPlayer candidate ? candidate : null;
      }
   }

   @Unique
   private RawAnimation dragonminez$resolveFlyAnimation(AbstractClientPlayer player) {
      Minecraft mc = Minecraft.getInstance();
      if (player == mc.player) {
         boolean forward = mc.options.keyUp.isDown();
         boolean back = mc.options.keyDown.isDown();
         boolean left = mc.options.keyLeft.isDown();
         boolean right = mc.options.keyRight.isDown();
         if (forward) {
            return BaseAnimations.FLY_FRONT;
         } else if (back) {
            return BaseAnimations.FLY_BACK;
         } else if (left) {
            return BaseAnimations.FLY_LEFT;
         } else {
            return right ? BaseAnimations.FLY_RIGHT : BaseAnimations.FLY_IDLE;
         }
      } else {
         double moveX = player.getX() - player.xOld;
         double moveZ = player.getZ() - player.zOld;
         double horizontal = Math.sqrt(moveX * moveX + moveZ * moveZ);
         if (horizontal < 0.01) {
            return BaseAnimations.FLY_IDLE;
         } else {
            float yawRad = player.yBodyRot * (float) (Math.PI / 180.0);
            double sin = (double)Mth.sin(yawRad);
            double cos = (double)Mth.cos(yawRad);
            double forwardComp = -moveX * sin + moveZ * cos;
            double rightComp = -moveX * cos - moveZ * sin;
            if (Math.abs(forwardComp) >= Math.abs(rightComp)) {
               return forwardComp >= 0.0 ? BaseAnimations.FLY_FRONT : BaseAnimations.FLY_BACK;
            } else {
               return rightComp >= 0.0 ? BaseAnimations.FLY_RIGHT : BaseAnimations.FLY_LEFT;
            }
         }
      }
   }

   @Unique
   private static boolean dragonminez$isEatingFood(AbstractClientPlayer player) {
      if (!player.isUsingItem()) {
         return false;
      } else {
         UseAnim anim = player.getUseItem().getUseAnimation();
         return anim == UseAnim.EAT || anim == UseAnim.DRINK;
      }
   }

   public void registerControllers(ControllerRegistrar registrar) {
      registrar.add(new AnimationController(this, "controller", 4, this::predicate));
      registrar.add(new AnimationController(this, "attack_controller", 0, this::attackPredicate));
      registrar.add(new AnimationController(this, "mining_controller", 0, this::miningPredicate));
      registrar.add(new AnimationController(this, "block_controller", 3, this::blockPredicate));
      registrar.add(new AnimationController(this, "shield_controller", 3, this::shieldPredicate));
      registrar.add(new AnimationController(this, "tailcontroller", 0, this::tailpredicate));
      registrar.add(new AnimationController(this, "dash_controller", 0, this::dashPredicate));
      registrar.add(new AnimationController(this, "pose_controller", 4, this::posePredicate));
      registrar.add(new AnimationController(this, "ki_controller", 4, this::kiPredicate));
      registrar.add(new AnimationController(this, "eat_controller", 3, this::eatPredicate));
   }

   @Unique
   private <T extends GeoAnimatable> PlayState eatPredicate(AnimationState<T> state) {
      AbstractClientPlayer player = (AbstractClientPlayer)this;
      AnimationController<T> ctl = state.getController();
      if (dragonminez$isEatingFood(player)) {
         if (!this.dragonminez$wasEating) {
            ctl.setAnimation(BaseAnimations.EAT);
            ctl.forceAnimationReset();
            this.dragonminez$wasEating = true;
         }

         return PlayState.CONTINUE;
      } else {
         this.dragonminez$wasEating = false;
         return PlayState.STOP;
      }
   }

   @Unique
   private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> state) {
      AbstractClientPlayer player = (AbstractClientPlayer)this;
      state.getController().setAnimationSpeed(1.0);
      if (this.dragonminez$currentKiAnim != null && this.dragonminez$kiAnimHold) {
         if (!this.dragonminez$currentKiAnim.equals(this.dragonminez$lastKiAnim)) {
            state.getController().setAnimation(AnimationCache.getPlayAndHold(this.dragonminez$currentKiAnim));
            state.getController().forceAnimationReset();
            this.dragonminez$lastKiAnim = this.dragonminez$currentKiAnim;
         }

         return PlayState.CONTINUE;
      } else {
         this.dragonminez$lastKiAnim = null;
         if (this.dragonminez$dashAnimTicks > 0) {
            return PlayState.STOP;
         } else {
            boolean isMoving = this.dragonminez$isActuallyMoving(player);
            this.dragonminez$currentPoseAnim = CombatAnimationResolver.resolvePlayerPose(player);
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            if (data == null) {
               return state.setAndContinue(BaseAnimations.IDLE);
            } else if (data.getStatus().getPotaraPoseTimer() > 0) {
               AbstractClientPlayer potaraPartner = this.dragonminez$findPotaraPartner(player, data.getStatus().getPotaraPartnerUUID());
               if (potaraPartner != null) {
                  AbstractClientPlayer leader = player.getUUID().compareTo(potaraPartner.getUUID()) < 0 ? player : potaraPartner;
                  double refRad = Math.toRadians((double)leader.getYRot());
                  double rightX = -Math.cos(refRad);
                  double rightZ = -Math.sin(refRad);
                  double side = (player.getX() - potaraPartner.getX()) * rightX + (player.getZ() - potaraPartner.getZ()) * rightZ;
                  return state.setAndContinue(side > 0.0 ? BaseAnimations.FUSION_POTHALA_LEFT : BaseAnimations.FUSION_POTHALA_RIGHT);
               } else {
                  return state.setAndContinue(BaseAnimations.FUSION_POTHALA_RIGHT);
               }
            } else {
               boolean isDraining = data.getCooldowns().hasCooldown("DrainActive");
               boolean flySkillActive = data.getSkills().isSkillActive("fly");
               boolean isChargingKi = data.getStatus().isChargingKi();
               boolean isBlocking = data.getStatus().isBlocking();
               boolean isKnockedDown = data.getStatus().isKnockedDown();
               boolean isOozaru = data.getCharacter().isOozaruCached();
               FormConfig.FormData nextFormConfig = TransformationsHelper.getNextAvailableForm(data);
               FormConfig.FormData nextStackFormConfig = TransformationsHelper.getNextAvailableStackForm(data);
               String nextForm = nextFormConfig != null ? nextFormConfig.getName().toLowerCase() : "";
               boolean isTransforming = data.getStatus().isActionCharging();
               ActionMode actionMode = data.getStatus().getSelectedAction();
               if (isKnockedDown) {
                  return state.setAndContinue(BaseAnimations.KNOCKBACK_HORIZONTAL);
               } else if (isDraining) {
                  return state.setAndContinue(BaseAnimations.DRAIN);
               } else if (player.isPassenger()) {
                  return state.setAndContinue(BaseAnimations.SIT);
               } else if (isChargingKi && !isMoving && !isBlocking) {
                  return state.setAndContinue(BaseAnimations.KI_CHARGE);
               } else if (isTransforming && actionMode.equals(ActionMode.FORM)) {
                  if (nextFormConfig != null && nextFormConfig.hasTransformationAnimation()) {
                     return state.setAndContinue(AnimationCache.getPlay(nextFormConfig.getTransformationAnimation()));
                  } else {
                     return nextForm.contains("ozaru")
                        ? state.setAndContinue(BaseAnimations.OOZARU_TRANSFORMATION)
                        : state.setAndContinue(BaseAnimations.TRANSFORMATION);
                  }
               } else if (isTransforming && actionMode.equals(ActionMode.STACK)) {
                  return nextStackFormConfig != null && nextStackFormConfig.hasTransformationAnimation()
                     ? state.setAndContinue(AnimationCache.getPlay(nextStackFormConfig.getTransformationAnimation()))
                     : state.setAndContinue(BaseAnimations.TRANSFORMATION);
               } else if (isTransforming && actionMode.equals(ActionMode.RACIAL)) {
                  return state.setAndContinue(BaseAnimations.ABSORB);
               } else if (isTransforming && actionMode.equals(ActionMode.FUSION)) {
                  AbstractClientPlayer fusionPartner = this.dragonminez$findFusionPartner(player);
                  if (fusionPartner != null) {
                     AbstractClientPlayer leader = player.getUUID().compareTo(fusionPartner.getUUID()) < 0 ? player : fusionPartner;
                     double refRad = Math.toRadians((double)leader.getYRot());
                     double rightX = -Math.cos(refRad);
                     double rightZ = -Math.sin(refRad);
                     double side = (player.getX() - fusionPartner.getX()) * rightX + (player.getZ() - fusionPartner.getZ()) * rightZ;
                     state.getController().setAnimationSpeed(0.583);
                     return state.setAndContinue(side > 0.0 ? BaseAnimations.FUSION_DANCE_LEFT : BaseAnimations.FUSION_DANCE_RIGHT);
                  } else {
                     return state.setAndContinue(BaseAnimations.TRANSFORMATION);
                  }
               } else if (isTransforming) {
                  return state.setAndContinue(BaseAnimations.TRANSFORMATION);
               } else if (player.isSwimming()) {
                  return state.setAndContinue(BaseAnimations.SWIMMING);
               } else if (player.isVisuallyCrawling()) {
                  return isMoving ? state.setAndContinue(BaseAnimations.CRAWLING_MOVE) : state.setAndContinue(BaseAnimations.CRAWLING);
               } else if (flySkillActive || player.isFallFlying() || this.dragonminez$isFlying() || player.getAbilities().flying) {
                  return FlySkillEvent.getInstance().isFlyingFast(player)
                     ? state.setAndContinue(BaseAnimations.FLY_FAST)
                     : state.setAndContinue(this.dragonminez$resolveFlyAnimation(player));
               } else if (player.onClimbable() && !player.onGround()) {
                  return state.setAndContinue(BaseAnimations.CLIMB);
               } else if (this.dragonminez$landingTicks > 0 && !player.isCrouching()) {
                  return state.setAndContinue(BaseAnimations.LANDING);
               } else if (player.onGround()) {
                  if (player.isCrouching()) {
                     return isMoving ? state.setAndContinue(BaseAnimations.CROUCHING_WALK) : state.setAndContinue(BaseAnimations.CROUCHING);
                  } else if (isMoving && player.isSprinting()) {
                     state.getController().setAnimationSpeed((double)this.dragonminez$movementSpeedFactor(0.2806));
                     return state.setAndContinue(BaseAnimations.RUN);
                  } else if (isMoving) {
                     state.getController().setAnimationSpeed((double)this.dragonminez$movementSpeedFactor(0.2158));
                     return isOozaru ? state.setAndContinue(BaseAnimations.WALK_OOZARU) : state.setAndContinue(BaseAnimations.WALK);
                  } else {
                     return isOozaru ? state.setAndContinue(BaseAnimations.IDLE_OOZARU) : state.setAndContinue(BaseAnimations.IDLE);
                  }
               } else {
                  return player.getDeltaMovement().y < -0.08 ? state.setAndContinue(BaseAnimations.FLY_IDLE) : state.setAndContinue(BaseAnimations.JUMP);
               }
            }
         }
      }
   }

   @Unique
   private <T extends GeoAnimatable> PlayState kiPredicate(AnimationState<T> state) {
      AbstractClientPlayer player = (AbstractClientPlayer)this;
      if (this.dragonminez$currentKiAnim != null && !this.dragonminez$kiAnimHold) {
         AnimationController<T> ctl = state.getController();
         boolean isFire = this.dragonminez$currentKiAnim.endsWith("_fire");
         if (!isFire) {
            if (!this.dragonminez$currentKiAnim.equals(this.dragonminez$lastKiCtlAnim)) {
               ctl.setAnimation(AnimationCache.getPlayAndHold(this.dragonminez$currentKiAnim));
               ctl.forceAnimationReset();
               this.dragonminez$lastKiCtlAnim = this.dragonminez$currentKiAnim;
            }

            return PlayState.CONTINUE;
         } else {
            if (!this.dragonminez$currentKiAnim.equals(this.dragonminez$lastKiCtlAnim)) {
               ctl.setAnimation(AnimationCache.getPlay(this.dragonminez$currentKiAnim));
               ctl.forceAnimationReset();
               this.dragonminez$lastKiCtlAnim = this.dragonminez$currentKiAnim;
               this.dragonminez$kiAnimTicks = 14;
            }

            if (player.tickCount != this.dragonminez$lastKiTickRun) {
               this.dragonminez$lastKiTickRun = player.tickCount;
               if (this.dragonminez$kiAnimTicks > 0) {
                  this.dragonminez$kiAnimTicks--;
               }
            }

            if (this.dragonminez$kiAnimTicks > 0) {
               return PlayState.CONTINUE;
            } else {
               this.dragonminez$currentKiAnim = null;
               this.dragonminez$lastKiCtlAnim = null;
               return PlayState.STOP;
            }
         }
      } else {
         this.dragonminez$lastKiCtlAnim = null;
         return PlayState.STOP;
      }
   }

   @Unique
   private <T extends GeoAnimatable> PlayState posePredicate(AnimationState<T> state) {
      AbstractClientPlayer player = (AbstractClientPlayer)this;
      if (this.dragonminez$attackAnimTicks > 0) {
         this.dragonminez$poseInstantResume = true;
         return PlayState.STOP;
      } else if (this.dragonminez$dashAnimTicks > 0) {
         return PlayState.STOP;
      } else if (this.dragonminez$isShootingKi) {
         return PlayState.STOP;
      } else if (this.dragonminez$currentKiAnim != null) {
         return PlayState.STOP;
      } else if (!player.isSwimming() && !player.isVisuallyCrawling() && !player.isPassenger()) {
         StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
         if (data == null || !data.getStatus().isBlocking() && !data.getStatus().isChargingKi()) {
            if (this.dragonminez$currentPoseAnim != null && !this.dragonminez$currentPoseAnim.isEmpty()) {
               state.getController().transitionLength(this.dragonminez$poseInstantResume ? 0 : 4);
               this.dragonminez$poseInstantResume = false;
               return state.setAndContinue(AnimationCache.getLoop(this.dragonminez$currentPoseAnim));
            } else {
               return PlayState.STOP;
            }
         } else {
            return PlayState.STOP;
         }
      } else {
         return PlayState.STOP;
      }
   }

   @Unique
   private <T extends GeoAnimatable> PlayState tailpredicate(AnimationState<T> state) {
      AbstractClientPlayer player = (AbstractClientPlayer)this;
      StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
      if (data == null) {
         return PlayState.STOP;
      } else {
         String race = data.getCharacter().getRaceName().toLowerCase();
         if (race.equals("bioandroid") && data.getCooldowns().hasCooldown("DrainActive")) {
            return PlayState.STOP;
         } else {
            state.getController().setAnimation(BaseAnimations.TAIL);
            return PlayState.CONTINUE;
         }
      }
   }

   @Unique
   private <T extends GeoAnimatable> PlayState attackPredicate(AnimationState<T> state) {
      AbstractClientPlayer player = (AbstractClientPlayer)this;
      if (player.isSleeping()) {
         this.dragonminez$attackAnimTicks = 0;
         this.dragonminez$currentMeleeAnim = null;
         return PlayState.STOP;
      } else {
         if (player.tickCount != this.dragonminez$lastAttackTickRun) {
            this.dragonminez$lastAttackTickRun = player.tickCount;
            if (this.dragonminez$attackAnimTicks > 0) {
               this.dragonminez$attackAnimTicks--;
            }
         }

         AnimationController<T> ctl = state.getController();
         boolean hasSwingFrame = player.attackAnim > 0.0F || player.swinging || player.swingTime > 0;
         if (this.dragonminez$currentMeleeAnim != null) {
            ctl.setAnimationSpeed((double)this.dragonminez$currentMeleeSpeed);
            if (!this.dragonminez$currentMeleeAnim.equals("fallback")) {
               ctl.setAnimation(AnimationCache.getPlay(this.dragonminez$currentMeleeAnim));
            } else {
               ctl.setAnimation(BaseAnimations.ATTACK);
            }

            ctl.forceAnimationReset();
            this.dragonminez$currentMeleeAnim = null;
            this.dragonminez$attackAnimTicks = Math.max(8, Math.round(12.0F / Math.max(this.dragonminez$currentMeleeSpeed, 0.1F)));
            return PlayState.CONTINUE;
         } else if (this.dragonminez$attackAnimTicks > 0 || !hasSwingFrame || !shouldPlayUnarmedAttack(player) && !shouldPlayGenericAttack(player)) {
            if (this.dragonminez$attackAnimTicks > 0) {
               return PlayState.CONTINUE;
            } else {
               ctl.setAnimationSpeed(1.0);
               return PlayState.STOP;
            }
         } else {
            ctl.setAnimationSpeed(1.0);
            ctl.setAnimation(BaseAnimations.MINING1);
            ctl.forceAnimationReset();
            this.dragonminez$attackAnimTicks = 8;
            return PlayState.CONTINUE;
         }
      }
   }

   @Unique
   private <T extends GeoAnimatable> PlayState miningPredicate(AnimationState<T> state) {
      AbstractClientPlayer player = (AbstractClientPlayer)this;
      AnimationController<T> ctl = state.getController();
      if (!player.isSleeping() && !dragonminez$isEatingFood(player)) {
         boolean hasMiningSwing = player.attackAnim > 0.0F || player.swinging || player.swingTime > 0;
         if (player.tickCount != this.dragonminez$lastMiningTickRun) {
            this.dragonminez$lastMiningTickRun = player.tickCount;
            if (this.dragonminez$miningAnimTicks > 0) {
               this.dragonminez$miningAnimTicks--;
            }
         }

         if (isPlacingBlock(player) && !isBlocking(player)) {
            if (ctl.getAnimationState() == State.STOPPED) {
               RawAnimation placeAnim = isMainHandBlock(player) ? BaseAnimations.ATTACK : BaseAnimations.ATTACK2;
               ctl.setAnimation(placeAnim);
               ctl.forceAnimationReset();
            }

            this.dragonminez$miningAnimTicks = 8;
            return PlayState.CONTINUE;
         } else if (hasMiningSwing && isUsingTool(player) && !isPlacingBlock(player) && !isBlocking(player)) {
            if (ctl.getAnimationState() == State.STOPPED) {
               if (isMainHandTool(player)) {
                  ctl.setAnimation(BaseAnimations.MINING1);
               } else if (isOffHandTool(player)) {
                  ctl.setAnimation(BaseAnimations.MINING2);
               }

               ctl.forceAnimationReset();
            }

            this.dragonminez$miningAnimTicks = 10;
            return PlayState.CONTINUE;
         } else {
            return this.dragonminez$miningAnimTicks > 0 ? PlayState.CONTINUE : PlayState.STOP;
         }
      } else {
         this.dragonminez$miningAnimTicks = 0;
         return PlayState.STOP;
      }
   }

   @Unique
   private <T extends GeoAnimatable> PlayState blockPredicate(AnimationState<T> state) {
      AbstractClientPlayer player = (AbstractClientPlayer)this;
      AnimationController<T> ctl = state.getController();
      StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
      if (data != null && data.getStatus().isBlocking()) {
         if (ctl.getAnimationState() == State.STOPPED || !ctl.getCurrentRawAnimation().equals(BaseAnimations.BLOCK)) {
            ctl.setAnimation(BaseAnimations.BLOCK);
            ctl.forceAnimationReset();
         }

         return PlayState.CONTINUE;
      } else {
         return PlayState.STOP;
      }
   }

   @Unique
   private <T extends GeoAnimatable> PlayState shieldPredicate(AnimationState<T> state) {
      AbstractClientPlayer player = (AbstractClientPlayer)this;
      AnimationController<T> ctl = state.getController();
      ItemStack mainHand = player.getMainHandItem();
      ItemStack offHand = player.getOffhandItem();
      boolean mainHandIsShield = mainHand.getItem() instanceof ShieldItem;
      boolean offHandIsShield = offHand.getItem() instanceof ShieldItem;
      if (player.isUsingItem() && (mainHandIsShield || offHandIsShield)) {
         boolean isRightHanded = player.getMainArm() == HumanoidArm.RIGHT;
         RawAnimation targetAnimation;
         if (mainHandIsShield) {
            targetAnimation = isRightHanded ? BaseAnimations.SHIELD_RIGHT : BaseAnimations.SHIELD_LEFT;
         } else {
            targetAnimation = isRightHanded ? BaseAnimations.SHIELD_LEFT : BaseAnimations.SHIELD_RIGHT;
         }

         if (ctl.getAnimationState() == State.STOPPED || !ctl.getCurrentRawAnimation().equals(targetAnimation)) {
            ctl.setAnimation(targetAnimation);
            ctl.forceAnimationReset();
         }

         return PlayState.CONTINUE;
      } else {
         return PlayState.STOP;
      }
   }

   @Unique
   private <T extends GeoAnimatable> PlayState dashPredicate(AnimationState<T> state) {
      AbstractClientPlayer player = (AbstractClientPlayer)this;
      AnimationController<T> ctl = state.getController();
      if (player.tickCount != this.dragonminez$lastDashTickRun) {
         this.dragonminez$lastDashTickRun = player.tickCount;
         if (this.dragonminez$dashAnimTicks > 0) {
            this.dragonminez$dashAnimTicks--;
         }
      }

      if (this.dragonminez$dashAnimTicks > 0) {
         return PlayState.CONTINUE;
      } else if (this.dragonminez$isEvading) {
         RawAnimation evasionAnim = switch (this.dragonminez$evasionVariant) {
            case 1 -> BaseAnimations.EVASION_FRONT;
            case 2 -> BaseAnimations.EVASION_BACK;
            case 3 -> BaseAnimations.EVASION_LEFT;
            default -> BaseAnimations.EVASION_RIGHT;
         };
         ctl.setAnimation(evasionAnim);
         ctl.forceAnimationReset();
         this.dragonminez$dashAnimTicks = 12;
         this.dragonminez$isEvading = false;
         return PlayState.CONTINUE;
      } else if (this.dragonminez$dashDirection != 0) {
         RawAnimation dashAnim = switch (this.dragonminez$dashDirection) {
            case 2 -> BaseAnimations.DASH_BACKWARD;
            case 3 -> BaseAnimations.DASH_RIGHT;
            case 4 -> BaseAnimations.DASH_LEFT;
            default -> BaseAnimations.DASH_FORWARD;
            case 6 -> BaseAnimations.DOUBLEDASH_BACKWARD;
            case 7 -> BaseAnimations.DOUBLEDASH_RIGHT;
            case 8 -> BaseAnimations.DOUBLEDASH_LEFT;
         };
         ctl.setAnimation(dashAnim);
         ctl.forceAnimationReset();
         this.dragonminez$dashAnimTicks = 12;
         this.dragonminez$dashDirection = 0;
         return PlayState.CONTINUE;
      } else {
         return PlayState.STOP;
      }
   }

   @Unique
   private static boolean isUsingTool(AbstractClientPlayer player) {
      Item mainHand = player.getMainHandItem().getItem();
      Item offHand = player.getOffhandItem().getItem();
      return mainHand.getDescriptionId().contains("pickaxe")
         || mainHand.getDescriptionId().contains("axe")
         || mainHand.getDescriptionId().contains("shovel")
         || mainHand.getDescriptionId().contains("hoe")
         || offHand.getDescriptionId().contains("pickaxe")
         || offHand.getDescriptionId().contains("axe")
         || offHand.getDescriptionId().contains("shovel")
         || offHand.getDescriptionId().contains("hoe");
   }

   @Unique
   private static boolean isMainHandTool(AbstractClientPlayer player) {
      Item mainHand = player.getMainHandItem().getItem();
      return mainHand.getDescriptionId().contains("pickaxe")
         || mainHand.getDescriptionId().contains("axe")
         || mainHand.getDescriptionId().contains("shovel")
         || mainHand.getDescriptionId().contains("hoe");
   }

   @Unique
   private static boolean isOffHandTool(AbstractClientPlayer player) {
      Item offHand = player.getOffhandItem().getItem();
      return offHand.getDescriptionId().contains("pickaxe")
         || offHand.getDescriptionId().contains("axe")
         || offHand.getDescriptionId().contains("shovel")
         || offHand.getDescriptionId().contains("hoe");
   }

   @Unique
   private static boolean isMainHandBlock(AbstractClientPlayer player) {
      return player.getMainHandItem().getItem() instanceof BlockItem;
   }

   @Unique
   private static boolean isPlacingBlock(AbstractClientPlayer player) {
      ItemStack mainHand = player.getMainHandItem();
      ItemStack offHand = player.getOffhandItem();
      boolean hasBlockInMainHand = mainHand.getItem() instanceof BlockItem;
      boolean hasBlockInOffHand = offHand.getItem() instanceof BlockItem;
      boolean hasPlaceActionFrame = player.isUsingItem() && !dragonminez$isEatingFood(player) || player.swinging || player.swingTime > 0;
      return (hasBlockInMainHand || hasBlockInOffHand) && hasPlaceActionFrame;
   }

   @Unique
   private static boolean isBlocking(AbstractClientPlayer player) {
      StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
      return data != null && data.getStatus().isBlocking();
   }

   @Unique
   private static boolean shouldPlayUnarmedAttack(AbstractClientPlayer player) {
      return player.getMainHandItem().isEmpty()
         && player.getOffhandItem().isEmpty()
         && !player.isUsingItem()
         && !isBlocking(player)
         && !isPlacingBlock(player)
         && !isUsingTool(player);
   }

   @Unique
   private static boolean shouldPlayGenericAttack(AbstractClientPlayer player) {
      if (player.isUsingItem()) {
         return false;
      } else if (!isBlocking(player) && !isPlacingBlock(player) && !isUsingTool(player)) {
         if (player instanceof PlayerAttackProperties props) {
            AttackHand hand = PlayerAttackHelper.getCurrentAttack(player, props.getComboCount());
            if (hand != null) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public double getTick(Object o) {
      return (double)((AbstractClientPlayer)o).tickCount;
   }

   public double getBoneResetTime() {
      return this.dragonminez$attackAnimTicks <= 0 && this.dragonminez$combatGraceFrames <= 0 ? 5.0 : 0.0;
   }

   @Override
   public void dragonminez$setFlying(boolean flying) {
      this.dragonminez$isFlying = flying;
   }

   @Override
   public boolean dragonminez$isFlying() {
      return this.dragonminez$isFlying;
   }

   @Override
   public void dragonminez$triggerDash(int direction) {
      this.dragonminez$dashDirection = direction;
   }

   @Override
   public void dragonminez$triggerEvasion() {
      this.dragonminez$isEvading = true;
      this.dragonminez$evasionVariant = (int)(Math.random() * 4.0) + 1;
   }

   @Override
   public void dragonminez$setShootingKi(boolean shootingKi) {
      this.dragonminez$isShootingKi = shootingKi;
   }

   @Override
   public boolean dragonminez$isShootingKi() {
      return this.dragonminez$isShootingKi;
   }

   @Override
   public void dragonminez$playMeleeAnimation(String animationName, boolean isOffhand, float speedMultiplier) {
      AbstractClientPlayer self = (AbstractClientPlayer)this;
      boolean isLocal = self == Minecraft.getInstance().player;
      boolean vanillaFirstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson() && !FirstPersonManager.shouldRenderFirstPerson(self);
      if (isLocal && vanillaFirstPerson) {
         this.dragonminez$currentMeleeAnim = null;
         this.dragonminez$isOffhandAttack = isOffhand;
      } else {
         ItemStack attackingStack = isOffhand ? self.getOffhandItem() : self.getMainHandItem();
         boolean armSpecific = !attackingStack.isEmpty() && !PlayerAttackHelper.isTwoHandedWielding(self);
         boolean useLeftArm = armSpecific && isOffhand != (self.getMainArm() == HumanoidArm.LEFT);
         String resolved = CombatAnimationResolver.resolveAttack(animationName, useLeftArm);
         this.dragonminez$currentMeleeAnim = resolved.isEmpty() ? "fallback" : resolved;
         this.dragonminez$currentMeleeSpeed = Math.max(0.15F, speedMultiplier);
         this.dragonminez$isOffhandAttack = isOffhand;
      }
   }

   @Override
   public boolean dragonminez$isPlayingCombatAnimation() {
      if (this.dragonminez$attackAnimTicks > 0) {
         this.dragonminez$combatGraceFrames = 8;
         return true;
      } else if (this.dragonminez$combatGraceFrames > 0) {
         this.dragonminez$combatGraceFrames--;
         return true;
      } else {
         return this.dragonminez$currentPoseAnim != null && !this.dragonminez$currentPoseAnim.isEmpty();
      }
   }

   @Override
   public boolean dragonminez$isAttackingWithOffhand() {
      return this.dragonminez$isOffhandAttack;
   }

   @Override
   public float dragonminez$getCombatPlacementWeight() {
      return this.dragonminez$attackAnimTicks > 0 ? 1.0F : 0.0F;
   }

   @Override
   public void dragonminez$playKiAnimation(String animationName, boolean hold) {
      this.dragonminez$currentKiAnim = animationName;
      this.dragonminez$kiAnimHold = hold;
      this.dragonminez$kiAnimTicks = 0;
   }

   @Override
   public void dragonminez$stopKiAnimation() {
      this.dragonminez$currentKiAnim = null;
      this.dragonminez$lastKiAnim = null;
      this.dragonminez$lastKiCtlAnim = null;
      this.dragonminez$kiAnimHold = true;
      this.dragonminez$kiAnimTicks = 0;
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }

   @Override
   public String dragonminez$getCurrentPlayingAnimation() {
      long instanceId = (long)((AbstractClientPlayer)this).getId();
      AnimatableManager<GeoAnimatable> manager = this.geoCache.getManagerForId(instanceId);
      if (manager != null) {
         AnimationController<?> controller = (AnimationController<?>)manager.getAnimationControllers().get("controller");
         if (controller != null && controller.getCurrentAnimation() != null) {
            return controller.getCurrentAnimation().animation().name();
         }
      }

      return "";
   }
}
