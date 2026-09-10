package com.dragonminez.common.init.entities;

import com.dragonminez.common.alignment.NpcDispositionService;
import com.dragonminez.common.init.entities.sagas.helper.DBSagasAnimationHandler;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.OpenQuestNPCDialogueS2C;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;

public class MastersEntity extends PathfinderMob implements GeoEntity {
   private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);
   private static final EntityDataAccessor<Float> SCALE_VAL = SynchedEntityData.defineId(MastersEntity.class, EntityDataSerializers.FLOAT);
   protected String masterName = null;

   protected MastersEntity(EntityType<? extends PathfinderMob> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setPersistenceRequired();
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
      this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
   }

   public static Builder createAttributes() {
      return PathfinderMob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 100.0)
         .add(Attributes.MOVEMENT_SPEED, 2.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
   }

   public boolean canBeCollidedWith() {
      return false;
   }

   public boolean canCollideWith(Entity entity) {
      return !(entity instanceof Player);
   }

   public boolean canBeHitByProjectile() {
      return false;
   }

   public boolean isPushable() {
      return false;
   }

   protected void doPush(Entity p_20971_) {
   }

   public boolean hurt(DamageSource source, float amount) {
      return !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL) ? false : super.hurt(source, amount);
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "controller", 0, event -> event.setAndContinue(RawAnimation.begin().thenLoop("idle"))));
      if ("frieza".equals(this.masterName)) {
         controllers.add(new AnimationController(this, "tail_controller", 5, DBSagasAnimationHandler::tailPredicate));
      }
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }

   public boolean isPersistenceRequired() {
      return true;
   }

   public void checkDespawn() {
   }

   public void setScaleVal(float scale) {
      this.entityData.set(SCALE_VAL, scale);
   }

   public float getScale() {
      float customScale = (Float)this.entityData.get(SCALE_VAL);
      return customScale > 0.0F ? customScale : 1.0F;
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(SCALE_VAL, 1.0F);
   }

   public void addAdditionalSaveData(CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
      pCompound.putFloat("EntityScale", (Float)this.entityData.get(SCALE_VAL));
   }

   public void readAdditionalSaveData(CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
      if (pCompound.contains("EntityScale")) {
         this.setScaleVal(pCompound.getFloat("EntityScale"));
      }
   }

   protected InteractionResult mobInteract(Player pPlayer, InteractionHand pHand) {
      if (pHand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      } else {
         if (!this.level().isClientSide && pPlayer instanceof ServerPlayer serverPlayer && this.masterName != null) {
            StatsProvider.get(StatsCapability.INSTANCE, serverPlayer)
               .ifPresent(
                  data -> {
                     if (!data.getStatus().isHasCreatedCharacter()) {
                        serverPlayer.displayClientMessage(Component.translatable("gui.dragonminez.lines.generic.createcharacter"), true);
                     } else {
                        Component blocker = NpcDispositionService.getDialogueBlocker(serverPlayer, this);
                        if (blocker != null) {
                           serverPlayer.displayClientMessage(blocker, true);
                        } else {
                           QuestService.NPCQuestOptions options = QuestService.collectNpcQuestOptions(this.masterName, data);
                           NetworkHandler.sendToPlayer(
                              new OpenQuestNPCDialogueS2C(
                                 this.masterName, options.offerableQuestIds(), options.turnInQuestIds(), options.inProgressQuestIds(), true, this.getId()
                              ),
                              serverPlayer
                           );
                        }
                     }
                  }
               );
            return InteractionResult.SUCCESS;
         }

         return InteractionResult.SUCCESS;
      }
   }

   public String getMasterName() {
      return this.masterName;
   }
}
