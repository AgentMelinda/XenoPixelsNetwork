package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.item.custom.XenoNpcWandItem;
import net.bullettrain.xenopixelsmod.item.custom.XenoNpcScriptToolItem;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcEditorPacket;
import net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcScriptPacket;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.brain.XenoNpcBrainV5;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class XenoNpcEntity extends PathfinderMob
        implements GeoEntity, com.dragonminez.common.init.entities.IBattlePower,
        net.minecraft.world.item.trading.Merchant {
    /**
     * GeckoLib state, present on every Xeno NPC but only consulted when the profile selects the
     * GECKOLIB model kind. GeckoLib is a hard dependency of this mod and loads on both sides, so
     * holding the cache here costs nothing on a dedicated server and keeps the renderer simple.
     */
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    /**
     * DMZ's shared saga rig (the model fallback) names its one-punch clip {@code attack1_1} and
     * has no plain {@code attack}; used when the baked registry carries no {@code attack} clip.
     */
    private static final RawAnimation ATTACK_SAGA = RawAnimation.begin().thenPlay("attack1_1");

    /**
     * Set while the one-shot {@link #ATTACK} clip owns the attack controller. Client-only:
     * GeckoLib controllers are only processed during rendering. Mirrors DragonMineZ's own
     * {@code attackPredicate} rising-edge flag (DinoGlobalEntity).
     */
    private boolean attackClipPlaying;
    private boolean scriptedAttackPlaying;
    private int previousAttackSwingTime;
    /** Set by the native Gecko renderer when a scripted combat packet reaches this NPC. */
    private String scriptedAttackClip;
    private boolean scriptedAttackHold;
    private boolean scriptedAttackStop;
    /**
     * Animation file the client model last resolved for this NPC. Written by
     * {@code XenoNpcGeoModel.getAnimationResource} on the render thread; null on the server and
     * before the first frame. Kept here so the attack controller can ask about this rig's own
     * clips without referencing a client-only class.
     */
    private volatile net.minecraft.resources.ResourceLocation bakedAnimationFile;
    private volatile String bakedConfiguredAttackClip = "";


    private final XenoNpcRole registeredRole;
    private XenoNpcData npcData;
    private boolean dispatchingTargetScript;
    /** Only created on the server when a player starts tracking this NPC. */
    private ServerBossEvent nativeBossBar;

    public XenoNpcEntity(EntityType<? extends XenoNpcEntity> type, Level level, XenoNpcRole role) {
        super(type, level);
        this.registeredRole = role == null ? XenoNpcRole.HUMANOID : role;
        this.npcData = new XenoNpcData(this.registeredRole);
        refreshNameplate();
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                // Players begin with 20 health before DragonMineZ adds the vitality bonus.
                // Keeping the NPC base at 40 gave equal VIT NPCs twenty free HP over players.
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                // DragonMineZ player melee starts at one damage before the STR formula is applied.
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15)
                // DragonMineZ's six main stats. An entity that does not declare an attribute
                // answers null for it, and every write is then silently dropped - which is what
                // happened here: NpcDmzStats could set an NPC's strength all it liked and
                // getAttribute(STRENGTH) still came back null, so nothing on the DMZ side ever saw
                // a stat change. Zero is the right base; the profile supplies the real value.
                .add(com.dragonminez.common.init.MainAttributes.STRENGTH, 0.0)
                .add(com.dragonminez.common.init.MainAttributes.STRIKE_POWER, 0.0)
                .add(com.dragonminez.common.init.MainAttributes.RESISTANCE, 0.0)
                .add(com.dragonminez.common.init.MainAttributes.VITALITY, 0.0)
                .add(com.dragonminez.common.init.MainAttributes.KI_POWER, 0.0)
                .add(com.dragonminez.common.init.MainAttributes.ENERGY, 0.0);
    }

    // ---------------------------------------------------------------- battle power

    /**
     * Battle power, computed on the server and synced to every client watching this NPC.
     *
     * <p>It has to be synced rather than read on demand. The scouter and ki sense are client-side,
     * and the DragonMineZ stats blob they would need lives in a server-only attachment
     * ({@code NpcDmzStats.NPC_DMZ_STATS} is {@code serializable}, not {@code sync}ed). Reading it
     * from the client answers null, which is how this briefly reported a flat zero.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_BATTLE_POWER =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    XenoNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_MOUNT_CONTROL =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    XenoNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> DATA_HITBOX_SCALE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    XenoNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    /** The title, with its colour codes, drawn under the name on the nameplate. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> DATA_TITLE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    XenoNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);

    /** How often the server recomputes it. Stats change on a save, not on a tick. */
    private static final int BATTLE_POWER_INTERVAL = 20;

    /**
     * How often an NPC is offered the chance to say an idle line.
     *
     * <p>Five seconds. The real pacing is {@code XenoNpcSpeech}'s own cooldown; this only decides
     * how often we bother asking, and asking cheaply is the point of the stagger.
     */
    private static final int AMBIENT_SPEECH_INTERVAL = 100;

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BATTLE_POWER, 0);
        builder.define(DATA_MOUNT_CONTROL, false);
        builder.define(DATA_HITBOX_SCALE, 1.0f);
        builder.define(DATA_TITLE, "");
    }

    /**
     * Pushes the name and title to the nameplate: the coloured name as the vanilla custom name,
     * the title through synced data. Call after any identity change.
     */
    public void refreshNameplate() {
        setCustomName(XenoNpcNameFormat.component(npcData.rawDisplayName()));
        entityData.set(DATA_TITLE, npcData.rawTitle());
    }

    /** The coloured title line for the nameplate, or null when there is none. */
    public @javax.annotation.Nullable Component nameplateTitle() {
        String raw = entityData.get(DATA_TITLE);
        return raw == null || raw.isBlank() || XenoNpcNameFormat.plain(raw).isBlank()
                ? null : XenoNpcNameFormat.component(raw);
    }

    public void setHitboxScale(float value) {
        float scale = Math.max(0.05f, Math.min(24.0f, Float.isFinite(value) ? value : 1.0f));
        if (Float.compare(entityData.get(DATA_HITBOX_SCALE), scale) != 0) {
            entityData.set(DATA_HITBOX_SCALE, scale);
            refreshDimensions();
        }
    }

    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_HITBOX_SCALE.equals(key)) refreshDimensions();
    }

    /**
     * Battle power the scouter and ki sense actually read.
     *
     * <p>Without this, DragonMineZ falls back to {@code MobBattlePowerHelper.calculate}, which it
     * uses for any {@code Mob} outside its own namespace. That helper reads vanilla-shaped
     * attributes - max health, attack damage, armour, speed, ki damage - and <em>none</em> of the
     * six DragonMineZ main stats. Our editor writes strength, ki power and the rest, so the number
     * it produced never moved when those changed: only vitality shifted it, and only because
     * {@code NpcVitalitySync} happens to write {@code MAX_HEALTH} directly.
     */
    @Override
    public int getBattlePower() {
        return Math.max(0, entityData.get(DATA_BATTLE_POWER));
    }

    /**
     * Ignored: this NPC's battle power is derived from its stats, not stored.
     *
     * <p>DragonMineZ caches a value on every living entity and pushes it through this setter each
     * tick. Accepting it would let that cached approximation overwrite the real figure, and the
     * scouter would flicker between the two.
     */
    @Override
    public void setBattlePower(int battlePower) {
        // Intentionally empty - see refreshBattlePower.
    }

    /**
     * Recomputes battle power from this NPC's DragonMineZ stats, server side.
     *
     * <p>Uses DMZ's own {@code StatsData.getBattlePower()} rather than arithmetic of our own, so an
     * NPC reads the way a character does. An NPC with no blob yet keeps whatever it last published
     * instead of being forced to zero - a missing blob means "not configured", not "powerless", and
     * zeroing it is exactly the regression this method exists to avoid repeating.
     */
    private void refreshBattlePower() {
        var data = net.bullettrain.xenopixelsmod.compat.npc.NpcDmzStats.stats(this);
        if (data == null) {
            return;
        }
        float power = data.getBattlePower();
        if (!Float.isFinite(power) || power < 0.0f) {
            return;
        }
        int rounded = power >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.round(power);
        if (entityData.get(DATA_BATTLE_POWER) != rounded) {
            entityData.set(DATA_BATTLE_POWER, rounded);
        }
    }

    /**
     * Whether this NPC's faction is at odds with {@code other}.
     *
     * <p>A player is an enemy after their standing crosses the hostile threshold. An ordinary mob
     * is an enemy only when the faction explicitly enables aggression and lists its entity id.
     * Other native NPCs and DMZ masters are excluded from this automatic acquisition.
     *
     * <p>An NPC with no faction, or one naming a faction no pack defines, is enemy to nobody. A
     * label left over from a deleted faction should leave an NPC standing there, not turn it on
     * everything in sight.
     */
    private boolean isFactionEnemy(LivingEntity other) {
        String mine = npcData.faction();
        if (other == null || other == this || mine == null || mine.isBlank()) {
            return false;
        }
        if (!XenoNpcRoleBehaviour.fights(role())) return false;
        var faction = net.bullettrain.xenopixelsmod.npc.faction.XenoFactions.get(mine);
        if (faction == null) {
            return false;
        }
        if (other instanceof Player player) {
            int standing = net.bullettrain.xenopixelsmod.capability.XenoCapabilities.get(player)
                    .map(data -> data.getFactionStanding(mine))
                    .orElse(faction.defaultStanding());
            return net.bullettrain.xenopixelsmod.npc.faction.XenoFaction.attitudeAt(standing)
                    == net.bullettrain.xenopixelsmod.npc.faction.XenoFaction.Attitude.HOSTILE;
        }
        if (other instanceof XenoNpcEntity
                || other instanceof com.dragonminez.common.init.entities.MastersEntity) return false;
        net.minecraft.resources.ResourceLocation type =
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(other.getType());
        return faction.aggressiveToMobs() && type != null
                && faction.attackableMobs().contains(type.toString());
    }

    public XenoNpcData npcData() {
        // The profile itself is saved NBT and does not replicate on edits. CustomName is already
        // tracked by vanilla entity data, so mirror it into the client profile view on access.
        if (level().isClientSide && getCustomName() != null) {
            npcData.syncVisibleName(getCustomName().getString());
        }
        return npcData;
    }

    private void updateBossBar(NpcCombatProfile profile) {
        if (nativeBossBar == null) return;
        nativeBossBar.setVisible(profile.bossBar && isAlive());
        if (!profile.bossBar) return;
        nativeBossBar.setName(getDisplayName());
        nativeBossBar.setColor(XenoNpcBehaviour.bossBarColor(profile));
        float max = getMaxHealth();
        nativeBossBar.setProgress(max > 0.0f
                ? Math.max(0.0f, Math.min(1.0f, getHealth() / max)) : 0.0f);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (nativeBossBar == null) {
            nativeBossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.PROGRESS);
            nativeBossBar.setVisible(false);
        }
        updateBossBar(NpcCombatProfile.read(this));
        nativeBossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (nativeBossBar != null) nativeBossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (nativeBossBar != null) nativeBossBar.removeAllPlayers();
        if (!level().isClientSide()) net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.forget(getUUID());
        super.remove(reason);
    }
    public XenoNpcRole role() { return npcData == null ? registeredRole : npcData.role(); }

    // Patrol position. Transient for the same reason the bard's state is: which point an NPC was
    // walking toward is only meaningful inside one server run, and the route itself - the part
    // worth keeping - is on the profile and saved there.
    private int pathIndex = -1;
    private int pathPointTicks;
    private boolean pathForward = true;

    /** Which point of its route this NPC is heading for, or -1 when it is not patrolling. */
    public int pathIndex() { return pathIndex; }

    public void setPathIndex(int index) { this.pathIndex = index; }

    /** How long it has been trying to reach that point, so a wedged NPC gives up. */
    public int pathPointTicks() { return pathPointTicks; }

    public void setPathPointTicks(int ticks) { this.pathPointTicks = ticks; }

    /** Which way along a ping-pong route it is travelling. */
    public boolean pathForward() { return pathForward; }

    public void setPathForward(boolean forward) { this.pathForward = forward; }

    // Bard playback state. Deliberately transient and never saved: "is a player near enough to
    // hear this right now" is only true within one server run, and a tick count written to disk
    // is the exact mistake XenoNpcRespawnData was built to stop making.
    private boolean bardPlaying;
    private int bardNextPlayTick;

    public boolean bardPlaying() { return bardPlaying; }

    public void setBardPlaying(boolean playing) { this.bardPlaying = playing; }

    public int bardNextPlayTick() { return bardNextPlayTick; }

    public void setBardNextPlayTick(int tick) { this.bardNextPlayTick = tick; }

    @Override
    protected void registerGoals() {
        // The AI page's gated goals - swim, the two door behaviours, and the leap. Added always
        // and refusing to start while their switch is off, because registerGoals runs once and a
        // goal omitted here could never come back when the switch is turned on.
        net.bullettrain.xenopixelsmod.npc.NpcAiGoals.register(this,
                (goal, priority) -> goalSelector.addGoal(priority, goal));
        // Basic melee remains available when the optional combat brain is off. The gate prevents
        // this goal from competing with the combat brain when its distance-band logic is enabled.
        goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, true) {
            private boolean allowed() {
                return XenoNpcRoleBehaviour.fights(XenoNpcEntity.this.role())
                        && !NpcCombatProfile.readCached(XenoNpcEntity.this).combatBrain
                        && net.bullettrain.xenopixelsmod.compat.npc.NpcTargetKeeper
                                .isCombatTarget(XenoNpcEntity.this.getTarget());
            }

            @Override
            public boolean canUse() {
                return allowed() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return allowed() && super.canContinueToUse();
            }

            @Override
            protected int getAttackInterval() {
                return Math.max(2, Math.round(super.getAttackInterval()
                        / NpcCombatProfile.clampNpcMeleeSpeed(
                                NpcCombatProfile.readCached(XenoNpcEntity.this).npcMeleeSpeed)));
            }
        });
        // Gated rather than plain: an NPC told to stay home refuses to start a stroll, and can be
        // told otherwise later. registerGoals runs once, so a goal omitted here never comes back.
        goalSelector.addGoal(6, new StayHomeStrollGoal(this, 0.75));
        // Idle look goals yield while a combat brain flies the NPC; otherwise they turned the head
        // toward a bystander every tick and the body-rotation control dragged the body after it.
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f) {
            @Override
            public boolean canUse() {
                return !brainFlying() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !brainFlying() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return !brainFlying() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !brainFlying() && super.canContinueToUse();
            }
        });

        // These goals consult the saved role when they run rather than capturing the entity type's
        // creation role. That keeps editor role changes effective immediately and after reload.
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return XenoNpcRoleBehaviour.fights(XenoNpcEntity.this.role())
                        && NpcCombatProfile.readCached(XenoNpcEntity.this).aiOnFoundEnemy
                                == net.bullettrain.xenopixelsmod.npc.NpcOnFoundEnemy.RETALIATE
                        && super.canUse();
            }
        });
        // Faction hostility is limited to players below the hostile-standing threshold and
        // explicitly listed ordinary mobs. Native NPCs and DMZ masters are never acquired here.
        targetSelector.addGoal(3, new net.bullettrain.xenopixelsmod.npc.NpcAiGoals
                .ProfiledTargetGoal<>(this, LivingEntity.class, 10, this::isFactionEnemy));

        // What makes a guard a guard rather than a bystander: it picks hostiles up itself instead
        // of only answering when struck. Monster, not LivingEntity - a guard should not start on
        // the village cow. The role check is live so a later role edit takes effect immediately.
        targetSelector.addGoal(2, new net.bullettrain.xenopixelsmod.npc.NpcAiGoals
                .ProfiledTargetGoal<>(this, net.minecraft.world.entity.monster.Monster.class,
                        10, ignored -> true) {
            @Override
            public boolean canUse() {
                return XenoNpcRoleBehaviour.guardsHome(XenoNpcEntity.this.role())
                        && super.canUse();
            }
        });
    }

    /** Enables the first player passenger as the vehicle controller when requested by the profile. */
    @Override
    @org.jetbrains.annotations.Nullable
    public LivingEntity getControllingPassenger() {
        return entityData.get(DATA_MOUNT_CONTROL)
                && getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected void tickRidden(Player rider, net.minecraft.world.phys.Vec3 input) {
        super.tickRidden(rider, input);
        setRot(rider.getYRot(), rider.getXRot() * 0.5f);
        yRotO = yBodyRot = yHeadRot = getYRot();
    }

    @Override
    protected net.minecraft.world.phys.Vec3 getRiddenInput(Player rider,
                                                            net.minecraft.world.phys.Vec3 input) {
        float strafe = rider.xxa * 0.5f;
        float forward = rider.zza <= 0.0f ? rider.zza * 0.25f : rider.zza;
        return new net.minecraft.world.phys.Vec3(strafe, 0.0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    public void setMountControlEnabled(boolean enabled) {
        entityData.set(DATA_MOUNT_CONTROL, enabled);
    }

    public boolean mountControlEnabled() {
        return entityData.get(DATA_MOUNT_CONTROL);
    }

    // ---------------------------------------------------------------- behaviour flags
    //
    // Each of these answers a question vanilla asks, so they have to be overrides here rather than
    // something XenoNpcBehaviour can set from outside. The profile read is cached per call because
    // some of these are asked every tick.

    @Override
    public boolean fireImmune() {
        return NpcCombatProfile.readCached(this).fireImmune || super.fireImmune();
    }

    /**
     * With No Fall Damage there is nothing to fear from a drop, so the navigator may plan one: an
     * NPC on a ledge above its target walks down to it rather than standing at the edge.
     */
    @Override
    public int getMaxFallDistance() {
        return NpcCombatProfile.readCached(this).noFallDamage ? 64 : super.getMaxFallDistance();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier,
                                   net.minecraft.world.damagesource.DamageSource source) {
        if (NpcCombatProfile.readCached(this).noFallDamage) {
            return false;
        }
        return super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        if (NpcCombatProfile.readCached(this).potionImmune) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    /**
     * Keeps air topped up for an NPC that should not drown.
     *
     * <p>{@code canBreatheUnderwater()} is final and decided by an entity-type tag, so this is the
     * only place the flag can take effect.
     */
    @Override
    protected int decreaseAirSupply(int current) {
        return XenoNpcBehaviour.airSupply(NpcCombatProfile.readCached(this), current,
                super.decreaseAirSupply(current));
    }

    /** Cobwebs slow an entity through this hook, so declining it walks straight through. */
    @Override
    public void makeStuckInBlock(net.minecraft.world.level.block.state.BlockState state,
                                 net.minecraft.world.phys.Vec3 motionMultiplier) {
        if (!NpcCombatProfile.readCached(this).cobwebAffected) {
            return;
        }
        super.makeStuckInBlock(state, motionMultiplier);
    }

    /**
     * Scales the hitbox to the profile's Hitbox row.
     *
     * <p>{@code getDimensions} is {@code final} on {@code LivingEntity} - it applies
     * {@code getScale()} over whatever this returns - so this is the hook that exists for changing
     * an entity's box. Call {@code refreshDimensions()} after editing the value, which
     * {@code XenoNpcBehaviour.apply} does.
     */
    @Override
    protected net.minecraft.world.entity.EntityDimensions getDefaultDimensions(
            net.minecraft.world.entity.Pose pose) {
        float scale = entityData == null ? 1.0f
                : Math.max(0.05f, Math.min(24.0f, entityData.get(DATA_HITBOX_SCALE)));
        net.minecraft.world.entity.EntityDimensions base = super.getDefaultDimensions(pose);
        return scale == 1.0f ? base : base.scale(scale);
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide()) {
            net.bullettrain.xenopixelsmod.compat.npc.NpcTargetKeeper.dropInvalidTarget(this);
        }
        boolean sagaFlight = !level().isClientSide()
                && net.bullettrain.xenopixelsmod.compat.npc.NpcFlightOwnership.brainDirectsFlight(getUUID());
        if (sagaFlight) {
            setNoGravity(true);
            getNavigation().stop();
        }
        super.aiStep();
        if (net.bullettrain.xenopixelsmod.config.XenoServerConfig.npcSwingTimeInAiStep) {
            updateSwingTime();
        }
        if (!level().isClientSide()) {
            if (net.bullettrain.xenopixelsmod.compat.npc.NpcFlightOwnership.brainDirectsFlight(getUUID())) {
                setNoGravity(true);
                getNavigation().stop();
            }
            XenoNpcBrainV5.tick(this);
            // Read once and shared: every one of these wants the same profile, and read()
            // deserialises it. A call apiece would be that work several times over for one answer.
            // readCached is safe here because none of these consumers mutate or write the profile.
            NpcCombatProfile jobProfile = NpcCombatProfile.readCached(this);
            XenoNpcBehaviour.syncHitbox(this, jobProfile);
            updateBossBar(jobProfile);
            XenoNpcBehaviour.tick(this, jobProfile, isSunBurnTick());
            XenoNpcBehaviour.tickLeash(this);
            XenoNpcBehaviour.tickRegen(this, jobProfile);
            net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTriggers.tick(this, jobProfile);
            net.bullettrain.xenopixelsmod.npc.job.NpcBardJob.tick(this, jobProfile);
            net.bullettrain.xenopixelsmod.npc.job.NpcHealerJob.tick(this, jobProfile);
            net.bullettrain.xenopixelsmod.npc.job.NpcGuardJob.tick(this, jobProfile);
            net.bullettrain.xenopixelsmod.npc.job.NpcFollowerJob.tick(this, jobProfile);
            net.bullettrain.xenopixelsmod.npc.job.NpcItemGiverJob.tick(this, jobProfile);
            net.bullettrain.xenopixelsmod.npc.path.NpcPathWalker.tick(this, jobProfile);
            net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.tickTimers(this);
            if ((tickCount + getId()) % net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.TICK_INTERVAL == 0) {
                net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(this, "tick", null, null, null, 0.0f,
                        n -> new xenoapi.npcs.api.event.NpcEvent.UpdateEvent(n));
            }
            boolean sceneRunning = net.bullettrain.xenopixelsmod.npc.scene.XenoNpcScenePlayback
                    .playing(this);
            // A scene outranks everything that moves an NPC. Scenes do not move one themselves
            // yet - the steps say lines and play clips - but holding the claim is what stops the
            // leash or a patrol walking the NPC off its mark halfway through one.
            if (sceneRunning) {
                XenoNpcBehaviour.claimMovement(this,
                        net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.SCENE);
            } else {
                net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.release(this,
                        net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.SCENE);
            }
            if (!sceneRunning) {
                net.bullettrain.xenopixelsmod.npc.job.NpcSocialBehaviour.tick(this, jobProfile);
            }
            net.bullettrain.xenopixelsmod.npc.scene.XenoNpcScenePlayback.tick(this);
            // Staggered by entity id so a crowd of NPCs does not all recompute on the same tick.
            if ((tickCount + getId()) % BATTLE_POWER_INTERVAL == 0) {
                refreshBattlePower();
            }
            // Idle chatter. RANDOM and WORLD lines have been loadable from role definitions since
            // lines existed, but nothing ever said them - XenoNpcSpeech.ambient keeps its own
            // cooldown, so this only has to offer it the chance.
            if (!sceneRunning && (tickCount + getId()) % AMBIENT_SPEECH_INTERVAL == 0) {
                net.bullettrain.xenopixelsmod.npc.lines.XenoNpcSpeech.ambient(this);
            }
        }
    }

    /**
     * Opens the editor for a wand held in either hand, and otherwise reports the NPC.
     *
     * <p>The hand check deliberately ignores {@code hand} and looks at both. Vanilla runs
     * {@code mobInteract} for the main hand first and stops as soon as it consumes the interaction
     * ({@code Player.interactOn} returns early on {@code consumesAction}), and both branches here
     * consume. Checking only the passed hand therefore meant a wand in the off hand never got a
     * turn: the main-hand pass fell through to the report branch, printed
     * "Name - role - Brain v5 - revision N", and ate the click. That is the reported symptom of the
     * editor "not opening" while that line appeared in chat.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        // Every tool that acts on an NPC, in one call. This was an if-block per tool, each
        // repeating the same main-hand-then-off-hand check - and a tool that forgot to be added
        // here was simply never called, which looks exactly like the item being broken. The Cloner
        // shipped that way: registered, textured, compiling, unrouted.
        InteractionResult tool = net.bullettrain.xenopixelsmod.item.custom.XenoNpcTool
                .resolve(player, this);
        if (tool != InteractionResult.PASS) {
            return tool;
        }
        if (holdsWand(player)) {
            if (!level().isClientSide()) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                        && player.hasPermissions(2)) {
                    net.bullettrain.xenopixelsmod.compat.npc.NpcProfileWatchers.watch(serverPlayer, this);
                    ModNetwork.sendToPlayer(serverPlayer,
                            new OpenXenoNpcEditorPacket(getId(), XenoNpcData.editorPayload(this, npcData)));
                } else {
                    // Previously silent, which looked identical to the editor failing to open.
                    player.sendSystemMessage(Component.literal(
                            "You need operator permission to edit Xeno NPCs."));
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        // The scripting tool opens the script screen for this NPC, server-authoritative and
        // op-gated the same way the editor open above is.
        if (holdsScriptTool(player)) {
            if (!level().isClientSide()) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                        && player.hasPermissions(2)) {
                    ModNetwork.sendToPlayer(serverPlayer, new OpenXenoNpcScriptPacket(getId(),
                            scriptOpenPayload()));
                } else {
                    player.sendSystemMessage(Component.literal(
                            "You need operator permission to edit Xeno NPC scripts."));
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        if (!level().isClientSide() && hand == InteractionHand.MAIN_HAND
                && net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(this, "interact", player, null, null, 0.0f,
                        n -> new xenoapi.npcs.api.event.NpcEvent.InteractEvent(n, player))) {
            // A script's interact(event) called event.setCanceled(true): it handled the click.
            return InteractionResult.SUCCESS;
        }
        if (!level().isClientSide()) {
            // Talking counts before anything else is decided: a TALK_TO_NPC quest is advanced by
            // the interaction itself, whether this NPC answers with a dialogue, a line, or the
            // operator readout. Sneaking is excluded because that is the developer path.
            if (player instanceof net.minecraft.server.level.ServerPlayer talker
                    && !player.isShiftKeyDown()) {
                net.bullettrain.xenopixelsmod.features.progression.ProgressionEvents.onTalkedToNpc(
                        talker, getUUID(), getName().getString(), role().id());
            }
            // A transporter with somewhere to send you opens its destination list, for the same
            // reason a trader opens its shop: answering "where can you take me?" with an ambient
            // line would look like the NPC had not noticed.
            if (player instanceof net.minecraft.server.level.ServerPlayer traveller
                    && !player.isShiftKeyDown()
                    && role() == XenoNpcRole.TRANSPORTER) {
                if (net.bullettrain.xenopixelsmod.npc.transport.TransportMenu
                        .open(traveller, this)) {
                    return InteractionResult.sidedSuccess(level().isClientSide());
                }
            }
            // A trader with stock opens its shop. Before speech for the same reason hand-in is:
            // an NPC that answered "what have you got?" with an ambient line would look like it
            // had not noticed. Shift-click still falls through, so an operator can reach the
            // dialogue and the debug readout on a trader.
            if (player instanceof net.minecraft.server.level.ServerPlayer shopper
                    && !player.isShiftKeyDown()
                    && role() == XenoNpcRole.TRADER) {
                net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile shopProfile =
                        net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.read(this);
                if (!shopProfile.trades.isEmpty()) {
                    openXenoShop(shopper);
                    return InteractionResult.sidedSuccess(level().isClientSide());
                }
            }
            // A teller with a bank opens the vault, for the same reason a trader opens its shop.
            // Shift-click still falls through, so an operator can reach the editor on a teller.
            if (player instanceof net.minecraft.server.level.ServerPlayer saver
                    && !player.isShiftKeyDown()
                    && role() == XenoNpcRole.BANK) {
                if (net.bullettrain.xenopixelsmod.npc.bank.NpcBankService.open(saver, this, 0)) {
                    return InteractionResult.sidedSuccess(level().isClientSide());
                }
            }
            // Before anything the NPC might say: a finished quest is handed in here, because an
            // NPC that answered one with an ambient line would look like it had not noticed.
            if (player instanceof net.minecraft.server.level.ServerPlayer handing
                    && !player.isShiftKeyDown()
                    && net.bullettrain.xenopixelsmod.features.progression.ProgressionEvents
                            .tryHandIn(handing, getName().getString(), role().id(), this)) {
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
            // A line takes priority over the debug readout: an NPC with something to say should say
            // it, and the readout is a developer aid rather than the point of clicking an NPC.
            // Sneaking always reaches the readout, so it stays available to operators.
            // Dialogue first, then a line, then the operator readout. An NPC with a conversation
            // should open it; one with only lines speaks; the readout is the developer fallback.
            // A scene set to play on interact takes the click before the dialogue, the line or the
            // readout - it is the most deliberate of the four, and an NPC authored to perform when
            // clicked should perform rather than mutter. Shift still reaches the readout.
            if (!player.isShiftKeyDown()
                    && net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTriggers.fire(this,
                            NpcCombatProfile.read(this),
                            net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTrigger.INTERACT)) {
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
            boolean handled = !player.isShiftKeyDown() && openDialogue(player);
            boolean spoke = !handled && !player.isShiftKeyDown()
                    && net.bullettrain.xenopixelsmod.npc.lines.XenoNpcSpeech.speak(
                            this, net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.INTERACT,
                            player);
            if (!handled && !spoke && player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal(getName().getString() + " — " + role().id()
                        + " — Brain v5 — revision " + npcData.revision()));
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    /**
     * Opens this NPC's conversation, if it has one.
     *
     * @return true when a dialogue was sent, so the caller does not also speak a line
     */
    private boolean openDialogue(Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }
        String id = dialogueRef(this);
        // The tree travels with the packet: datapacks are server data, so the client has no copy
        // to resolve an id against on a dedicated server.
        var dialogue = dialogueFor(this);
        if (dialogue == null) {
            return false;
        }
        dialogue = net.bullettrain.xenopixelsmod.features.progression.QuestDialogueFilter
                .forPlayer(serverPlayer, dialogue);
        if (dialogue == null) {
            return false;
        }
        net.bullettrain.xenopixelsmod.features.progression.ProgressionEvents
                .onDialogViewed(serverPlayer, dialogue.start());
        net.bullettrain.xenopixelsmod.capability.XenoCapabilities.get(serverPlayer)
                .ifPresent(data -> data.recordViewedDialogue(id));
        net.bullettrain.xenopixelsmod.npc.dialog.ScriptShownDialogues.clear(serverPlayer);
        ModNetwork.sendToPlayer(serverPlayer,
                new net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcDialoguePacket(
                        getId(), id, npcData.displayName(), dialogue));
        net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(this, "dialog", serverPlayer, null, null, 0.0f);
        return true;
    }

    /**
     * The conversation this NPC actually uses.
     *
     * <p>An NPC's own dialogue wins over the one its role names. That is what makes authoring from
     * the editor possible at all: a datapack loader has nothing to write back to, so an in-game
     * edit has to live on the NPC, and it would be pointless if the role's copy still won.
     *
     * <p>Falling back rather than replacing means a pack can still ship one conversation for every
     * NPC of a role, and giving a single NPC its own script does not disturb the rest.
     *
     * <p>Static and public because the option-handling packet has to resolve the same dialogue the
     * player is looking at. If the two disagreed, picking option 2 would run option 2 of a
     * different tree.
     */
    public static net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue dialogueFor(
            XenoNpcEntity npc) {
        if (npc == null) {
            return null;
        }
        var profile = NpcCombatProfile.read(npc);
        var own = profile.dialogue();
        if (own != null) {
            return own;
        }
        for (var slot : profile.dialogSlots.all()) {
            if (!slot.assigned()) {
                continue;
            }
            var shared = net.bullettrain.xenopixelsmod.npc.store.XenoNpcDataSource
                    .assignedDialogue(slot.group(), slot.id());
            if (shared != null) {
                return shared;
            }
        }
        String id = roleDialogueRef(npc);
        // Through the data source rather than straight to the loader: a dialogue saved into the
        // world store shadows the one the pack ships under the same id, the way a pack quest
        // already shadows a built-in. Deleting the stored one brings the pack's back.
        return id.isBlank()
                ? null : net.bullettrain.xenopixelsmod.npc.store.XenoNpcDataSource.dialogue(id);
    }

    /** What to call the dialogue in the open packet - the role's id, or a marker for an own one. */
    private static String dialogueRef(XenoNpcEntity npc) {
        var profile = NpcCombatProfile.read(npc);
        if (profile.dialogue() != null) {
            return OWN_DIALOGUE;
        }
        for (var slot : profile.dialogSlots.all()) {
            if (slot.assigned() && net.bullettrain.xenopixelsmod.npc.store.XenoNpcDataSource
                    .assignedDialogue(slot.group(), slot.id()) != null) {
                return slot.group() + "/" + slot.id();
            }
        }
        return roleDialogueRef(npc);
    }

    /**
     * Names an NPC-owned dialogue in the open packet.
     *
     * <p>The client does not resolve the id - the tree rides along - so this is only ever a label.
     * Not a resolvable id on purpose: nothing should be able to look one of these up in a pack.
     */
    public static final String OWN_DIALOGUE = "<npc>";

    private static String roleDialogueRef(XenoNpcEntity npc) {
        var definition = XenoNpcRoleDefinitions.get(npc.role());
        if (definition == null || definition.refs() == null) {
            return "";
        }
        String id = definition.refs().dialogue();
        return id == null ? "" : id;
    }

    /**
     * Never despawns.
     *
     * <p>Every Xeno NPC is placed deliberately - by the wand, by {@code /xenostructure summon}, or
     * by the respawn handler. None is ever naturally spawned. Without this they were treated as
     * ordinary wildlife: {@code Mob.checkDespawn} discards a mob when neither
     * {@code isPersistenceRequired()} nor this is true and the nearest player is past the category's
     * despawn distance, {@code removeWhenFarAway} returns true by default, and
     * {@code MobCategory.CREATURE} - what {@code ModEntities.npc} registers these as - carries a
     * despawn distance of 128. {@code ServerLevel} calls it every tick.
     *
     * <p>So an operator placed an NPC, rode 128 blocks away, and it was gone. Not killed - discarded,
     * which means the respawn store never heard about it either.
     *
     * <p>This rather than {@code setPersistenceRequired()} in the constructor: that writes a
     * {@code PersistenceRequired} flag into the entity's NBT and so would only protect NPCs placed
     * after the fix, leaving every NPC already standing in an existing world still liable to vanish.
     * An override needs no flag and applies the moment the chunk loads.
     */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /**
     * Belt and braces for the same thing.
     *
     * <p>{@link #requiresCustomPersistence()} already short-circuits {@code checkDespawn}, so this
     * is unreachable through vanilla's path - but it is the method other mods and events ask when
     * they want to know whether something may be culled, and answering "yes" there while refusing
     * to despawn is the kind of disagreement that turns into a bug report.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    private static boolean holdsWand(Player player) {
        return player.getMainHandItem().getItem() instanceof XenoNpcWandItem
                || player.getOffhandItem().getItem() instanceof XenoNpcWandItem;
    }

    private static boolean holdsScriptTool(Player player) {
        return player.getMainHandItem().getItem() instanceof XenoNpcScriptToolItem
                || player.getOffhandItem().getItem() instanceof XenoNpcScriptToolItem;
    }

    /**
     * Reads the NPC's identity from the root of the tag.
     *
     * <p>Flat, the way My NPCs and CustomNPCs both write theirs: their
     * {@code readAdditionalSaveData} hands the same root compound to every sub-object, so their
     * {@code "display"}/{@code "stats"} labels are error-log labels and not sub-compounds. Matching
     * that shape is what lets a later import rename keys instead of restructuring the tree.
     *
     * <p>{@link XenoNpcData#fromTag} unwraps a pre-flatten tag on the way in, so a world saved
     * under the old nested shape still loads and is written back flat on its next save.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        npcData = XenoNpcData.fromTag(tag, registeredRole);
        refreshNameplate();
        // The set-rather-than-asked flags have to be re-applied on load; the overrides above take
        // care of themselves, but invisibility, glow and the hitbox do not.
        NpcCombatProfile profile = NpcCombatProfile.read(this);
        XenoNpcBehaviour.apply(this, profile);
        NpcAiGoals.apply(this, profile);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // Merged into the root rather than put under a key. XenoNpcFlatSchemaTest holds the line
        // that nothing here shares a name with vanilla - flat, a clash is silent and fatal.
        CompoundTag identity = npcData.toTag();
        for (String key : identity.getAllKeys()) {
            tag.put(key, identity.get(key).copy());
        }
    }

    /**
     * Refuses {@code /kill}.
     *
     * <p>{@code KillCommand} calls {@link net.minecraft.world.entity.Entity#kill()}, and
     * {@code LivingEntity.kill()} is {@code hurt(genericKill(), Float.MAX_VALUE)}. Overriding the
     * entry point is therefore enough to keep an operator from wiping out a world's NPCs with one
     * selector - which was easy to do by accident, and is not even a real delete: {@link #die} then
     * schedules a respawn and the NPC returns a few seconds later.
     *
     * <p><strong>This is not damage immunity.</strong> Only the {@code kill()} entry point is
     * refused. Ordinary combat damage still flows through {@code hurt()} and still ends in
     * {@code die()}, so NPCs remain defeatable exactly as before. Do not "fix" this into a blanket
     * invulnerability.
     *
     * <p>Removing an NPC for good goes through {@code XenoNpcDeletePacket}, which cancels the
     * respawn first and then discards the entity.
     */
    @Override
    public void kill() {
        if (!net.bullettrain.xenopixelsmod.config.XenoServerConfig.xenoNpcKillCommandImmune) {
            super.kill();
        }
    }

    /**
     * Removes this NPC permanently.
     *
     * <p>Cancels the respawn entry <em>before</em> discarding, because {@link #die} schedules one
     * and discarding afterwards would leave the NPC queued to come back. {@code discard()} rather
     * than {@code kill()} so {@code die()} never runs at all.
     */
    public void deletePermanently() {
        if (!level().isClientSide() && level() instanceof ServerLevel server) {
            XenoNpcRespawnData.get(server.getServer()).cancel(getUUID());
        }
        net.bullettrain.xenopixelsmod.npc.lines.XenoNpcSpeech.forget(getUUID());
        net.bullettrain.xenopixelsmod.npc.job.NpcSocialBehaviour.forget(getUUID());
        net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.forget(getUUID());
        net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTriggers.forget(getUUID());
        discard();
    }

    // ---------------------------------------------------------------- Advanced > Sounds
    //
    // Living / Hurt / Death / Step go through the vanilla overrides rather than an explicit play
    // call, so they keep vanilla's own timing, attenuation and broadcast - and cannot end up
    // played twice, once by us and once by the default. Angry has no vanilla equivalent and is
    // played from setTarget below.
    //
    // Each returns the default when the slot is blank or names a sound that does not exist, so a
    // typo in the editor is silent-as-before rather than a crash.

    private net.minecraft.sounds.SoundEvent customSound(String slot,
                                                        net.minecraft.sounds.SoundEvent fallback) {
        if (!net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.hasProfile(this)) {
            return fallback;
        }
        net.minecraft.sounds.SoundEvent custom =
                net.bullettrain.xenopixelsmod.compat.npc.NpcCustomSounds.resolve(
                        net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.readCached(this)
                                .soundFor(slot));
        return custom != null ? custom : fallback;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return customSound("living", super.getAmbientSound());
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(
            net.minecraft.world.damagesource.DamageSource source) {
        return customSound("hurt", super.getHurtSound(source));
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return customSound("death", super.getDeathSound());
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos,
                                 net.minecraft.world.level.block.state.BlockState state) {
        net.minecraft.sounds.SoundEvent step = customSound("step", null);
        if (step == null) {
            super.playStepSound(pos, state);
            return;
        }
        playSound(step, 0.15f, 1.0f);
    }

    /**
     * MyNPCs' "Has Pitch".
     *
     * <p>Vanilla already varies voice pitch a little on every play; turning the flag off pins it to
     * 1.0 so a sound comes out identical each time, which is what that switch means in MyNPCs.
     */
    @Override
    public float getVoicePitch() {
        if (net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.hasProfile(this)
                && !net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.readCached(this)
                        .soundHasPitch) {
            return 1.0f;
        }
        return super.getVoicePitch();
    }

    /**
     * Plays a scene set to fire when this NPC is hurt.
     *
     * <p>Only on damage that actually landed - {@code super.hurt} answering false means invulnerable,
     * already dead, or the wrong damage type, and a taunt for a hit that did nothing would be a
     * scene firing at a phantom.
     *
     * <p>The cooldown in {@code XenoNpcSceneTriggers} is what keeps this sane: without it a combo
     * would restart the scene from step one several times a second.
     */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!level().isClientSide() && isAlive()) {
            Float scripted = scriptedDamage(source, amount);
            if (scripted == null) return false;
            amount = scripted;
        }
        boolean landed = super.hurt(source, amount);
        if (landed && !level().isClientSide() && isAlive()) {
            net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTriggers.fire(this,
                    NpcCombatProfile.read(this),
                    net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTrigger.DAMAGED);
        }
        return landed;
    }

    /**
     * The {@code damaged} script hook and typed XenoAPI event: the damage to apply, or null when a
     * script or listener canceled the hit. Also honours the typed event's {@code clearTarget}.
     */
    private Float scriptedDamage(net.minecraft.world.damagesource.DamageSource source, float amount) {
        net.minecraft.world.entity.LivingEntity attacker =
                source.getEntity() instanceof net.minecraft.world.entity.LivingEntity living ? living : null;
        var scriptEvent = net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fireEvent(
                this, "damaged", attacker instanceof Player ? attacker : null,
                attacker, null, amount,
                n -> new xenoapi.npcs.api.event.NpcEvent.DamagedEvent(n, source.getEntity(), amount, source));
        if (scriptEvent == null) return amount;
        if (scriptEvent.isCanceled()) return null;
        if (scriptEvent.xeno instanceof xenoapi.npcs.api.event.NpcEvent.DamagedEvent typedDamaged
                && typedDamaged.clearTarget) setTarget(null);
        float scripted = scriptEvent.getDamage();
        return Float.isFinite(scripted) ? Math.max(0.0f, scripted) : 0.0f;
    }

    /** Says an attack line the first time this NPC picks a target, not on every swing. */
    @Override
    public void setTarget(@javax.annotation.Nullable net.minecraft.world.entity.LivingEntity target) {
        if (target != null && !net.bullettrain.xenopixelsmod.compat.npc.NpcTargetKeeper
                .isCombatTarget(target)) target = null;
        // Walking home on the leash: disengaged until it arrives (XenoNpcBehaviour.refusesNewTarget).
        if (XenoNpcBehaviour.refusesNewTarget(this, target)) target = null;
        net.minecraft.world.entity.LivingEntity previous = getTarget();
        if (!level().isClientSide() && target != null && previous != target
                && !dispatchingTargetScript) {
            dispatchingTargetScript = true;
            final net.minecraft.world.entity.LivingEntity chosen = target;
            try {
                if (net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(this, "target",
                        target instanceof Player ? target : null, null, target, 0.0f,
                        n -> new xenoapi.npcs.api.event.NpcEvent.TargetEvent(n, chosen))) return;
            } finally {
                dispatchingTargetScript = false;
            }
            // A script may have chosen another target itself. Keep that choice.
            if (getTarget() != previous) return;
        }
        super.setTarget(target);
        if (!level().isClientSide() && previous != target) {
            if (target == null) {
                net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(this, "targetLost", null, null, previous, 0.0f,
                        n -> new xenoapi.npcs.api.event.NpcEvent.TargetLostEvent(n, previous));
            }
        }
        if (!level().isClientSide() && target != null && previous == null) {
            net.bullettrain.xenopixelsmod.npc.lines.XenoNpcSpeech.speak(this,
                    net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.ATTACK,
                    target instanceof net.minecraft.world.entity.player.Player p ? p : null);
            // Advanced > Sounds > Angry, on the same edge as the attack line: when the NPC picks a
            // target, not on every swing.
            net.bullettrain.xenopixelsmod.compat.npc.NpcCustomSounds.play(this, "angry");
        }
    }

    /**
     * Advanced &gt; Inventory: the nine authored drops.
     *
     * <p>Worn gear is not dropped here and never is - {@code NpcGear.applyTo} equips every slot
     * with a drop chance of zero, so a player cannot strip an NPC's authored appearance by killing
     * it. The drop list is where drops are authored, which is why the reference page has nine
     * chance fields and only seven slot buttons.
     *
     * <p>{@code Auto Pickup} hands the stack to whoever landed the kill. What does not fit falls
     * where an ordinary drop would, rather than being destroyed - a full inventory is not a reason
     * to lose an item.
     */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level,
                                       net.minecraft.world.damagesource.DamageSource source,
                                       boolean hitByPlayer) {
        super.dropCustomDeathLoot(level, source, hitByPlayer);
        var drops = NpcCombatProfile.read(this).drops;
        var rolled = drops.roll(getRandom(), level.registryAccess());
        if (rolled.isEmpty()) {
            return;
        }
        net.minecraft.world.entity.player.Player killer =
                source.getEntity() instanceof net.minecraft.world.entity.player.Player p ? p : null;
        boolean pickUp = drops.lootMode()
                == net.bullettrain.xenopixelsmod.npc.inventory.NpcLootMode.AUTO_PICKUP
                && killer != null;
        for (net.minecraft.world.item.ItemStack stack : rolled) {
            if (pickUp && killer.getInventory().add(stack)) {
                continue;
            }
            spawnAtLocation(stack);
        }
    }

    /**
     * Advanced &gt; Inventory: Min Exp and Max Exp.
     *
     * <p>An NPC with neither set keeps whatever its type is worth, rather than dropping to zero -
     * the fields are an override, and an operator who never opened the page did not ask for one.
     */
    @Override
    protected int getBaseExperienceReward() {
        var drops = NpcCombatProfile.read(this).drops;
        if (drops.minExp() <= 0 && drops.maxExp() <= 0) {
            return super.getBaseExperienceReward();
        }
        return drops.rollExperience(getRandom());
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!level().isClientSide()) {
            net.minecraft.world.entity.LivingEntity killer =
                    source.getEntity() instanceof net.minecraft.world.entity.LivingEntity living
                            ? living : null;
            net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(this, "died", killer instanceof Player ? killer : null, killer, null, 0.0f,
                    n -> new xenoapi.npcs.api.event.NpcEvent.DiedEvent(n, source, source.getEntity()));
            // Sent before super.die(), which can drop this entity from its trackers - after that,
            // a broadcast to trackers would reach nobody.
            net.bullettrain.xenopixelsmod.npc.lines.XenoNpcSpeech.speak(this,
                    net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.KILLED,
                    source.getEntity() instanceof net.minecraft.world.entity.player.Player p
                            ? p : null);
            // Same edge and the same reason: after super.die() this entity may be out of its
            // trackers, and a last word nobody can see is not a last word.
            net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTriggers.fire(this,
                    NpcCombatProfile.read(this),
                    net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTrigger.DEATH);
        }
        if (!level().isClientSide() && npcData.respawnEnabled()
                && level() instanceof ServerLevel server) {
            XenoNpcRespawnData data = XenoNpcRespawnData.get(server.getServer());
            // Stores the combat profile too. npcData.toTag() is identity only - name, role,
            // revision - so scheduling with it alone meant a respawned NPC came back with default
            // stats, appearance and behaviour. Same omission that made the editor look like it was
            // not saving; XenoNpcData.editorPayload exists for exactly this reason.
            // Respawns at its home rather than where it fell, which is what CustomNPCs and MyNPCs
            // do - an NPC dragged across the world by a fight belongs back where it was placed.
            double rx = npcData.hasHome() ? npcData.homeX() : getX();
            double ry = npcData.hasHome() ? npcData.homeY() : getY();
            double rz = npcData.hasHome() ? npcData.homeZ() : getZ();
            // Game time, not the server's tick counter: the counter restarts at 0 every launch and
            // is never saved, so a deadline written before a restart was compared against a clock
            // that had gone backwards and the NPC never returned.
            long now = server.getGameTime();
            data.schedule(new XenoNpcRespawnData.Entry(getUUID(), server.dimension().location(),
                    rx, ry, rz, getYRot(), getXRot(), now + npcData.respawnDelayTicks(),
                    role(), now, XenoNpcData.editorPayload(this, npcData)));
        }
        super.die(source);
    }
    // ------------------------------------------------------------------ Merchant

    /**
     * Who is looking at the shop, or null.
     *
     * <p>Not saved: a trade screen cannot survive a restart, and persisting the trader would leave
     * it convinced a player who is no longer here still has it open.
     */
    @org.jetbrains.annotations.Nullable
    private Player tradingPlayer;

    /**
     * The current offers.
     *
     * <p>Rebuilt from the profile rather than stored, so an operator who edits the stock while a
     * shop is open does not leave a stale copy behind. {@code overrideOffers} is the one path that
     * replaces it, and vanilla calls that on the client with what the server sent.
     */
    @org.jetbrains.annotations.Nullable
    private net.minecraft.world.item.trading.MerchantOffers offers;

    private void openXenoShop(net.minecraft.server.level.ServerPlayer shopper) {
        setTradingPlayer(shopper);
        // Level 0: the villager-style badge and progress bar are off, so the number is unused.
        openTradingScreen(shopper, getDisplayName(), 0);
    }

    @Override
    public void setTradingPlayer(@org.jetbrains.annotations.Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    @Override
    public net.minecraft.world.item.trading.MerchantOffers getOffers() {
        if (offers == null) {
            var tradeProfile = net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.read(this);
            offers = net.bullettrain.xenopixelsmod.npc.trade.NpcTradeOffers.build(
                    tradeProfile.trades, tradeProfile.tradeIgnoreDamage, tradeProfile.tradeIgnoreNbt);
        }
        return offers;
    }

    /** A profile edit must not leave a shop showing offers built from old stock. */
    public void invalidateTradeOffers() {
        offers = null;
    }

    @Override
    public void overrideOffers(net.minecraft.world.item.trading.MerchantOffers replacement) {
        // The client's copy, handed over by vanilla from what the server sent.
        this.offers = replacement;
    }

    @Override
    public void notifyTrade(net.minecraft.world.item.trading.MerchantOffer offer) {
        // Vanilla has already decremented the offer's uses. Nothing here levels up or restocks, so
        // there is nothing else to record - but the sound is what tells the player it worked.
        offer.increaseUses();
        playSound(getNotifyTradeSound(), getSoundVolume(), getVoicePitch());
    }

    @Override
    public void notifyTradeUpdated(net.minecraft.world.item.ItemStack stack) {
        // Vanilla plays a click while the player shuffles items in the trade slots. An NPC that
        // was silent here would feel less responsive than a villager.
        if (!level().isClientSide() && tickCount > 0) {
            playSound(getNotifyTradeSound(), getSoundVolume(), getVoicePitch());
        }
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
        // Nothing levels up, so there is nothing to override.
    }

    @Override
    public boolean showProgressBar() {
        // The bar tracks villager levelling, which does not exist here. Showing an empty one would
        // promise progression that never arrives.
        return false;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getNotifyTradeSound() {
        return net.minecraft.sounds.SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide();
    }

    /**
     * Idle and walk from the selected model, plus one-shot attacks.
     *
     * <p>A GeckoLib rig supplied through the editor is arbitrary third-party art, so the only
     * clips assumed to exist are these conventional names. A rig missing them still renders;
     * GeckoLib simply plays nothing, which is better than guessing at clip names that are not
     * there.
     *
     * <p>The attack controller also accepts a scripted clip handed to it by the native Gecko
     * renderer. Ordinary attacks still use {@code swingTime}, which the client advances when
     * the server broadcasts a swing.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state ->
                state.isMoving() ? state.setAndContinue(WALK) : state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, "attack", 0, this::routeAttack));
    }

    private PlayState routeAttack(AnimationState<XenoNpcEntity> state) {
        if (scriptedAttackStop) {
            scriptedAttackStop = false;
            scriptedAttackClip = null;
            attackClipPlaying = false;
            scriptedAttackPlaying = false;
            state.getController().stop();
            return PlayState.STOP;
        }
        if (scriptedAttackClip != null) {
            String clip = scriptedAttackClip;
            boolean hold = scriptedAttackHold;
            scriptedAttackClip = null;
            attackClipPlaying = true;
            scriptedAttackPlaying = true;
            state.getController().forceAnimationReset();
            state.getController().setAnimation(hold
                    ? RawAnimation.begin().thenPlayAndHold(clip)
                    : RawAnimation.begin().thenPlay(clip));
            return PlayState.CONTINUE;
        }
        if (scriptedAttackPlaying) {
            // The vanilla melee goal can swing during a scripted string. Keep that swing from
            // replacing a queued Xeno clip with the model's ordinary attack animation.
            previousAttackSwingTime = swingTime;
            if (state.getController().hasAnimationFinished()
                    || state.getController().getAnimationState() == AnimationController.State.STOPPED) {
                scriptedAttackPlaying = false;
                attackClipPlaying = false;
                return PlayState.STOP;
            }
            return PlayState.CONTINUE;
        }
        // A new swing may arrive before the previous clip ends. A falling swing counter also
        // catches it when no render frame observed the zero between two attacks.
        boolean newSwing = this.swingTime > 0
                && (this.previousAttackSwingTime <= 0
                || this.swingTime < this.previousAttackSwingTime);
        this.previousAttackSwingTime = this.swingTime;
        if (newSwing) {
            String clip = resolveBakedAttackClip();
            if (clip == null) {
                this.attackClipPlaying = false;
                return PlayState.STOP;
            }
            this.attackClipPlaying = true;
            state.getController().forceAnimationReset();
            state.getController().setAnimation(RawAnimation.begin().thenPlay(clip));
            return PlayState.CONTINUE;
        }
        if (this.attackClipPlaying) {
            if (state.getController().hasAnimationFinished()
                    || state.getController().getAnimationState() == AnimationController.State.STOPPED) {
                this.attackClipPlaying = false;
                return PlayState.STOP;
            }
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    /** A scripted clip (a wave, a nod) is queued or playing on the attack controller. */
    public boolean scriptedClipActive() {
        return scriptedAttackPlaying || scriptedAttackClip != null;
    }

    /** Called on the render thread after the queued server packet reaches the Gecko renderer. */
    public void acceptScriptAnimation(String clip, boolean hold, boolean stop) {
        scriptedAttackStop = stop;
        scriptedAttackClip = stop ? null : clip;
        scriptedAttackHold = hold;
    }

    /** What the script tool opens: this NPC's container plus its recent console lines. */
    public net.minecraft.nbt.CompoundTag scriptOpenPayload() {
        net.minecraft.nbt.CompoundTag payload = new net.minecraft.nbt.CompoundTag();
        NpcCombatProfile.readCached(this).scripts.write(payload);
        payload.put("Console", scriptConsoleTag());
        return payload;
    }

    /** Recent script prints and errors, newest first, each capped at 256 characters. */
    public net.minecraft.nbt.ListTag scriptConsoleTag() {
        net.minecraft.nbt.ListTag console = new net.minecraft.nbt.ListTag();
        for (String line : net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.console(getUUID())) {
            console.add(net.minecraft.nbt.StringTag.valueOf(line.length() > 256
                    ? line.substring(0, 256) : line));
        }
        return console;
    }

    /** collide hook, throttled: pushing runs every tick while two bodies overlap. */
    @Override
    protected void doPush(net.minecraft.world.entity.Entity other) {
        super.doPush(other);
        if (!level().isClientSide() && tickCount % 5 == 0
                && other instanceof net.minecraft.world.entity.LivingEntity living) {
            net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(this, "collide", living instanceof Player ? living : null, null, living, 0.0f,
                    n -> new xenoapi.npcs.api.event.NpcEvent.CollideEvent(n, living));
        }
    }

    private boolean brainFlying() {
        return !level().isClientSide()
                && net.bullettrain.xenopixelsmod.compat.npc.NpcFlightOwnership.brainDirectsFlight(getUUID());
    }

    /** Called by the client model each time it resolves this NPC's animation file. */
    public void noteAnimationFile(net.minecraft.resources.ResourceLocation file, String attackClip) {
        this.bakedAnimationFile = file;
        this.bakedConfiguredAttackClip = attackClip == null ? "" : attackClip;
    }

    /** Choose only a clip present in this NPC's animation file, honoring its authored attack. */
    private String resolveBakedAttackClip() {
        net.minecraft.resources.ResourceLocation file = this.bakedAnimationFile;
        if (file == null) return "attack";
        BakedAnimations baked = GeckoLibCache.getBakedAnimations().get(file);
        if (baked == null) return "attack";
        return NpcAttackClipSelector.select(baked.animations().keySet(), bakedConfiguredAttackClip);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
