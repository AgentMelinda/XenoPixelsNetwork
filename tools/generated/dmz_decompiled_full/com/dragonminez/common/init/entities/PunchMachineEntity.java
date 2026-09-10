package com.dragonminez.common.init.entities;

import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.init.MainItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.Animation.LoopType;

public class PunchMachineEntity extends Mob implements GeoEntity {
   private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
   private long lastHitTime = 0L;
   private double accumulatedDamage = 0.0;
   private long combatStartTime = 0L;

   public PunchMachineEntity(EntityType<? extends Mob> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setNoAi(true);
      this.setPersistenceRequired();
   }

   public static Builder createAttributes() {
      return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.MOVEMENT_SPEED, 2.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
   }

   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide) {
         return false;
      } else {
         if (source.getEntity() instanceof Player player && player.isCrouching()) {
            if (!this.isRemoved()) {
               this.spawnAtLocation((ItemLike)MainItems.PUNCH_MACHINE_ITEM.get());
               this.discard();
            }

            return false;
         }

         if (!MainDamageTypes.isStrikeAttackDamage(source) && !MainDamageTypes.isKiblastDamage(source)) {
            return super.hurt(source, amount);
         } else {
            if (source.getEntity() instanceof Player player) {
               this.processHit(amount, player);
            }

            return false;
         }
      }
   }

   public void processHit(float damage, Player attacker) {
      long currentTime = System.currentTimeMillis();
      if (currentTime - this.lastHitTime > 5000L) {
         this.accumulatedDamage = 0.0;
         this.combatStartTime = currentTime;
      }

      if (this.accumulatedDamage == 0.0) {
         this.combatStartTime = currentTime;
      }

      this.lastHitTime = currentTime;
      this.accumulatedDamage += (double)damage;
      double seconds = (double)(currentTime - this.combatStartTime) / 1000.0;
      if (seconds < 1.0) {
         seconds = 1.0;
      }

      double dps = this.accumulatedDamage / seconds;
      this.spawnDamageIndicator(damage);
      String msg = String.format("DMG: %.1f | DPS: %.1f", damage, dps);
      attacker.displayClientMessage(Component.literal(msg).withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}), true);
   }

   private void spawnDamageIndicator(float damage) {
      AreaEffectCloud indicator = new AreaEffectCloud(this.level(), this.getX(), this.getY() + 0.8, this.getZ());
      indicator.setRadius(0.0F);
      indicator.setDuration(20);
      indicator.setParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AIR.defaultBlockState()));
      indicator.setWaitTime(0);
      String dmgText = String.format("%.0f", damage);
      indicator.setCustomName(Component.literal(dmgText).withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD}));
      indicator.setCustomNameVisible(true);
      this.level().addFreshEntity(indicator);
   }

   public boolean removeWhenFarAway(double pDistanceToClosestPlayer) {
      return false;
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(1, new FloatGoal(this));
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "controller", 0, this::predicate));
   }

   private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> tAnimationState) {
      tAnimationState.getController().setAnimation(RawAnimation.begin().then("idle", LoopType.LOOP));
      return PlayState.CONTINUE;
   }

   public boolean canBeCollidedWith() {
      return false;
   }

   public boolean canCollideWith(Entity pEntity) {
      return false;
   }

   public boolean canBeHitByProjectile() {
      return false;
   }

   public void push(Entity pEntity) {
   }

   public boolean isPushable() {
      return false;
   }

   protected void doPush(Entity p_20971_) {
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
