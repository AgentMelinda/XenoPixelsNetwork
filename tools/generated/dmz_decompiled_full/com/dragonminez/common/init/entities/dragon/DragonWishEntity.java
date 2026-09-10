package com.dragonminez.common.init.entities.dragon;

import com.dragonminez.client.gui.WishesScreen;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonDefinition;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.server.events.DragonBallsHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
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

public class DragonWishEntity extends Mob implements GeoEntity {
   private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(DragonWishEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Boolean> GRANTED_WISH = SynchedEntityData.defineId(DragonWishEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<String> DRAGON_DEFINITION_ID = SynchedEntityData.defineId(DragonWishEntity.class, EntityDataSerializers.STRING);
   private long invokingTime;
   private int despawnDelay = 100;
   private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
   private final String defaultDragonDefinitionId;

   public DragonWishEntity(EntityType<? extends Mob> entityType, Level level, String dragonDefinitionId) {
      super(entityType, level);
      this.defaultDragonDefinitionId = dragonDefinitionId;
   }

   public static Builder createAttributes() {
      return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.MOVEMENT_SPEED, 2.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(OWNER_NAME, "");
      builder.define(GRANTED_WISH, false);
      builder.define(DRAGON_DEFINITION_ID, this.defaultDragonDefinitionId == null ? "" : this.defaultDragonDefinitionId);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(1, new FloatGoal(this));
      this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 35.0F));
      this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
   }

   public void tick() {
      super.tick();
      if (this.hasGrantedWish()) {
         this.despawnDelay--;
      }

      if (this.despawnDelay <= 0) {
         this.discard();
      }
   }

   @OnlyIn(Dist.CLIENT)
   public InteractionResult mobInteract(Player player, InteractionHand hand) {
      DragonDefinition definition = this.getDragonDefinition();
      if (definition != null
         && this.level().isClientSide
         && this.getOwnerName().equals(player.getName().getString())
         && !this.hasGrantedWish()
         && Minecraft.getInstance().player != null
         && Minecraft.getInstance().player.equals(player)) {
         Minecraft.getInstance().setScreen(new WishesScreen(definition.getWishScreenId(), definition.getWishCount()));
         Minecraft.getInstance().player.playSound((SoundEvent)MainSounds.UI_MENU_SWITCH.get());
      }

      return super.mobInteract(player, hand);
   }

   public void remove(RemovalReason reason) {
      if (!this.level().isClientSide && reason == RemovalReason.DISCARDED) {
         this.onDespawn();
      }

      super.remove(reason);
   }

   private void onDespawn() {
      DragonDefinition definition = this.getDragonDefinition();
      if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
         serverLevel.setWeatherParameters(6000, 0, false, false);
         serverLevel.setDayTime(this.getInvokingTime());
         if (definition != null && ConfigManager.getServerConfig().getWorldGen().getGenerateDragonBalls()) {
            if (definition.getBallSetId() != null && !definition.getBallSetId().isBlank()) {
               DragonBallsHandler.scatterDragonBalls(serverLevel, definition.getBallSetId());
            }

            ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayerByName(this.getOwnerName());
            if (owner != null) {
               DragonBallsHandler.syncRadar(owner.serverLevel());
            }
         }
      }
   }

   public void registerControllers(ControllerRegistrar controllerRegistrar) {
      controllerRegistrar.add(new AnimationController(this, "controller", 0, this::predicate));
   }

   private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> animationState) {
      animationState.getController().setAnimation(RawAnimation.begin().then("idle", LoopType.LOOP));
      return PlayState.CONTINUE;
   }

   public boolean hurt(DamageSource source, float amount) {
      return !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC) && !source.is(DamageTypes.GENERIC_KILL)
         ? false
         : super.hurt(source, amount);
   }

   public boolean canBeCollidedWith() {
      return false;
   }

   public boolean canCollideWith(Entity entity) {
      return false;
   }

   public boolean canBeHitByProjectile() {
      return false;
   }

   public void push(Entity entity) {
   }

   public boolean isPushable() {
      return false;
   }

   protected void doPush(Entity entity) {
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   public void setOwnerName(String name) {
      this.entityData.set(OWNER_NAME, name);
   }

   public String getOwnerName() {
      return (String)this.entityData.get(OWNER_NAME);
   }

   public void setGrantedWish(boolean granted) {
      this.entityData.set(GRANTED_WISH, granted);
   }

   public boolean hasGrantedWish() {
      return (Boolean)this.entityData.get(GRANTED_WISH);
   }

   public void setInvokingTime(long time) {
      this.invokingTime = time;
   }

   public long getInvokingTime() {
      return this.invokingTime;
   }

   public void setDragonDefinitionId(String id) {
      this.entityData.set(DRAGON_DEFINITION_ID, id);
   }

   public String getDragonDefinitionId() {
      return (String)this.entityData.get(DRAGON_DEFINITION_ID);
   }

   public DragonDefinition getDragonDefinition() {
      String definitionId = this.getDragonDefinitionId();
      if (definitionId == null || definitionId.isBlank()) {
         ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(this.getType());
         definitionId = key == null ? this.defaultDragonDefinitionId : key.getPath();
      }

      return DragonBallDefinitions.getDragon(definitionId);
   }

   public void addAdditionalSaveData(CompoundTag compound) {
      super.addAdditionalSaveData(compound);
      compound.putLong("InvokingTime", this.invokingTime);
      compound.putInt("DespawnDelay", this.despawnDelay);
      compound.putString("OwnerName", this.getOwnerName());
      compound.putBoolean("GrantedWish", this.hasGrantedWish());
      compound.putString("DragonDefinitionId", this.getDragonDefinitionId());
   }

   public void readAdditionalSaveData(CompoundTag compound) {
      super.readAdditionalSaveData(compound);
      if (compound.contains("InvokingTime")) {
         this.invokingTime = compound.getLong("InvokingTime");
      }

      if (compound.contains("DespawnDelay")) {
         this.despawnDelay = compound.getInt("DespawnDelay");
      }

      if (compound.contains("OwnerName")) {
         this.setOwnerName(compound.getString("OwnerName"));
      }

      if (compound.contains("GrantedWish")) {
         this.setGrantedWish(compound.getBoolean("GrantedWish"));
      }

      if (compound.contains("DragonDefinitionId")) {
         this.setDragonDefinitionId(compound.getString("DragonDefinitionId"));
      }
   }
}
