package net.bullettrain.xenopixelsmod.combat;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import com.dragonminez.compat.util.LazyOptional;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.fx.HakaiFx;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDissolve;
import net.bullettrain.xenopixelsmod.compat.sable.SableKiClip;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.event.DmzMasterProtection;
import net.bullettrain.xenopixelsmod.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Area Hakai (2026-09-29 owner: "a per radius where player looking at a multitarget hakai without
 * the need to lock on the target"): everything alive in a sphere where the caster looks is channelled
 * at once and erased together, then the blocks in the sphere follow ({@link HakaiBlockErasure}).
 *
 * <p>Chosen with {@code /xenoset hakaiMode area}; {@code single} (the default) is the original
 * {@link HakaiChannelSystem}, which this does not touch. The channel keeps that one's rules - ki per
 * tick, poise budget, hold still, release J to cancel - so the two feel the same to cast. Unlike
 * the single channel, losing one target does not end it: that target is simply let go.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class HakaiAreaSystem {
    private static final Map<UUID, Channel> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Restore>> RESTORES = new ConcurrentHashMap<>();
    /** Channel FX go to this many of the nearest targets, to keep the packet load sane. */
    private static final int FX_TARGETS = 8;
    private static final int RESCAN_TICKS = 10;

    private HakaiAreaSystem() {
    }

    private static final class Restore {
        final int targetId;
        float keepBelow;
        final float step;

        Restore(int targetId, float keepBelow, float step) {
            this.targetId = targetId;
            this.keepBelow = keepBelow;
            this.step = step;
        }
    }

    private static final class Channel {
        final Vec3 centre;
        final double radius;
        final int totalTicks;
        final boolean skipEnvironment;
        /** Target entity id to its outline lease, nearest first when first gathered. */
        final Map<Integer, GlowLease> targets = new LinkedHashMap<>();
        Vec3 startPos;
        int ticksElapsed;
        float damageTaken;

        Channel(Vec3 centre, double radius, int totalTicks, Vec3 startPos, boolean skipEnvironment) {
            this.centre = centre;
            this.radius = radius;
            this.totalTicks = totalTicks;
            this.startPos = startPos;
            this.skipEnvironment = skipEnvironment;
        }

        float progress() {
            return ticksElapsed / (float) Math.max(1, totalTicks);
        }
    }

    public static boolean isChanneling(ServerPlayer caster) {
        return caster != null && ACTIVE.containsKey(caster.getUUID());
    }

    /** Where an area Hakai centres: what the crosshair hits (a block, a ship, a body), within range. */
    public static Vec3 lookCentre(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        Vec3 best = end;
        BlockHitResult block = SableKiClip.clip(player.level(), eye, end, player);
        if (block.getType() != HitResult.Type.MISS) {
            best = SableKiClip.worldHit(player.level(), block);
        }
        LivingEntity body = HakaiChannelSystem.findLookTarget(player, range, false);
        if (body != null) {
            Vec3 at = body.position().add(0.0, body.getBbHeight() * 0.5, 0.0);
            if (eye.distanceToSqr(at) < eye.distanceToSqr(best)) best = at;
        }
        return best;
    }

    /** Starts an area channel at {@code centre}. The caller has applied the unlock/enable gates. */
    public static void start(ServerPlayer caster, Vec3 centre, boolean skipEnvironment) {
        cancel(caster, null);
        int totalTicks = Math.max(10, XenoServerConfig.hakaiChannelTicks);
        Channel channel = new Channel(centre, XenoServerConfig.hakaiAreaRadius, totalTicks,
                caster.position(), skipEnvironment);
        ACTIVE.put(caster.getUUID(), channel);
        gather(caster, channel);
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                ModSounds.HAKAI_CHARGE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        DmzAnimHelper.broadcastHakaiHold(caster);
        caster.displayClientMessage(Component.literal("§dHakai §7- " + channel.targets.size()
                + " caught, radius " + String.format("%.0f", channel.radius)), true);
    }

    /** Everything alive in the sphere, nearest first, up to the target cap; newcomers join. */
    private static void gather(ServerPlayer caster, Channel channel) {
        int room = XenoServerConfig.hakaiAreaMaxTargets - channel.targets.size();
        if (room <= 0) return;
        AABB box = new AABB(channel.centre, channel.centre).inflate(channel.radius);
        List<LivingEntity> found = caster.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != caster && e.isAlive() && !channel.targets.containsKey(e.getId())
                        && !DmzMasterProtection.isDmzMaster(e)
                        && HakaiAreaRules.inSphere(channel.centre, e.position()
                        .add(0.0, e.getBbHeight() * 0.5, 0.0), channel.radius + e.getBbWidth() * 0.5));
        for (LivingEntity e : HakaiAreaRules.capTargets(found, t -> t.distanceToSqr(channel.centre), room)) {
            channel.targets.put(e.getId(), new GlowLease(e::hasGlowingTag, e::setGlowingTag,
                    XenoServerConfig.hakaiTargetGlow));
        }
    }

    public static void cancel(ServerPlayer caster, String message) {
        if (caster == null) return;
        Channel removed = ACTIVE.remove(caster.getUUID());
        if (removed == null) return;
        DmzAnimHelper.broadcastHakaiStop(caster);
        List<Restore> ramps = new ArrayList<>();
        for (Map.Entry<Integer, GlowLease> e : removed.targets.entrySet()) {
            e.getValue().release();
            Restore r = restoreFor(caster, e.getKey(), 1.0f - removed.progress());
            if (r != null) ramps.add(r);
        }
        if (!ramps.isEmpty()) RESTORES.put(caster.getUUID(), ramps);
        if (message != null) {
            caster.displayClientMessage(Component.literal(message), true);
            caster.sendSystemMessage(Component.literal(message));
        }
    }

    /** The same restore ramp {@code HakaiChannelSystem.beginRestore} gives, one per target. */
    private static Restore restoreFor(ServerPlayer caster, int targetId, float keepBelow) {
        float start = Math.max(0.02f, Math.min(1.0f, keepBelow));
        Entity raw = caster.level().getEntity(targetId);
        int restoreTicks = XenoServerConfig.hakaiFadeRestoreTicks;
        if (start >= 0.98f || restoreTicks <= 0) {
            if (raw instanceof LivingEntity living) {
                HakaiFx.reveal(living);
                NpcDissolve.clear(living);
            }
            return null;
        }
        return new Restore(targetId, start, Math.max(0.01f, (1.0f - start) / restoreTicks));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer caster)) return;
        if (!(caster.level() instanceof ServerLevel level)) return;
        tickRestores(caster, level);
        Channel channel = ACTIVE.get(caster.getUUID());
        if (channel == null) return;

        double moveLimit = XenoServerConfig.hakaiMoveInterruptDistance;
        boolean skipEnv = channel.skipEnvironment || XenoServerConfig.lockOnThroughBlocks;
        if (!skipEnv && caster.position().distanceToSqr(channel.startPos) > moveLimit * moveLimit) {
            cancel(caster, "§7Hakai interrupted — hold still");
            return;
        }
        float perTick = XenoServerConfig.hakaiKiCost / Math.max(1, channel.totalTicks);
        if (!spendKi(caster, perTick)) {
            cancel(caster, "§7Hakai stopped — no KI");
            return;
        }
        if (channel.ticksElapsed % RESCAN_TICKS == 0) gather(caster, channel);

        channel.ticksElapsed++;
        float progress = channel.progress();
        List<LivingEntity> live = new ArrayList<>();
        Iterator<Map.Entry<Integer, GlowLease>> it = channel.targets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, GlowLease> e = it.next();
            Entity raw = level.getEntity(e.getKey());
            // Gone, dead, or walked out of the sphere: let that one go, keep the rest.
            if (!(raw instanceof LivingEntity target) || !target.isAlive()
                    || !HakaiAreaRules.inSphere(channel.centre, target.position(), channel.radius + 2.0)) {
                e.getValue().release();
                if (raw instanceof LivingEntity living) {
                    HakaiFx.reveal(living);
                    NpcDissolve.clear(living);
                }
                it.remove();
                continue;
            }
            live.add(target);
        }
        for (int i = 0; i < live.size(); i++) {
            LivingEntity target = live.get(i);
            NpcDissolve.apply(target, progress);
            if (i < FX_TARGETS && channel.ticksElapsed % 2 == 0) {
                // Only the first gets the caster's palm effect; one hand, not eight.
                HakaiFx.tick(level, i == 0 ? caster : null, target, progress);
            }
        }
        if (channel.ticksElapsed % 2 == 0) {
            caster.displayClientMessage(progressBar(progress, live.size()), true);
        }
        if (channel.ticksElapsed % 10 == 1) {
            // The veil over the whole sphere, sized to it (2026-09-29: "hakai fx radius or scale?").
            net.bullettrain.xenopixelsmod.fx.effek.XenoEffects.playWide(level,
                    net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.HAKAI_CHANNEL,
                    channel.centre.subtract(0.0, channel.radius, 0.0), UP, areaScale(channel),
                    channel.radius * 2.0);
        }
        if (channel.ticksElapsed >= channel.totalTicks) {
            ACTIVE.remove(caster.getUUID());
            for (GlowLease glow : channel.targets.values()) glow.release();
            DmzAnimHelper.broadcastHakaiFire(caster);
            for (LivingEntity target : live) {
                NpcDissolve.clear(target);
                HakaiErase.eraseTarget(level, target);
            }
            net.bullettrain.xenopixelsmod.fx.effek.XenoEffects.playWide(level,
                    net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.HAKAI_ERASE,
                    channel.centre, UP, areaScale(channel), channel.radius);
            HakaiBlockErasure.queue(level, caster, channel.centre, channel.radius);
        }
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static float areaScale(Channel channel) {
        return net.bullettrain.xenopixelsmod.combat.fx.HakaiEffectRules.areaScale(channel.radius,
                XenoServerConfig.hakaiAreaFxScale);
    }

    private static void tickRestores(ServerPlayer caster, ServerLevel level) {
        List<Restore> ramps = RESTORES.get(caster.getUUID());
        if (ramps == null) return;
        ramps.removeIf(r -> {
            Entity raw = level.getEntity(r.targetId);
            if (!(raw instanceof LivingEntity target) || !target.isAlive()) return true;
            r.keepBelow = Math.min(1.0f, r.keepBelow + r.step);
            HakaiFx.restore(level, target, r.keepBelow);
            NpcDissolve.apply(target, 1.0f - r.keepBelow);
            if (r.keepBelow >= 1.0f) {
                HakaiFx.reveal(target);
                NpcDissolve.clear(target);
                return true;
            }
            return false;
        });
        if (ramps.isEmpty()) RESTORES.remove(caster.getUUID());
    }

    private static boolean spendKi(ServerPlayer player, float cost) {
        if (cost <= 0f) return true;
        try {
            LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, player);
            StatsData data = opt.orElse(null);
            Resources res = data != null ? data.getResources() : null;
            if (res == null || res.getCurrentEnergy() < cost) return false;
            res.removeEnergy(cost);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Component progressBar(float progress, int caught) {
        int filled = Math.max(0, Math.min(10, Math.round(progress * 10.0f)));
        return Component.literal("§dHakai §8[§d" + "|".repeat(filled) + "§8" + "|".repeat(10 - filled)
                + "§8] §7x" + caught);
    }

    /** Same poise rule as the single channel: harassment is absorbed, a real burst breaks it. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCasterDamaged(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer caster)) return;
        Channel channel = ACTIVE.get(caster.getUUID());
        if (channel == null) return;
        channel.damageTaken += Math.max(0.0f, event.getNewDamage());
        float budget = Math.max(0.0f, caster.getMaxHealth() * XenoServerConfig.hakaiPoiseFraction);
        if (channel.damageTaken > budget) {
            cancel(caster, "§7Hakai broken — too much damage taken");
            return;
        }
        channel.startPos = caster.position();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        dropAll(player, ACTIVE.remove(player.getUUID()), RESTORES.remove(player.getUUID()));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (event.getServer() == null) return;
        for (Map.Entry<UUID, Channel> e : ACTIVE.entrySet()) {
            ServerPlayer caster = event.getServer().getPlayerList().getPlayer(e.getKey());
            dropAll(caster, e.getValue(), RESTORES.get(e.getKey()));
        }
        ACTIVE.clear();
        RESTORES.clear();
    }

    /** Nothing can advance a ramp once the caster is gone: outlines off and bodies back at once. */
    private static void dropAll(ServerPlayer caster, Channel channel, List<Restore> ramps) {
        if (channel != null) {
            for (Map.Entry<Integer, GlowLease> e : channel.targets.entrySet()) {
                e.getValue().release();
                reveal(caster, e.getKey());
            }
        }
        if (ramps != null) {
            for (Restore r : ramps) reveal(caster, r.targetId);
        }
    }

    private static void reveal(ServerPlayer caster, int id) {
        if (caster == null) return;
        if (caster.level().getEntity(id) instanceof LivingEntity living) {
            HakaiFx.reveal(living);
            NpcDissolve.clear(living);
        }
    }
}
