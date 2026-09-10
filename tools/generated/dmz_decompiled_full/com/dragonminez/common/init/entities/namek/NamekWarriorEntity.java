package com.dragonminez.common.init.entities.namek;

import com.dragonminez.common.init.entities.IBattlePower;
import com.dragonminez.common.init.entities.goals.NamekDefendVillageGoal;
import com.dragonminez.common.init.entities.goals.VillageAlertSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveBackToVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
import software.bernie.geckolib.animation.AnimationController.State;

public class NamekWarriorEntity extends PathfinderMob implements GeoEntity {
   private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);
   private boolean isAttacking = false;
   private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(NamekWarriorEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> IS_FLYING = SynchedEntityData.defineId(NamekWarriorEntity.class, EntityDataSerializers.BOOLEAN);
   public static final int VARIANT_COUNT = 4;
   public static final ResourceLocation[] TEXTURES = new ResourceLocation[4];

   public NamekWarriorEntity(EntityType<? extends PathfinderMob> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      VillageAlertSystem.registerWarrior(this);
      if (this instanceof IBattlePower bp) {
         bp.setBattlePower(750);
      }
   }

   public static Builder createAttributes() {
      return Monster.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 200.0)
         .add(Attributes.MOVEMENT_SPEED, 0.2)
         .add(Attributes.ATTACK_DAMAGE, 15.0)
         .add(Attributes.FOLLOW_RANGE, 64.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(1, new FloatGoal(this));
      this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.9, false));
      this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0));
      this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 15.0F));
      this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
      this.goalSelector.addGoal(2, new MoveBackToVillageGoal(this, 1.0, false));
      this.targetSelector.addGoal(1, new NamekDefendVillageGoal(this));
      this.targetSelector.addGoal(2, new HurtByTargetGoal(this, new Class[0]));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Monster.class, 10, true, false, entity -> !(entity instanceof NamekWarriorEntity)));
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(VARIANT, 0);
      builder.define(IS_FLYING, false);
   }

   public void addAdditionalSaveData(CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
      pCompound.putInt("Variant", this.getVariantNamek());
   }

   public void readAdditionalSaveData(CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
      this.setVariantNamek(pCompound.getInt("Variant"));
   }

   public int getVariantNamek() {
      return (Integer)this.entityData.get(VARIANT);
   }

   public void setVariantNamek(int variant) {
      this.entityData.set(VARIANT, variant);
   }

   public void setFlying(boolean flying) {
      this.entityData.set(IS_FLYING, flying);
   }

   public boolean isFlying() {
      return (Boolean)this.entityData.get(IS_FLYING);
   }

   public ResourceLocation getCurrentTexture() {
      int variant = this.getVariantNamek();
      if (variant < 0 || variant >= TEXTURES.length) {
         variant = 0;
      }

      return TEXTURES[variant];
   }

   public boolean causeFallDamage(float pFallDistance, float pMultiplier, DamageSource pSource) {
      return false;
   }

   @Nullable
   public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData) {
      this.setVariantNamek(this.random.nextInt(4));
      return super.finalizeSpawn(pLevel, pDifficulty, pReason, pSpawnData);
   }

   public void remove(RemovalReason reason) {
      super.remove(reason);
      VillageAlertSystem.unregisterWarrior(this);
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "base_controller", 5, this::walkPredicate));
      controllers.add(new AnimationController(this, "attack_controller", 0, this::attackPredicate));
   }

   public void tick() {
      super.tick();
      LivingEntity target = this.getTarget();
      if (target != null && target.isAlive() && this.isFlying()) {
         this.rotateBodyToTarget(target);
      }

      if (!this.level().isClientSide) {
         if (target != null && target.isAlive()) {
            double yDiff = target.getY() - this.getY();
            if (yDiff > 2.0) {
               if (!this.isFlying()) {
                  this.setFlying(true);
               }
            } else if (yDiff <= 1.0 && this.onGround() && this.isFlying()) {
               this.setFlying(false);
               this.setNoGravity(false);
            }
         } else if (this.onGround() && this.isFlying()) {
            this.setFlying(false);
            this.setNoGravity(false);
         }

         if (this.isFlying()) {
            this.setNoGravity(true);
            if (target != null) {
               this.moveTowardsTargetInAir(target);
            } else {
               this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.03, 0.0));
            }
         } else {
            this.setNoGravity(false);
         }
      }
   }

   private void rotateBodyToTarget(LivingEntity target) {
      double d0 = target.getX() - this.getX();
      double d2 = target.getZ() - this.getZ();
      float targetYaw = (float)(Mth.atan2(d2, d0) * 180.0F / (float)Math.PI) - 90.0F;
      this.setYRot(targetYaw);
      this.setYBodyRot(targetYaw);
      this.setYHeadRot(targetYaw);
      this.yRotO = targetYaw;
      this.yBodyRotO = targetYaw;
      this.yHeadRotO = targetYaw;
   }

   private void moveTowardsTargetInAir(LivingEntity target) {
      double flyspeed = 0.65;
      double dx = target.getX() - this.getX();
      double dy = target.getY() + 1.0 - this.getY();
      double dz = target.getZ() - this.getZ();
      double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (!(distance < 1.0)) {
         Vec3 movement = new Vec3(dx / distance * flyspeed, dy / distance * flyspeed, dz / distance * flyspeed);
         double gravityDrag = dy < -0.5 ? -0.05 : -0.03;
         this.setDeltaMovement(movement.add(0.0, gravityDrag, 0.0));
      }
   }

   private <T extends GeoAnimatable> PlayState walkPredicate(AnimationState<T> event) {
      NamekWarriorEntity entity = (NamekWarriorEntity)event.getAnimatable();
      if (entity.isFlying()) {
         event.getController().setAnimation(RawAnimation.begin().thenLoop("fly"));
         return PlayState.CONTINUE;
      } else if (!event.isMoving()) {
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
      NamekWarriorEntity entity = (NamekWarriorEntity)event.getAnimatable();
      if (entity.swingTime > 0 && !this.isAttacking) {
         this.isAttacking = true;
         event.getController().forceAnimationReset();
         if (this.random.nextBoolean()) {
            event.getController().setAnimation(RawAnimation.begin().thenPlay("attack1"));
         } else {
            event.getController().setAnimation(RawAnimation.begin().thenPlay("attack2"));
         }

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
      return true;
   }

   public static boolean canSpawnHere(
      EntityType<? extends NamekWarriorEntity> entity, ServerLevelAccessor world, MobSpawnType spawn, BlockPos pos, RandomSource random
   ) {
      if (world.getDifficulty() == Difficulty.PEACEFUL) {
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

   static {
      for (int i = 0; i < 4; i++) {
         TEXTURES[i] = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/enemies/namek_warrior_" + i + ".png");
      }
   }
}
