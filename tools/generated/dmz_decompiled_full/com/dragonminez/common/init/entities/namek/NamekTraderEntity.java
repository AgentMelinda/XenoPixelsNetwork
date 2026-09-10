package com.dragonminez.common.init.entities.namek;

import com.dragonminez.common.init.entities.goals.VillageAlertSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;

public class NamekTraderEntity extends NamekVillagerEntity {
   private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);
   private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(NamekTraderEntity.class, EntityDataSerializers.INT);
   public static final int VARIANT_COUNT = 4;
   public static final ResourceLocation[] TEXTURES = new ResourceLocation[4];

   public NamekTraderEntity(EntityType<? extends Villager> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
   }

   protected Component getTypeName() {
      return Component.translatable("entity.dragonminez.namek_trader");
   }

   public static Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 100.0)
         .add(Attributes.MOVEMENT_SPEED, 0.2)
         .add(Attributes.ATTACK_DAMAGE, 5.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
   }

   @Override
   public boolean isPersistenceRequired() {
      return true;
   }

   @Override
   public void checkDespawn() {
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(VARIANT, 0);
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

   public ResourceLocation getCurrentTexture() {
      int variant = this.getVariantNamek();
      if (variant < 0 || variant >= TEXTURES.length) {
         variant = 0;
      }

      return TEXTURES[variant];
   }

   @Nullable
   public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData) {
      this.setVariantNamek(this.random.nextInt(4));
      return super.finalizeSpawn(pLevel, pDifficulty, pReason, pSpawnData);
   }

   @Override
   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "base_controller", 5, this::walkPredicate));
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      boolean isHurt = super.hurt(source, amount);
      if (isHurt && source.getEntity() instanceof Player) {
         Player player = (Player)source.getEntity();
         VillageAlertSystem.alertAll(player);
      }

      return isHurt;
   }

   private <T extends GeoAnimatable> PlayState walkPredicate(AnimationState<T> event) {
      NamekTraderEntity entity = (NamekTraderEntity)event.getAnimatable();
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

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }

   static {
      for (int i = 0; i < 4; i++) {
         TEXTURES[i] = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/enemies/namek_trader_" + i + ".png");
      }
   }
}
