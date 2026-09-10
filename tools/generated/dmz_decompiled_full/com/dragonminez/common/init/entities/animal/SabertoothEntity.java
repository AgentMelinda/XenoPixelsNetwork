package com.dragonminez.common.init.entities.animal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.AnimationController.State;

public class SabertoothEntity extends DinoGlobalEntity {
   private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(SabertoothEntity.class, EntityDataSerializers.INT);
   private boolean isAttacking = false;
   public static final int VARIANT_COUNT = 3;
   public static final ResourceLocation[] TEXTURES = new ResourceLocation[3];

   public SabertoothEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
   }

   public static Builder createAttributes() {
      return Monster.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 20.0)
         .add(Attributes.MOVEMENT_SPEED, 0.35)
         .add(Attributes.ATTACK_DAMAGE, 5.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(VARIANT, 0);
   }

   public void addAdditionalSaveData(CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
      pCompound.putInt("Variant", this.getVariantTiger());
   }

   public void readAdditionalSaveData(CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
      this.setVariantTiger(pCompound.getInt("Variant"));
   }

   public int getVariantTiger() {
      return (Integer)this.entityData.get(VARIANT);
   }

   public void setVariantTiger(int variant) {
      this.entityData.set(VARIANT, variant);
   }

   public ResourceLocation getCurrentTexture() {
      int variant = this.getVariantTiger();
      if (variant < 0 || variant >= TEXTURES.length) {
         variant = 0;
      }

      return TEXTURES[variant];
   }

   @Nullable
   public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData) {
      this.setVariantTiger(this.random.nextInt(3));
      return super.finalizeSpawn(pLevel, pDifficulty, pReason, pSpawnData);
   }

   @Override
   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "base_controller", 5, this::walkPredicate));
      controllers.add(new AnimationController(this, "attack_controller", 0, this::attackPredicate));
      controllers.add(new AnimationController(this, "tail_controller", 0, this::tailPredicate));
   }

   private <T extends GeoAnimatable> PlayState walkPredicate(AnimationState<T> event) {
      SabertoothEntity entity = (SabertoothEntity)event.getAnimatable();
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

   static {
      for (int i = 0; i < 3; i++) {
         TEXTURES[i] = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/animal/sabertooth_" + i + ".png");
      }
   }
}
