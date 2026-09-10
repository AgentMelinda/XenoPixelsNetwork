package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.init.entities.IBattlePower;
import com.dragonminez.common.init.entities.sagas.helper.DBSagasAnimations;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.AnimationController.State;

public class SagaSaibamanEntity extends DBSagasEntity {
   private static final EntityDataAccessor<Boolean> IS_EXPLODING = SynchedEntityData.defineId(SagaSaibamanEntity.class, EntityDataSerializers.BOOLEAN);
   private boolean isAttacking = false;
   private int fuseTimer = 0;
   private int explodeTimer = 3;
   private boolean isAttached = false;
   private boolean hasCheckedExplosionChance = false;

   public SagaSaibamanEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      if (this instanceof IBattlePower bp) {
         bp.setBattlePower(1200);
      }

      this.setCanFly(false);
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 80.0)
         .add(Attributes.MOVEMENT_SPEED, 0.2)
         .add(Attributes.ATTACK_DAMAGE, 15.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.isAlive()) {
         float healthThreshold = this.getMaxHealth() * 0.25F;
         if (this.getHealth() <= healthThreshold && !this.hasCheckedExplosionChance && !this.isExploding()) {
            this.hasCheckedExplosionChance = true;
            if (this.random.nextFloat() < 0.05F) {
               this.setExploding(true);
            }
         }

         if (this.isExploding()) {
            LivingEntity target = this.getTarget();
            if (target == null) {
               target = this.level().getNearestPlayer(this, 10.0);
               if (target != null) {
                  this.setTarget(target);
               }
            }

            if (target != null) {
               if (!this.isAttached && !((double)this.distanceTo(target) <= 2.0)) {
                  this.getNavigation().moveTo(target, 1.5);
               } else {
                  this.isAttached = true;
                  Vec3 lookAngle = target.getLookAngle();
                  double behindX = target.getX() - lookAngle.x * 0.8;
                  double behindZ = target.getZ() - lookAngle.z * 0.8;
                  this.setPos(behindX, target.getY(), behindZ);
                  this.lookAt(Anchor.EYES, target.getEyePosition());
                  this.setYBodyRot(this.getYRot());
                  this.setYHeadRot(this.getYRot());
                  this.setDeltaMovement(0.0, 0.0, 0.0);
                  this.getNavigation().stop();
                  this.setAggressive(false);
                  this.fuseTimer++;
                  if (this.fuseTimer == 1 || this.fuseTimer % 15 == 0) {
                     this.playSound(SoundEvents.CREEPER_PRIMED, 1.0F, 0.5F + (float)this.fuseTimer / 60.0F);
                  }

                  if (this.fuseTimer >= this.explodeTimer * 20) {
                     this.explode();
                  }
               }
            }
         }
      }
   }

   @Override
   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "base_controller", 5, this::walkPredicate));
      controllers.add(new AnimationController(this, "attack_controller", 0, this::attackPredicate));
      controllers.add(new AnimationController(this, "explode_controller", 0, this::explodePredicate));
   }

   private <T extends GeoAnimatable> PlayState explodePredicate(AnimationState<T> event) {
      if (this.isExploding()) {
         event.getController().setAnimation(RawAnimation.begin().thenPlay("explode"));
         return PlayState.CONTINUE;
      } else {
         return PlayState.STOP;
      }
   }

   private <T extends GeoAnimatable> PlayState walkPredicate(AnimationState<T> event) {
      DBSagasEntity entity = (DBSagasEntity)event.getAnimatable();
      if (!event.isMoving()) {
         event.getController().setAnimation(DBSagasAnimations.ANIM_IDLE);
         return PlayState.CONTINUE;
      } else {
         if (!entity.isAggressive() && entity.getTarget() == null) {
            event.getController().setAnimation(DBSagasAnimations.ANIM_WALK);
         } else {
            event.getController().setAnimation(DBSagasAnimations.ANIM_RUN);
         }

         return PlayState.CONTINUE;
      }
   }

   private <T extends GeoAnimatable> PlayState attackPredicate(AnimationState<T> event) {
      DBSagasEntity entity = (DBSagasEntity)event.getAnimatable();
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

   private void explode() {
      if (!this.level().isClientSide) {
         float radius = 4.0F;
         double baseDamage = this.getAttributeValue(Attributes.ATTACK_DAMAGE);
         float finalDamage = (float)(baseDamage * 3.0);
         DamageSource damageSource = this.level().damageSources().explosion(this, this);
         AABB area = this.getBoundingBox().inflate((double)radius);

         for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (target != this && this.distanceToSqr(target) <= (double)(radius * radius)) {
               target.hurt(damageSource, finalDamage);
            }
         }

         this.level().explode(this, this.getX(), this.getY(), this.getZ(), radius, ExplosionInteraction.MOB);
         this.discard();
      }
   }

   @Override
   public boolean doHurtTarget(Entity pEntity) {
      return this.isExploding() ? false : super.doHurtTarget(pEntity);
   }

   public boolean canAttack(LivingEntity pTarget) {
      return this.isExploding() ? false : super.canAttack(pTarget);
   }

   @Override
   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(IS_EXPLODING, false);
   }

   public void setExploding(boolean exploding) {
      this.entityData.set(IS_EXPLODING, exploding);
   }

   public boolean isExploding() {
      return (Boolean)this.entityData.get(IS_EXPLODING);
   }
}
