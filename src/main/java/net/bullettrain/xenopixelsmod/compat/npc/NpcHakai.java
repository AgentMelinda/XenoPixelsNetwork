package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.anim.CombatStateAnim;
import net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.bullettrain.xenopixelsmod.combat.fx.HakaiFx;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.event.DmzMasterProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC Hakai channel. Player Hakai lives on {@code HakaiChannelSystem} and is typed
 * to {@code ServerPlayer}; this is the NPC clock, reusing dissolve / FX / erase.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcHakai {
    private static final float RESIST_STAT_RATIO = 3.0f;

    private record Channel(int targetId, int totalTicks, boolean hadGlow, int ticksElapsed) {
        Channel next() {
            return new Channel(targetId, totalTicks, hadGlow, ticksElapsed + 1);
        }
    }

    private static final Map<UUID, Channel> CHANNELS = new ConcurrentHashMap<>();

    private NpcHakai() {}

    public static boolean isChanneling(LivingEntity npc) {
        return npc != null && CHANNELS.containsKey(npc.getUUID());
    }

    public static boolean start(LivingEntity npc, LivingEntity target) {
        if (!canStart(npc, target)) {
            return false;
        }
        cancel(npc);
        int total = Math.max(10, XenoServerConfig.hakaiChannelTicks);
        boolean hadGlow = target.hasGlowingTag();
        if (XenoServerConfig.hakaiTargetGlow) {
            target.setGlowingTag(true);
        }
        CHANNELS.put(npc.getUUID(), new Channel(target.getId(), total, hadGlow, 0));
        NpcEntityLookup.remember(npc);
        CombatStateAnim.play(npc, TechniqueAnimSlot.HAKAI_HOLD);
        npc.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                net.bullettrain.xenopixelsmod.sound.ModSounds.HAKAI_CHARGE.get(),
                SoundSource.HOSTILE, 1.0f, 1.0f);
        return true;
    }

    public static boolean cancel(LivingEntity npc) {
        if (npc == null) {
            return false;
        }
        Channel channel = CHANNELS.remove(npc.getUUID());
        if (channel == null) {
            return false;
        }
        restoreTarget(npc, channel, true);
        net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.stopClip(npc);
        return true;
    }

    public static void forget(UUID npcId) {
        if (npcId != null) {
            CHANNELS.remove(npcId);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (CHANNELS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Channel>> it = CHANNELS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Channel> entry = it.next();
            LivingEntity npc = NpcEntityLookup.findAlive(event.getServer(), entry.getKey());
            if (npc == null || !(npc.level() instanceof ServerLevel level)) {
                continue;
            }
            Channel channel = entry.getValue();
            Entity raw = level.getEntity(channel.targetId());
            if (!(raw instanceof LivingEntity target) || !target.isAlive()) {
                it.remove();
                net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.stopClip(npc);
                continue;
            }
            if (npc.distanceTo(target) > XenoServerConfig.hakaiMaxRange) {
                it.remove();
                restoreTarget(npc, channel, true);
                net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.stopClip(npc);
                continue;
            }
            NpcCombatProfile profile = NpcCombatProfile.readCached(npc);
            float perTick = XenoServerConfig.hakaiKiCost / (float) Math.max(1, channel.totalTicks());
            if (!NpcResources.spendEnergy(npc, profile, perTick)) {
                it.remove();
                restoreTarget(npc, channel, true);
                net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.stopClip(npc);
                continue;
            }
            Channel next = channel.next();
            float progress = next.ticksElapsed() / (float) next.totalTicks();
            NpcDissolve.apply(target, progress);
            if (next.ticksElapsed() % 2 == 0) {
                HakaiFx.tick(level, npc, target, progress);
            }
            if (next.ticksElapsed() < next.totalTicks()) {
                entry.setValue(next);
                continue;
            }
            it.remove();
            restoreTarget(npc, next, false);
            finish(level, npc, target);
        }
    }

    private static boolean canStart(LivingEntity npc, LivingEntity target) {
        return npc != null && target != null && npc != target
                && npc.isAlive() && target.isAlive()
                && !npc.level().isClientSide()
                && NpcCombatProfile.hasProfile(npc)
                && !DmzMasterProtection.isDmzMaster(target)
                && npc.distanceTo(target) <= XenoServerConfig.hakaiMaxRange
                && hasLineOfSight(npc, target);
    }

    private static boolean hasLineOfSight(LivingEntity caster, Entity target) {
        Vec3 from = caster.getEyePosition();
        Vec3 to = target.getEyePosition();
        HitResult hit = caster.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS || XenoServerConfig.lockOnThroughBlocks;
    }

    private static void restoreTarget(LivingEntity npc, Channel channel, boolean fadeBack) {
        if (npc == null || channel == null || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        Entity raw = level.getEntity(channel.targetId());
        if (!(raw instanceof LivingEntity target)) {
            return;
        }
        if (!channel.hadGlow()) {
            target.setGlowingTag(false);
        }
        if (fadeBack) {
            HakaiFx.reveal(target);
            NpcDissolve.clear(target);
        }
    }

    private static void finish(ServerLevel level, LivingEntity npc, LivingEntity target) {
        CombatStateAnim.play(npc, TechniqueAnimSlot.HAKAI_FIRE);
        Vec3 pos = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        CombatFx.cue(level, pos, CombatFxKind.HAKAI_ERASE, 1.0f);
        HakaiFx.burst(level, target, true);
        HakaiFx.reveal(target);
        NpcDissolve.clear(target);
        if (resistsErase(npc, target)) {
            return;
        }
        // A native Xeno NPC dies rather than being erased. discard() would skip die(), which is
        // what schedules its respawn, so an NPC caught by Hakai would vanish permanently - exactly
        // the removal /kill was already guarded against. Going through damage instead means Hakai
        // still kills it, and its respawn still happens.
        if (target instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity
                || target instanceof Player || NpcCounterpartSync.isCustomNpc(target)) {
            var source = target.level().damageSources().genericKill();
            target.hurt(source, Float.MAX_VALUE);
            if (target.isAlive()) {
                target.setHealth(0.0f);
                target.die(source);
            }
        } else {
            target.discard();
        }
    }

    private static boolean resistsErase(LivingEntity caster, LivingEntity target) {
        if (target instanceof Player player
                && (player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable)) {
            return true;
        }
        if (target.getMaxHealth() > caster.getMaxHealth() * RESIST_STAT_RATIO) {
            return true;
        }
        if (!NpcCombatProfile.hasProfile(caster)) {
            return false;
        }
        NpcCombatProfile casterProfile = NpcCombatProfile.readCached(caster);
        double casterMelee = NpcDmzStats.meleeDamage(caster, casterProfile);
        float targetMelee = target instanceof LivingEntity living && NpcCombatProfile.hasProfile(living)
                ? (float) NpcDmzStats.meleeDamage(living, NpcCombatProfile.readCached(living))
                : 0.0f;
        return casterMelee > 0.0f && targetMelee > casterMelee * RESIST_STAT_RATIO;
    }
}
