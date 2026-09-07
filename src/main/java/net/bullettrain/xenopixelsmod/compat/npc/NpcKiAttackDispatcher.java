package net.bullettrain.xenopixelsmod.compat.npc;

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
        float charge = profile.chargeFactor();
        float damage = profile.kiDamage() * charge;
        float speed = (1.2f + profile.kiPower * 0.02f) * Math.min(2.0f, charge);
        float size = (0.5f + profile.kiPower * 0.01f) * charge;
        int color = resolveColor(profile, colorOverride, DEFAULT_COLOR);
        KiBlastEntity blast = new KiBlastEntity(caster.level(), caster);
        poseCaster(caster, aimAt);
        blast.setupKiBlast(caster, damage, speed, color, size, DEFAULT_CAST_TIME);
        rescaleSpawnHeight(blast, caster);
        applyDuration(blast, durationTicks);
        applyKiColor(blast, color);
        aimAlongLook(blast, caster, aimAt);
    }

    /** Fires DMZ's beam-style wave attack, scaled from the NPC's profile. */
    public static void fireKiWave(LivingEntity caster, NpcCombatProfile profile, int durationTicks,
                                  LivingEntity aimAt, int colorOverride) {
        float charge = profile.chargeFactor();
        float damage = profile.kiDamage() * charge;
        float speed = (1.5f + profile.kiPower * 0.02f) * Math.min(2.0f, charge);
        float size = (0.6f + profile.kiPower * 0.015f) * charge;
        KiWaveEntity wave = new KiWaveEntity(caster.level(), caster);
        poseCaster(caster, aimAt);
        wave.setupKiHame(caster, damage, speed, size, DEFAULT_CAST_TIME);
        rescaleSpawnHeight(wave, caster);
        applyDuration(wave, durationTicks);
        applyKiColor(wave, resolveColor(profile, colorOverride, 0));
        aimAlongLook(wave, caster, aimAt);
    }

    /**
     * Base damage before a technique's own {@code getDamageMultiplier()} is applied. Mirrors
     * DMZ's real {@code StatsData.getKiDamage()} ({@code kiPower * releaseMultiplier}, see
     * {@link NpcCombatProfile#kiDamage()}) rather than the flat/{@code strikePower}-scaled
     * approximation this used to be. The active form's PWR multiplier is applied inside the
     * profile calculation; {@code chargeFactor()} remains the NPC-specific charge extension.
     */
    private static float baseDamage(NpcCombatProfile profile) {
        float base = profile.kiDamage();
        float charge = profile != null ? profile.chargeFactor() : 1.0f;
        return base * charge;
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
        KiAttackData data = PredefinedTechniqueLookup.find(id);
        if (data == null) {
            return false;
        }
        if (!NpcKiCooldowns.ready(caster, data.getId())) {
            return false;
        }
        if (!NpcResources.spendEnergy(caster, profile, NpcTechniqueMath.kiCost(profile, data))) {
            return false;
        }
        Level level = caster.level();
        float charge = profile.chargeFactor();
        float damage = baseDamage(profile) * data.getDamageMultiplier();
        float speed = data.getSpeed() * Math.min(2.0f, charge);
        float size = data.getSize() * charge;
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
        float sizeScale = NpcDisplayApply.getSize(caster) / 5.0f;
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
        Vec3 dir = target.getEyePosition().subtract(caster.getEyePosition());
        if (dir.lengthSqr() > 1.0E-8) {
            dir = dir.normalize();
            CameraAimHelper.store(caster, dir);
        }
        NpcKiAim.hold(caster, target, AIM_HOLD_TICKS);
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
        NpcKiAim.updateHold(caster, yaw, pitch);
        if (projectile instanceof KiWaveEntity wave) {
            wave.setContinuousFollow(false);
        }
        if (projectile instanceof KiFixedAim aim) {
            aim.xenopixels$setFixedAim(yaw, pitch);
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
     * and "full lead" by the NPC's own {@code aimAccuracy}, so a weak NPC can be authored to
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

    /** World point to shoot at: the target's eyes, pulled toward the intercept by accuracy. */
    private static Vec3 leadPoint(Vec3 from, LivingEntity caster, LivingEntity target,
                                  float projectileSpeed) {
        Vec3 eyes = target.getEyePosition();
        if (projectileSpeed <= 0.0f || !(caster.level() instanceof ServerLevel level)) {
            return eyes;
        }
        float accuracy = NpcCombatProfile.clampAimAccuracy(
                NpcCombatProfile.readCached(caster).aimAccuracy);
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
