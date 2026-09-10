package com.dragonminez.common.init.entities.animal;

import com.dragonminez.common.init.MainSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreathAirGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;

public class NamekFrogGinyuEntity extends NamekFrogEntity {
   private AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

   public NamekFrogGinyuEntity(EntityType<? extends Animal> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(1, new FloatGoal(this));
      this.goalSelector.addGoal(2, new RandomStrollGoal(this, 1.0));
      this.goalSelector.addGoal(3, new BreathAirGoal(this));
      this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
      this.goalSelector.addGoal(1, new PanicGoal(this, 1.5));
   }

   public static AttributeSupplier createAttributes() {
      return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.22F).build();
   }

   @Nullable
   @Override
   public AgeableMob getBreedOffspring(ServerLevel pLevel, AgeableMob pOtherParent) {
      return null;
   }

   @Override
   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "base_controller", 5, this::walkPredicate));
   }

   private <T extends GeoAnimatable> PlayState walkPredicate(AnimationState<T> event) {
      NamekFrogGinyuEntity entity = (NamekFrogGinyuEntity)event.getAnimatable();
      if (event.isMoving()) {
         event.getController().setAnimation(RawAnimation.begin().thenLoop("walkginyu"));
         return PlayState.CONTINUE;
      } else {
         event.getController().setAnimation(RawAnimation.begin().thenLoop("idleginyu"));
         return PlayState.CONTINUE;
      }
   }

   @Override
   public void die(DamageSource source) {
      super.die(source);
      RandomSource random = this.level().random;
      if (random.nextInt(5) == 0) {
         this.playSound((SoundEvent)MainSounds.FROG_LAUGH.get(), 1.0F, 1.0F);
      }
   }

   @Nullable
   @Override
   protected SoundEvent getAmbientSound() {
      RandomSource random = this.level().random;
      int choice = random.nextInt(3);

      return switch (choice) {
         case 0 -> (SoundEvent)MainSounds.FROG1.get();
         case 1 -> (SoundEvent)MainSounds.FROG2.get();
         case 2 -> (SoundEvent)MainSounds.FROG3.get();
         default -> null;
      };
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
