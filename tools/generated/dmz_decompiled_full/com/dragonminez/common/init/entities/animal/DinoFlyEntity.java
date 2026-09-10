package com.dragonminez.common.init.entities.animal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.AnimationController.State;

public class DinoFlyEntity extends Monster implements GeoEntity {
   private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);
   private boolean isAttacking = false;

   public DinoFlyEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.moveControl = new FlyingMoveControl(this, 20, true);
   }

   protected PathNavigation createNavigation(Level pLevel) {
      FlyingPathNavigation navigation = new FlyingPathNavigation(this, this.level());
      navigation.setCanOpenDoors(false);
      navigation.setCanFloat(true);
      navigation.setCanPassDoors(true);
      return navigation;
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 80.0)
         .add(Attributes.MOVEMENT_SPEED, 0.2)
         .add(Attributes.ATTACK_DAMAGE, 20.0)
         .add(Attributes.FLYING_SPEED, 0.6)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(1, new WaterAvoidingRandomFlyingGoal(this, 1.0));
      this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
      this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
      this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[0]));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
   }

   public boolean causeFallDamage(float pFallDistance, float pMultiplier, DamageSource pSource) {
      return false;
   }

   protected void checkFallDamage(double pY, boolean pOnGround, BlockState pState, BlockPos pPos) {
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "base_controller", 5, this::walkPredicate));
      controllers.add(new AnimationController(this, "attack_controller", 0, this::attackPredicate));
      controllers.add(new AnimationController(this, "tail_controller", 0, this::tailPredicate));
   }

   private <T extends GeoAnimatable> PlayState walkPredicate(AnimationState<T> event) {
      if (event.isMoving()) {
         event.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
         return PlayState.CONTINUE;
      } else if (!this.onGround()) {
         event.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
         return PlayState.CONTINUE;
      } else {
         event.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
         return PlayState.CONTINUE;
      }
   }

   private <T extends GeoAnimatable> PlayState attackPredicate(AnimationState<T> event) {
      DinoFlyEntity entity = (DinoFlyEntity)event.getAnimatable();
      if (entity.swingTime > 0 && !this.isAttacking) {
         this.isAttacking = true;
         event.getController().forceAnimationReset();
         event.getController().setAnimation(RawAnimation.begin().thenPlay("attack"));
         return PlayState.CONTINUE;
      } else if (this.isAttacking) {
         if (event.getController().getAnimationState() == State.STOPPED) {
            this.isAttacking = false;
            return PlayState.STOP;
         } else {
            return PlayState.CONTINUE;
         }
      } else {
         return PlayState.STOP;
      }
   }

   private <T extends GeoAnimatable> PlayState tailPredicate(AnimationState<T> event) {
      event.getController().setAnimation(RawAnimation.begin().thenLoop("tail"));
      return PlayState.CONTINUE;
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }
}
