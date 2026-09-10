package com.dragonminez.common.init.entities.animal;

import com.dragonminez.common.init.MainSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreathAirGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;

public class NamekFrogEntity extends Animal implements GeoEntity {
   private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(NamekFrogEntity.class, EntityDataSerializers.INT);
   public static final int VARIANT_COUNT = 3;
   public static final ResourceLocation[] TEXTURES = new ResourceLocation[3];
   private AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

   public NamekFrogEntity(EntityType<? extends Animal> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(1, new FloatGoal(this));
      this.goalSelector.addGoal(2, new RandomStrollGoal(this, 1.0));
      this.goalSelector.addGoal(3, new BreathAirGoal(this));
      this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
      this.goalSelector.addGoal(1, new PanicGoal(this, 1.5));
   }

   public static AttributeSupplier createAttributes() {
      return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.22F).build();
   }

   public int getVariant() {
      return (Integer)this.entityData.get(VARIANT);
   }

   public void setVariant(int variant) {
      this.entityData.set(VARIANT, variant);
   }

   public ResourceLocation getCurrentTexture() {
      int variant = this.getVariant();
      if (variant < 0 || variant >= TEXTURES.length) {
         variant = 0;
      }

      return TEXTURES[variant];
   }

   @Nullable
   public AgeableMob getBreedOffspring(ServerLevel pLevel, AgeableMob pOtherParent) {
      return null;
   }

   public void addAdditionalSaveData(CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
      pCompound.putInt("Variant", this.getVariant());
   }

   public void readAdditionalSaveData(CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
      this.setVariant(pCompound.getInt("Variant"));
   }

   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(VARIANT, 0);
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "base_controller", 5, this::walkPredicate));
   }

   private <T extends GeoAnimatable> PlayState walkPredicate(AnimationState<T> event) {
      NamekFrogEntity entity = (NamekFrogEntity)event.getAnimatable();
      if (event.isMoving()) {
         event.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
         return PlayState.CONTINUE;
      } else {
         event.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
         return PlayState.CONTINUE;
      }
   }

   public boolean isFood(ItemStack stack) {
      return false;
   }

   public SpawnGroupData finalizeSpawn(
      ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @javax.annotation.Nullable SpawnGroupData pSpawnData
   ) {
      this.setVariant(this.random.nextInt(3));
      return super.finalizeSpawn(pLevel, pDifficulty, pReason, pSpawnData);
   }

   public void die(DamageSource source) {
      super.die(source);
      RandomSource random = this.level().random;
      if (random.nextInt(5) == 0) {
         this.playSound((SoundEvent)MainSounds.FROG_LAUGH.get(), 1.0F, 1.0F);
      }
   }

   @Nullable
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

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   static {
      for (int i = 0; i < 3; i++) {
         TEXTURES[i] = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/animal/namekfrog_" + i + ".png");
      }
   }
}
