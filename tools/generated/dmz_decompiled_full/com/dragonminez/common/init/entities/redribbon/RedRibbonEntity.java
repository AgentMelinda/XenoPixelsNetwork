package com.dragonminez.common.init.entities.redribbon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
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

public class RedRibbonEntity extends Monster implements GeoEntity {
   private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);
   private boolean isAttacking = false;

   public RedRibbonEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 20.0)
         .add(Attributes.MOVEMENT_SPEED, 0.2)
         .add(Attributes.ATTACK_DAMAGE, 3.5)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(1, new FloatGoal(this));
      this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.6, false));
      this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0));
      this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 15.0F));
      this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[0]));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Villager.class, true));
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "base_controller", 5, this::walkPredicate));
      controllers.add(new AnimationController(this, "attack_controller", 0, this::attackPredicate));
   }

   private <T extends GeoAnimatable> PlayState walkPredicate(AnimationState<T> event) {
      RedRibbonEntity entity = (RedRibbonEntity)event.getAnimatable();
      if (!event.isMoving()) {
         event.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
         return PlayState.CONTINUE;
      } else {
         if (!entity.isAggressive() && entity.getTarget() == null) {
            event.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
         } else {
            event.getController().setAnimation(RawAnimation.begin().thenLoop("run"));
         }

         return PlayState.CONTINUE;
      }
   }

   private <T extends GeoAnimatable> PlayState attackPredicate(AnimationState<T> event) {
      RedRibbonEntity entity = (RedRibbonEntity)event.getAnimatable();
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

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }

   public boolean checkSpawnRules(LevelAccessor pLevel, MobSpawnType reason) {
      return pLevel.getDifficulty() != Difficulty.PEACEFUL && this.checkSpawnObstruction(pLevel);
   }

   public static boolean canSpawnHere(
      EntityType<? extends RedRibbonEntity> entity, ServerLevelAccessor world, MobSpawnType spawn, BlockPos pos, RandomSource random
   ) {
      if (world.getDifficulty() == Difficulty.PEACEFUL) {
         return false;
      } else if (world.getBrightness(LightLayer.BLOCK, pos) > 7) {
         return false;
      } else {
         BlockState stateAtPos = world.getBlockState(pos);
         if (!stateAtPos.isAir() && !stateAtPos.canBeReplaced()) {
            return false;
         } else {
            BlockState ground = world.getBlockState(pos.below());
            return !ground.isFaceSturdy(world, pos.below(), Direction.UP)
               ? false
               : world.noCollision(entity.getDimensions().makeBoundingBox((double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5));
         }
      }
   }
}
