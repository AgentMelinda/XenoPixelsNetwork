package com.dragonminez.common.init.entities.ki;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainGameRules;
import com.dragonminez.common.init.block.custom.DragonBallBlock;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import com.dragonminez.common.passives.ClassPassives;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.EntityStatDebuffs;
import com.dragonminez.common.stats.character.SecondaryStatEffects;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public abstract class AbstractKiProjectile extends Projectile {
   private static final EntityDataAccessor<Integer> COLOR_MAIN = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> COLOR_BORDER = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> COLOR_OUTLINE = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Integer> KI_BALL_RENDER_TYPE = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<String> TECHNIQUE_ID = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Integer> ARMOR_PENETRATION = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> IS_HEAL = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> MAX_LIFE = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> KI_TYPE = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> IS_FIRING = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> FIRE_TICK = SynchedEntityData.defineId(AbstractKiProjectile.class, EntityDataSerializers.INT);
   private transient float[] cachedColorMainRgb;
   private transient float[] cachedColorBorderRgb;
   private transient float[] cachedColorOutlineRgb;
   private transient float kiLifetimeDrainPerTick = 0.0F;
   private transient float kiDrainAccumulator = 0.0F;
   private static final int HOMING_GRACE_TICKS = 30;
   private static final int HOMING_EXTENDED_TICKS = 90;
   private static final double HOMING_RANGE = 30.0;
   private static final double HOMING_TURN_RATE = 0.2;
   private transient int homingTargetId = -1;
   private transient int firingStartTick = -1;
   private static final float KI_INDESTRUCTIBLE_RESISTANCE = 1000.0F;
   private UUID cachedOwnerUUID;
   private transient float clashLockedLength = -1.0F;
   private transient UUID clashOpponentId = null;
   private transient int strikeStunTicks = 0;
   private transient UUID strikeStunExcludeId = null;
   public static final int CONTINUOUS_HIT_INTERVAL = 20;
   private static final float BARRAGE_XP_HIT_WEIGHT = 0.34F;
   private transient boolean blockDestructionEnabled = true;

   public void setHomingTarget(int targetId) {
      this.homingTargetId = targetId;
      this.firingStartTick = -1;
   }

   private boolean canHome() {
      return switch (this.getKiType()) {
         case SMALL_BALL, MEDIUM_BALL, WAVE, DISK -> true;
         default -> false;
      };
   }

   private void applyHomingSteering() {
      if (!this.level().isClientSide && this.isFiring() && this.canHome()) {
         if (this.firingStartTick < 0) {
            this.firingStartTick = this.tickCount;
         }

         if (this.homingTargetId >= 0 && this.level() instanceof ServerLevel serverLevel) {
            if (serverLevel.getEntity(this.homingTargetId) instanceof LivingEntity target && target.isAlive()) {
               Vec3 selfPos = this.position();
               Vec3 targetPos = target.position().add(0.0, (double)target.getBbHeight() * 0.5, 0.0);
               double dist = selfPos.distanceTo(targetPos);
               int elapsed = this.tickCount - this.firingStartTick;
               if (elapsed > 30) {
                  boolean windowExpired = elapsed > 120;
                  if (windowExpired || dist > 30.0) {
                     this.homingTargetId = -1;
                     return;
                  }
               }

               Vec3 vel = this.getDeltaMovement();
               double speed = vel.length();
               if (speed < 1.0E-4) {
                  return;
               }

               Vec3 dir = vel.scale(1.0 / speed);
               Vec3 toTarget = targetPos.subtract(selfPos).normalize();
               Vec3 newDir = dir.add(toTarget.subtract(dir).scale(0.2));
               if (newDir.lengthSqr() < 1.0E-8) {
                  newDir = toTarget;
               }

               this.setDeltaMovement(newDir.normalize().scale(speed));
               return;
            }

            this.homingTargetId = -1;
         }
      }
   }

   public void setKiLifetimeDrainPerTick(float perTick) {
      this.kiLifetimeDrainPerTick = Math.max(0.0F, perTick);
   }

   public void setStrikeStun(int ticks, UUID excludeMainTargetId) {
      this.strikeStunTicks = Math.max(0, ticks);
      this.strikeStunExcludeId = excludeMainTargetId;
   }

   protected void applyStrikeStun(Entity target) {
      if (this.strikeStunTicks > 0) {
         if (target instanceof LivingEntity living) {
            if (living != this.getOwner()) {
               if (!(living instanceof Player) || this.strikeStunExcludeId == null || !living.getUUID().equals(this.strikeStunExcludeId)) {
                  living.addEffect(new MobEffectInstance(MainEffects.STUN, this.strikeStunTicks, 0, false, false, true));
               }
            }
         }
      }
   }

   public AbstractKiProjectile(EntityType<? extends Projectile> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.noCulling = true;
   }

   public abstract int getMaxHits();

   protected int firingWindowTicks() {
      int fireTick = this.getFireTick();
      int window = fireTick >= 0 ? this.getMaxLife() - fireTick : this.getMaxLife();
      return Math.max(1, window);
   }

   public AbstractKiProjectile.ClashRole getClashRole() {
      return AbstractKiProjectile.ClashRole.NONE;
   }

   public boolean isMinorClashAttack() {
      return this.getClashRole() == AbstractKiProjectile.ClashRole.MINOR;
   }

   public boolean isClashableBeam() {
      return this.getClashRole() == AbstractKiProjectile.ClashRole.MAJOR && this.isFiring();
   }

   public float getClashYaw() {
      return this.getYRot();
   }

   public float getClashPitch() {
      return this.getXRot();
   }

   public float getClashBeamLength() {
      return 0.0F;
   }

   public void setClashLock(float lockedLength, UUID opponentId) {
      this.clashLockedLength = lockedLength;
      this.clashOpponentId = opponentId;
   }

   public void clearClashLock() {
      this.clashLockedLength = -1.0F;
      this.clashOpponentId = null;
   }

   public boolean isClashLocked() {
      return this.clashLockedLength >= 0.0F;
   }

   public float getDamagePerHit() {
      return this.getKiDamage() / Math.max(1.0F, (float)this.getMaxHits());
   }

   protected boolean applyContinuousDamage(Entity target) {
      if (target.invulnerableTime > 0) {
         return false;
      } else {
         boolean hit = this.applyDamageOrHeal(target, this.getDamagePerHit());
         if (hit) {
            target.invulnerableTime = 20;
         }

         return hit;
      }
   }

   protected void moveEntityTowards(Entity target, Vec3 desired) {
      target.move(MoverType.SELF, desired.subtract(target.position()));
   }

   protected Vec3 clipAgainstBlocks(Vec3 from, Vec3 to) {
      BlockHitResult hit = this.level().clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, this));
      return hit.getType() == Type.MISS ? to : hit.getLocation();
   }

   protected void holdTargetAtCaster(Entity target, Vec3 desiredFeet) {
      Vec3 delta = new Vec3(desiredFeet.x - target.getX(), desiredFeet.y - target.getY(), desiredFeet.z - target.getZ());
      Vec3 collided = Entity.collideBoundingBox(target, delta, target.getBoundingBox(), this.level(), List.of());
      target.setPos(target.getX() + collided.x, target.getY() + collided.y, target.getZ() + collided.z);
   }

   public void setup(LivingEntity owner, float damage, float size, float speed, int colorMain, int colorBorder, int colorOutline) {
      this.setOwner(owner);
      this.setKiDamage(damage);
      this.setSize(size);
      this.setKiSpeed(speed);
      this.setColors(colorMain, colorBorder, colorOutline);
   }

   public void setup(LivingEntity owner, float damage, float size, float speed, int colorMain, int colorBorder) {
      this.setup(owner, damage, size, speed, colorMain, colorBorder, 16777215);
   }

   public boolean shouldDamage(Entity target) {
      target = TargetHelper.resolveHittable(target);
      if (target == this) {
         return false;
      } else {
         Entity owner = this.getOwner();
         if (target instanceof AbstractKiProjectile kiProj && kiProj.getOwner() == owner) {
            return false;
         }

         if (this.isOwner(target)) {
            return this.isHeal();
         } else if (owner instanceof Player playerOwner) {
            TargetHelper.Relation relation = TargetHelper.getRelation(playerOwner, target);
            if (this.isHeal()) {
               return relation == TargetHelper.Relation.FRIENDLY;
            } else if (relation == TargetHelper.Relation.FRIENDLY) {
               return false;
            } else if (relation == TargetHelper.Relation.NEUTRAL) {
               Vec3 viewVector = playerOwner.getViewVector(1.0F).normalize();
               Vec3 toTarget = target.position().add(0.0, (double)target.getBbHeight() / 2.0, 0.0).subtract(playerOwner.getEyePosition()).normalize();
               double dotProduct = viewVector.dot(toTarget);
               return dotProduct > 0.95;
            } else {
               return true;
            }
         } else {
            if (!(this.getOwner() instanceof LivingEntity ownerLiving) || !(target instanceof LivingEntity targetLiving)) {
               return !this.isHeal();
            }

            return this.isHeal() ? ownerLiving.isAlliedTo(targetLiving) : !ownerLiving.isAlliedTo(targetLiving);
         }
      }
   }

   public void setOwner(Entity owner) {
      super.setOwner(owner);
      if (owner != null) {
         this.cachedOwnerUUID = owner.getUUID();
      }
   }

   public UUID getOwnerUUID() {
      Entity owner = this.getOwner();
      return owner != null ? owner.getUUID() : this.cachedOwnerUUID;
   }

   public boolean isOwner(Entity target) {
      if (target == null) {
         return false;
      } else {
         Entity owner = this.getOwner();
         if (owner == null || target != owner && !target.is(owner)) {
            UUID ownerUUID = this.cachedOwnerUUID;
            return ownerUUID != null && target.getUUID().equals(ownerUUID);
         } else {
            return true;
         }
      }
   }

   protected boolean canHitEntity(Entity target) {
      return !super.canHitEntity(target) ? false : this.isHeal() || !this.isOwner(target);
   }

   public void playInitialSound(SoundEvent sound) {
      this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.PLAYERS, 0.7F, 0.8F + this.random.nextFloat() * 0.2F);
   }

   public boolean applyDamageOrHeal(Entity target, float amount) {
      target = TargetHelper.resolveHittable(target);
      if (target instanceof LivingEntity livingTarget) {
         if (!this.isHeal()) {
            if (!this.level().isClientSide && this.getOwner() instanceof Player ownerPlayer) {
               float[] mod = new float[]{amount};
               StatsProvider.get(StatsCapability.INSTANCE, ownerPlayer).ifPresent(stats -> {
                  mod[0] *= (float)stats.getKiAttackDamageModifier();
                  double bonusDmg = ownerPlayer.getPersistentData().getDouble("dmz_human_ki_bonus_dmg");
                  if (bonusDmg > 0.0) {
                     mod[0] += (float)bonusDmg;
                     ownerPlayer.getPersistentData().remove("dmz_human_ki_bonus_dmg");
                  }
               });
               amount = mod[0];
            }

            return livingTarget.hurt(MainDamageTypes.kiblast(this.level(), this, this.getOwner()), amount);
         } else if ((
               !(livingTarget.getHealth() < livingTarget.getMaxHealth())
                  || !(this.getOwner() instanceof Player playerOwner)
                  || TargetHelper.getRelation(playerOwner, target) != TargetHelper.Relation.FRIENDLY
            )
            && (!(livingTarget.getHealth() < livingTarget.getMaxHealth()) || !target.isAlliedTo(this.getOwner()))) {
            return false;
         } else {
            livingTarget.heal(amount);
            return true;
         }
      } else {
         return false;
      }
   }

   protected Entity getKiGriefingSource() {
      Entity owner = this.getOwner();
      return (Entity)(owner != null ? owner : this);
   }

   public void setBlockDestructionEnabled(boolean enabled) {
      this.blockDestructionEnabled = enabled;
   }

   public boolean isBlockDestructionEnabled() {
      return this.blockDestructionEnabled;
   }

   protected boolean canKiDestroyBlock(BlockPos pos) {
      if (!this.blockDestructionEnabled) {
         return false;
      } else {
         BlockState state = this.level().getBlockState(pos);
         if (state.getBlock() instanceof DragonBallBlock) {
            return false;
         } else {
            return state.getExplosionResistance(this.level(), pos, null) >= 1000.0F
               ? false
               : MainGameRules.canKiGrief(this.level(), pos, this.getKiGriefingSource());
         }
      }
   }

   public double getDestructionMultiplier() {
      KiAttackData.KiType type;
      try {
         type = KiAttackData.KiType.valueOf(this.getKiType().name());
      } catch (IllegalArgumentException var3) {
         type = KiAttackData.KiType.SMALL_BALL;
      }

      return Math.max(0.0, ConfigManager.getTechniqueConfig().getKiTypeConfig(type).getDestructionMultiplier());
   }

   protected float scaledDestructionRadius(float baseRadius) {
      return (float)((double)baseRadius * this.getDestructionMultiplier());
   }

   protected boolean destroyKiBlock(BlockPos pos, boolean dropBlock) {
      return !this.canKiDestroyBlock(pos) ? false : this.level().destroyBlock(pos, dropBlock);
   }

   protected boolean setKiBlockToAir(BlockPos pos, int flags) {
      return !this.canKiDestroyBlock(pos) ? false : this.level().setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
   }

   protected ExplosionInteraction getKiExplosionInteraction(BlockPos pos) {
      return this.canKiDestroyBlock(pos) ? ExplosionInteraction.MOB : ExplosionInteraction.NONE;
   }

   public void onSuccessfulHit(Entity target) {
      if (target != null && this.homingTargetId >= 0 && target.getId() == this.homingTargetId) {
         this.homingTargetId = -1;
      }

      if (!this.level().isClientSide && this.getOwner() instanceof Player player) {
         String techId = this.getTechniqueId();
         if (techId != null && !techId.isEmpty()) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
               TechniqueData tech = stats.getTechniques().getUnlockedTechniques().get(techId);
               if (tech instanceof KiAttackData kiAttackData) {
                  if (kiAttackData.getKiType() == KiAttackData.KiType.BARRAGE) {
                     stats.getTechniques().addFractionalExperienceToTechnique(techId, (float)kiAttackData.getXpGainPerHit() * 0.34F);
                  } else {
                     stats.getTechniques().addExperienceToTechnique(techId, kiAttackData.getXpGainPerHit());
                  }

                  this.applySecondaryEffect(target, kiAttackData, stats);
               } else if (tech != null) {
                  stats.getTechniques().addExperienceToTechnique(techId, 1);
               }
            });
         }
      }
   }

   public void applyTechniqueSecondaryEffect(Entity target) {
      if (!this.level().isClientSide) {
         if (this.getOwner() instanceof Player player) {
            String techId = this.getTechniqueId();
            if (techId != null && !techId.isEmpty()) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
                  TechniqueData tech = stats.getTechniques().getUnlockedTechniques().get(techId);
                  if (tech instanceof KiAttackData kiAttackData) {
                     this.applySecondaryEffect(target, kiAttackData, stats);
                  }
               });
            }
         }
      }
   }

   private void applySecondaryEffect(Entity target, KiAttackData kiAttack, StatsData casterStats) {
      if (kiAttack.hasValidSecondaryEffect()) {
         if (target instanceof LivingEntity living) {
            KiAttackData.AffectedStat affected = kiAttack.getAffectedStat();
            if (affected != null) {
               boolean isBuff = kiAttack.getSecondaryEffectType() == KiAttackData.SecondaryEffectType.BUFF;
               if (this.secondaryRelationAllows(target, isBuff)) {
                  double magnitude = (double)kiAttack.getSecondaryIntensity() / 100.0;
                  double factor = isBuff ? magnitude : -magnitude;
                  double durationMult = ClassPassives.get(casterStats).secondaryDurationMultiplier(casterStats, kiAttack);
                  int durationTicks = Math.max(1, (int)Math.round((double)(kiAttack.getSecondaryDuration() * 20) * durationMult));
                  Optional<StatsData> targetStatsOpt = StatsProvider.get(StatsCapability.INSTANCE, living).resolve();
                  if (targetStatsOpt.isPresent()) {
                     SecondaryStatEffects effects = targetStatsOpt.get().getSecondaryStatEffects();
                     if (isBuff && effects.hasOtherActiveBuff(affected.name())) {
                        return;
                     }

                     effects.apply(affected.name(), factor, durationTicks);
                  } else if (!isBuff && EntityStatDebuffs.isSupported(affected.name())) {
                     EntityStatDebuffs.applyDebuff(living, affected.name(), factor, durationTicks);
                  }
               }
            }
         }
      }
   }

   private boolean secondaryRelationAllows(Entity target, boolean isBuff) {
      Entity owner = this.getOwner();
      if (target == owner) {
         return isBuff;
      } else if (owner instanceof Player playerOwner) {
         TargetHelper.Relation relation = TargetHelper.getRelation(playerOwner, target);
         return isBuff ? relation != TargetHelper.Relation.HOSTILE : relation != TargetHelper.Relation.FRIENDLY;
      } else {
         if (owner instanceof LivingEntity ownerLiving && target instanceof LivingEntity targetLiving) {
            boolean allied = ownerLiving.isAlliedTo(targetLiving);
            return isBuff ? allied : !allied;
         }

         return false;
      }
   }

   protected void defineSynchedData(Builder builder) {
      builder.define(COLOR_MAIN, 16777215);
      builder.define(COLOR_BORDER, 16777215);
      builder.define(COLOR_OUTLINE, 16777215);
      builder.define(DAMAGE, 5.0F);
      builder.define(SIZE, 1.0F);
      builder.define(SPEED, 1.0F);
      builder.define(KI_BALL_RENDER_TYPE, 1);
      builder.define(TECHNIQUE_ID, "");
      builder.define(ARMOR_PENETRATION, 0);
      builder.define(MAX_LIFE, 100);
      builder.define(IS_HEAL, false);
      builder.define(KI_TYPE, AbstractKiProjectile.KiType.SMALL_BALL.ordinal());
      builder.define(IS_FIRING, false);
      builder.define(FIRE_TICK, -1);
   }

   public void tick() {
      if (!this.level().isClientSide) {
         Entity owner = this.getOwner();
         if (owner != null && owner.level() != this.level()) {
            this.discard();
            return;
         }
      }

      super.tick();
      this.applyHomingSteering();
      Vec3 movement = this.getDeltaMovement();
      if (!this.level().isClientSide) {
         HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
         if (hitResult.getType() != Type.MISS) {
            this.onHit(hitResult);
            if (this.isRemoved()) {
               return;
            }
         }
      }

      double nextX = this.getX() + movement.x;
      double nextY = this.getY() + movement.y;
      double nextZ = this.getZ() + movement.z;
      this.setPos(nextX, nextY, nextZ);
      ProjectileUtil.rotateTowardsMovement(this, 0.2F);
      if (!this.level().isClientSide && this.isFiring() && this.kiLifetimeDrainPerTick > 0.0F && this.getOwner() instanceof Player ownerPlayer) {
         this.kiDrainAccumulator = this.kiDrainAccumulator + this.kiLifetimeDrainPerTick;
         int whole = (int)this.kiDrainAccumulator;
         if (whole > 0) {
            this.kiDrainAccumulator -= (float)whole;
            StatsProvider.get(StatsCapability.INSTANCE, ownerPlayer).ifPresent(stats -> stats.getResources().removeEnergy((float)whole));
         }
      }

      this.onKiTick();
   }

   public AbstractKiProjectile.KiType getKiType() {
      return AbstractKiProjectile.KiType.values()[this.entityData.get(KI_TYPE)];
   }

   public void setKiType(AbstractKiProjectile.KiType type) {
      this.entityData.set(KI_TYPE, type.ordinal());
   }

   public void setKiType(int type) {
      this.entityData.set(KI_TYPE, type);
   }

   protected void onKiTick() {
   }

   public void setColors(int colorMain, int colorBorder) {
      this.setColors(colorMain, colorBorder, 16777215);
   }

   public void setColors(int main, int border, int outline) {
      this.entityData.set(COLOR_MAIN, main);
      this.entityData.set(COLOR_BORDER, border);
      this.entityData.set(COLOR_OUTLINE, outline);
      this.cachedColorMainRgb = ColorUtils.rgbIntToFloat(main);
      this.cachedColorBorderRgb = ColorUtils.rgbIntToFloat(border);
      this.cachedColorOutlineRgb = ColorUtils.rgbIntToFloat(outline);
   }

   public void setColorOutline(int outline) {
      this.entityData.set(COLOR_OUTLINE, outline);
      this.cachedColorOutlineRgb = ColorUtils.rgbIntToFloat(outline);
   }

   public int getColor() {
      return (Integer)this.entityData.get(COLOR_MAIN);
   }

   public int getColorBorder() {
      return (Integer)this.entityData.get(COLOR_BORDER);
   }

   public int getColorOutline() {
      return (Integer)this.entityData.get(COLOR_OUTLINE);
   }

   public float[] getRgbColorMain() {
      if (this.cachedColorMainRgb == null) {
         this.cachedColorMainRgb = ColorUtils.rgbIntToFloat(this.getColor());
      }

      return this.cachedColorMainRgb;
   }

   public float[] getRgbColorBorder() {
      if (this.cachedColorBorderRgb == null) {
         this.cachedColorBorderRgb = ColorUtils.rgbIntToFloat(this.getColorBorder());
      }

      return this.cachedColorBorderRgb;
   }

   public float[] getRgbColorOutline() {
      if (this.cachedColorOutlineRgb == null) {
         this.cachedColorOutlineRgb = ColorUtils.rgbIntToFloat(this.getColorOutline());
      }

      return this.cachedColorOutlineRgb;
   }

   public void setKiDamage(float damage) {
      this.entityData.set(DAMAGE, damage);
   }

   public float getKiDamage() {
      return (Float)this.entityData.get(DAMAGE);
   }

   public void setSize(float size) {
      this.entityData.set(SIZE, size);
      this.refreshDimensions();
   }

   public float getSize() {
      return (Float)this.entityData.get(SIZE);
   }

   public void setKiSpeed(float speed) {
      this.entityData.set(SPEED, speed);
   }

   public float getKiSpeed() {
      return (Float)this.entityData.get(SPEED);
   }

   public void setKiRenderType(int type) {
      this.entityData.set(KI_BALL_RENDER_TYPE, type);
   }

   public int getKiRenderType() {
      return (Integer)this.entityData.get(KI_BALL_RENDER_TYPE);
   }

   public String getTechniqueId() {
      return (String)this.entityData.get(TECHNIQUE_ID);
   }

   public void setTechniqueId(String id) {
      this.setTechniqueIdInternal(id, true);
   }

   private void setTechniqueIdInternal(String id, boolean triggerAnimation) {
      this.entityData.set(TECHNIQUE_ID, id);
      if (triggerAnimation && !id.isEmpty() && !this.level().isClientSide && !this.isFiring()) {
         this.triggerAnimationPacket("_cast");
      }
   }

   public int getArmorPenetration() {
      return (Integer)this.entityData.get(ARMOR_PENETRATION);
   }

   public void setArmorPenetration(int pen) {
      this.entityData.set(ARMOR_PENETRATION, pen);
   }

   public boolean isHeal() {
      return (Boolean)this.entityData.get(IS_HEAL);
   }

   public void setHeal(boolean heal) {
      this.entityData.set(IS_HEAL, heal);
   }

   public void setMaxLife(int lifeInTicks) {
      this.entityData.set(MAX_LIFE, lifeInTicks);
   }

   public int getMaxLife() {
      return (Integer)this.entityData.get(MAX_LIFE);
   }

   public boolean isFiring() {
      return (Boolean)this.entityData.get(IS_FIRING);
   }

   public void setFiring(boolean firing) {
      this.entityData.set(IS_FIRING, firing);
   }

   public int getFireTick() {
      return (Integer)this.entityData.get(FIRE_TICK);
   }

   public void setFireTick(int tick) {
      this.entityData.set(FIRE_TICK, tick);
   }

   protected void addAdditionalSaveData(CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
      pCompound.putInt("ColorMain", this.getColor());
      pCompound.putInt("ColorBorder", this.getColorBorder());
      pCompound.putInt("ColorOutline", this.getColorOutline());
      pCompound.putFloat("Damage", this.getKiDamage());
      pCompound.putFloat("Size", this.getSize());
      pCompound.putFloat("Speed", this.getKiSpeed());
      pCompound.putString("TechniqueId", this.getTechniqueId());
      pCompound.putInt("ArmorPenetration", this.getArmorPenetration());
      pCompound.putBoolean("IsHeal", this.isHeal());
      pCompound.putBoolean("IsFiring", this.isFiring());
      pCompound.putInt("FireTick", this.getFireTick());
   }

   protected void readAdditionalSaveData(CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
      if (pCompound.contains("ColorMain")) {
         int outline = pCompound.contains("ColorOutline") ? pCompound.getInt("ColorOutline") : 16777215;
         this.setColors(pCompound.getInt("ColorMain"), pCompound.getInt("ColorBorder"), outline);
      }

      if (pCompound.contains("Damage")) {
         this.setKiDamage(pCompound.getFloat("Damage"));
      }

      if (pCompound.contains("Size")) {
         this.setSize(pCompound.getFloat("Size"));
      }

      if (pCompound.contains("Speed")) {
         this.setKiSpeed(pCompound.getFloat("Speed"));
      }

      if (pCompound.contains("TechniqueId")) {
         this.setTechniqueIdInternal(pCompound.getString("TechniqueId"), false);
      }

      if (pCompound.contains("ArmorPenetration")) {
         this.setArmorPenetration(pCompound.getInt("ArmorPenetration"));
      }

      if (pCompound.contains("IsHeal")) {
         this.setHeal(pCompound.getBoolean("IsHeal"));
      }

      if (pCompound.contains("IsFiring")) {
         this.setFiring(pCompound.getBoolean("IsFiring"));
      }

      if (pCompound.contains("FireTick")) {
         this.setFireTick(pCompound.getInt("FireTick"));
      }
   }

   public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
      super.onSyncedDataUpdated(pKey);
      if (SIZE.equals(pKey)) {
         this.refreshDimensions();
      }
   }

   public EntityDimensions getDimensions(Pose pPose) {
      return super.getDimensions(pPose).scale(this.getSize());
   }

   public boolean isMovementRestrictedType() {
      return switch (this.getKiType()) {
         case GIANT_BALL, WAVE, BEAM, EXPLOSION, BARRAGE -> true;
         default -> false;
      };
   }

   public void triggerAnimationPacket(String suffix) {
      if (!this.level().isClientSide && this.getOwner() instanceof ServerPlayer sp) {
         String techId = this.getTechniqueId();
         if (techId != null && !techId.isEmpty()) {
            StatsProvider.get(StatsCapability.INSTANCE, sp)
               .ifPresent(
                  data -> {
                     TechniqueData tech = data.getTechniques().getUnlockedTechniques().get(techId);
                     if (tech instanceof KiAttackData kiData) {
                        String fullAnim = kiData.getAnimationPrefix() + suffix;
                        int hold = this.isMovementRestrictedType() ? 1 : 0;
                        NetworkHandler.sendToTrackingEntityAndSelf(
                           new TriggerAnimationS2C(sp.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION, hold, -1, fullAnim), sp
                        );
                     }
                  }
               );
         }
      }
   }

   public float getClashLockedLength() {
      return this.clashLockedLength;
   }

   public UUID getClashOpponentId() {
      return this.clashOpponentId;
   }

   public static enum ClashRole {
      NONE,
      MAJOR,
      MINOR;
   }

   public static enum KiType {
      SMALL_BALL,
      MEDIUM_BALL,
      GIANT_BALL,
      WAVE,
      LASER,
      BEAM,
      DISK,
      EXPLOSION,
      SHIELD,
      BARRAGE,
      AREA;
   }
}
