package com.dragonminez.common.init.entities.animal;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.shapes.CollisionContext;
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

public class DinoGlobalEntity extends Monster implements GeoEntity {
   private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);
   private boolean isAttacking = false;

   protected DinoGlobalEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(1, new FloatGoal(this));
      this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
      this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0));
      this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 15.0F));
      this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[0]));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Villager.class, true));
      this.targetSelector.addGoal(4, new NearestAttackableTargetGoal(this, Animal.class, true));
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
      } else {
         event.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
         return PlayState.CONTINUE;
      }
   }

   private <T extends GeoAnimatable> PlayState attackPredicate(AnimationState<T> event) {
      DinoGlobalEntity entity = (DinoGlobalEntity)event.getAnimatable();
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

   public boolean checkSpawnRules(LevelAccessor pLevel, MobSpawnType reason) {
      return pLevel.getDifficulty() != Difficulty.PEACEFUL && this.checkSpawnObstruction(pLevel);
   }

   public static boolean canSpawnHere(
      EntityType<? extends DinoGlobalEntity> entity, ServerLevelAccessor world, MobSpawnType spawn, BlockPos pos, RandomSource random
   ) {
      if (world.getDifficulty() == Difficulty.PEACEFUL) {
         return false;
      } else if (random.nextFloat() < 0.65F) {
         return false;
      } else if (world.getBrightness(LightLayer.BLOCK, pos) > 7) {
         return false;
      } else {
         boolean solidGround = world.getBlockState(pos.below()).isSolidRender(world, pos.below());
         boolean noCollision = world.isUnobstructed(world.getBlockState(pos), pos, CollisionContext.empty());
         return solidGround && noCollision;
      }
   }
}
