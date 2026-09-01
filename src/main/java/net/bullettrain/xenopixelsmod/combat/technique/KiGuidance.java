package net.bullettrain.xenopixelsmod.combat.technique;

import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.compat.CameraAimHelper;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.KiAttackData;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Applies Ki Guidance to a live projectile while the key is held.
 * Lock-on homes at the skill turn rate; with no lock, ki follows the camera
 * the same way Sokidan does ({@link CameraAimHelper#resolve}).
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class KiGuidance {

    private static final int BARRAGE_EMITTER_TYPE = 9;
    private static final int HOLD_GRACE_DEFAULT = 8;
    private static final Map<UUID, Integer> ARMED_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> GUIDED_TARGET = new HashMap<>();

    private KiGuidance() {
    }

    public static void reportHeld(ServerPlayer player, int targetId) {
        if (player == null) return;
        ARMED_UNTIL.put(player.getUUID(), player.tickCount + holdGraceTicks());
        if (targetId >= 0) GUIDED_TARGET.put(player.getUUID(), targetId);
    }

    public static boolean isArmed(ServerPlayer player) {
        if (player == null) return false;
        Integer until = ARMED_UNTIL.get(player.getUUID());
        return until != null && player.tickCount <= until;
    }

    /**
     * Sokidan-style: find this owner's live ki and steer it now, instead of
     * waiting for {@code applyHomingSteering} on the next entity tick.
     */
    public static void guideOwned(ServerPlayer player) {
        if (player == null || player.level().isClientSide || !isArmed(player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        int levelSkill = guideLevel(player);
        double reach = KiGuidanceMath.controlRange(levelSkill);
        AABB box = player.getBoundingBox().inflate(reach);
        for (AbstractKiProjectile ki : level.getEntitiesOfClass(AbstractKiProjectile.class, box,
                candidate -> candidate.isAlive() && candidate.isOwner(player))) {
            if (ki instanceof KiWaveEntity || ki instanceof KiLaserEntity) {
                aimAnchoredBeam(player, ki);
            } else if (ki.isFiring() && canSteerLive(ki) && canGuide(ki)) {
                steer(ki, -1, -1, ki::setHomingTarget, ignored -> {
                });
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ARMED_UNTIL.remove(player.getUUID());
            GUIDED_TARGET.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ARMED_UNTIL.clear();
        GUIDED_TARGET.clear();
    }

    /**
     * @return true if this replaced DMZ steering (caller should skip the stock method)
     */
    public static boolean steer(AbstractKiProjectile ki, int homingTargetId, int firingStartTick,
                                IntConsumer setTargetId, IntConsumer setFiringStartTick) {
        if (ki == null || ki.level().isClientSide || !ki.isFiring()) return false;
        if (!canSteerLive(ki)) return false;
        if (!canGuide(ki)) return false;
        if (!(ki.getOwner() instanceof ServerPlayer player)) {
            // NPC / boss ki: do not apply player Guidance. NpcKiAttackDispatcher
            // clears homing and fires along the caster's head-aim look.
            return false;
        }
        int level = guideLevel(player);
        if (!isArmed(player)) return false;

        if (firingStartTick < 0) {
            firingStartTick = ki.tickCount;
            setFiringStartTick.accept(firingStartTick);
        }
        if (homingTargetId < 0) {
            LivingEntity locked = resolveLiveTarget(player, level);
            if (locked != null) {
                homingTargetId = locked.getId();
                setTargetId.accept(homingTargetId);
            } else {
                applyCameraSteer(ki, player);
                return true;
            }
        }
        if (!(ki.level() instanceof ServerLevel serverLevel)) return false;
        if (!(serverLevel.getEntity(homingTargetId) instanceof LivingEntity target) || !target.isAlive()) {
            setTargetId.accept(-1);
            applyCameraSteer(ki, player);
            return true;
        }

        Vec3 selfPos = ki.position();
        Vec3 targetPos = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        double dist = selfPos.distanceTo(targetPos);
        int elapsed = ki.tickCount - firingStartTick;
        int grace = KiGuidanceMath.graceTicks();
        if (elapsed > grace) {
            boolean windowExpired = elapsed > grace + KiGuidanceMath.extendedTicks(level);
            if (windowExpired || dist > KiGuidanceMath.controlRange(level)) {
                setTargetId.accept(-1);
                applyCameraSteer(ki, player);
                return true;
            }
        }
        steerToward(ki, target, level);
        return true;
    }

    private static void steerToward(AbstractKiProjectile ki, LivingEntity target, int level) {
        Vec3 vel = ki.getDeltaMovement();
        double speed = vel.length();
        if (speed < 1.0E-4) return;
        Vec3 from = ki.position();
        Vec3 body = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
        Vec3 motion = target.getDeltaMovement();
        double[] lead = KiGuidanceMath.leadPoint(
                body.x, body.y, body.z,
                motion.x, motion.y, motion.z,
                from.x, from.y, from.z, speed);
        Vec3 want = new Vec3(lead[0] - from.x, lead[1] - from.y, lead[2] - from.z);
        if (want.lengthSqr() < 1.0E-8) return;
        want = want.normalize();
        Vec3 dir = vel.scale(1.0 / speed);
        double[] steered = KiGuidanceMath.steerDir(
                dir.x, dir.y, dir.z, want.x, want.y, want.z, level);
        ki.setDeltaMovement(new Vec3(steered[0], steered[1], steered[2]).scale(speed));
    }

    /** Slide the shot onto the look ray (Sokidan parked-point), keeping speed. */
    private static void applyCameraSteer(AbstractKiProjectile ki, ServerPlayer player) {
        if (ki == null || player == null) return;
        Vec3 aim = CameraAimHelper.resolve(player);
        if (aim == null || aim.lengthSqr() < 1.0E-8) return;
        if (ki instanceof KiWaveEntity || ki instanceof KiLaserEntity) {
            applyBeamAim(ki, aim);
            return;
        }
        Vec3 vel = ki.getDeltaMovement();
        double speed = vel.length();
        if (speed < 1.0E-4) return;
        Vec3 eye = player.getEyePosition();
        Vec3 from = ki.position();
        double[] point = KiGuidanceMath.lookRayPoint(
                eye.x, eye.y, eye.z, aim.x, aim.y, aim.z,
                from.x, from.y, from.z, speed);
        Vec3 want = new Vec3(point[0] - from.x, point[1] - from.y, point[2] - from.z);
        if (want.lengthSqr() < 1.0E-8) return;
        // Turn the direction and put the original speed back on it.
        //
        // This used to blend the two velocity *vectors* linearly, which is where guided ki lost
        // its speed: the straight line between two vectors of equal length is shorter than either
        // of them, so every steering tick shaved a little off, and a shot under continuous
        // guidance slowed to a crawl and outlived its usefulness. Guidance is an aim device; it
        // decides where the attack points, never how fast it travels.
        double[] steered = KiGuidanceMath.slerpDir(
                vel.x, vel.y, vel.z, want.x, want.y, want.z, KiGuidanceMath.cameraRate());
        ki.setDeltaMovement(new Vec3(steered[0], steered[1], steered[2]).scale(speed));
    }

    private static boolean canSteerLive(AbstractKiProjectile ki) {
        int max = ki.getMaxLife();
        if (max == 99999) return false;
        return ki.tickCount < max;
    }

    /** Thrown ki plus surged waves / lasers / beams. Barrage emitters stay on the caster. */
    private static boolean canGuide(AbstractKiProjectile ki) {
        return switch (ki.getKiType()) {
            case SMALL_BALL, MEDIUM_BALL, GIANT_BALL, DISK, WAVE, LASER, BEAM -> true;
            default -> false;
        };
    }

    /**
     * Point a surged wave/laser at the lock (or look-target at level 2+).
     * These beams zero velocity every tick and grow along yaw/pitch, so
     * {@link #steer} cannot turn them.
     */
    public static void aimAnchoredBeam(ServerPlayer player, AbstractKiProjectile beam) {
        if (player == null || beam == null || beam.level().isClientSide) return;
        if (!canSteerLive(beam)) return;
        if (beam.isClashLocked()) return;
        if (!(beam instanceof KiWaveEntity) && !(beam instanceof KiLaserEntity)) return;
        int level = guideLevel(player);
        if (!isArmed(player)) return;

        LivingEntity target = resolveLiveTarget(player, level);
        Vec3 want;
        if (target != null) {
            if (player.distanceTo(target) > KiGuidanceMath.controlRange(level)) return;
            Vec3 from = player.getEyePosition();
            Vec3 body = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
            Vec3 motion = target.getDeltaMovement();
            double[] lead = KiGuidanceMath.leadPoint(
                    body.x, body.y, body.z,
                    motion.x, motion.y, motion.z,
                    from.x, from.y, from.z, 1.0);
            want = new Vec3(lead[0] - from.x, lead[1] - from.y, lead[2] - from.z);
        } else {
            want = CameraAimHelper.resolve(player);
        }
        if (want == null || want.lengthSqr() < 1.0e-8) return;
        want = want.normalize();
        float curYaw;
        float curPitch;
        if (beam instanceof KiLaserEntity laser) {
            curYaw = laser.getFixedYaw();
            curPitch = laser.getFixedPitch();
        } else {
            KiWaveEntity wave = (KiWaveEntity) beam;
            curYaw = wave.getFixedYaw();
            curPitch = wave.getFixedPitch();
        }
        Vec3 current = Vec3.directionFromRotation(curPitch, curYaw);
        if (current.lengthSqr() < 1.0e-8) current = player.getLookAngle();
        double[] steered = KiGuidanceMath.steerDir(
                current.x, current.y, current.z, want.x, want.y, want.z, level);
        applyBeamAim(beam, new Vec3(steered[0], steered[1], steered[2]));
    }

    private static void applyBeamAim(AbstractKiProjectile beam, Vec3 dir) {
        if (dir.lengthSqr() < 1.0e-8) return;
        dir = dir.normalize();
        float yaw = CameraAimHelper.yaw(dir);
        float pitch = CameraAimHelper.pitch(dir);
        beam.setYRot(yaw);
        beam.setXRot(pitch);
        if (beam instanceof KiFixedAim aim) {
            aim.xenopixels$setFixedAim(yaw, pitch);
        }
    }

    /** Held key is at least level 1 so the bind works before spending a skill point. */
    private static int guideLevel(ServerPlayer player) {
        return Math.max(1, CombatSkills.level(player, CombatSkills.GUIDE));
    }

    private static int holdGraceTicks() {
        int configured = XenoServerConfig.guidanceHoldGraceTicks;
        return configured > 0 ? configured : HOLD_GRACE_DEFAULT;
    }

    private static LivingEntity resolveLiveTarget(ServerPlayer player, int level) {
        Integer packed = GUIDED_TARGET.get(player.getUUID());
        int id = packed != null ? packed : lockOnId(player);
        if (id >= 0 && player.level() instanceof ServerLevel server) {
            if (server.getEntity(id) instanceof LivingEntity living && living.isAlive()) {
                return living;
            }
        }
        if (level >= 2) {
            return lookTarget(player, player.level(), KiGuidanceMath.controlRange(level), false);
        }
        return null;
    }

    public static void tagBarragePellet(KiBlastEntity emitter, KiBlastEntity pellet) {
        if (emitter == null || pellet == null) return;
        if (emitter.getKiRenderType() != BARRAGE_EMITTER_TYPE) return;
        if (!(emitter.getOwner() instanceof ServerPlayer player)) return;
        if (!isArmed(player)) return;
        int targetId = lockOnId(player);
        if (targetId < 0) {
            LivingEntity looked = lookHostile(player, player.level(), KiGuidanceMath.controlRange(1));
            if (looked != null) targetId = looked.getId();
        }
        if (targetId >= 0) pellet.setHomingTarget(targetId);
    }

    /**
     * Level 2+: home custom thrown ki at whatever the caster is looking at when no lock is set.
     */
    public static LivingEntity lookHomeFallback(LivingEntity owner, Level level, KiAttackData data,
                                                LivingEntity already) {
        if (already != null) return already;
        if (data == null || owner == null) return already;
        KiAttackData.KiType type = data.getKiType();
        if (type != KiAttackData.KiType.SMALL_BALL
                && type != KiAttackData.KiType.MEDIUM_BALL
                && type != KiAttackData.KiType.GIANT_BALL
                && type != KiAttackData.KiType.DISK
                && type != KiAttackData.KiType.WAVE
                && type != KiAttackData.KiType.LASER
                && type != KiAttackData.KiType.BEAM) {
            return already;
        }
        if (!(owner instanceof ServerPlayer player)) return already;
        if (guideLevel(player) < 2 || !isArmed(player)) return already;
        boolean heal = data.getEffectiveUtility() == KiAttackData.Utility.HEAL;
        LivingEntity looked = lookTarget(player, level, KiGuidanceMath.controlRange(2), heal);
        return looked != null ? looked : already;
    }

    private static int lockOnId(ServerPlayer player) {
        try {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            if (data == null || data.getTechniques() == null) return -1;
            return data.getTechniques().getHomingTargetId();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static LivingEntity lookHostile(Player player, Level level, double range) {
        return lookTarget(player, level, range, false);
    }

    private static LivingEntity lookTarget(Player player, Level level, double range, boolean heal) {
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0F);
        Vec3 end = eye.add(view.scale(range));
        AABB box = player.getBoundingBox().expandTowards(view.scale(range)).inflate(1.0);
        LivingEntity best = null;
        double bestDist = range * range;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                x -> x != player && x.isAlive() && x.isPickable() && !x.isSpectator())) {
            TargetHelper.Relation relation = TargetHelper.getRelation(player, e);
            if (heal ? relation == TargetHelper.Relation.HOSTILE
                    : relation == TargetHelper.Relation.FRIENDLY) {
                continue;
            }
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = eye.distanceToSqr(hit.get());
            if (d < bestDist) {
                best = e;
                bestDist = d;
            }
        }
        return best;
    }
}
