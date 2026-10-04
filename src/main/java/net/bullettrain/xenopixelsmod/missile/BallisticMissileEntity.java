package net.bullettrain.xenopixelsmod.missile;

import net.bullettrain.xenopixelsmod.api.event.MissileWarheadEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.UUID;

/**
 * Lightweight server-authoritative ballistic missile (entity, not a VS mini-ship).
 * Phases: eject → boost → coast → terminal guidance → impact.
 */
public class BallisticMissileEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(BallisticMissileEntity.class, EntityDataSerializers.INT);
    /** True while the server's Effekseer thruster plays, so the client skips its vanilla flame. */
    private static final EntityDataAccessor<Boolean> DATA_EFFEK_TRAIL =
            SynchedEntityData.defineId(BallisticMissileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SIZE =
            SynchedEntityData.defineId(BallisticMissileEntity.class, EntityDataSerializers.INT);

    private static final double TERMINAL_ACQUIRE_RANGE = 48.0;
    private static final double TERMINAL_MAX_TURN = 0.12;
    private static final double TERMINAL_GRAVITY_SCALE = 0.85;
    private static final double TERMINAL_MAX_LEAD_TICKS = 60.0;
    private static final int MIN_LIFETIME = 20 * 90;

    private double targetX, targetY, targetZ;
    private boolean hasTarget;
    private boolean terminalEnabled = true;
    private double boostAccel = 0.35;
    private double gravitySi = BallisticCalculator.EARTH_GRAVITY;
    private double dragCoefficient = BallisticCalculator.DEFAULT_DRAG;
    private int maxLifetime = MIN_LIFETIME;
    private double terminalAcquireRange = TERMINAL_ACQUIRE_RANGE;
    private int boostTicksLeft = 40;
    private int phaseAge;
    private float explosionPower = 4.0f;
    private UUID ownerId;
    private Vec3 loftDir = new Vec3(0, 1, 0);
    private Vec3 ejectOrigin = Vec3.ZERO;
    private double siloClearance = MissileEject.DEFAULT_CLEARANCE;
    private int launchWorldY;
    private int apexY;
    private int cruiseY;
    private ItemStack warhead = ItemStack.EMPTY;
    private MissileSize size = MissileSize.MEDIUM;
    /** Silo/tube rounds fly the planner's corridor after the rail instead of an open-loop burn. */
    private MissileGuidance.Corridor corridor;
    /** Guidance V3 after the rail (taken from /xenoguidance at launch), or null. */
    private net.bullettrain.xenopixelsmod.missile.v3.TubeV3 v3;

    public static final class LaunchConfig {
        public Vec3 spawn = Vec3.ZERO;
        public Vec3 target = Vec3.ZERO;
        public Vec3 loft = new Vec3(0, 1, 0);
        public double boostAccel = 0.35;
        public int boostTicks = 40;
        public boolean terminal = true;
        public float yield = 0.0f;
        public UUID owner;
        public double gravitySi = BallisticCalculator.EARTH_GRAVITY;
        public double dragCoefficient = BallisticCalculator.DEFAULT_DRAG;
        public int apexY;
        public int cruiseY;
        public double siloClearance = MissileEject.DEFAULT_CLEARANCE;
        public int launchWorldY;
        public MissileSize size = MissileSize.MEDIUM;
        public ItemStack warhead = ItemStack.EMPTY;
        public Vec3 ejectOrigin;
        /**
         * Fly the planned corridor (climb, then glide to target) after the rail. {@code loft} is
         * then only the rail axis. Without it a silo round burned straight up and fell back.
         */
        public boolean guided;
    }

    public BallisticMissileEntity(EntityType<? extends BallisticMissileEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public static BallisticMissileEntity create(ServerLevel level, Vec3 spawn, Vec3 target,
                                                Vec3 loftDirection, double boostAccel, int boostTicks,
                                                boolean terminal, float yield, UUID owner) {
        return create(level, spawn, target, loftDirection, boostAccel, boostTicks,
                terminal, yield, owner, BallisticCalculator.EARTH_GRAVITY,
                BallisticCalculator.DEFAULT_DRAG);
    }

    public static BallisticMissileEntity create(ServerLevel level, Vec3 spawn, Vec3 target,
                                                Vec3 loftDirection, double boostAccel, int boostTicks,
                                                boolean terminal, float yield, UUID owner,
                                                double gravitySi, double dragCoefficient) {
        LaunchConfig config = new LaunchConfig();
        config.spawn = spawn;
        config.target = target;
        config.loft = loftDirection;
        config.boostAccel = boostAccel;
        config.boostTicks = boostTicks;
        config.terminal = terminal;
        config.yield = yield;
        config.owner = owner;
        config.gravitySi = gravitySi;
        config.dragCoefficient = dragCoefficient;
        config.ejectOrigin = spawn;
        return create(level, config);
    }

    public static BallisticMissileEntity create(ServerLevel level, LaunchConfig config) {
        BallisticMissileEntity m = ModEntities.BALLISTIC_MISSILE.get().create(level);
        if (m == null || config == null) return null;
        Vec3 spawn = config.spawn == null ? Vec3.ZERO : config.spawn;
        m.setPos(spawn.x, spawn.y, spawn.z);
        if (config.target != null) {
            m.setTarget(config.target.x, config.target.y, config.target.z);
        }
        m.loftDir = config.loft == null || config.loft.lengthSqr() < 1.0e-6
                ? new Vec3(0, 1, 0) : config.loft.normalize();
        m.boostAccel = config.boostAccel;
        m.gravitySi = BallisticCalculator.sanitizeGravity(config.gravitySi);
        m.dragCoefficient = BallisticCalculator.sanitizeDrag(config.dragCoefficient);
        m.boostTicksLeft = Math.max(5, config.boostTicks);
        m.terminalEnabled = config.terminal;
        m.explosionPower = Mth.clamp(config.yield, 0f, MissileWarhead.MAX_YIELD);
        m.ownerId = config.owner;
        m.size = config.size == null ? MissileSize.MEDIUM : config.size;
        m.entityData.set(DATA_SIZE, m.size.ordinal());
        m.warhead = config.warhead == null ? ItemStack.EMPTY : config.warhead.copy();
        m.apexY = Math.max(0, config.apexY);
        m.cruiseY = Math.max(0, config.cruiseY);
        m.siloClearance = MissileEject.clampClearance((int) Math.round(config.siloClearance));
        m.launchWorldY = MissileEject.clampLaunchWorldY(config.launchWorldY);
        m.ejectOrigin = config.ejectOrigin == null ? spawn : config.ejectOrigin;
        if (config.guided && config.target != null
                && net.bullettrain.xenopixelsmod.aero.GuidanceVersion.active().usesV3Missiles()) {
            m.v3 = net.bullettrain.xenopixelsmod.missile.v3.TubeV3.plan(spawn, config.target, m.boostAccel,
                    m.boostTicksLeft, m.gravitySi, m.dragCoefficient, m.apexY, m.cruiseY, m.terminalEnabled,
                    m.siloClearance, m.loftDir);
        } else if (config.guided && config.target != null) {
            int planLoft = m.apexY > 0 ? m.apexY : m.cruiseY;
            m.corridor = MissileGuidance.Corridor.plan(spawn, config.target, m.boostAccel, m.boostTicksLeft,
                    planLoft, m.gravitySi, m.dragCoefficient);
        }
        double exitSpeed = BallisticTrajectory.boostExitSpeed(m.boostAccel, m.boostTicksLeft);
        Vec3 tgt = config.target == null ? spawn : config.target;
        long eta = BallisticTrajectory.estimateEtaTicks(spawn, tgt, Math.max(0.25, exitSpeed * 0.5));
        m.maxLifetime = (int) Math.min(Integer.MAX_VALUE - 1L,
                Math.max(MIN_LIFETIME, eta * 4L + 20L * 60L));
        m.terminalAcquireRange = Math.max(TERMINAL_ACQUIRE_RANGE,
                Math.min(2_000.0, Math.hypot(tgt.x - spawn.x, tgt.z - spawn.z) * 0.02));
        m.setPhase(MissilePhase.EJECT);
        m.noPhysics = true;
        m.setDeltaMovement(m.loftDir.scale(0.8));
        m.refreshDimensions();
        return m;
    }

    public void setTarget(double x, double y, double z) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.hasTarget = true;
    }

    public MissilePhase getPhase() {
        int i = this.entityData.get(DATA_PHASE);
        MissilePhase[] vals = MissilePhase.values();
        if (i < 0 || i >= vals.length) return MissilePhase.DEAD;
        return vals[i];
    }

    public MissileSize getMissileSize() {
        return MissileSize.byOrdinal(this.entityData.get(DATA_SIZE));
    }

    private void setPhase(MissilePhase phase) {
        this.entityData.set(DATA_PHASE, phase.ordinal());
        this.phaseAge = 0;
        // Flight is kinematic. A loaded/unloaded border or a stale collision shape must not
        // zero the authoritative velocity; target and entity hits are handled explicitly below.
        this.noPhysics = phase != MissilePhase.DEAD;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PHASE, MissilePhase.EJECT.ordinal());
        builder.define(DATA_SIZE, MissileSize.MEDIUM.ordinal());
        builder.define(DATA_EFFEK_TRAIL, false);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float hit = getMissileSize().hitbox();
        return EntityDimensions.scalable(hit, hit);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        float len = getMissileSize().visualLength();
        return getBoundingBox().inflate(Math.max(1.0f, len * 0.55f));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double range = 256.0 * getViewScale();
        return distance < range * range;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount > maxLifetime) {
            detonate();
            return;
        }

        MissilePhase phase = getPhase();
        if (phase == MissilePhase.DEAD) {
            discard();
            return;
        }

        phaseAge++;
        Vec3 pos = position();
        Vec3 vel = getDeltaMovement();

        if (v3 != null && hasTarget && phase != MissilePhase.EJECT) {
            // V3 flies from where the round actually is, every tick.
            vel = v3.step(pos, vel, new Vec3(targetX, targetY, targetZ));
            if (v3.phase() != phase) setPhase(v3.phase());
        } else switch (phase) {
            case EJECT -> {
                vel = loftDir.scale(1.2 + Math.min(phaseAge, 40) * 0.15);
                if (MissileEject.finished(phaseAge, ejectOrigin, pos, loftDir, siloClearance, launchWorldY)) {
                    beginBoost(pos);
                }
            }
            case BOOST -> {
                if (corridor != null && hasTarget) {
                    vel = MissileGuidance.boost(pos, vel, corridor, MissileGuidance.burnPerTick(boostAccel),
                            BallisticCalculator.toTickGravity(gravitySi), dragCoefficient);
                    if (--boostTicksLeft <= 0) setPhase(MissilePhase.COAST);
                    break;
                }
                vel = vel.add(0, -BallisticCalculator.toTickGravity(gravitySi), 0);
                Vec3 burn = loftDir;
                if (apexY > 0 && pos.y < apexY) {
                    burn = new Vec3(loftDir.x, Math.max(loftDir.y, 0.85), loftDir.z).normalize();
                }
                vel = vel.add(burn.scale(boostAccel * 55.0 / 400.0));
                vel = applyDrag(vel, dragCoefficient, pos.y);
                boostTicksLeft--;
                if (boostTicksLeft <= 0 || (apexY > 0 && pos.y >= apexY)) {
                    setPhase(MissilePhase.COAST);
                }
            }
            case COAST -> {
                if (cruiseY > 0 && hasTarget) {
                    vel = cruiseTowardTarget(pos, vel);
                } else if (corridor != null && hasTarget) {
                    vel = MissileGuidance.glide(pos, vel, corridor,
                            BallisticCalculator.toTickGravity(gravitySi), dragCoefficient);
                } else {
                    vel = vel.add(0, -BallisticCalculator.toTickGravity(gravitySi), 0);
                    vel = applyDrag(vel, dragCoefficient, pos.y);
                }
                if (terminalEnabled && hasTarget && pos.distanceToSqr(targetX, targetY, targetZ)
                        < terminalAcquireRange * terminalAcquireRange) {
                    setPhase(MissilePhase.TERMINAL);
                }
            }
            case TERMINAL -> {
                double tickGravity = BallisticCalculator.toTickGravity(gravitySi) * TERMINAL_GRAVITY_SCALE;
                vel = vel.add(0, -tickGravity, 0);
                if (hasTarget) {
                    Vec3 toTarget = new Vec3(targetX - pos.x, targetY - pos.y, targetZ - pos.z);
                    double distance = toTarget.length();
                    if (distance > 1.0e-3) {
                        double speed = Math.max(0.8, vel.length());
                        double drop = 0.0;
                        if (net.bullettrain.xenopixelsmod.config.XenoServerConfig
                                .missileTerminalGravityCompensation) {
                            double timeToGo = Math.min(TERMINAL_MAX_LEAD_TICKS, distance / speed);
                            drop = 0.5 * tickGravity * timeToGo * timeToGo;
                        }
                        Vec3 desired = toTarget.add(0, drop, 0).normalize().scale(speed);
                        vel = steer(vel, desired, TERMINAL_MAX_TURN);
                    }
                }
                vel = applyDrag(vel, dragCoefficient * 0.5, pos.y);
            }
            default -> {
            }
        }

        BlockPos tgt = hasTarget ? BlockPos.containing(targetX, targetY, targetZ) : null;
        if (level() instanceof ServerLevel sl
                && (tickCount <= 1 || tickCount % MissileChunkLoadManager.LIVE_TRACK_INTERVAL_TICKS == 0)) {
            MissileChunkLoadManager.trackLiveMissile(sl, pos, vel, tgt);
        }
        boolean nextReady = MissileChunkLoadManager.isEntityChunkReady(level(), pos.add(vel));
        this.noPhysics = MissileFlightSim.kinematic(getPhase() == MissilePhase.EJECT, nextReady);

        setDeltaMovement(vel);
        move(MoverType.SELF, vel);

        if (vel.lengthSqr() > 1.0e-6) {
            float yRot = (float) (Mth.atan2(vel.x, vel.z) * (180F / Math.PI));
            float xRot = (float) (Mth.atan2(vel.y, vel.horizontalDistance()) * (180F / Math.PI));
            setYRot(yRot);
            setXRot(-xRot);
        }

        if (!level().isClientSide) {
            boolean impactReady = MissileChunkLoadManager.isEntityChunkReady(level(), position());
            boolean collided = horizontalCollision || verticalCollision || onGround();
            if (MissileFlightSim.impactDetonation(getPhase() == MissilePhase.EJECT, collided, impactReady)) {
                detonate();
                return;
            }
            if (hasTarget && MissileFlightSim.reachedTarget(pos, position(), targetX, targetY, targetZ, 1.5)) {
                detonate();
                return;
            }
            AABB box = getBoundingBox().inflate(0.3);
            List<Entity> hits = level().getEntities(this, box,
                    e -> e.isAlive() && e != this && !(e instanceof BallisticMissileEntity));
            if (!hits.isEmpty() && getPhase() != MissilePhase.EJECT) {
                detonate();
            }
            MissilePhase now = getPhase();
            if (level() instanceof ServerLevel sl && (now == MissilePhase.BOOST || now == MissilePhase.EJECT)
                    && net.bullettrain.xenopixelsmod.fx.effek.MissileEffectRules.thrusterPulseDue(tickCount)) {
                // Bound to this missile: AAA keeps the flame at the tail and turns it with the
                // velocity every frame, while the smoke it sheds stays behind as the trail.
                // (Positional pulses stayed where they were dropped while the missile flew on.)
                boolean effek = net.bullettrain.xenopixelsmod.fx.effek.XenoEffects.playBound(sl,
                        net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.MISSILE_THRUSTER, position(), getId(),
                        net.bullettrain.xenopixelsmod.fx.effek.MissileEffectRules.boundThrusterScale(
                                getMissileSize().visualLength()));
                if (entityData.get(DATA_EFFEK_TRAIL) != effek) entityData.set(DATA_EFFEK_TRAIL, effek);
            } else if (now != MissilePhase.BOOST && now != MissilePhase.EJECT && entityData.get(DATA_EFFEK_TRAIL)) {
                entityData.set(DATA_EFFEK_TRAIL, false);
            }
        } else {
            clientTrail(phase);
        }
    }

    private void beginBoost(Vec3 pos) {
        setPhase(MissilePhase.BOOST);
        // The launch configuration already contains the planner's chosen loft vector. Do not
        // recompute it from the intermediate eject position: that changes the planned azimuth
        // and pitch exactly when the missile leaves the tube.
        Vec3 current = getDeltaMovement();
        double minimum = Math.max(0.8, BallisticTrajectory.boostExitSpeed(boostAccel, boostTicksLeft)
                / Math.max(1, boostTicksLeft));
        if (current.lengthSqr() < minimum * minimum) {
            setDeltaMovement(loftDir.scale(minimum));
        }
    }

    private Vec3 cruiseTowardTarget(Vec3 pos, Vec3 vel) {
        double holdY = cruiseY;
        Vec3 to = new Vec3(targetX - pos.x, 0.0, targetZ - pos.z);
        double horiz = to.horizontalDistance();
        Vec3 desiredDir = horiz < 1.0e-3
                ? new Vec3(0, Math.signum(targetY - pos.y), 0)
                : to.normalize();
        double speed = Math.max(0.8, vel.length());
        Vec3 desired = desiredDir.scale(speed);
        double yError = holdY - pos.y;
        desired = new Vec3(desired.x, Mth.clamp(yError * 0.12, -speed, speed), desired.z);
        return steer(vel, desired, 0.08);
    }

    private static Vec3 applyDrag(Vec3 velocity, double dragCoefficient, double altitude) {
        double speed = velocity.length();
        if (speed < 1.0e-9 || dragCoefficient <= 0.0) return velocity;
        double dragAccel = Math.min(speed * 0.95, dragCoefficient
                * BallisticCalculator.airDensityFactor(altitude) * speed * speed);
        return velocity.scale(Math.max(0.0, 1.0 - dragAccel / speed));
    }

    private static Vec3 steer(Vec3 current, Vec3 desired, double maxTurn) {
        Vec3 c = current.lengthSqr() < 1.0e-6 ? desired : current.normalize();
        Vec3 d = desired.normalize();
        double dot = Mth.clamp(c.dot(d), -1.0, 1.0);
        double angle = Math.acos(dot);
        if (angle < 1.0e-4) return desired;
        double t = Math.min(1.0, maxTurn / angle);
        Vec3 mixed = c.scale(1.0 - t).add(d.scale(t)).normalize();
        return mixed.scale(desired.length());
    }

    private void clientTrail(MissilePhase phase) {
        if (phase == MissilePhase.BOOST || phase == MissilePhase.EJECT) {
            // The Effekseer thruster replaces the flame while the server plays it.
            if (entityData.get(DATA_EFFEK_TRAIL)) return;
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
        } else if (phase == MissilePhase.TERMINAL) {
            level().addParticle(ParticleTypes.CRIT, getX(), getY(), getZ(), 0, 0, 0);
        } else {
            level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    private void detonate() {
        if (level().isClientSide || getPhase() == MissilePhase.DEAD) {
            discard();
            return;
        }
        setPhase(MissilePhase.DEAD);
        if (level() instanceof ServerLevel sl) {
            float power = explosionPower;
            MissileWarheadEvent event = new MissileWarheadEvent(
                    sl, this, position(), warhead, getMissileSize().name(), power);
            NeoForge.EVENT_BUS.post(event);
            Entity owner = ownerId == null ? null : sl.getEntity(ownerId);
            if (event.isCanceled() || event.getExplosionPower() > 0.05f || MissileWarhead.isWarhead(warhead)) {
                // Every warhead that goes off (vanilla, other mods' native, or an addon's) gets the
                // Effekseer explosion; the explosion's own particles come from the game and stay.
                net.bullettrain.xenopixelsmod.fx.effek.XenoEffects.play(sl,
                        net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.MISSILE_EXPLOSION, position(),
                        null, 1.0f, -1);  // size: effekseerExplosionScale (default 15)
            }
            if (!event.isCanceled() && MissileWarhead.detonateNative(
                    new WarheadDetonator.Context(sl, position(), warhead, this, owner))) {
                // Another mod's explosive went off as itself (Ballistix, Big Cannons, ...).
            } else if (!event.isCanceled() && event.getExplosionPower() > 0.05f) {
                boolean grief = sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
                sl.explode(this, getX(), getY(), getZ(), event.getExplosionPower(), grief,
                        Level.ExplosionInteraction.MOB);
                sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                        SoundSource.BLOCKS, 2f, 0.9f);
            } else if (event.isCanceled()) {
                // Addon handled the blast.
            } else {
                sl.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 8, 0.3, 0.2, 0.3, 0.01);
                sl.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.1f);
            }
        }
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        targetX = tag.getDouble("TX");
        targetY = tag.getDouble("TY");
        targetZ = tag.getDouble("TZ");
        hasTarget = tag.getBoolean("HasTarget");
        terminalEnabled = tag.getBoolean("Terminal");
        boostAccel = tag.getDouble("BoostA");
        boostTicksLeft = tag.getInt("BoostT");
        if (tag.contains("GravitySI")) gravitySi = BallisticCalculator.sanitizeGravity(tag.getDouble("GravitySI"));
        if (tag.contains("DragCoefficient")) dragCoefficient = BallisticCalculator.sanitizeDrag(tag.getDouble("DragCoefficient"));
        if (tag.contains("MaxLifetime")) maxLifetime = Math.max(MIN_LIFETIME, tag.getInt("MaxLifetime"));
        if (tag.contains("TerminalRange")) {
            terminalAcquireRange = Math.max(TERMINAL_ACQUIRE_RANGE, tag.getDouble("TerminalRange"));
        }
        explosionPower = tag.getFloat("Yield");
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        int savedAge = tag.getInt("PhaseAge");
        setPhase(MissilePhase.values()[Mth.clamp(tag.getInt("Phase"), 0, MissilePhase.values().length - 1)]);
        phaseAge = savedAge;
        loftDir = new Vec3(tag.getDouble("LX"), tag.getDouble("LY"), tag.getDouble("LZ"));
        if (loftDir.lengthSqr() < 1.0e-6) loftDir = new Vec3(0, 1, 0);
        ejectOrigin = new Vec3(tag.getDouble("OX"), tag.getDouble("OY"), tag.getDouble("OZ"));
        siloClearance = tag.contains("SiloClear") ? tag.getDouble("SiloClear") : MissileEject.DEFAULT_CLEARANCE;
        launchWorldY = tag.getInt("LaunchY");
        apexY = tag.getInt("ApexY");
        cruiseY = tag.getInt("CruiseY");
        v3 = tag.getBoolean("V3")
                ? net.bullettrain.xenopixelsmod.missile.v3.TubeV3.restore(ejectOrigin,
                        new Vec3(targetX, targetY, targetZ), boostAccel, tag.getInt("V3Fuel"), gravitySi,
                        dragCoefficient, tag.getDouble("V3ApexY"), cruiseY, terminalEnabled, siloClearance, loftDir,
                        tag.getInt("V3Phase"))
                : null;
        corridor = tag.getBoolean("Guided")
                ? new MissileGuidance.Corridor(
                        new Vec3(tag.getDouble("CLX"), tag.getDouble("CLY"), tag.getDouble("CLZ")),
                        new Vec3(targetX, targetY, targetZ), tag.getDouble("CApexY"), tag.getDouble("CPeakF"))
                : null;
        size = MissileSize.byOrdinal(tag.getInt("Size"));
        entityData.set(DATA_SIZE, size.ordinal());
        if (tag.contains("Warhead") && level() instanceof ServerLevel sl) {
            HolderLookup.Provider registries = sl.registryAccess();
            Tag saved = tag.get("Warhead");
            warhead = saved == null ? ItemStack.EMPTY : ItemStack.parse(registries, saved).orElse(ItemStack.EMPTY);
        }
        refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("TX", targetX);
        tag.putDouble("TY", targetY);
        tag.putDouble("TZ", targetZ);
        tag.putBoolean("HasTarget", hasTarget);
        tag.putBoolean("Terminal", terminalEnabled);
        tag.putDouble("BoostA", boostAccel);
        tag.putInt("BoostT", boostTicksLeft);
        tag.putDouble("GravitySI", gravitySi);
        tag.putDouble("DragCoefficient", dragCoefficient);
        tag.putInt("MaxLifetime", maxLifetime);
        tag.putDouble("TerminalRange", terminalAcquireRange);
        tag.putFloat("Yield", explosionPower);
        tag.putInt("PhaseAge", phaseAge);
        tag.putInt("Phase", getPhase().ordinal());
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putDouble("LX", loftDir.x);
        tag.putDouble("LY", loftDir.y);
        tag.putDouble("LZ", loftDir.z);
        tag.putDouble("OX", ejectOrigin.x);
        tag.putDouble("OY", ejectOrigin.y);
        tag.putDouble("OZ", ejectOrigin.z);
        tag.putDouble("SiloClear", siloClearance);
        tag.putInt("LaunchY", launchWorldY);
        tag.putInt("ApexY", apexY);
        tag.putInt("CruiseY", cruiseY);
        tag.putBoolean("V3", v3 != null);
        if (v3 != null) {
            tag.putInt("V3Fuel", v3.fuelTicks());
            tag.putInt("V3Phase", v3.phaseOrdinal());
            tag.putDouble("V3ApexY", v3.apexY());
        }
        tag.putBoolean("Guided", corridor != null);
        if (corridor != null) {
            tag.putDouble("CLX", corridor.launch().x);
            tag.putDouble("CLY", corridor.launch().y);
            tag.putDouble("CLZ", corridor.launch().z);
            tag.putDouble("CApexY", corridor.apexY());
            tag.putDouble("CPeakF", corridor.peakF());
        }
        tag.putInt("Size", getMissileSize().ordinal());
        if (!warhead.isEmpty() && level() instanceof ServerLevel sl) {
            tag.put("Warhead", warhead.save(sl.registryAccess()));
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player == null || hand != InteractionHand.MAIN_HAND || isRemoved()
                || getPhase() == MissilePhase.DEAD) {
            return InteractionResult.PASS;
        }
        if (isPassengerOfSameVehicle(player)) {
            player.stopRiding();
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide && getPassengers().isEmpty()) {
            player.startRiding(this, true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().isEmpty()
                && getPhase() != MissilePhase.DEAD;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
