package net.bullettrain.xenopixelsmod.missile;

import net.bullettrain.xenopixelsmod.missile.MissileChunkLoadManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Lightweight server-authoritative ballistic missile (entity, not a VS mini-ship).
 * Phases: eject → boost → coast → terminal guidance → impact.
 */
public class BallisticMissileEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(BallisticMissileEntity.class, EntityDataSerializers.INT);

    private static final int EJECT_TICKS = 8;
    private static final double TERMINAL_ACQUIRE_RANGE = 48.0;
    private static final double TERMINAL_MAX_TURN = 0.12; // rad/tick steer limit
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
        BallisticMissileEntity m = ModEntities.BALLISTIC_MISSILE.get().create(level);
        if (m == null) return null;
        m.setPos(spawn.x, spawn.y, spawn.z);
        m.setTarget(target.x, target.y, target.z);
        m.loftDir = loftDirection.normalize();
        m.boostAccel = boostAccel;
        m.gravitySi = BallisticCalculator.sanitizeGravity(gravitySi);
        m.dragCoefficient = BallisticCalculator.sanitizeDrag(dragCoefficient);
        m.boostTicksLeft = Math.max(5, boostTicks);
        m.terminalEnabled = terminal;
        m.explosionPower = Mth.clamp(yield, 1f, 12f);
        m.ownerId = owner;
        double exitSpeed = BallisticTrajectory.boostExitSpeed(boostAccel, boostTicks);
        long eta = BallisticTrajectory.estimateEtaTicks(spawn, target, Math.max(0.25, exitSpeed * 0.5));
        m.maxLifetime = (int) Math.min(Integer.MAX_VALUE - 1L,
                Math.max(MIN_LIFETIME, eta * 4L + 20L * 60L));
        m.terminalAcquireRange = Math.max(TERMINAL_ACQUIRE_RANGE,
                Math.min(2_000.0, Math.hypot(target.x - spawn.x, target.z - spawn.z) * 0.02));
        m.setPhase(MissilePhase.EJECT);
        // Initial eject velocity along silo / loft
        m.setDeltaMovement(m.loftDir.scale(0.8));
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

    private void setPhase(MissilePhase phase) {
        this.entityData.set(DATA_PHASE, phase.ordinal());
        this.phaseAge = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PHASE, MissilePhase.EJECT.ordinal());
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

        switch (phase) {
            case EJECT -> {
                // Rail: accelerate along loft, ignore gravity briefly
                vel = loftDir.scale(1.2 + phaseAge * 0.15);
                if (phaseAge >= EJECT_TICKS) {
                    setPhase(MissilePhase.BOOST);
                    // Orient toward solved loft for boost
                    if (hasTarget) {
                        Vec3 target = new Vec3(targetX, targetY, targetZ);
                        double available = BallisticTrajectory.boostExitSpeed(boostAccel, boostTicksLeft);
                        var calculation = BallisticCalculator.calculate(pos, target, available,
                                0, gravitySi, dragCoefficient);
                        loftDir = BallisticTrajectory.launchDirection(pos, target,
                                Math.toRadians(calculation.selectedAngleDeg()));
                    }
                }
            }
            case BOOST -> {
                vel = vel.add(0, -BallisticCalculator.toTickGravity(gravitySi), 0);
                vel = vel.add(loftDir.scale(boostAccel * 55.0 / 400.0));
                vel = applyDrag(vel, dragCoefficient, pos.y);
                boostTicksLeft--;
                if (boostTicksLeft <= 0) {
                    setPhase(MissilePhase.COAST);
                }
            }
            case COAST -> {
                vel = vel.add(0, -BallisticCalculator.toTickGravity(gravitySi), 0);
                vel = applyDrag(vel, dragCoefficient, pos.y);
                if (terminalEnabled && hasTarget && pos.distanceToSqr(targetX, targetY, targetZ)
                        < terminalAcquireRange * terminalAcquireRange) {
                    setPhase(MissilePhase.TERMINAL);
                }
            }
            case TERMINAL -> {
                vel = vel.add(0, -BallisticCalculator.toTickGravity(gravitySi) * 0.85, 0);
                if (hasTarget) {
                    Vec3 toTarget = new Vec3(targetX - pos.x, targetY - pos.y, targetZ - pos.z);
                    if (toTarget.lengthSqr() > 1.0e-6) {
                        Vec3 desired = toTarget.normalize().scale(Math.max(0.8, vel.length()));
                        // Limited turn rate (no magic 180° snaps)
                        vel = steer(vel, desired, TERMINAL_MAX_TURN);
                    }
                }
                vel = applyDrag(vel, dragCoefficient * 0.5, pos.y);
            }
            default -> {
            }
        }

        setDeltaMovement(vel);
        move(MoverType.SELF, vel);

        // Face velocity
        if (vel.lengthSqr() > 1.0e-6) {
            float yRot = (float) (Mth.atan2(vel.x, vel.z) * (180F / Math.PI));
            float xRot = (float) (Mth.atan2(vel.y, vel.horizontalDistance()) * (180F / Math.PI));
            setYRot(yRot);
            setXRot(-xRot);
        }

        if (!level().isClientSide) {
            serverSideExtras(pos);
            if (horizontalCollision || verticalCollision || onGround()) {
                detonate();
                return;
            }
            // Proximity fuse near target
            if (hasTarget && pos.distanceToSqr(targetX, targetY, targetZ) < 2.25) {
                detonate();
                return;
            }
            // Entity hit
            AABB box = getBoundingBox().inflate(0.3);
            List<Entity> hits = level().getEntities(this, box, e -> e.isAlive() && e != this && !(e instanceof BallisticMissileEntity));
            if (!hits.isEmpty()) {
                detonate();
            }
        } else {
            clientTrail(phase);
        }
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

    private void serverSideExtras(Vec3 pos) {
        if (!(level() instanceof ServerLevel sl)) return;
        if (tickCount % 20 == 0) {
            MissileChunkLoadManager.forceNear(sl, pos, 1, 20 * 8, MissileChunkLoadManager.Role.VEHICLE);
            if (hasTarget) {
                MissileChunkLoadManager.forceNear(sl, new BlockPos((int) targetX, (int) targetY, (int) targetZ),
                        1, 20 * 8, MissileChunkLoadManager.Role.TARGET);
            }
        }
        // Tracked clients already render clientTrail(). Sending two server particle
        // packets every other tick duplicated the trail and scaled poorly in salvos.
    }

    private void clientTrail(MissilePhase phase) {
        if (phase == MissilePhase.BOOST || phase == MissilePhase.EJECT) {
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
        // Yield <= 0: inert / practice munition — no explosion
        if (explosionPower > 0.05f && level() instanceof ServerLevel sl) {
            boolean grief = sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            sl.explode(this, getX(), getY(), getZ(), explosionPower, grief, Level.ExplosionInteraction.MOB);
            sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2f, 0.9f);
        } else if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 8, 0.3, 0.2, 0.3, 0.01);
            sl.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.1f);
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
        phaseAge = tag.getInt("PhaseAge");
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        setPhase(MissilePhase.values()[Mth.clamp(tag.getInt("Phase"), 0, MissilePhase.values().length - 1)]);
        loftDir = new Vec3(tag.getDouble("LX"), tag.getDouble("LY"), tag.getDouble("LZ"));
        if (loftDir.lengthSqr() < 1.0e-6) loftDir = new Vec3(0, 1, 0);
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
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
