package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.init.entities.IBattlePower;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

public class SagaZFightersEntity {
   public static class BasicNPCEntity extends DBSagasEntity {
      public BasicNPCEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(false);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
      }
   }

   public static class ChaozEntity extends DBSagasEntity {
      private static final EntityDataAccessor<Boolean> IS_EXPLODING = SynchedEntityData.defineId(
         SagaZFightersEntity.ChaozEntity.class, EntityDataSerializers.BOOLEAN
      );
      private boolean isAttached = false;
      private int fuseTimer = 0;
      private int explodeTimer = 3;
      private boolean hasCheckedExplosionChance = false;

      public ChaozEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 100);
         this.setisKid(true);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 100, 1.0F, 16770657, 16770657);
      }

      @Override
      public void tick() {
         super.tick();
         if (this.isAlive()) {
            float healthThreshold = this.getMaxHealth() * 0.25F;
            if (this.getHealth() <= healthThreshold && !this.hasCheckedExplosionChance && !this.isExploding()) {
               this.hasCheckedExplosionChance = true;
               if (this.random.nextFloat() < 1.0F) {
                  this.setExploding(true);
               }
            }

            if (this.isExploding()) {
               LivingEntity target = this.getTarget();
               if (target == null) {
                  target = this.level().getNearestPlayer(this, 15.0);
                  if (target != null) {
                     this.setTarget(target);
                  }
               }

               if (target != null) {
                  if (!this.isAttached && !((double)this.distanceTo(target) <= 2.0)) {
                     this.getNavigation().moveTo(target, 1.8);
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

      private void explode() {
         if (!this.level().isClientSide) {
            float radius = 5.0F;
            double baseDamage = this.getAttributeValue(Attributes.ATTACK_DAMAGE);
            float finalDamage = (float)(baseDamage * 4.0);
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
      protected void defineSynchedData(Builder builder) {
         super.defineSynchedData(builder);
         builder.define(IS_EXPLODING, false);
      }

      public void setExploding(boolean exploding) {
         this.entityData.set(IS_EXPLODING, exploding);
      }

      public boolean isExploding() {
         return (Boolean)this.entityData.get(IS_EXPLODING);
      }

      @Override
      public void registerControllers(ControllerRegistrar controllers) {
         super.registerControllers(controllers);
         controllers.add(new AnimationController(this, "explode_controller", 0, this::explodePredicate));
      }

      private <T extends GeoAnimatable> PlayState explodePredicate(AnimationState<T> event) {
         if (this.isExploding()) {
            event.getController().setAnimation(RawAnimation.begin().thenPlay("cell_absorb"));
            return PlayState.CONTINUE;
         } else {
            return PlayState.STOP;
         }
      }
   }

   public static class SagaKibitoEntity extends DBSagasEntity {
      public SagaKibitoEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 100);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 170, 1.0F, 16770657, 16770657);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.0F, 16770657, 16770657);
      }
   }

   public static class SagaKrillinEntity extends DBSagasEntity {
      public SagaKrillinEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(13000);
         }

         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 100);
         this.setisKid(true);
         this.addKiSkill(DBSagasEntity.KiSkillType.KIENZAN, 100, 1.4F, 16776051, 16776051);
         this.addKiSkill(DBSagasEntity.KiSkillType.KAMEHAMEHA, 200);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_vegeta";
      }
   }

   public static class SagaShinEntity extends DBSagasEntity {
      public SagaShinEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 100);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 170, 1.0F, 16770657, 16770657);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.0F, 16770657, 16770657);
      }
   }

   public static class SagaTienShinhanEntity extends DBSagasEntity {
      public SagaTienShinhanEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 100);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 100, 1.0F, 16770657, 16770657);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_goku";
      }
   }

   public static class SagaYamchaEntity extends DBSagasEntity {
      public SagaYamchaEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.0F);
         this.setDBZStyle(0);
         this.setEvade(true, 100);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 100, 1.0F, 16770657, 16770657);
         this.addKiSkill(DBSagasEntity.KiSkillType.KAMEHAMEHA, 200);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_yamcha";
      }
   }
}
