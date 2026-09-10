package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.combat.clash.BeamClashManager;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.EntitiesConfig;
import com.dragonminez.common.init.EntityAttributes;
import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.entities.ITextureVariant;
import com.dragonminez.common.init.entities.goals.SagasUseSkillGoal;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.sagas.ai.CombatContext;
import com.dragonminez.common.init.entities.sagas.ai.SagasCombatBrain;
import com.dragonminez.common.init.entities.sagas.helper.ComboManager;
import com.dragonminez.common.init.entities.sagas.helper.DBSagasAnimationHandler;
import com.dragonminez.common.init.entities.sagas.helper.SkillManager;
import com.dragonminez.common.quest.Difficulty;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.TechniqueDispatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.entity.PartEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;

public abstract class DBSagasEntity extends Monster implements GeoEntity, ITextureVariant {
   private static final EntityDataAccessor<Boolean> IS_CASTING = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> IS_FLYING = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> IS_FLYING_FAST = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> SKILL_TYPE = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> AURA_COLOR = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<String> AURA_TYPE = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Boolean> IS_EVADING = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> IS_COMBOING = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> CURRENT_COMBO_ID = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> BATTLE_POWER = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> TRANSFORMING = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> KI_CHARGE = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> IS_LIGHTNING = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> LIGHTNING_COLOR = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> TEXTURE_VARIANT = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> DBZ_STYLE = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> IS_ZANZOKEN = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> IS_KID = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Float> SCALE_VAL = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Integer> LOCOMOTION_MODE = SynchedEntityData.defineId(DBSagasEntity.class, EntityDataSerializers.INT);
   private static final int SKILL_GRACE_TICKS = 80;
   public static final float SKILL_COOLDOWN_MULTIPLIER = 2.0F;
   private static final int POST_CAST_LOCKOUT = 100;
   private static final int GLOBAL_ACTION_LOCKOUT = 60;
   private static final float CAST_COMMIT_CHANCE = 0.5F;
   private int postCastCooldown = 0;
   private int globalActionCooldown = 0;
   private int knockbackLockTicks = 0;
   private int kiHitSlowTicks = 0;
   private static final int KI_HIT_SLOW_TICKS = 22;
   private static final double KI_HIT_FLY_SLOW_FACTOR = 0.2;
   protected int castTimer = 0;
   protected int transformTick = 0;
   private int chargeSoundTimer = 0;
   private static final int AURA_LIGHT_LEVEL = 12;
   private static final int AURA_LIGHT_INTERVAL = 2;
   private static final int AURA_LIGHT_STEP = 1;
   private BlockPos auraLightPos;
   private int auraLightLevel = 0;
   private boolean canEvade = false;
   private int evadeCooldownMax = 0;
   private int currentEvadeTimer = 0;
   private int evasionStateTicks = 0;
   private boolean canUseZanzoken = false;
   private int zanzokenCooldownMax = 0;
   private int currentZanzokenCooldown = 0;
   private int zanzokenTicks = 0;
   private boolean canUseWildSense = false;
   private int wildSenseCooldownMax = 0;
   private int currentWildSenseCooldown = 0;
   private boolean comboEnabled = false;
   private int comboCooldownMax = 0;
   private int currentComboCooldown = 0;
   public int comboTimer = 0;
   private LivingEntity comboTarget = null;
   private int activeComboId = -1;
   private int[] allowedCombos = new int[0];
   protected double defaultMovementSpeed = 0.25;
   protected double defaultAttackSpeed = 4.0;
   private DBSagasEntity.AiTier aiTier = DBSagasEntity.AiTier.SIMPLE;
   private static final int DECISION_INTERVAL = 6;
   private int decisionCooldown = 0;
   private boolean meleeAllowed = true;
   private int currentDashCooldown = 0;
   private int dashTicks = 0;
   private static final int DASH_DURATION = 7;
   private static final int DASH_COOLDOWN = 80;
   private static final double DASH_SPEED_MULTIPLIER = 2.6;
   private boolean wasTargetCasting = false;
   private boolean isAttacking = false;
   private boolean transformationDisabled = false;
   private final List<DBSagasEntity.KiSkill> skillPool = new ArrayList<>();
   private float currentPoolSkillSize = 1.0F;
   private int currentPoolColorMain = 16777215;
   private int currentPoolColorBorder = 16777215;
   private int currentPoolColorOutline = 16777215;
   private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);
   private PartEntity<?>[] hitboxParts;
   private static final UUID GLOBAL_GATE_KEY = new UUID(0L, 0L);
   private final Map<UUID, Long> partHitGate = new HashMap<>();
   private static final String[] TRANSFORM_OVERRIDE_TAGS = new String[]{
      "dmz_quest_tf_hp_abs",
      "dmz_quest_tf_melee_abs",
      "dmz_quest_tf_ki_abs",
      "dmz_quest_tf_hp_mult",
      "dmz_quest_tf_melee_mult",
      "dmz_quest_tf_ki_mult",
      "dmz_quest_tf_trigger"
   };

   public void setAiTierById(int id) {
      DBSagasEntity.AiTier[] values = DBSagasEntity.AiTier.values();
      int index = id - 1;
      if (index >= 0 && index < values.length) {
         this.aiTier = values[index];
      }
   }

   public int getAiTierId() {
      return this.aiTier.ordinal() + 1;
   }

   protected DBSagasEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      if (this.hasHitboxParts()) {
         this.hitboxParts = this.createHitboxParts();
         this.noCulling = true;
         this.refreshDimensions();
      }
   }

   public boolean hasHitboxParts() {
      return false;
   }

   protected DBSagasPart[] createHitboxParts() {
      return new DBSagasPart[0];
   }

   protected EntityDimensions getCoreDimensions() {
      return null;
   }

   public boolean isMultipartEntity() {
      return this.hitboxParts != null;
   }

   public PartEntity<?>[] getParts() {
      return this.hitboxParts;
   }

   public void setId(int pId) {
      super.setId(pId);
      if (this.hitboxParts != null) {
         for (int i = 0; i < this.hitboxParts.length; i++) {
            this.hitboxParts[i].setId(pId + i + 1);
         }
      }
   }

   protected EntityDimensions getDefaultDimensions(Pose pPose) {
      if (this.hasHitboxParts()) {
         EntityDimensions core = this.getCoreDimensions();
         if (core != null) {
            return core;
         }
      }

      return super.getDefaultDimensions(pPose);
   }

   private void positionHitboxParts() {
      if (this.hitboxParts != null) {
         Vec3 forward = Vec3.directionFromRotation(0.0F, this.yBodyRot);
         Vec3 side = new Vec3(-forward.z, 0.0, forward.x);

         for (PartEntity<?> generic : this.hitboxParts) {
            if (generic instanceof DBSagasPart part) {
               double cx = this.getX() + forward.x * (double)part.forwardOffset + side.x * (double)part.sideOffset;
               double cz = this.getZ() + forward.z * (double)part.forwardOffset + side.z * (double)part.sideOffset;
               double cy = this.getY() + (double)part.yOffset - (double)part.getBbHeight() / 2.0;
               double prevX = part.getX();
               double prevY = part.getY();
               double prevZ = part.getZ();
               part.setPos(cx, cy, cz);
               part.xo = part.xOld = prevX;
               part.yo = part.yOld = prevY;
               part.zo = part.zOld = prevZ;
            }
         }

         if (!this.partHitGate.isEmpty()) {
            long now = this.level().getGameTime();
            this.partHitGate.values().removeIf(tick -> tick < now);
         }
      }
   }

   public boolean receivePartDamage(DamageSource pSource, float pAmount, DBSagasPart part) {
      if (this.isInvulnerableTo(pSource)) {
         return false;
      } else {
         long now = this.level().getGameTime();
         Entity attacker = pSource.getEntity();
         UUID key = attacker != null ? attacker.getUUID() : GLOBAL_GATE_KEY;
         Long last = this.partHitGate.get(key);
         if (last != null && last == now) {
            return false;
         } else {
            this.partHitGate.put(key, now);
            return this.hurt(pSource, pAmount);
         }
      }
   }

   public void setSkillColors(int mainColor, int borderColor, int outlineColor) {
      this.currentPoolColorMain = mainColor;
      this.currentPoolColorBorder = borderColor;
      this.currentPoolColorOutline = outlineColor;
   }

   public void setSkillColors(int mainColor, int borderColor) {
      this.currentPoolColorMain = mainColor;
      this.currentPoolColorBorder = borderColor;
      this.currentPoolColorOutline = ColorUtils.darkenColor(borderColor, 0.6F);
   }

   public boolean isZanzoken() {
      return (Boolean)this.entityData.get(IS_ZANZOKEN);
   }

   public void setZanzokenState(boolean active) {
      this.entityData.set(IS_ZANZOKEN, active);
   }

   private boolean isSafeTeleportLocation(double targetX, double targetY, double targetZ) {
      AABB targetBox = this.getBoundingBox().move(targetX - this.getX(), targetY - this.getY(), targetZ - this.getZ());
      return this.level().noCollision(this, targetBox);
   }

   public void performZanzoken() {
      this.setZanzokenState(true);
      this.zanzokenTicks = 0;
      this.currentZanzokenCooldown = this.zanzokenCooldownMax;
      this.executeZanzokenJump();
   }

   private void executeZanzokenJump() {
      this.playSound((SoundEvent)MainSounds.ZANZOKEN.get(), 1.0F, 1.0F);
      boolean teleported = false;
      if (this.getTarget() != null) {
         for (int i = 0; i < 10; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double distance = 4.0 + this.random.nextDouble() * 2.0;
            double newX = this.getTarget().getX() + Math.cos(angle) * distance;
            double newZ = this.getTarget().getZ() + Math.sin(angle) * distance;
            double newY = this.getTarget().getY();
            if (this.isSafeTeleportLocation(newX, newY, newZ)) {
               this.teleportTo(newX, newY, newZ);
               teleported = true;
               break;
            }

            if (this.isSafeTeleportLocation(newX, newY + 1.0, newZ)) {
               this.teleportTo(newX, newY + 1.0, newZ);
               teleported = true;
               break;
            }

            if (this.isSafeTeleportLocation(newX, newY - 1.0, newZ)) {
               this.teleportTo(newX, newY - 1.0, newZ);
               teleported = true;
               break;
            }
         }

         if (teleported) {
            this.lookAt(this.getTarget(), 360.0F, 360.0F);
         }
      }

      if (!teleported) {
         Vec3 look = this.getLookAngle();
         this.setDeltaMovement(new Vec3(-look.x * 2.0, 0.2, -look.z * 2.0));
      }

      if (this.level() instanceof ServerLevel serverLevel) {
         serverLevel.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 1.0, this.getZ(), 5, 0.2, 0.5, 0.2, 0.0);
      }
   }

   public void setDBZStyle(int style) {
      this.entityData.set(DBZ_STYLE, style);
   }

   public int getDBZStyle() {
      return (Integer)this.entityData.get(DBZ_STYLE);
   }

   public boolean canFly() {
      return this.getFlySpeed() > 0.0;
   }

   public void setFlyingFast(boolean flyingFast) {
      this.entityData.set(IS_FLYING_FAST, flyingFast);
   }

   public boolean isFlyingFast() {
      return (Boolean)this.entityData.get(IS_FLYING_FAST);
   }

   public String getAuraType() {
      return (String)this.entityData.get(AURA_TYPE);
   }

   public void setAuraType(String type) {
      this.entityData.set(AURA_TYPE, type);
   }

   public float getScale() {
      if (this.isKid()) {
         return 0.7F;
      } else {
         float customScale = (Float)this.entityData.get(SCALE_VAL);
         return customScale > 0.0F ? customScale : 1.0F;
      }
   }

   public void setScaleVal(float scale) {
      this.entityData.set(SCALE_VAL, scale);
   }

   public void setCombo(int id, int cooldown) {
      this.comboEnabled = true;
      this.activeComboId = id;
      this.comboCooldownMax = cooldown;
      this.currentComboCooldown = cooldown;
   }

   public void setAllowedCombos(int cooldown, int... comboIds) {
      this.comboEnabled = true;
      this.comboCooldownMax = cooldown;
      this.currentComboCooldown = 10;
      this.allowedCombos = comboIds;
   }

   public void setAllowedCombos(int cooldown, DBSagasEntity.ComboType... combos) {
      this.comboEnabled = true;
      this.comboCooldownMax = cooldown;
      this.currentComboCooldown = 10;
      int[] ids = new int[combos.length];

      for (int i = 0; i < combos.length; i++) {
         ids[i] = combos[i].getId();
      }

      this.allowedCombos = ids;
   }

   public void setEvade(boolean active, int cooldown) {
      this.canEvade = active;
      this.evadeCooldownMax = cooldown;
      this.currentEvadeTimer = cooldown;
   }

   public void setWildSense(boolean active, int cooldown) {
      this.canUseWildSense = active;
      this.wildSenseCooldownMax = cooldown;
      this.currentWildSenseCooldown = cooldown;
   }

   public void setZanzoken(boolean active, int cooldown) {
      this.canUseZanzoken = active;
      this.zanzokenCooldownMax = cooldown;
      this.currentZanzokenCooldown = cooldown;
   }

   public void addKiSkill(DBSagasEntity.KiSkillType type, int cooldown, float size, int colorMain, int colorBorder, int colorOutline) {
      this.skillPool.add(new DBSagasEntity.KiSkill(type.getId(), cooldown, size, colorMain, colorBorder, colorOutline));
   }

   public void addKiSkill(DBSagasEntity.KiSkillType type, int cooldown, float size, int colorMain, int colorBorder) {
      this.skillPool.add(new DBSagasEntity.KiSkill(type.getId(), cooldown, size, colorMain, colorBorder, ColorUtils.darkenColor(colorBorder, 0.6F)));
   }

   public void addKiSkill(DBSagasEntity.KiSkillType type, int cooldown, float size) {
      this.addKiSkill(type, cooldown, size, 16777215, 16777215);
   }

   public void addKiSkill(DBSagasEntity.KiSkillType type, int cooldown) {
      this.addKiSkill(type, cooldown, 1.0F);
   }

   public List<DBSagasEntity.KiSkill> getSkillPool() {
      return this.skillPool;
   }

   public int[] getAllowedCombos() {
      return this.allowedCombos;
   }

   public boolean isZanzokenReady() {
      return this.canUseZanzoken && this.currentZanzokenCooldown <= 0 && !this.isZanzoken();
   }

   public boolean isWildSenseReady() {
      return this.canUseWildSense && this.currentWildSenseCooldown <= 0;
   }

   public boolean isComboReady() {
      return this.comboEnabled && this.currentComboCooldown <= 0 && this.globalActionCooldown <= 0;
   }

   public boolean isDashReady() {
      return this.aiTier != DBSagasEntity.AiTier.SIMPLE && this.currentDashCooldown <= 0 && this.dashTicks <= 0;
   }

   public void setLocomotionMode(DBSagasEntity.LocomotionMode mode) {
      this.entityData.set(LOCOMOTION_MODE, mode.ordinal());
   }

   public DBSagasEntity.LocomotionMode getLocomotionMode() {
      int ordinal = (Integer)this.entityData.get(LOCOMOTION_MODE);
      DBSagasEntity.LocomotionMode[] values = DBSagasEntity.LocomotionMode.values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DBSagasEntity.LocomotionMode.IDLE;
   }

   public void applyApproach(DBSagasEntity.LocomotionMode mode, LivingEntity target) {
      this.setLocomotionMode(mode);
      switch (mode) {
         case WALK_SLOW:
            this.meleeAllowed = true;
            if (this.dashTicks <= 0) {
               this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.defaultMovementSpeed * 0.45);
            }
            break;
         case DASH:
            this.meleeAllowed = true;
            this.tryDash(target);
            break;
         default:
            this.meleeAllowed = true;
            if (this.dashTicks <= 0) {
               this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.defaultMovementSpeed);
            }
      }
   }

   private void tryDash(LivingEntity target) {
      if (this.isDashReady() && target != null) {
         this.dashTicks = 7;
         this.currentDashCooldown = 80;
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.defaultMovementSpeed * 2.6);
         this.getNavigation().moveTo(target, 1.0);
         if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(), 6, 0.2, 0.05, 0.2, 0.02);
         }
      }
   }

   public void performProactiveTeleport(LivingEntity target) {
      if (target != null) {
         this.performTeleport(target);
         this.currentWildSenseCooldown = this.wildSenseCooldownMax;
      }
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(1, new FloatGoal(this) {
         public boolean canUse() {
            return super.canUse() && DBSagasEntity.this.getTarget() == null;
         }

         public boolean canContinueToUse() {
            return super.canContinueToUse() && DBSagasEntity.this.getTarget() == null;
         }
      });
      this.goalSelector.addGoal(2, new SagasUseSkillGoal(this));
      this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.8, false) {
         public boolean canUse() {
            return DBSagasEntity.this.isMeleeAllowed() && !DBSagasEntity.this.isStunned() && super.canUse();
         }

         public boolean canContinueToUse() {
            return DBSagasEntity.this.isMeleeAllowed() && !DBSagasEntity.this.isStunned() && super.canContinueToUse();
         }

         protected int getAttackInterval() {
            double attackSpeed = this.mob.getAttributeValue(Attributes.ATTACK_SPEED);
            return attackSpeed <= 0.0 ? 20 : (int)Math.max(2.0, 20.0 / attackSpeed);
         }
      });
      this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0));
      this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 45.0F));
      this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[0]));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, false));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Villager.class, false));
      this.targetSelector.addGoal(4, new NearestAttackableTargetGoal(this, IronGolem.class, false));
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 300.0)
         .add(Attributes.MOVEMENT_SPEED, 0.25)
         .add(Attributes.ATTACK_DAMAGE, 15.0)
         .add(Attributes.FOLLOW_RANGE, 64.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
         .add(Attributes.ATTACK_SPEED, 4.0)
         .add(EntityAttributes.KI_BLAST_DAMAGE, 20.0)
         .add(EntityAttributes.FLY_SPEED, 0.35)
         .add(EntityAttributes.KI_BLAST_SPEED, 0.6);
   }

   public void setKiBlastDamage(float damage) {
      if (this.getAttributes().hasAttribute(EntityAttributes.KI_BLAST_DAMAGE)) {
         this.getAttribute(EntityAttributes.KI_BLAST_DAMAGE).setBaseValue((double)damage);
      }
   }

   public float getKiBlastDamage() {
      return this.getAttributes().hasAttribute(EntityAttributes.KI_BLAST_DAMAGE) ? (float)this.getAttributeValue(EntityAttributes.KI_BLAST_DAMAGE) : 20.0F;
   }

   public void setFlySpeed(double speed) {
      if (this.getAttributes().hasAttribute(EntityAttributes.FLY_SPEED)) {
         this.getAttribute(EntityAttributes.FLY_SPEED).setBaseValue(speed);
      }
   }

   public double getFlySpeed() {
      return this.getAttributes().hasAttribute(EntityAttributes.FLY_SPEED) ? this.getAttributeValue(EntityAttributes.FLY_SPEED) : 0.35;
   }

   public void setKiBlastSpeed(float speed) {
      if (this.getAttributes().hasAttribute(EntityAttributes.KI_BLAST_SPEED)) {
         this.getAttribute(EntityAttributes.KI_BLAST_SPEED).setBaseValue((double)speed);
      }
   }

   public float getKiBlastSpeed() {
      return this.getAttributes().hasAttribute(EntityAttributes.KI_BLAST_SPEED) ? (float)this.getAttributeValue(EntityAttributes.KI_BLAST_SPEED) : 0.6F;
   }

   public void setCanFly(boolean canFly) {
      double currentFlySpeed = this.getFlySpeed();
      if (canFly) {
         if (currentFlySpeed <= 0.0) {
            this.setFlySpeed(0.35);
         }
      } else {
         this.setFlySpeed(0.0);
      }
   }

   public void tick() {
      super.tick();
      if (this.hitboxParts != null) {
         this.positionHitboxParts();
      }

      if (!this.level().isClientSide) {
         if (!this.isAlive()) {
            if (this.isCasting()) {
               this.stopCasting();
            }

            if (this.isComboing()) {
               this.stopCombo();
            }

            return;
         }

         if (this.isStunned()) {
            if (this.isCasting()) {
               this.stopCasting();
            }

            if (this.isComboing()) {
               this.stopCombo();
            }

            this.getNavigation().stop();
         }

         if (this.kiHitSlowTicks > 0) {
            this.kiHitSlowTicks--;
         }

         this.handleCommonCombatMovement(this.getTarget(), this.isCasting() || this.isComboing() || this.isTransforming() || this.isStunned());
         if (this.tickCount % 2 == 0) {
            this.updateAuraLight();
         }

         if (!this.isFlying() && this.isFlyingFast()) {
            this.setFlyingFast(false);
         }

         if (!this.isTransforming()) {
            boolean clashing = BeamClashManager.isClashing(this.getUUID());
            if (!this.isCasting() && !this.isComboing()) {
               if (this.canEvade && this.currentEvadeTimer > 0) {
                  this.currentEvadeTimer--;
               }

               if (this.canUseWildSense && this.currentWildSenseCooldown > 0) {
                  this.currentWildSenseCooldown--;
               }

               if (this.comboEnabled && this.currentComboCooldown > 0) {
                  this.currentComboCooldown--;
               }

               if (this.canUseZanzoken && this.currentZanzokenCooldown > 0) {
                  this.currentZanzokenCooldown--;
               }

               if (this.currentDashCooldown > 0) {
                  this.currentDashCooldown--;
               }

               if (this.postCastCooldown > 0) {
                  this.postCastCooldown--;
               }

               if (this.globalActionCooldown > 0) {
                  this.globalActionCooldown--;
               }

               for (DBSagasEntity.KiSkill skill : this.skillPool) {
                  if (skill.currentCooldown > 0) {
                     skill.currentCooldown--;
                  }
               }
            }

            if (this.dashTicks > 0 && !this.isCasting() && !this.isComboing()) {
               this.dashTicks--;
               if (this.getTarget() != null) {
                  this.getNavigation().moveTo(this.getTarget(), 1.0);
               }

               if (this.dashTicks <= 0) {
                  this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.defaultMovementSpeed);
               }
            }

            if (this.isZanzoken()) {
               this.zanzokenTicks++;
               if (this.zanzokenTicks % 5 == 0) {
                  this.executeZanzokenJump();
               }

               if (this.zanzokenTicks >= 40) {
                  this.setZanzokenState(false);
                  this.zanzokenTicks = 0;
               }
            }

            if (this.isCasting()) {
               this.castTimer++;
               this.getNavigation().stop();
               this.setDeltaMovement(0.0, 0.0, 0.0);
               if (this.getTarget() != null) {
                  this.lookAt(this.getTarget(), 360.0F, 360.0F);
               }

               int skillx = this.getSkillType();
               if (skillx == 20) {
                  this.setDeltaMovement(0.0, 0.15, 0.0);
               } else {
                  this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
               }

               if (skillx == 7 && this.level() instanceof ServerLevel serverLevel && this.castTimer > 5 && this.castTimer < 30) {
                  for (int i = 0; i < 25; i++) {
                     double distance = 3.0 + this.random.nextDouble() * 2.0;
                     double angle = this.random.nextDouble() * Math.PI * 2.0;
                     double heightOffset = (this.random.nextDouble() - 0.5) * (double)this.getBbHeight();
                     double spawnX = this.getX() + Math.cos(angle) * distance;
                     double spawnY = this.getY() + (double)this.getBbHeight() / 2.0 + heightOffset;
                     double spawnZ = this.getZ() + Math.sin(angle) * distance;
                     double velX = (this.getX() - spawnX) * 0.15;
                     double velY = (this.getY() + (double)this.getBbHeight() * 0.8 - spawnY) * 0.15;
                     double velZ = (this.getZ() - spawnZ) * 0.15;
                     serverLevel.sendParticles(ParticleTypes.CLOUD, spawnX, spawnY, spawnZ, 0, velX, velY, velZ, 1.0);
                  }
               }

               if (this.castTimer == 1 && skillx != 7 && skillx != 13) {
                  this.executeSkillEffect(skillx);
               }

               if (skillx == 7 && this.castTimer == 30) {
                  this.executeSkillEffect(skillx);
               }

               if (skillx == 13 && (this.castTimer == 10 || this.castTimer == 20 || this.castTimer == 30)) {
                  this.executeSkillEffect(13);
               }

               int maxCastDuration = SkillManager.getCastDuration(skillx);
               if (this.castTimer >= maxCastDuration) {
                  this.stopCasting();
               }
            }

            if (this.aiTier == DBSagasEntity.AiTier.SIMPLE
               && this.canUseWildSense
               && this.currentWildSenseCooldown <= 0
               && this.getTarget() != null
               && !this.isCasting()
               && !this.isComboing()
               && !clashing) {
               this.performTeleport(this.getTarget());
               this.currentWildSenseCooldown = this.wildSenseCooldownMax;
            }

            if (this.hurtTime > 0 && !this.isCasting() && !this.isComboing() && !this.isZanzoken()) {
               if (this.canUseZanzoken && this.currentZanzokenCooldown <= 0 && !this.isZanzoken()) {
                  this.performZanzoken();
               } else if (this.canEvade && this.currentEvadeTimer <= 0 && !this.isEvading()) {
                  this.performEvasion();
               }
            }

            if (this.isEvading()) {
               this.evasionStateTicks++;
               if (this.evasionStateTicks > 12) {
                  this.setEvading(false);
                  this.evasionStateTicks = 0;
               }
            }

            this.tickDodgeReaction();
            if (this.comboEnabled) {
               if (this.isComboing()) {
                  this.comboTimer++;
                  this.handleComboLogic();
               } else if (this.aiTier == DBSagasEntity.AiTier.SIMPLE
                  && this.currentComboCooldown <= 0
                  && this.globalActionCooldown <= 0
                  && !this.isCasting()
                  && this.getTarget() != null
                  && !clashing
                  && !this.isStunned()
                  && (double)this.distanceTo(this.getTarget()) < 6.0) {
                  this.startComboAuto();
               }
            }

            if (this.aiTier != DBSagasEntity.AiTier.SIMPLE) {
               LivingEntity decisionTarget = this.getTarget();
               if (decisionTarget != null && decisionTarget.isAlive()) {
                  if (this.decisionCooldown > 0) {
                     this.decisionCooldown--;
                  }

                  if (this.decisionCooldown <= 0
                     && !this.isCasting()
                     && !this.isComboing()
                     && !this.isZanzoken()
                     && !this.isEvading()
                     && !clashing
                     && !this.isStunned()) {
                     this.decisionCooldown = 6;
                     this.runBrainDecision();
                  }
               } else {
                  if (!this.meleeAllowed) {
                     this.meleeAllowed = true;
                  }

                  this.setLocomotionMode(DBSagasEntity.LocomotionMode.WALK);
               }
            }
         }

         if (this.isTransforming()) {
            this.transformTick++;
            if (this.handleTransformationLogic(this.transformTick, 80) && !this.level().isClientSide) {
               if (!this.canTransform()) {
                  this.setTransforming(false);
                  this.transformTick = 0;
               } else {
                  EntityType<? extends DBSagasEntity> nextFormType = this.getNextTransform();
                  if (nextFormType != null) {
                     DBSagasEntity nextForm = (DBSagasEntity)nextFormType.create(this.level());
                     this.finishTransformationSpawn(nextForm, this.spawnsNewFormFullHealth());
                  }
               }
            }
         }

         if (this.isCharge()) {
            if (this.chargeSoundTimer <= 0) {
               this.playSound((SoundEvent)MainSounds.KI_CHARGE_LOOP.get(), 0.8F, 1.0F);
               this.chargeSoundTimer = 40;
            }

            this.chargeSoundTimer--;
         } else {
            this.chargeSoundTimer = 0;
         }

         if (this.isLightning() && this.random.nextInt(30) == 0) {
            float pitch = 0.9F + this.random.nextFloat() * 0.2F;
            this.playSound((SoundEvent)MainSounds.KI_SPARKS.get(), 0.3F, pitch);
         }
      }
   }

   private void executeSkillEffect(int skillType) {
      if (this.getTarget() != null) {
         SkillManager.execute(skillType, this, this.getTarget());
      }
   }

   private void handleComboLogic() {
      int comboId = this.getComboId();
      ComboManager.handleCombo(this, this.comboTarget, comboId, this.comboTimer);
   }

   private void tickDodgeReaction() {
      if (this.aiTier == DBSagasEntity.AiTier.ADVANCED && this.canUseZanzoken) {
         if (!this.isCasting() && !this.isComboing() && !this.isZanzoken() && !this.isTransforming()) {
            if (this.getTarget() instanceof ServerPlayer sp) {
               StatsData data = StatsProvider.get(StatsCapability.INSTANCE, sp).resolve().orElse(null);
               if (data == null) {
                  this.wasTargetCasting = false;
               } else {
                  boolean nowCasting = data.getTechniques().isTechniqueCharging();
                  boolean firing = TechniqueDispatcher.isFiringKiAttack(sp);
                  float chargePct = data.getTechniques().getTechniqueChargePercent();
                  if (this.wasTargetCasting && !nowCasting && firing && this.currentZanzokenCooldown <= 0) {
                     float chance = 0.35F + Math.min(0.45F, chargePct / 200.0F * 0.5F);
                     if (this.random.nextFloat() < chance) {
                        this.performZanzoken();
                     }
                  }

                  this.wasTargetCasting = nowCasting;
               }
            } else {
               this.wasTargetCasting = false;
            }
         }
      }
   }

   private void runBrainDecision() {
      LivingEntity target = this.getTarget();
      if (target != null) {
         CombatContext ctx = CombatContext.snapshot(this, target);
         SagasCombatBrain.Intent intent = SagasCombatBrain.decide(ctx);
         switch (intent.type) {
            case CAST:
               if (this.random.nextFloat() < 0.5F) {
                  this.startSkill(intent.skill);
               } else {
                  this.meleeAllowed = true;
                  this.setLocomotionMode(DBSagasEntity.LocomotionMode.RUN);
                  if (this.dashTicks <= 0) {
                     this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.defaultMovementSpeed);
                  }
               }
               break;
            case COMBO:
               if (this.comboEnabled && this.currentComboCooldown <= 0) {
                  this.startCombo(intent.comboId);
               }
               break;
            case TELEPORT:
               this.performProactiveTeleport(target);
               break;
            case APPROACH:
               this.applyApproach(intent.locomotion, target);
               break;
            case MELEE:
               this.meleeAllowed = true;
               this.setLocomotionMode(DBSagasEntity.LocomotionMode.RUN);
               if (this.dashTicks <= 0) {
                  this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.defaultMovementSpeed);
               }
         }
      }
   }

   public boolean hasSkillReady() {
      if (this.isInSkillGracePeriod()) {
         return false;
      } else if (this.postCastCooldown > 0 || this.globalActionCooldown > 0) {
         return false;
      } else if (this.isComboing() || this.isZanzoken()) {
         return false;
      } else if (BeamClashManager.isClashing(this.getUUID())) {
         return false;
      } else if (this.getTarget() != null && !((double)this.distanceTo(this.getTarget()) <= 4.0)) {
         for (DBSagasEntity.KiSkill skill : this.skillPool) {
            if (skill.currentCooldown <= 0) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public void startSkill(DBSagasEntity.KiSkill skill) {
      if (skill != null) {
         if (!BeamClashManager.isClashing(this.getUUID())) {
            this.currentPoolSkillSize = skill.size;
            this.currentPoolColorMain = skill.colorMain;
            this.currentPoolColorBorder = skill.colorBorder;
            this.currentPoolColorOutline = skill.colorOutline;
            this.startCasting(skill.id);
            skill.currentCooldown = skill.cooldownMax;
         }
      }
   }

   public void startFirstAvailableSkill() {
      for (DBSagasEntity.KiSkill skill : this.skillPool) {
         if (skill.currentCooldown <= 0) {
            this.startSkill(skill);
            return;
         }
      }
   }

   private void updateAuraLight() {
      if (this.level() instanceof ServerLevel serverLevel) {
         boolean shouldEmitLight = this.isTransforming() || this.isCharge();
         int targetLevel = shouldEmitLight ? 12 : 0;
         this.auraLightLevel = this.approach(this.auraLightLevel, targetLevel, 1);
         if (this.auraLightLevel <= 0) {
            this.removeAuraLight(serverLevel);
         } else {
            BlockPos targetPos = this.blockPosition().above();
            if (!this.canHostAuraLight(serverLevel, targetPos)) {
               targetPos = this.blockPosition();
               if (!this.canHostAuraLight(serverLevel, targetPos)) {
                  this.removeAuraLight(serverLevel);
                  return;
               }
            }

            if (this.auraLightPos != null && !this.auraLightPos.equals(targetPos)) {
               this.clearAuraLightIfOwned(serverLevel, this.auraLightPos);
            }

            BlockState currentState = serverLevel.getBlockState(targetPos);
            if (!this.isAuraLight(currentState) || (Integer)currentState.getValue(LightBlock.LEVEL) != this.auraLightLevel) {
               BlockState lightState = (BlockState)Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, this.auraLightLevel);
               serverLevel.setBlock(targetPos, lightState, 3);
            }

            this.auraLightPos = targetPos.immutable();
         }
      }
   }

   private boolean canHostAuraLight(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.isAir() || this.isAuraLight(state);
   }

   private boolean isAuraLight(BlockState state) {
      return state.is(Blocks.LIGHT);
   }

   private void removeAuraLight(ServerLevel level) {
      if (this.auraLightPos != null) {
         this.clearAuraLightIfOwned(level, this.auraLightPos);
         this.auraLightPos = null;
         this.auraLightLevel = 0;
      }
   }

   private void clearAuraLightIfOwned(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (this.isAuraLight(state) && (Integer)state.getValue(LightBlock.LEVEL) <= 12) {
         level.removeBlock(pos, false);
      }
   }

   private int approach(int current, int target, int step) {
      if (current < target) {
         return Math.min(target, current + step);
      } else {
         return current > target ? Math.max(target, current - step) : current;
      }
   }

   public void remove(RemovalReason reason) {
      if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
         this.removeAuraLight(serverLevel);
      }

      super.remove(reason);
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "base_controller", 5, DBSagasAnimationHandler::walkPredicate));
      controllers.add(new AnimationController(this, "skill_controller", 5, DBSagasAnimationHandler::skillPredicate));
      controllers.add(new AnimationController(this, "evasion_controller", 5, DBSagasAnimationHandler::evasionPredicate));
      controllers.add(new AnimationController(this, "attack_controller", 0, DBSagasAnimationHandler::attackPredicate));
      controllers.add(new AnimationController(this, "tail_controller", 5, DBSagasAnimationHandler::tailPredicate));
      controllers.add(new AnimationController(this, "cape_controller", 5, DBSagasAnimationHandler::capePredicate));
   }

   public void travel(Vec3 pTravelVector) {
      if (!this.isCasting() && !this.isComboing() && !this.isTransforming() && !this.isZanzoken() && !this.isStunned()) {
         if (this.isEffectiveAi() && this.isInWater()) {
            float waterSpeed = 0.15F;
            this.moveRelative(waterSpeed, pTravelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
            if (this.getTarget() != null && this.getTarget().getY() > this.getY()) {
               this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.02, 0.0));
            }
         } else {
            super.travel(pTravelVector);
         }
      } else {
         this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
         super.travel(Vec3.ZERO);
      }
   }

   public void setCasting(boolean casting) {
      this.entityData.set(IS_CASTING, casting);
   }

   public boolean isCasting() {
      return (Boolean)this.entityData.get(IS_CASTING);
   }

   public void setFlying(boolean flying) {
      this.entityData.set(IS_FLYING, flying);
   }

   public boolean isFlying() {
      return (Boolean)this.entityData.get(IS_FLYING);
   }

   public int getSkillType() {
      return (Integer)this.entityData.get(SKILL_TYPE);
   }

   public void setSkillType(int type) {
      this.entityData.set(SKILL_TYPE, type);
   }

   public int getBattlePower() {
      return (Integer)this.entityData.get(BATTLE_POWER);
   }

   public void setBattlePower(int type) {
      this.entityData.set(BATTLE_POWER, type);
   }

   public int getAuraColor() {
      return (Integer)this.entityData.get(AURA_COLOR);
   }

   public void setAuraColor(int color) {
      this.entityData.set(AURA_COLOR, color);
   }

   public boolean isTransforming() {
      return (Boolean)this.entityData.get(TRANSFORMING);
   }

   public void setTransforming(boolean transforming) {
      this.entityData.set(TRANSFORMING, transforming);
   }

   public boolean isCharge() {
      return (Boolean)this.entityData.get(KI_CHARGE);
   }

   public void setKiCharge(boolean charge) {
      this.entityData.set(KI_CHARGE, charge);
   }

   public boolean isLightning() {
      return (Boolean)this.entityData.get(IS_LIGHTNING);
   }

   public void setLightning(boolean active) {
      this.entityData.set(IS_LIGHTNING, active);
   }

   public int getLightningColor() {
      return (Integer)this.entityData.get(LIGHTNING_COLOR);
   }

   public void setLightningColor(int color) {
      this.entityData.set(LIGHTNING_COLOR, color);
   }

   public void setEvading(boolean evading) {
      this.entityData.set(IS_EVADING, evading);
   }

   public boolean isEvading() {
      return (Boolean)this.entityData.get(IS_EVADING);
   }

   public void setComboing(boolean comboing) {
      this.entityData.set(IS_COMBOING, comboing);
   }

   public boolean isComboing() {
      return (Boolean)this.entityData.get(IS_COMBOING);
   }

   public void setisKid(boolean iskid) {
      this.entityData.set(IS_KID, iskid);
   }

   public boolean isKid() {
      return (Boolean)this.entityData.get(IS_KID);
   }

   @Override
   public int getTextureVariant() {
      return (Integer)this.entityData.get(TEXTURE_VARIANT);
   }

   @Override
   public void setTextureVariant(int variant) {
      this.entityData.set(TEXTURE_VARIANT, variant);
   }

   public int getComboId() {
      return (Integer)this.entityData.get(CURRENT_COMBO_ID);
   }

   public void stopCombo() {
      this.setComboing(false);
      this.entityData.set(CURRENT_COMBO_ID, -1);
      this.comboTimer = 0;
      this.comboTarget = null;
      this.globalActionCooldown = 60;
      if (this.isCharge()) {
         this.setKiCharge(false);
      }
   }

   public void interruptCombo() {
      if (this.isComboing()) {
         this.stopCombo();
      }

      this.currentComboCooldown = this.comboCooldownMax;
   }

   public void startCombo(int comboId) {
      if (!this.isInSkillGracePeriod()) {
         if (!this.isStunned()) {
            if (this.globalActionCooldown <= 0) {
               if (this.getTarget() != null) {
                  int resolved = comboId;
                  if (comboId < 0) {
                     if (this.allowedCombos != null && this.allowedCombos.length > 0) {
                        resolved = this.allowedCombos[this.random.nextInt(this.allowedCombos.length)];
                     } else if (this.activeComboId == 10) {
                        resolved = this.random.nextInt(3);
                     } else {
                        resolved = this.activeComboId;
                     }
                  }

                  this.comboTarget = this.getTarget();
                  this.setComboing(true);
                  this.entityData.set(CURRENT_COMBO_ID, resolved);
                  this.comboTimer = 0;
                  this.currentComboCooldown = Math.round((float)this.comboCooldownMax * DBSagasEntity.ComboType.tierOf(resolved).getCooldownFactor());
               }
            }
         }
      }
   }

   public void startComboAuto() {
      this.startCombo(-1);
   }

   protected boolean spawnsNewFormFullHealth() {
      return true;
   }

   public boolean causeFallDamage(float pFallDistance, float pMultiplier, DamageSource pSource) {
      return false;
   }

   public boolean isBattleDamaged() {
      return this.getHealth() <= this.getMaxHealth() / 2.0F;
   }

   private void performEvasion() {
      this.setEvading(true);
      this.evasionStateTicks = 0;
      this.currentEvadeTimer = this.evadeCooldownMax;
      if (this.level() instanceof ServerLevel serverLevel) {
         serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 1.0, this.getZ(), 5, 0.2, 0.1, 0.2, 0.1);
      }

      Vec3 look = this.getLookAngle();
      this.setDeltaMovement(new Vec3(-look.x * 1.5, 0.3, -look.z * 1.5));
      this.playSound((SoundEvent)MainSounds.TP.get(), 1.0F, 1.2F);
   }

   public void spawnPunchParticles(LivingEntity target) {
      if (this.level() instanceof ServerLevel serverLevel) {
         serverLevel.sendParticles(
            (SimpleParticleType)MainParticles.PUNCH_PARTICLE.get(),
            target.getX(),
            target.getY() + (double)target.getBbHeight() / 2.0,
            target.getZ(),
            0,
            1.0,
            1.0,
            1.0,
            1.0
         );
      }
   }

   public void performTeleport(LivingEntity target) {
      if (!this.isInSkillGracePeriod()) {
         Vec3 targetLook = target.getLookAngle().normalize();
         double distanceBehind = 1.5;
         double destX = target.getX() - targetLook.x * distanceBehind;
         double destZ = target.getZ() - targetLook.z * distanceBehind;
         double destY = target.getY();
         this.teleportTo(destX, destY, destZ);
         this.playSound((SoundEvent)MainSounds.TP.get(), 1.0F, 1.0F);
         if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, destX, destY + 1.0, destZ, 10, 0.2, 0.1, 0.2, 0.1);
         }

         this.lookAt(target, 360.0F, 360.0F);
      }
   }

   public void addAdditionalSaveData(CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
      pCompound.putDouble("FlySpeed", this.getFlySpeed());
      pCompound.putFloat("KiBlastDamage", this.getKiBlastDamage());
      pCompound.putFloat("KiBlastSpeed", this.getKiBlastSpeed());
      pCompound.putString("AuraType", this.getAuraType());
      pCompound.putInt("DBZStyle", this.getDBZStyle());
      pCompound.putBoolean("isKid", this.isKid());
      pCompound.putInt("TextureVariant", this.getTextureVariant());
      pCompound.putBoolean("TransformationDisabled", this.transformationDisabled);
      pCompound.putBoolean("CanUseZanzoken", this.canUseZanzoken);
      pCompound.putInt("ZanzokenCooldownMax", this.zanzokenCooldownMax);
      pCompound.putInt("AITier", this.getAiTierId());
      pCompound.putFloat("SkillSize", this.currentPoolSkillSize);
      pCompound.putInt("ColorMain", this.currentPoolColorMain);
      pCompound.putInt("ColorBorder", this.currentPoolColorBorder);
      pCompound.putInt("ColorOutline", this.currentPoolColorOutline);
      pCompound.putInt("CastTimer", this.castTimer);
      pCompound.putInt("TransformTick", this.transformTick);
      pCompound.putInt("ComboTimer", this.comboTimer);
      pCompound.putInt("CurrentComboId", this.getComboId());
      pCompound.putFloat("EntityScale", (Float)this.entityData.get(SCALE_VAL));
      pCompound.putBoolean("isKid", this.isKid());
   }

   public void readAdditionalSaveData(CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
      if (pCompound.contains("FlySpeed")) {
         this.setFlySpeed(pCompound.getDouble("FlySpeed"));
      }

      if (pCompound.contains("KiBlastDamage")) {
         this.setKiBlastDamage(pCompound.getFloat("KiBlastDamage"));
      }

      if (pCompound.contains("KiBlastSpeed")) {
         this.setKiBlastSpeed(pCompound.getFloat("KiBlastSpeed"));
      }

      if (pCompound.contains("AuraType")) {
         this.setAuraType(pCompound.getString("AuraType"));
      }

      if (pCompound.contains("DBZStyle")) {
         this.setDBZStyle(pCompound.getInt("DBZStyle"));
      }

      if (pCompound.contains("isKid")) {
         this.setisKid(pCompound.getBoolean("isKid"));
      }

      if (pCompound.contains("CanFly") && pCompound.getBoolean("CanFly") && this.getFlySpeed() <= 0.0) {
         this.setFlySpeed(0.35);
      }

      if (pCompound.contains("TextureVariant")) {
         this.setTextureVariant(pCompound.getInt("TextureVariant"));
      }

      if (pCompound.contains("TransformationDisabled")) {
         this.transformationDisabled = pCompound.getBoolean("TransformationDisabled");
      }

      if (pCompound.contains("CanUseZanzoken")) {
         this.canUseZanzoken = pCompound.getBoolean("CanUseZanzoken");
      }

      if (pCompound.contains("ZanzokenCooldownMax")) {
         this.zanzokenCooldownMax = pCompound.getInt("ZanzokenCooldownMax");
      }

      if (pCompound.contains("AITier")) {
         this.setAiTierById(pCompound.getInt("AITier"));
      }

      if (pCompound.contains("SkillSize")) {
         this.currentPoolSkillSize = pCompound.getFloat("SkillSize");
      }

      if (pCompound.contains("ColorMain")) {
         this.currentPoolColorMain = pCompound.getInt("ColorMain");
      }

      if (pCompound.contains("ColorBorder")) {
         this.currentPoolColorBorder = pCompound.getInt("ColorBorder");
      }

      if (pCompound.contains("ColorOutline")) {
         this.currentPoolColorOutline = pCompound.getInt("ColorOutline");
      }

      if (pCompound.contains("CastTimer")) {
         this.castTimer = pCompound.getInt("CastTimer");
      }

      if (pCompound.contains("TransformTick")) {
         this.transformTick = pCompound.getInt("TransformTick");
      }

      if (pCompound.contains("ComboTimer")) {
         this.comboTimer = pCompound.getInt("ComboTimer");
      }

      if (pCompound.contains("CurrentComboId")) {
         this.entityData.set(CURRENT_COMBO_ID, pCompound.getInt("CurrentComboId"));
      }

      if (pCompound.contains("EntityScale")) {
         this.setScaleVal(pCompound.getFloat("EntityScale"));
      }

      if (pCompound.contains("isKid")) {
         this.setisKid(pCompound.getBoolean("isKid"));
      }
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(IS_CASTING, false);
      builder.define(IS_FLYING, false);
      builder.define(IS_FLYING_FAST, false);
      builder.define(SKILL_TYPE, 0);
      builder.define(BATTLE_POWER, 20);
      builder.define(AURA_COLOR, 16777215);
      builder.define(AURA_TYPE, "kakarot");
      builder.define(TRANSFORMING, false);
      builder.define(KI_CHARGE, false);
      builder.define(IS_LIGHTNING, false);
      builder.define(LIGHTNING_COLOR, 16777215);
      builder.define(IS_EVADING, false);
      builder.define(IS_COMBOING, false);
      builder.define(CURRENT_COMBO_ID, -1);
      builder.define(DBZ_STYLE, 0);
      builder.define(TEXTURE_VARIANT, 0);
      builder.define(IS_ZANZOKEN, false);
      builder.define(IS_KID, false);
      builder.define(SCALE_VAL, 1.0F);
      builder.define(LOCOMOTION_MODE, DBSagasEntity.LocomotionMode.IDLE.ordinal());
   }

   public boolean isMeleeAllowed() {
      return this.aiTier == DBSagasEntity.AiTier.SIMPLE || this.meleeAllowed;
   }

   public boolean isStunned() {
      return this.hasEffect(MainEffects.STUN);
   }

   public String getQuestTeam() {
      return this.getPersistentData().getString("dmz_quest_team");
   }

   public boolean hasQuestTeam() {
      return !this.getPersistentData().getString("dmz_quest_team").isEmpty();
   }

   public boolean isQuestTeammate(Entity other) {
      if (other != null && other != this && this.hasQuestTeam()) {
         String team = this.getQuestTeam();
         if (team.equals(other.getPersistentData().getString("dmz_quest_team"))) {
            return true;
         } else {
            if (other instanceof AbstractKiProjectile proj && proj.getOwner() != null) {
               return team.equals(proj.getOwner().getPersistentData().getString("dmz_quest_team"));
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public boolean isAlliedTo(Entity pEntity) {
      return this.isQuestTeammate(pEntity) ? true : super.isAlliedTo(pEntity);
   }

   public void setTarget(LivingEntity pTarget) {
      if (pTarget == null || !this.isQuestTeammate(pTarget)) {
         if (pTarget == null || this.getPersistentData().getLong("dmz_taiyoken_blind_until") <= this.level().getGameTime()) {
            super.setTarget(pTarget);
         }
      }
   }

   public boolean hurt(DamageSource pSource, float pAmount) {
      if (this.isQuestTeammate(pSource.getEntity())) {
         return false;
      } else if (!this.isTransforming() && !this.isZanzoken()) {
         if (this.isComboing() && (Integer)this.entityData.get(CURRENT_COMBO_ID) == 4) {
            return false;
         } else {
            boolean isAbsoluteDeath = pSource.is(DamageTypes.FELL_OUT_OF_WORLD) || pSource.is(DamageTypes.GENERIC_KILL);
            if (!this.level().isClientSide && pAmount >= this.getHealth() && this.canTransform() && !isAbsoluteDeath) {
               this.setHealth(1.0F);
               this.startTransformation();
               return false;
            } else {
               boolean actuallyHurt = super.hurt(pSource, pAmount);
               if (!this.level().isClientSide && this.isComboing() && this.getComboId() == 7) {
                  this.stopCombo();
               }

               if (actuallyHurt && !this.level().isClientSide) {
                  if (pSource.is(MainDamageTypes.KIBLAST)) {
                     this.kiHitSlowTicks = 22;
                  }

                  if (this.getHealth() <= 0.0F && this.canTransform() && !isAbsoluteDeath) {
                     this.setHealth(1.0F);
                     this.deathTime = 0;
                     this.startTransformation();
                     return false;
                  }

                  if (!this.isTransforming()
                     && (double)this.getHealth() <= (double)this.getMaxHealth() * this.resolveTransformTriggerFraction()
                     && this.canTransform()) {
                     this.startTransformation();
                  }

                  if (pSource.getEntity() instanceof LivingEntity livingAttacker) {
                     boolean var10000;
                     label74: {
                        if (livingAttacker instanceof Player player && (player.isCreative() || player.isSpectator())) {
                           var10000 = true;
                           break label74;
                        }

                        var10000 = false;
                     }

                     boolean isUntouchablePlayer = var10000;
                     if (!this.isCasting() && !this.isComboing() && !isUntouchablePlayer && this.getTarget() != livingAttacker) {
                        this.setTarget(livingAttacker);
                     }
                  }
               }

               return actuallyHurt;
            }
         }
      } else {
         return false;
      }
   }

   protected boolean hasTransformation() {
      return false;
   }

   protected boolean canTransform() {
      return this.hasTransformation() && !this.transformationDisabled ? !this.getPersistentData().getBoolean("dmz_quest_no_transform") : false;
   }

   private double resolveTransformTriggerFraction() {
      return this.getPersistentData().contains("dmz_quest_tf_trigger")
         ? Mth.clamp(this.getPersistentData().getDouble("dmz_quest_tf_trigger"), 0.0, 1.0)
         : ConfigManager.getEntityTransformDefaults().triggerHealthFractionOr(0.5);
   }

   public void die(DamageSource pCause) {
      boolean isAbsoluteDeath = pCause.is(DamageTypes.FELL_OUT_OF_WORLD) || pCause.is(DamageTypes.GENERIC_KILL);
      if (this.canTransform() && !this.isTransforming() && !isAbsoluteDeath) {
         this.setHealth(1.0F);
         this.deathTime = 0;
         this.startTransformation();
      } else {
         super.die(pCause);
      }
   }

   public boolean doHurtTarget(Entity pEntity) {
      if (this.isTransforming()) {
         return false;
      } else if (this.isCasting() || this.isComboing()) {
         return false;
      } else {
         return this.isStunned() ? false : super.doHurtTarget(pEntity);
      }
   }

   protected void handleCommonCombatMovement(LivingEntity target, boolean isActionActive) {
      if (!this.level().isClientSide) {
         if (this.knockbackLockTicks > 0) {
            this.knockbackLockTicks--;
            this.getNavigation().stop();
            if (target != null) {
               this.rotateBodyToTarget(target);
            }
         } else if (isActionActive) {
            this.getNavigation().stop();
            this.setDeltaMovement(0.0, 0.0, 0.0);
            if (target != null) {
               this.rotateBodyToTarget(target);
            }
         } else {
            if (target != null && target.isAlive()) {
               double yDiff = target.getY() - this.getY();
               if (this.canFly() && yDiff >= 4.0 && !this.isFlying()) {
                  this.setFlying(true);
               } else if (this.isFlying() && (!this.canFly() || yDiff < 1.0 && this.onGround())) {
                  this.setFlying(false);
                  this.setFlyingFast(false);
                  this.setNoGravity(false);
               }
            } else if (this.onGround() && this.isFlying()) {
               this.setFlying(false);
               this.setFlyingFast(false);
               this.setNoGravity(false);
            }

            if (this.isFlying()) {
               this.setNoGravity(true);
               if (target != null) {
                  this.moveTowardsTargetInAir(target);
                  this.rotateBodyToTarget(target);
               } else {
                  this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.01, 0.0));
               }
            } else {
               this.setNoGravity(false);
            }
         }
      }
   }

   public void lockKnockback(int ticks) {
      this.knockbackLockTicks = Math.max(this.knockbackLockTicks, ticks);
   }

   public void rotateBodyToTarget(LivingEntity target) {
      double d0 = target.getX() - this.getX();
      double d2 = target.getZ() - this.getZ();
      float targetYaw = (float)(Mth.atan2(d2, d0) * 180.0F / (float)Math.PI) - 90.0F;
      this.setYRot(targetYaw);
      this.setYBodyRot(targetYaw);
      this.setYHeadRot(targetYaw);
   }

   public void moveTowardsTargetInAir(LivingEntity target) {
      if (!this.isCasting() && !this.isComboing() && !this.isEvading() && !this.isZanzoken() && !this.isStunned()) {
         double flyspeed = this.getFlySpeed();
         double distance = (double)this.distanceTo(target);
         if (distance > 15.0) {
            if (!this.isFlyingFast()) {
               this.setFlyingFast(true);
            }
         } else if (distance < 7.0 && this.isFlyingFast()) {
            this.setFlyingFast(false);
         }

         if (this.isFlyingFast()) {
            flyspeed *= 2.0;
         }

         if (this.kiHitSlowTicks > 0) {
            this.setFlyingFast(false);
            flyspeed *= 0.2;
         }

         double dx = target.getX() - this.getX();
         double dy = target.getY() + 1.0 - this.getY();
         double dz = target.getZ() - this.getZ();
         double dist3D = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (!(dist3D < 1.0)) {
            Vec3 movement = new Vec3(dx / dist3D * flyspeed, dy / dist3D * flyspeed, dz / dist3D * flyspeed);
            double gravityDrag = dy < -0.5 ? -0.05 : -0.03;
            this.setDeltaMovement(movement.add(0.0, gravityDrag, 0.0));
         }
      }
   }

   public boolean isInSkillGracePeriod() {
      return this.tickCount < 80;
   }

   public void startCasting(int type) {
      if (!this.isInSkillGracePeriod()) {
         if (!this.isStunned()) {
            if (this.globalActionCooldown <= 0) {
               if (!BeamClashManager.isClashing(this.getUUID())) {
                  this.setCasting(true);
                  this.setSkillType(type);
                  this.castTimer = 0;
                  this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
                  this.getNavigation().stop();
                  this.setDeltaMovement(0.0, 0.0, 0.0);
               }
            }
         }
      }
   }

   public void stopCasting() {
      this.setCasting(false);
      this.castTimer = 0;
      this.setSkillType(0);
      this.postCastCooldown = 100;
      this.globalActionCooldown = 60;
      this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.defaultMovementSpeed);
   }

   public boolean isSkillCastReady() {
      return this.postCastCooldown <= 0 && this.globalActionCooldown <= 0;
   }

   protected boolean handleTransformationLogic(int transformTick, int duration) {
      this.getNavigation().stop();
      this.setDeltaMovement(0.0, 0.0, 0.0);
      if (this.isCasting()) {
         this.stopCasting();
      }

      return transformTick >= duration;
   }

   protected void finishTransformationSpawn(DBSagasEntity newEntity, boolean fullHealth) {
      if (!this.level().isClientSide && newEntity != null) {
         Level level = this.level();
         if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1.0, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            newEntity.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
            newEntity.setTarget(this.getTarget());
            newEntity.setTextureVariant(this.getTextureVariant());
            if (this.getPersistentData().contains("dmz_quest_texture_variant")) {
               newEntity.getPersistentData().putInt("dmz_quest_texture_variant", this.getPersistentData().getInt("dmz_quest_texture_variant"));
            }

            EntitiesConfig.TransformSettings transformCfg = ConfigManager.getEntityTransformDefaults();
            CompoundTag pd = this.getPersistentData();
            Difficulty difficulty = Difficulty.fromName(pd.getString("dmz_difficulty"));
            double scaledMaxHealth;
            if (pd.contains("dmz_quest_tf_hp_abs")) {
               scaledMaxHealth = pd.getDouble("dmz_quest_tf_hp_abs") * difficulty.hpMultiplier();
            } else {
               double hpMult = pd.contains("dmz_quest_tf_hp_mult") ? pd.getDouble("dmz_quest_tf_hp_mult") : transformCfg.healthMultiplierOr(1.5);
               scaledMaxHealth = (double)this.getMaxHealth() * hpMult;
            }

            scaledMaxHealth = Math.max(1.0, scaledMaxHealth);
            if (newEntity.getAttributes().hasAttribute(Attributes.MAX_HEALTH)) {
               newEntity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(scaledMaxHealth);
            }

            double scaledKiDamage;
            if (pd.contains("dmz_quest_tf_ki_abs")) {
               scaledKiDamage = pd.getDouble("dmz_quest_tf_ki_abs") * difficulty.damageMultiplier();
            } else {
               double kiMult = pd.contains("dmz_quest_tf_ki_mult") ? pd.getDouble("dmz_quest_tf_ki_mult") : transformCfg.kiMultiplierOr(1.5);
               scaledKiDamage = (double)this.getKiBlastDamage() * kiMult;
            }

            newEntity.setKiBlastDamage((float)scaledKiDamage);
            if (newEntity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
               double scaledMelee;
               if (pd.contains("dmz_quest_tf_melee_abs")) {
                  scaledMelee = pd.getDouble("dmz_quest_tf_melee_abs") * difficulty.damageMultiplier();
               } else {
                  double meleeMult = pd.contains("dmz_quest_tf_melee_mult") ? pd.getDouble("dmz_quest_tf_melee_mult") : transformCfg.meleeMultiplierOr(1.5);
                  scaledMelee = this.getAttributeValue(Attributes.ATTACK_DAMAGE) * meleeMult;
               }

               newEntity.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(scaledMelee);
            }

            if (fullHealth) {
               newEntity.setHealth(newEntity.getMaxHealth());
            } else {
               newEntity.setHealth(newEntity.getMaxHealth() / 2.0F);
            }

            newEntity.setAiTier(this.getAiTier());
            newEntity.getPersistentData().putBoolean("dmz_stats_configured", true);
            if (this.getPersistentData().contains("dmz_difficulty")) {
               newEntity.getPersistentData().putString("dmz_difficulty", this.getPersistentData().getString("dmz_difficulty"));
            }

            if (this.getPersistentData().contains("dmz_saga_id")) {
               newEntity.getPersistentData().putString("dmz_saga_id", this.getPersistentData().getString("dmz_saga_id"));
            }

            if (this.getPersistentData().contains("dmz_quest_owner")) {
               newEntity.getPersistentData().putString("dmz_quest_owner", this.getPersistentData().getString("dmz_quest_owner"));
            }

            if (this.getPersistentData().contains("dmz_quest_key")) {
               newEntity.getPersistentData().putString("dmz_quest_key", this.getPersistentData().getString("dmz_quest_key"));
            }

            if (this.getPersistentData().contains("dmz_quest_team")) {
               newEntity.getPersistentData().putString("dmz_quest_team", this.getPersistentData().getString("dmz_quest_team"));
            }

            if (this.getPersistentData().contains("dmz_quest_objective_index")) {
               newEntity.getPersistentData().putInt("dmz_quest_objective_index", this.getPersistentData().getInt("dmz_quest_objective_index"));
            }

            for (String tag : TRANSFORM_OVERRIDE_TAGS) {
               if (pd.contains(tag)) {
                  newEntity.getPersistentData().putDouble(tag, pd.getDouble(tag));
               }
            }

            if (this.transformationDisabled || pd.getBoolean("dmz_quest_no_transform")) {
               newEntity.setTransformationDisabled(true);
               newEntity.getPersistentData().putBoolean("dmz_quest_no_transform", true);
            }

            level.addFreshEntity(newEntity);
            this.discard();
         }
      }
   }

   public EntityType<? extends DBSagasEntity> getNextTransform() {
      return null;
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }

   public boolean checkSpawnRules(LevelAccessor pLevel, MobSpawnType reason) {
      return pLevel.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL && this.checkSpawnObstruction(pLevel);
   }

   public static boolean canSpawnHere(
      EntityType<? extends DBSagasEntity> entity, ServerLevelAccessor world, MobSpawnType spawn, BlockPos pos, RandomSource random
   ) {
      if (world.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
         return false;
      } else if (random.nextFloat() < 0.65F) {
         return false;
      } else if (world.getBrightness(LightLayer.BLOCK, pos) > 7) {
         return false;
      } else {
         boolean solidGround = world.getBlockState(pos.below()).isSolidRender(world, pos.below());
         boolean noCollision = world.isUnobstructed(world.getBlockState(pos), pos, CollisionContext.empty());
         return solidGround && noCollision;
      }
   }

   protected void startTransformation() {
      this.setTransforming(true);
      this.playSound((SoundEvent)MainSounds.KI_CHARGE_LOOP.get(), 1.0F, 1.2F);
   }

   public String getGeckolibModelName() {
      return BuiltInRegistries.ENTITY_TYPE.getKey(this.getType()).getPath();
   }

   public boolean isCanUseZanzoken() {
      return this.canUseZanzoken;
   }

   public void setCanUseZanzoken(boolean canUseZanzoken) {
      this.canUseZanzoken = canUseZanzoken;
   }

   public int getZanzokenCooldownMax() {
      return this.zanzokenCooldownMax;
   }

   public void setZanzokenCooldownMax(int zanzokenCooldownMax) {
      this.zanzokenCooldownMax = zanzokenCooldownMax;
   }

   public int getCurrentZanzokenCooldown() {
      return this.currentZanzokenCooldown;
   }

   public void setCurrentZanzokenCooldown(int currentZanzokenCooldown) {
      this.currentZanzokenCooldown = currentZanzokenCooldown;
   }

   public int getZanzokenTicks() {
      return this.zanzokenTicks;
   }

   public void setZanzokenTicks(int zanzokenTicks) {
      this.zanzokenTicks = zanzokenTicks;
   }

   public double getDefaultMovementSpeed() {
      return this.defaultMovementSpeed;
   }

   public void setDefaultMovementSpeed(double defaultMovementSpeed) {
      this.defaultMovementSpeed = defaultMovementSpeed;
   }

   public double getDefaultAttackSpeed() {
      return this.defaultAttackSpeed;
   }

   public void setDefaultAttackSpeed(double defaultAttackSpeed) {
      this.defaultAttackSpeed = defaultAttackSpeed;
   }

   public void setAiTier(DBSagasEntity.AiTier aiTier) {
      this.aiTier = aiTier;
   }

   public DBSagasEntity.AiTier getAiTier() {
      return this.aiTier;
   }

   public boolean isAttacking() {
      return this.isAttacking;
   }

   public void setAttacking(boolean isAttacking) {
      this.isAttacking = isAttacking;
   }

   public boolean isTransformationDisabled() {
      return this.transformationDisabled;
   }

   public void setTransformationDisabled(boolean transformationDisabled) {
      this.transformationDisabled = transformationDisabled;
   }

   public float getCurrentPoolSkillSize() {
      return this.currentPoolSkillSize;
   }

   public int getCurrentPoolColorMain() {
      return this.currentPoolColorMain;
   }

   public int getCurrentPoolColorBorder() {
      return this.currentPoolColorBorder;
   }

   public int getCurrentPoolColorOutline() {
      return this.currentPoolColorOutline;
   }

   public static enum AiTier {
      SIMPLE,
      TACTICAL,
      ADVANCED;
   }

   public static enum ComboType {
      BASIC(0, DBSagasEntity.Tier.MEDIUM),
      AIR(1, DBSagasEntity.Tier.MEDIUM),
      KI_CHARGE_ATTACK(2, DBSagasEntity.Tier.STRONG),
      METEOR_COMBINATION(3, DBSagasEntity.Tier.STRONG),
      ANDROID_ABSORPTION(4, DBSagasEntity.Tier.STRONG),
      GUM_PUNCH(5, DBSagasEntity.Tier.MEDIUM),
      GUM_EXPAND(6, DBSagasEntity.Tier.WEAK),
      SLEEP_RECOVERY(7, DBSagasEntity.Tier.WEAK),
      RAPID_KICKS(8, DBSagasEntity.Tier.WEAK);

      private final int id;
      private final DBSagasEntity.Tier tier;

      private ComboType(int id, DBSagasEntity.Tier tier) {
         this.id = id;
         this.tier = tier;
      }

      public static DBSagasEntity.ComboType fromId(int id) {
         for (DBSagasEntity.ComboType type : values()) {
            if (type.id == id) {
               return type;
            }
         }

         return null;
      }

      public static DBSagasEntity.Tier tierOf(int id) {
         DBSagasEntity.ComboType type = fromId(id);
         return type != null ? type.tier : DBSagasEntity.Tier.MEDIUM;
      }

      public int getId() {
         return this.id;
      }

      public DBSagasEntity.Tier getTier() {
         return this.tier;
      }
   }

   public static class KiSkill {
      public int id;
      public int cooldownMax;
      public int currentCooldown;
      public float size;
      public int colorMain;
      public int colorBorder;
      public int colorOutline;
      public DBSagasEntity.SkillRole role;

      public KiSkill(int id, int cooldown, float size, int colorMain, int colorBorder, int colorOutline) {
         this.id = id;
         DBSagasEntity.KiSkillType type = DBSagasEntity.KiSkillType.fromId(id);
         float tierFactor = type != null ? type.getTier().getCooldownFactor() : 1.0F;
         this.cooldownMax = Math.max(1, Math.round((float)cooldown * 2.0F * tierFactor));
         this.currentCooldown = 0;
         this.size = size;
         this.colorMain = colorMain;
         this.colorBorder = colorBorder;
         this.colorOutline = colorOutline;
         this.role = DBSagasEntity.KiSkillType.roleOf(id);
      }
   }

   public static enum KiSkillType {
      KAMEHAMEHA(1, DBSagasEntity.SkillRole.RANGED_TRAVEL, DBSagasEntity.Tier.MEDIUM),
      GALICK_GUN(2, DBSagasEntity.SkillRole.RANGED_TRAVEL, DBSagasEntity.Tier.MEDIUM),
      MAKANKOSAPPO(3, DBSagasEntity.SkillRole.HITSCAN, DBSagasEntity.Tier.MEDIUM),
      KI_LASER(4, DBSagasEntity.SkillRole.HITSCAN, DBSagasEntity.Tier.WEAK),
      KI_EXPLOSION(5, DBSagasEntity.SkillRole.AOE_BURST, DBSagasEntity.Tier.MEDIUM),
      KI_BARRIER(6, DBSagasEntity.SkillRole.DEFENSIVE, DBSagasEntity.Tier.WEAK),
      OOZARU_ROAR(7, DBSagasEntity.SkillRole.AOE_BURST, DBSagasEntity.Tier.STRONG),
      GENERIC_KI_WAVE(8, DBSagasEntity.SkillRole.RANGED_TRAVEL, DBSagasEntity.Tier.WEAK),
      OOZARU_BEAM(9, DBSagasEntity.SkillRole.RANGED_TRAVEL, DBSagasEntity.Tier.MEDIUM),
      KI_VOLLEY(10, DBSagasEntity.SkillRole.PROJECTILE_FAST, DBSagasEntity.Tier.WEAK),
      KI_SMALL(11, DBSagasEntity.SkillRole.PROJECTILE_FAST, DBSagasEntity.Tier.WEAK),
      BLUE_HURRICANE(12, DBSagasEntity.SkillRole.AOE_BURST, DBSagasEntity.Tier.STRONG),
      TRIPLE_LASER(13, DBSagasEntity.SkillRole.HITSCAN, DBSagasEntity.Tier.MEDIUM),
      KIENZAN(14, DBSagasEntity.SkillRole.HITSCAN, DBSagasEntity.Tier.MEDIUM),
      DEATH_BALL(15, DBSagasEntity.SkillRole.GUARD_BREAK, DBSagasEntity.Tier.STRONG),
      MASENKO(16, DBSagasEntity.SkillRole.RANGED_TRAVEL, DBSagasEntity.Tier.MEDIUM),
      BIG_BANG(17, DBSagasEntity.SkillRole.GUARD_BREAK, DBSagasEntity.Tier.STRONG),
      FINAL_FLASH(18, DBSagasEntity.SkillRole.RANGED_TRAVEL, DBSagasEntity.Tier.STRONG),
      MAJIN_CANDY(19, DBSagasEntity.SkillRole.ZONING, DBSagasEntity.Tier.STRONG),
      KI_AIR_VOLLEY(20, DBSagasEntity.SkillRole.ZONING, DBSagasEntity.Tier.WEAK),
      DOUBLE_SUNDAY(21, DBSagasEntity.SkillRole.RANGED_TRAVEL, DBSagasEntity.Tier.STRONG);

      private final int id;
      private final DBSagasEntity.SkillRole role;
      private final DBSagasEntity.Tier tier;

      private KiSkillType(int id, DBSagasEntity.SkillRole role, DBSagasEntity.Tier tier) {
         this.id = id;
         this.role = role;
         this.tier = tier;
      }

      public static DBSagasEntity.KiSkillType fromId(int id) {
         for (DBSagasEntity.KiSkillType type : values()) {
            if (type.id == id) {
               return type;
            }
         }

         return null;
      }

      public static DBSagasEntity.SkillRole roleOf(int id) {
         DBSagasEntity.KiSkillType type = fromId(id);
         return type != null ? type.role : DBSagasEntity.SkillRole.RANGED_TRAVEL;
      }

      public int getId() {
         return this.id;
      }

      public DBSagasEntity.SkillRole getRole() {
         return this.role;
      }

      public DBSagasEntity.Tier getTier() {
         return this.tier;
      }
   }

   public static enum LocomotionMode {
      IDLE,
      WALK,
      WALK_SLOW,
      RUN,
      DASH;
   }

   public static enum SkillRole {
      RANGED_TRAVEL,
      HITSCAN,
      GUARD_BREAK,
      PROJECTILE_FAST,
      ZONING,
      DEFENSIVE,
      AOE_BURST;
   }

   public static enum Tier {
      WEAK(1.25F, 1.0F),
      MEDIUM(1.5F, 1.5F),
      STRONG(1.75F, 2.0F);

      private final float damageMultiplier;
      private final float cooldownFactor;

      private Tier(float damageMultiplier, float cooldownFactor) {
         this.damageMultiplier = damageMultiplier;
         this.cooldownFactor = cooldownFactor;
      }

      public float getDamageMultiplier() {
         return this.damageMultiplier;
      }

      public float getCooldownFactor() {
         return this.cooldownFactor;
      }
   }
}
