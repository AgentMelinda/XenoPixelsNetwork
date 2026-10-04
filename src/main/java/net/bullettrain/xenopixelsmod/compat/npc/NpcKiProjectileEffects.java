package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Runtime effects for profile-configured basic DragonMineZ ki projectiles. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcKiProjectileEffects {
    private static final Map<AbstractKiProjectile, String> GRAVITY =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<AbstractKiProjectile, Boolean> EXPLODED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<AbstractKiProjectile, String> GROUND_SOUND =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** Projectiles drawing a particle trail, and which particle. */
    private static final Map<AbstractKiProjectile, String> TRAIL =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** Projectiles tumbling in flight. The DMZ renderer lerps entity yaw and pitch. */
    private static final Map<AbstractKiProjectile, Boolean> SPIN =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** Projectiles that stop where they land rather than carrying on. */
    private static final Map<AbstractKiProjectile, Boolean> STICK =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Degrees per tick a spinning projectile turns. A full turn every second and a half. */
    private static final float SPIN_DEGREES_PER_TICK = 12.0f;

    private NpcKiProjectileEffects() {}

    public static void track(AbstractKiProjectile projectile, NpcCombatProfile profile) {
        if (projectile == null || profile == null) return;
        String mode = NpcCombatProfile.canonicalProjectileGravity(profile.npcProjectileGravity);
        if (!"none".equals(mode)) GRAVITY.put(projectile, mode);
        if (profile.npcRangedGroundSound != null && !profile.npcRangedGroundSound.isBlank()) {
            GROUND_SOUND.put(projectile, profile.npcRangedGroundSound);
        }
        String trail = NpcCombatProfile.canonicalProjectileTrail(profile.npcProjectileTrail);
        if (!"none".equals(trail)) TRAIL.put(projectile, trail);
        if (profile.npcProjectileSpins) SPIN.put(projectile, Boolean.TRUE);
        if (profile.npcProjectileSticks) STICK.put(projectile, Boolean.TRUE);
        // Applied once rather than per tick: the glowing flag is synced entity data, and rewriting
        // it every tick would resend it to every watcher for nothing.
        if (profile.npcProjectileGlows) projectile.setGlowingTag(true);
    }

    /** The vanilla particle a trail name draws. */
    private static net.minecraft.core.particles.SimpleParticleType particle(String trail) {
        return switch (trail) {
            case "smoke" -> net.minecraft.core.particles.ParticleTypes.SMOKE;
            case "flame" -> net.minecraft.core.particles.ParticleTypes.FLAME;
            case "crit" -> net.minecraft.core.particles.ParticleTypes.CRIT;
            case "portal" -> net.minecraft.core.particles.ParticleTypes.PORTAL;
            case "end_rod" -> net.minecraft.core.particles.ParticleTypes.END_ROD;
            case "soul" -> net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME;
            default -> null;
        };
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        synchronized (GRAVITY) {
            GRAVITY.entrySet().removeIf(entry -> {
                AbstractKiProjectile projectile = entry.getKey();
                if (projectile == null || projectile.isRemoved()
                        || projectile.level().isClientSide()) return true;
                Vec3 motion = projectile.getDeltaMovement();
                double y = switch (entry.getValue()) {
                    case "normal" -> motion.y - 0.03;
                    case "constant" -> -0.03;
                    case "accelerate" -> motion.y * 1.02 - 0.01;
                    default -> motion.y;
                };
                projectile.setDeltaMovement(motion.x, Math.max(-1.0, Math.min(1.0, y)), motion.z);
                return false;
            });
        }
        synchronized (TRAIL) {
            TRAIL.entrySet().removeIf(entry -> {
                AbstractKiProjectile projectile = entry.getKey();
                if (projectile == null || projectile.isRemoved()) return true;
                if (!(projectile.level() instanceof ServerLevel level)) return true;
                net.minecraft.core.particles.SimpleParticleType type = particle(entry.getValue());
                if (type == null) return true;
                // Server side, so every watching client draws it without a packet of our own.
                level.sendParticles(type, projectile.getX(), projectile.getY(), projectile.getZ(),
                        1, 0.0, 0.0, 0.0, 0.0);
                return false;
            });
        }
        synchronized (SPIN) {
            SPIN.entrySet().removeIf(entry -> {
                AbstractKiProjectile projectile = entry.getKey();
                if (projectile == null || projectile.isRemoved()
                        || projectile.level().isClientSide()) return true;
                // yRotO is what the renderer lerps from, so it has to carry the previous frame or
                // the projectile jitters between two angles instead of turning.
                projectile.yRotO = projectile.getYRot();
                projectile.setYRot(projectile.getYRot() + SPIN_DEGREES_PER_TICK);
                return false;
            });
        }
        synchronized (STICK) {
            STICK.entrySet().removeIf(entry -> {
                AbstractKiProjectile projectile = entry.getKey();
                if (projectile == null || projectile.isRemoved()
                        || projectile.level().isClientSide()) return true;
                if (!projectile.onGround()) return false;
                projectile.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                projectile.setNoGravity(true);
                // Landed and stopped; there is nothing left to hold it still for.
                return true;
            });
        }
        synchronized (GROUND_SOUND) {
            GROUND_SOUND.entrySet().removeIf(entry -> {
                AbstractKiProjectile projectile = entry.getKey();
                if (projectile == null || projectile.isRemoved()
                        || projectile.level().isClientSide()) return true;
                if (!projectile.onGround()) return false;
                net.minecraft.sounds.SoundEvent sound = NpcCustomSounds.resolve(entry.getValue());
                if (sound != null) projectile.level().playSound(null, projectile.getX(),
                        projectile.getY(), projectile.getZ(), sound,
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f);
                return true;
            });
        }
    }

    @SubscribeEvent
    public static void onProjectileDamage(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        if (!(source.getDirectEntity() instanceof AbstractKiProjectile projectile)
                || !(projectile.getOwner() instanceof LivingEntity owner)
                || !NpcCombatProfile.hasProfile(owner)) return;

        NpcCombatProfile profile = NpcCombatProfile.readCached(owner);
        LivingEntity target = event.getEntity();
        net.minecraft.sounds.SoundEvent hitSound = NpcCustomSounds.resolve(profile.npcRangedHitSound);
        if (hitSound != null) target.level().playSound(null, target.getX(), target.getY(),
                target.getZ(), hitSound, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f);
        if (profile.npcProjectileKnockback > 0.0f) {
            target.knockback(profile.npcProjectileKnockback,
                    owner.getX() - target.getX(), owner.getZ() - target.getZ());
        }
        // The projectile's own timing. This used to read the melee pair, so lengthening a melee
        // poison silently lengthened the projectile's unrelated effect with it.
        applyEffect(target, profile.npcProjectileEffect, profile.npcProjectileEffectDuration,
                profile.npcProjectileEffectAmplifier);

        if (profile.npcProjectileExplosion > 0.0f
                && EXPLODED.putIfAbsent(projectile, Boolean.TRUE) == null
                && target.level() instanceof ServerLevel level) {
            // Projectile effects never break blocks; grief behavior is not part of an NPC editor.
            level.explode(owner, target.getX(), target.getY(), target.getZ(),
                    profile.npcProjectileExplosion, Level.ExplosionInteraction.NONE);
        }
    }

    private static void applyEffect(LivingEntity target, String rawId, int duration, int amplifier) {
        if (target == null || rawId == null || rawId.isBlank()) return;
        if ("minecraft:fire".equals(rawId)) {
            target.igniteForSeconds(Math.max(1, duration / 20));
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) return;
        BuiltInRegistries.MOB_EFFECT.getHolder(id).ifPresent(effect -> target.addEffect(
                new MobEffectInstance(effect, NpcCombatProfile.clampNpcEffectDuration(duration),
                        NpcCombatProfile.clampNpcEffectAmplifier(amplifier))));
    }
}
