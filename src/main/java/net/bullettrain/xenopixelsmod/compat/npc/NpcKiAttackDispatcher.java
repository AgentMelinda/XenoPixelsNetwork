package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.combat.clash.BeamClashManager;
import com.dragonminez.common.compat.CameraAimHelper;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.init.entities.ki.KiDiskEntity;
import com.dragonminez.common.init.entities.ki.KiExplosionEntity;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.stats.techniques.KiAttackData;
import net.bullettrain.xenopixelsmod.combat.targeting.LeadCalculator;
import net.bullettrain.xenopixelsmod.combat.targeting.TargetMotionEstimator;
import net.bullettrain.xenopixelsmod.combat.technique.KiFixedAim;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fires a real DragonMineZ ki projectile from a non-player NPC (CustomNPCs / My NPCs).
 *
 * <p>DMZ's {@code StatsData} constructor is {@code Player}-only, so damage is scaled from
 * {@link NpcCombatProfile} instead. Projectiles themselves accept any {@link LivingEntity}
 * caster — that is how {@code SkillManager} fires ki for {@code DBSagasEntity} bosses.
 *
 * <p>Named techniques use the non-player {@code setup*} overloads (those self-spawn via
 * {@code finalizeSetupAndShoot} / {@code addFreshEntity}). {@code setup*Player} methods are
 * the charge-then-release path {@code TechniqueDispatcher.executeKiAttack} uses for players:
 * they leave {@code firing=false} until {@code fireHability(int)} runs. NPCs have no charge
 * release, so those overloads are used only when DMZ has no non-player equivalent, and then
 * {@code fireHability} is called immediately — the same two calls the dispatcher makes, on
 * one tick.
 */
public final class NpcKiAttackDispatcher {
    private NpcKiAttackDispatcher() {}
    private static final ThreadLocal<Boolean> SCRIPT_RANGED_HOOK = ThreadLocal.withInitial(() -> false);
    /**
     * Last tick each NPC's rangedLaunched hook ran. The thread-local above cannot see a ki attack
     * fired by a command the hook queued (1.21 runs it after the hook returned), so the hook also
     * runs at most once per NPC per tick; otherwise hook -> command -> attack -> hook never ends.
     */
    private static final java.util.Map<java.util.UUID, Long> LAST_RANGED_HOOK = new java.util.concurrent.ConcurrentHashMap<>();

    /** True for the first rangedLaunched hook of this NPC in {@code tick}; records it. */
    public static boolean firstRangedHookThisTick(java.util.UUID npc, long tick) {
        if (LAST_RANGED_HOOK.size() > 4096) LAST_RANGED_HOOK.clear();
        Long last = LAST_RANGED_HOOK.put(npc, tick);
        return last == null || last != tick;
    }

    private static void rangedHook(LivingEntity caster, LivingEntity target) {
        if (!(caster instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc)
                || SCRIPT_RANGED_HOOK.get()) return;
        if (!firstRangedHookThisTick(npc.getUUID(), npc.level().getGameTime())) return;
        SCRIPT_RANGED_HOOK.set(true);
        try {
            net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(
                    npc, "rangedLaunched", null, null, target, 0, n ->
                            new xenoapi.npcs.api.event.NpcEvent.RangedLaunchedEvent(n, target, 0.0f));
        } finally {
            SCRIPT_RANGED_HOOK.remove();
        }
    }

    private static final int DEFAULT_COLOR = 0xFFFFFF;
    private static final int DEFAULT_CAST_TIME = 20;

    /**
     * Default life ticks {@code TechniqueDispatcher.resolvePlayerMaxLifeTicks} returns at
     * charge {@code 1.0} for MEDIUM_BALL / GIANT_BALL (the only KiTypes that have
     * {@code *Player}-only setup methods).
     */
    private static final int PLAYER_ONLY_RELEASE_LIFE = 90;

    /** No duration override — the projectile keeps the life the DMZ setup method assigned. */
    public static final int NO_DURATION_OVERRIDE = -1;

    /** Short visual cast pose; CustomNPCs remains the sole owner of combat targeting. */
    private static final int AIM_HOLD_TICKS = 8;

    /** Fires DMZ's basic ki blast ball, scaled from the NPC's profile. */
    public static void fireKiBlast(LivingEntity caster, NpcCombatProfile profile, int durationTicks,
                                   LivingEntity aimAt, int colorOverride) {
        if (caster == null || profile == null) return;
        float charge = profile.chargeFactor();
        int color = resolveColor(profile, colorOverride, DEFAULT_COLOR);
        // Two nested counts, as My NPCs has: a burst is a repeat over time, a shot is a spread
        // released together. Burst Count alone could only ever produce a stream; Shot Count is what
        // makes a volley. Total projectiles is the product, which is why both are capped at 16.
        int bursts = NpcCombatProfile.clampNpcBurstCount(profile.npcRangedBurstCount);
        int shots = NpcCombatProfile.clampNpcShotCount(profile.npcRangedShotCount);
        int gap = NpcCombatProfile.clampNpcRangedDelay(profile.npcRangedBurstRate);
        for (int burst = 0; burst < bursts; burst++) {
            final int burstIndex = burst;
            Runnable fire = () -> {
                if (!caster.isAlive() || caster.isRemoved()
                        || aimAt != null && !aimAt.isAlive()) return;
                for (int shot = 0; shot < shots; shot++) {
                    // Only the first projectile of the first burst poses the caster; posing once
                    // per projectile would restart the animation several times in one tick.
                    fireSingleKiBlast(caster, profile, durationTicks, aimAt, color, charge,
                            shot, shots, burstIndex == 0 && shot == 0);
                }
            };
            if (burst == 0 || !(caster.level() instanceof ServerLevel level)
                    || level.getServer() == null) {
                fire.run();
            } else {
                int tick = level.getServer().getTickCount() + burst * gap;
                level.getServer().tell(new net.minecraft.server.TickTask(tick, fire));
            }
        }
        rangedHook(caster, aimAt);
    }

    private static void fireSingleKiBlast(LivingEntity caster, NpcCombatProfile profile,
                                         int durationTicks, LivingEntity aimAt, int color,
                                         float charge, int shotIndex, int shotCount,
                                         boolean poseCaster) {
        float damage = profile.npcProjectileStrength > 0.0f
                ? profile.npcProjectileStrength
                : kiDamage(caster, profile) * charge;
        float speed = profile.npcProjectileSpeed > 0.0f
                ? profile.npcProjectileSpeed : NpcKiProjectileMath.blastSpeed(charge);
        float size = profile.npcProjectileSize > 0.0f
                ? profile.npcProjectileSize : NpcKiProjectileMath.blastSize(charge);
        if (poseCaster) poseCaster(caster, aimAt);
        playConfiguredSound(caster, profile.npcRangedFireSound);
        KiBlastEntity blast = new KiBlastEntity(caster.level(), caster);
        blast.setupKiBlast(caster, damage, speed, color, size, DEFAULT_CAST_TIME);
        rescaleSpawnHeight(blast, caster);
        applyDuration(blast, durationTicks);
        applyKiColor(blast, color);
        aimAlongLook(blast, caster, aimAt);
        if (shotCount > 1) {
            // A bounded fan gives Shot Count/Burst Count a visible effect rather than placing
            // several projectiles into one identical path.
            float fan = (shotIndex - (shotCount - 1) / 2.0f) * 0.045f;
            Vec3 motion = blast.getDeltaMovement();
            double cos = Math.cos(fan);
            double sin = Math.sin(fan);
            blast.setDeltaMovement(motion.x * cos - motion.z * sin, motion.y,
                    motion.x * sin + motion.z * cos);
        }
        if (profile.npcRangedIndirect) {
            blast.setDeltaMovement(blast.getDeltaMovement().add(0.0, 0.08, 0.0));
        }
        NpcKiProjectileEffects.track(blast, profile);
    }

    /** Fires DMZ's beam-style wave attack, scaled from the NPC's profile. */
    public static void fireKiWave(LivingEntity caster, NpcCombatProfile profile, int durationTicks,
                                  LivingEntity aimAt, int colorOverride) {
        float charge = profile.chargeFactor();
        fireKiWave(caster, kiDamage(caster, profile) * charge,
                NpcKiProjectileMath.waveSpeed(charge),
                NpcKiProjectileMath.waveSize(charge), DEFAULT_CAST_TIME,
                durationTicks, aimAt, resolveColor(profile, colorOverride, 0), false, null);
        rangedHook(caster, aimAt);
    }

    /** Immediate MAJOR firing wave so DMZ can clash it with a player beam. */
    public static boolean fireClashWave(LivingEntity caster, NpcCombatProfile profile,
                                        LivingEntity aimAt) {
        if (caster == null || profile == null || aimAt == null) {
            return false;
        }
        if (!NpcResources.spendEnergy(caster, profile, 2.0)) {
            return false;
        }
        float charge = profile.chargeFactor();
        fireKiWave(caster, kiDamage(caster, profile) * charge,
                NpcKiProjectileMath.waveSpeed(charge),
                NpcKiProjectileMath.waveSize(charge), DEFAULT_CAST_TIME,
                NpcBrainKiRotation.CLASH_WAVE_LIFE, aimAt, resolveColor(profile, 0, 0), true, null);
        return true;
    }

    public static boolean isOwnerClashing(LivingEntity npc) {
        return npc != null && BeamClashManager.isClashing(npc.getUUID());
    }

    /**
     * Owner: while any of this NPC's Ki projectiles still exist in the world, the combat brain
     * must not move them (stand still until the projectile despawns).
     */
    public static boolean ownsLiveProjectile(LivingEntity npc) {
        if (npc == null || !(npc.level() instanceof ServerLevel level)) return false;
        AABB box = npc.getBoundingBox().inflate(160.0);
        for (AbstractKiProjectile ki : level.getEntitiesOfClass(AbstractKiProjectile.class, box,
                p -> !p.isRemoved() && p.getOwner() == npc)) {
            return true;
        }
        return false;
    }

    public static boolean victimOwnsClashableBeam(LivingEntity victim) {
        if (victim == null || !(victim.level() instanceof ServerLevel level)) {
            return false;
        }
        AABB box = victim.getBoundingBox().inflate(80.0);
        for (AbstractKiProjectile ki : level.getEntitiesOfClass(AbstractKiProjectile.class, box,
                p -> !p.isRemoved() && p.getOwner() == victim && p.isClashableBeam())) {
            return true;
        }
        return false;
    }

    /** Mirrors the exact charge and tunable values of a successfully released player wave. */
    public static void fireMirroredWave(LivingEntity caster, NpcCombatProfile profile,
                                        KiAttackData data, float chargeMultiplier,
                                        LivingEntity aimAt) {
        fireMirroredWave(caster, profile, data, chargeMultiplier, aimAt, null);
    }

    public static void fireMirroredWave(LivingEntity caster, NpcCombatProfile profile,
                                        KiAttackData data, float chargeMultiplier,
                                        LivingEntity aimAt, Vec3 ownerLook) {
        if (caster == null || profile == null || data == null
                || data.getKiType() != KiAttackData.KiType.WAVE) return;
        float charge = Math.max(0.5f, Math.min(2.0f, chargeMultiplier));
        float damage = kiDamage(caster, profile) * data.getDamageMultiplier()
                * data.getConfiguredDamageMultiplier() * data.getOutputMultiplier() * charge;
        fireKiWave(caster, damage,
                NpcKiProjectileMath.clampSpeed(data.getActualSpeed() * Math.min(2.0f, charge)),
                NpcKiProjectileMath.clampSize(data.getActualSize() * charge), data.getActualCastTime(),
                NO_DURATION_OVERRIDE, aimAt, data.getColorInterior(), false, ownerLook);
    }

    private static void fireKiWave(LivingEntity caster, float damage, float speed, float size,
                                   int castTime, int durationTicks, LivingEntity aimAt, int color,
                                   boolean clashImmediate, Vec3 ownerLook) {
        KiWaveEntity wave = new KiWaveEntity(caster.level(), caster);
        boolean crosshair = ownerLook != null && ownerLook.lengthSqr() > 1.0E-8;
        if (crosshair) {
            poseCasterLook(caster, ownerLook);
        } else {
            poseCaster(caster, aimAt);
        }
        wave.setupKiHame(caster, damage, speed, size, castTime);
        rescaleSpawnHeight(wave, caster);
        if (clashImmediate) {
            wave.setFiring(true);
            int life = durationTicks > 0 ? durationTicks : NpcBrainKiRotation.CLASH_WAVE_LIFE;
            wave.setMaxLife(Math.max(life, NpcBrainKiRotation.CLASH_WAVE_LIFE));
        } else {
            applyDuration(wave, durationTicks);
        }
        applyKiColor(wave, color);
        aimAlongLook(wave, caster, crosshair ? null : aimAt);
    }

    /**
     * Base damage before a technique's own {@code getDamageMultiplier()} is applied. Mirrors
     * DMZ's real {@code StatsData.getKiDamage()} ({@code kiPower * releaseMultiplier}, see
     * {@link NpcCombatProfile#kiDamage()}) rather than the flat/{@code strikePower}-scaled
     * approximation this used to be. The active form's PWR multiplier is applied inside the
     * profile calculation; {@code chargeFactor()} remains the NPC-specific charge extension.
     */
    private static float baseDamage(LivingEntity caster, NpcCombatProfile profile) {
        float base = kiDamage(caster, profile);
        float charge = profile != null ? profile.chargeFactor() : 1.0f;
        return base * charge;
    }

    /**
     * Real {@code StatsData.getKiDamage()} when the NPC carries an attached DragonMineZ blob;
     * otherwise the profile stand-in that mirrors the same formula.
     */
    static float kiDamage(LivingEntity caster, NpcCombatProfile profile) {
        if (caster != null) {
            var data = NpcDmzStats.stats(caster);
            if (data != null) {
                double damage = data.getKiDamage();
                if (Double.isFinite(damage) && damage >= 0.0) {
                    return (float) Math.min(damage, Float.MAX_VALUE);
                }
            }
        }
        return profile == null ? 0.0f : profile.kiDamage();
    }

    /**
     * Fires a technique from {@code PredefinedTechniques.REGISTRY} using the non-player
     * {@code setup*} method that matches that id (or the {@code *Player} + {@code fireHability}
     * pair when DMZ has no non-player overload).
     */
    public static boolean supportsPredefinedTechnique(String id) {
        if (id == null) return false;
        return switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "kamehameha", "galick_gun", "final_flash", "masenko", "sokidan", "burning_attack",
                    "big_bang", "spiritbomb", "supernova", "ki_barrage", "taiyoken", "kienzan",
                    "kienzan_doble", "death_beam", "emperor_death_beam", "makkanko", "final_explosion",
                    "soul_punisher", "fake_moon", "supernova_cooler" -> true;
            default -> false;
        };
    }

    public static boolean firePredefinedTechnique(String id, LivingEntity caster, NpcCombatProfile profile,
                                                  int durationTicks, LivingEntity aimAt, int colorOverride) {
        // Reject unsupported registry entries before charging, and use the same canonical id in
        // lookup and dispatch (lookup has always been case-insensitive).
        if (!supportsPredefinedTechnique(id) || caster == null || profile == null) return false;
        id = id.toLowerCase(java.util.Locale.ROOT);
        KiAttackData data = PredefinedTechniqueLookup.forNpc(id, profile);
        if (data == null) {
            return false;
        }
        if (!NpcKiCooldowns.ready(caster, data.getId())) {
            return false;
        }
        if (!NpcResources.spendEnergy(caster, profile,
                NpcTechniqueMath.kiCost(kiDamage(caster, profile), profile, data))) {
            return false;
        }
        Level level = caster.level();
        float charge = profile.chargeFactor();
        float damage = baseDamage(caster, profile) * data.getDamageMultiplier();
        float speed = NpcKiProjectileMath.clampSpeed(data.getSpeed() * Math.min(2.0f, charge));
        float size = NpcKiProjectileMath.clampSize(data.getSize() * charge);
        int colorInterior = data.getColorInterior();
        int colorExterior = data.getColorExterior();
        int colorOutline = data.getColorOutline();
        int castTime = data.getActualCastTime();
        poseCaster(caster, aimAt);

        AbstractKiProjectile projectile = switch (id) {
            case "kamehameha" -> {
                KiWaveEntity wave = new KiWaveEntity(level, caster);
                wave.setupKiHame(caster, damage, speed, size, castTime);
                yield wave;
            }
            case "galick_gun" -> {
                KiWaveEntity wave = new KiWaveEntity(level, caster);
                wave.setupKiGalickGun(caster, damage, speed, size, castTime);
                yield wave;
            }
            case "final_flash" -> {
                KiWaveEntity wave = new KiWaveEntity(level, caster);
                wave.setupFinalFlash(caster, damage, speed, size, castTime);
                yield wave;
            }
            case "masenko" -> {
                KiWaveEntity wave = new KiWaveEntity(level, caster);
                wave.setupKiMasenko(caster, damage, speed, size, castTime);
                yield wave;
            }
            case "sokidan" -> {
                KiBlastEntity blast = new KiBlastEntity(level, caster);
                blast.setupSokidan(caster, damage, speed, colorInterior, size, castTime);
                yield blast;
            }
            case "burning_attack", "big_bang" -> {
                KiBlastEntity blast = new KiBlastEntity(level, caster);
                blast.setupKiBlast(caster, damage, speed, colorInterior, size, castTime);
                yield blast;
            }
            case "spiritbomb" -> {
                KiBlastEntity blast = new KiBlastEntity(level, caster);
                blast.setupKiGenki(caster, damage, speed, castTime);
                yield blast;
            }
            case "supernova" -> {
                KiBlastEntity blast = new KiBlastEntity(level, caster);
                blast.setupKiNova(caster, damage, speed, castTime);
                yield blast;
            }
            case "ki_barrage" -> {
                KiBlastEntity blast = new KiBlastEntity(level, caster);
                blast.setupKiVolley(caster, damage, speed, colorInterior, castTime);
                yield blast;
            }
            case "taiyoken" -> {
                KiBlastEntity blast = new KiBlastEntity(level, caster);
                blast.setupKiSmall(caster, damage, speed, colorInterior);
                yield blast;
            }
            case "kienzan", "kienzan_doble" -> {
                KiDiskEntity disk = new KiDiskEntity(level, caster);
                disk.setupKiDisk(caster, damage, speed, colorInterior, size, castTime);
                yield disk;
            }
            case "death_beam", "emperor_death_beam" -> {
                KiLaserEntity laser = new KiLaserEntity(level, caster);
                laser.setupKiLaser(caster, damage, speed, colorInterior, castTime);
                yield laser;
            }
            case "makkanko" -> {
                KiLaserEntity laser = new KiLaserEntity(level, caster);
                laser.setupKiMakkankosanpo(caster, damage, speed, castTime);
                yield laser;
            }
            case "final_explosion" -> {
                KiExplosionEntity explosion = new KiExplosionEntity(level, caster);
                explosion.setupKiExplosion(caster, damage, colorInterior, colorExterior, castTime);
                yield explosion;
            }
            // No non-player overload in DMZ 2.1.3 — these three are *Player-only.
            // Their setup methods already add the projectile to the level. Release it once.
            case "soul_punisher" -> {
                KiBlastEntity blast = new KiBlastEntity(level, caster);
                blast.setupSoulPunisherPlayer(caster, damage, speed, colorInterior, colorOutline, size);
                yield releasePlayerAttack(blast, durationTicks);
            }
            case "fake_moon" -> {
                KiBlastEntity blast = new KiBlastEntity(level, caster);
                blast.setupFakeMoonPlayer(caster, speed, colorInterior, colorOutline, size);
                yield releasePlayerAttack(blast, durationTicks);
            }
            case "supernova_cooler" -> {
                KiBlastEntity blast = new KiBlastEntity(level, caster);
                blast.setupKiNovaCoolerPlayer(caster, damage, speed);
                yield releasePlayerAttack(blast, durationTicks);
            }
            default -> null;
        };
        if (projectile == null) {
            return false;
        }
        rescaleSpawnHeight(projectile, caster);
        projectile.setTechniqueId(data.getId());
        applyDuration(projectile, durationTicks);
        applyKiColor(projectile, resolveColor(profile, colorOverride, 0));
        aimAlongLook(projectile, caster, aimAt);
        NpcKiCooldowns.consume(caster, data, charge);
        rangedHook(caster, aimAt);
        return true;
    }

    /**
     * Completes the player charge/release pair {@code TechniqueDispatcher} splits across two
     * {@code executeKiAttack} calls: spawn the charging projectile, then {@code fireHability}.
     */
    private static KiBlastEntity releasePlayerAttack(KiBlastEntity projectile, int durationTicks) {
        int life = durationTicks > 0 ? durationTicks : PLAYER_ONLY_RELEASE_LIFE;
        projectile.fireHability(life);
        return projectile;
    }

    /**
     * DMZ's own {@code setup*}/{@code setCastOffsets} position the projectile at
     * {@code owner.getY() + owner.getBbHeight()/2.0} (or {@code owner.getEyeY()} for a
     * continuous-follow wave, which this dispatcher always disables afterward via
     * {@code aimAlongLook}'s {@code setContinuousFollow(false)} -- so that branch is never the
     * one actually used here) plus a flat, hardcoded per-technique Y literal from
     * {@code setCastOffsets}. The anchor term already scales with a CustomNPC's Display Size
     * (confirmed: {@code EntityCustomNpc.getDimensions()} scales by {@code display.getSize() *
     * 0.2F}, which vanilla {@code getBbHeight()} derives from), but the flat literal does not.
     * Recompute the anchor from the caster's own (already-scaled) height, treat whatever is left
     * over as that flat literal, and scale only that leftover by the caster's size ratio -- self
     * corrects without needing to know DMZ's per-technique constant.
     */
    private static void rescaleSpawnHeight(AbstractKiProjectile projectile, LivingEntity caster) {
        if (projectile == null || caster == null) {
            return;
        }
        // Was getSize()/5, so a native NPC scored 0 - and the early-out below compares against
        // 1.0, so a zero scale sailed past it and repositioned the projectile onto the caster.
        float sizeScale = NpcDisplayApply.sizeScale(caster);
        if (Math.abs(sizeScale - 1.0f) < 1.0e-3f) {
            return;
        }
        double anchorY = caster.getY() + caster.getBbHeight() / 2.0;
        double flatOffset = projectile.getY() - anchorY;
        projectile.setPos(projectile.getX(), anchorY + flatOffset * sizeScale, projectile.getZ());
    }

    private static void applyDuration(AbstractKiProjectile projectile, int durationTicks) {
        if (durationTicks > 0) {
            projectile.setMaxLife(durationTicks);
        }
    }

    private static void poseCaster(LivingEntity caster, LivingEntity target) {
        if (caster == null || target == null || !target.isAlive() || target == caster) {
            return;
        }
        if (!shouldPoseForShot(caster, target)) return;
        Vec3 dir = desiredAim(caster.getEyePosition(), caster, target, 1.2f);
        if (dir.lengthSqr() > 1.0E-8) {
            CameraAimHelper.store(caster, dir.normalize());
        }
        NpcKiAim.hold(caster, target, AIM_HOLD_TICKS);
    }

    private static boolean shouldPoseForShot(LivingEntity caster, LivingEntity target) {
        if (caster == null || target == null || !target.isAlive()) return false;
        String mode = NpcCombatProfile.canonicalNpcAimMode(
                NpcCombatProfile.readCached(caster).npcRangedAimMode);
        return switch (mode) {
            case "distant" -> caster.distanceTo(target) > NpcCombatRanges.meleeReach(caster, target);
            case "hidden" -> !caster.hasLineOfSight(target);
            default -> false;
        };
    }

    private static void playConfiguredSound(LivingEntity caster, String soundId) {
        if (caster == null || caster.level().isClientSide()) return;
        net.minecraft.sounds.SoundEvent sound = NpcCustomSounds.resolve(soundId);
        if (sound != null) caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                sound, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    private static void poseCasterLook(LivingEntity caster, Vec3 look) {
        if (caster == null || look == null || look.lengthSqr() < 1.0E-8) {
            return;
        }
        Vec3 dir = look.normalize();
        CameraAimHelper.store(caster, dir);
        NpcKiAim.applyLook(caster, CameraAimHelper.yaw(dir), CameraAimHelper.pitch(dir));
    }

    /**
     * NPC has a short visual pose toward the target ({@link CameraAimHelper#store}).
     * The shot flies that stored/computed angle —
     * including pitch for a different Y. Not homing after it leaves.
     */
    static void aimAlongLook(AbstractKiProjectile projectile, LivingEntity caster, LivingEntity target) {
        if (projectile == null || caster == null) {
            return;
        }
        if (projectile instanceof KiWaveEntity && target != null && target != caster) {
            projectile.getPersistentData().putUUID("XenoNpcWaveAimTarget", target.getUUID());
        }
        double moving = projectile.getDeltaMovement().length();
        float speed = moving > 1.0E-4 ? (float) moving : projectile.getKiSpeed();
        if (speed <= 0f) {
            speed = 1.0f;
        }
        Vec3 dir = desiredAim(projectile.position(), caster, target, speed);
        if (dir.lengthSqr() < 1.0E-8) {
            return;
        }
        dir = dir.normalize();
        projectile.setHomingTarget(-1);
        projectile.setDeltaMovement(dir.scale(speed));
        float yaw = CameraAimHelper.yaw(dir);
        float pitch = CameraAimHelper.pitch(dir);
        projectile.setYRot(yaw);
        projectile.setXRot(pitch);
        if (shouldPoseForShot(caster, target)) NpcKiAim.updateHold(caster, yaw, pitch);
        if (projectile instanceof KiWaveEntity wave) {
            wave.setContinuousFollow(false);
        }
        if (projectile instanceof KiFixedAim aim) {
            aim.xenopixels$setFixedAim(yaw, pitch);
        }
    }

    /** DMZ rewrites a charging wave's orientation from its owner every tick, including launch. */
    public static void refreshWaveAim(KiWaveEntity wave, LivingEntity owner) {
        if (wave.level().isClientSide() || !(wave.level() instanceof ServerLevel level)
                || owner instanceof net.minecraft.world.entity.player.Player
                || !wave.getPersistentData().hasUUID("XenoNpcWaveAimTarget")) return;
        net.minecraft.world.entity.Entity found = level.getEntity(
                wave.getPersistentData().getUUID("XenoNpcWaveAimTarget"));
        if (found instanceof LivingEntity target && target.isAlive()) {
            aimAlongLook(wave, owner, target);
        }
    }

    /**
     * Direction from {@code from} to the target's eyes. Includes the Y delta so
     * a flyer or someone below is not shot on a flat line.
     */
    static Vec3 desiredAim(Vec3 from, LivingEntity caster, LivingEntity target) {
        return desiredAim(from, caster, target, 0.0f);
    }

    /**
     * As above, but leading a moving target when {@code projectileSpeed} is known.
     *
     * <p>Aiming at {@code getEyePosition()} alone fires at where the target <em>is</em>, so
     * anything strafing is missed by construction. This solves the intercept with the same
     * {@link LeadCalculator} the player-facing lead marker uses, then blends between "no lead"
     * and "full lead" by the NPC's own {@code npcRangedAccuracy}, so a weak NPC can be authored to
     * miss on purpose.
     *
     * <p>{@code speed} arrives in blocks/tick (it is a delta-movement length), while the
     * intercept solver works in blocks/second, hence the scale.
     */
    static Vec3 desiredAim(Vec3 from, LivingEntity caster, LivingEntity target, float projectileSpeed) {
        if (from != null && target != null && target.isAlive() && target != caster) {
            Vec3 to = leadPoint(from, caster, target, projectileSpeed);
            Vec3 dir = to.subtract(from);
            if (dir.lengthSqr() > 1.0E-8) {
                return dir.normalize();
            }
        }
        Vec3 look = caster != null ? caster.getLookAngle() : Vec3.ZERO;
        return look.lengthSqr() > 1.0E-8 ? look.normalize() : look;
    }

    /** World point to shoot at: ordinary NPCs use eyes and authored accuracy; an active
     * Ki Sense lock uses DMZ's midpoint and full lead at launch. No post-launch homing. */
    private static Vec3 leadPoint(Vec3 from, LivingEntity caster, LivingEntity target,
                                  float projectileSpeed) {
        boolean senseLocked = NpcTargetKeeper.isKiSenseLockedOn(caster, target);
        Vec3 eyes = senseLocked
                ? target.position().add(0.0, target.getBbHeight() * 0.5, 0.0)
                : target.getEyePosition();
        if (projectileSpeed <= 0.0f || !(caster.level() instanceof ServerLevel level)) {
            return eyes;
        }
        float accuracy = NpcCombatProfile.clampNpcRangedAccuracy(
                NpcCombatProfile.readCached(caster).npcRangedAccuracy);
        if (senseLocked) {
            accuracy = 1.0f;
        } else if (NpcTargetKeeper.isRetaliating(caster, target)) {
            accuracy = Math.max(accuracy, 0.95f);
        }
        if (accuracy <= 0.0f) {
            return eyes;
        }
        Vec3 velocity = TargetMotionEstimator.velocityOf(level, target);
        if (velocity.lengthSqr() < 1.0E-6) {
            return eyes;
        }
        LeadCalculator.LeadResult lead =
                LeadCalculator.linear(from, eyes, velocity, projectileSpeed * 20.0);
        if (!lead.solvable()) {
            return eyes;
        }
        return eyes.add(lead.aimPoint().subtract(eyes).scale(accuracy));
    }

    /**
     * Fires the attack named by a script/command: generic {@code "kiblast"}/{@code "kiwave"},
     * or a {@code PredefinedTechniques} id such as {@code "kamehameha"} or {@code "sokidan"}.
     *
     * @param durationTicks projectile life in ticks via {@code AbstractKiProjectile#setMaxLife};
     *                      pass {@link #NO_DURATION_OVERRIDE} to keep DMZ's own default.
     * @param aimAt         living entity whose head the NPC aims at. Yaw/pitch are
     *                      computed from the shot origin to that head (including Y).
     *                      {@code null} keeps DMZ's caster-look aim. Not ki-guided.
     */
    public static boolean fire(String blastType, LivingEntity caster, NpcCombatProfile profile,
                               int durationTicks, LivingEntity aimAt) {
        return fire(blastType, caster, profile, durationTicks, aimAt, 0);
    }

    public static boolean fire(String blastType, LivingEntity caster, NpcCombatProfile profile,
                               int durationTicks, LivingEntity aimAt, int colorOverride) {
        String id = blastType.toLowerCase(java.util.Locale.ROOT);
        if (PredefinedTechniqueLookup.findStrike(id) != null) {
            return NpcStrikeDispatcher.fire(id, caster, profile, aimAt);
        }
        switch (id) {
            case "kiblast" -> {
                // Keep the two generic NPC attacks reliable for CustomNPC AI/scripts. Named
                // DMZ techniques still use their native-style energy costs and cooldowns.
                fireKiBlast(caster, profile, durationTicks, aimAt, colorOverride);
                return true;
            }
            case "kiwave", "kihame", "kamehame" -> {
                fireKiWave(caster, profile, durationTicks, aimAt, colorOverride);
                return true;
            }
            default -> {
                return firePredefinedTechnique(id, caster, profile, durationTicks, aimAt, colorOverride);
            }
        }
    }

    /** 0 means "do not override". */
    private static int resolveColor(NpcCombatProfile profile, int colorOverride, int fallback) {
        if (colorOverride != 0) {
            return colorOverride & 0xFFFFFF;
        }
        if (profile != null && profile.kiColor != 0) {
            return profile.kiColor & 0xFFFFFF;
        }
        return fallback;
    }

    private static void applyKiColor(AbstractKiProjectile projectile, int rgb) {
        if (projectile == null || rgb == 0) {
            return;
        }
        projectile.setColors(rgb, rgb, rgb);
    }
}
