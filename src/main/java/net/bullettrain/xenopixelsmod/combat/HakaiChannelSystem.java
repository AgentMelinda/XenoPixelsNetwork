package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.bullettrain.xenopixelsmod.combat.fx.HakaiFx;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative Hakai channel: an interruptible, multi-tick hold that ends in the
 * target's erasure. No existing ability in this mod runs over several seconds — every other
 * FX call fires once and returns — so this mirrors {@code ChaseFlightSystem}'s shape instead
 * (a per-caster tracked state, re-validated every server tick, cancelled the instant any
 * condition fails) since that is the only other system here that runs a move across ticks.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class HakaiChannelSystem {

    /** Caster must hold still; drifting past this (blocks, squared) cancels the channel. */
    private static final double MOVE_INTERRUPT_DIST_SQ = 1.0;

    private static final Map<UUID, Channel> ACTIVE = new ConcurrentHashMap<>();

    private static final class Channel {
        final int targetId;
        final int totalTicks;
        final Vec3 startPos;
        int ticksElapsed;

        Channel(int targetId, int totalTicks, Vec3 startPos) {
            this.targetId = targetId;
            this.totalTicks = totalTicks;
            this.startPos = startPos;
        }
    }

    private HakaiChannelSystem() {
    }

    public static boolean isChanneling(ServerPlayer caster) {
        return caster != null && ACTIVE.containsKey(caster.getUUID());
    }

    /** Starts (or restarts) a channel. Caller has already applied every gate (§2 of the plan). */
    public static void start(ServerPlayer caster, LivingEntity target) {
        int totalTicks = Math.max(10, XenoServerConfig.hakaiChannelTicks);
        ACTIVE.put(caster.getUUID(), new Channel(target.getId(), totalTicks, caster.position()));

        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                ModSounds.HAKAI_VOICE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                ModSounds.HAKAI_CHARGE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public static void cancel(ServerPlayer caster, String message) {
        Channel removed = ACTIVE.remove(caster.getUUID());
        if (removed != null && message != null) {
            caster.displayClientMessage(Component.literal(message), true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer caster)) return;
        Channel channel = ACTIVE.get(caster.getUUID());
        if (channel == null) return;
        if (!(caster.level() instanceof ServerLevel level)) {
            cancel(caster, null);
            return;
        }

        Entity raw = level.getEntity(channel.targetId);
        if (!(raw instanceof LivingEntity target) || !target.isAlive()) {
            cancel(caster, "§7Hakai lost the target");
            return;
        }
        if (caster.distanceTo(target) > XenoServerConfig.hakaiMaxRange) {
            cancel(caster, "§7Hakai lost the target");
            return;
        }
        if (!hasLineOfSight(caster, target)) {
            cancel(caster, "§7Hakai lost the target");
            return;
        }
        if (caster.position().distanceToSqr(channel.startPos) > MOVE_INTERRUPT_DIST_SQ) {
            cancel(caster, "§7Hakai interrupted");
            return;
        }

        channel.ticksElapsed++;
        float progress = channel.ticksElapsed / (float) channel.totalTicks;
        if (channel.ticksElapsed % 2 == 0) {
            HakaiFx.tick(level, target, progress);
        }

        if (channel.ticksElapsed >= channel.totalTicks) {
            ACTIVE.remove(caster.getUUID());
            finish(level, target);
        }
    }

    private static void finish(ServerLevel level, LivingEntity target) {
        Vec3 pos = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        CombatFx.cue(level, pos, CombatFxKind.HAKAI_ERASE, 1.0f);
        // True erasure: the same damage source vanilla's own /kill command uses. Tagged
        // bypasses_invulnerability (skips the totem-of-undying save), bypasses_armor and
        // bypasses_resistance -- and Float.MAX_VALUE overwhelms absorption regardless. Still
        // routes through the normal hurt()/die() flow, so a player target gets the real
        // respawn screen rather than being discarded outright.
        target.hurt(target.level().damageSources().genericKill(), Float.MAX_VALUE);
    }

    /** Any damage landing on a channeling caster breaks their concentration. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCasterDamaged(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer caster && isChanneling(caster)) {
            cancel(caster, "§7Hakai interrupted");
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player != null) {
            ACTIVE.remove(player.getUUID());
        }
    }

    /** Same clip-based line-of-sight check {@code LockOnValidator} uses, minus its seat gate. */
    private static boolean hasLineOfSight(ServerPlayer caster, Entity target) {
        Vec3 from = caster.getEyePosition();
        Vec3 to = target.getEyePosition();
        HitResult hit = caster.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS;
    }
}
