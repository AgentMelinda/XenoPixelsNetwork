package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.effect.ModEffects;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.HakaiFadePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-side Hakai dissolve. Amplifier 0..255 = erase progress.
 *
 * <p>The visual fade is {@link HakaiFadePacket}, sent with
 * {@link ModNetwork#sendToTrackingAndSelf}. The hidden {@code hakai_dissolve} effect is still
 * written (and still vanilla-broadcast) as a fallback, but drawing must not depend on it:
 * vanilla never syncs a non-player living entity's effects to tracker clients, and
 * {@code forceAddEffect} can no-op through {@code CommonHooks.canMobEffectBeApplied}.
 *
 * <p>{@code forceAddEffect} is used instead of {@code addEffect} because
 * {@code MobEffectInstance.update} never lowers an amplifier, which made the restore ramp unable
 * to fade the body back in. Packet and effect signatures verified with {@code javap} against
 * {@code neoforge-21.1.248-merged.jar}.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = net.bullettrain.xenopixelsmod.XenoPixelsMod.MOD_ID)
public final class NpcDissolve {
    private static final int DURATION_TICKS = 40;

    /**
     * One owner per target (Codex CL-150 review: HakaiChannelSystem, HakaiAreaSystem, NpcHakai and their restore ramps
     * all write this entity's progress with no arbitration, so overlapping sources interleave and the sweep jumps).
     * While some channel is still RAISING the progress (a rise within {@link #LIVE_TICKS}), a lower value from another
     * source (an old restore ramp) is ignored, and a mid-sweep clear is deferred until the live channel stops; the live
     * channel's own final clear (progress near 1) passes at once.
     */
    private static final class Owner {
        float last; long riseTick; boolean pendingClear;
        // Sweep timing (HakaiSweepTiming): previous accepted value/tick, and the line last sent to clients.
        float prev = -1f; long prevTick = Long.MIN_VALUE;
        float sentProgress; float sentRate; long sentTick = Long.MIN_VALUE;
    }
    private static final java.util.Map<Integer, Owner> OWNERS = new java.util.HashMap<>();
    private static final long LIVE_TICKS = 3;

    private NpcDissolve() {}

    public static int amplifierForProgress(float eraseProgress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, eraseProgress));
        return Math.max(0, Math.min(255, Math.round(clamped * 255.0f)));
    }

    public static void apply(LivingEntity target, float eraseProgress) {
        if (target == null) return;
        if (target.level() instanceof ServerLevel level) {
            long now = level.getGameTime();
            Owner owner = OWNERS.computeIfAbsent(target.getId(), id -> new Owner());
            boolean rising = eraseProgress >= owner.last - 0.001f;
            if (!rising && now - owner.riseTick <= LIVE_TICKS) return; // a live channel owns this target
            if (rising) owner.riseTick = now;
            owner.last = eraseProgress;
            owner.pendingClear = false;
            sendTiming(target, owner, eraseProgress, now);
        }
        int amplifier = amplifierForProgress(eraseProgress);
        if (amplifier <= 0) {
            clear(target);
            return;
        }
        MobEffectInstance instance = new MobEffectInstance(ModEffects.HAKAI_DISSOLVE, DURATION_TICKS,
                amplifier, true, false, false);
        target.forceAddEffect(instance, null);
        broadcast(target, new ClientboundUpdateMobEffectPacket(target.getId(), instance, false));
        sendFade(target, amplifier);
    }

    public static void clear(LivingEntity target) {
        if (target == null) return;
        if (target.level() instanceof ServerLevel level) {
            Owner owner = OWNERS.get(target.getId());
            if (owner != null && level.getGameTime() - owner.riseTick <= LIVE_TICKS && owner.last < 0.95f) {
                owner.pendingClear = true; // another channel is still erasing it; clear once that one stops
                return;
            }
            OWNERS.remove(target.getId());
        }
        clearNow(target);
    }

    /**
     * Server ticks are exact, so the rate is simply this tick's value minus the previous tick's. A new line goes to
     * clients only when the real value leaves the line already sent (first tick, cancel, restore, another source).
     */
    private static void sendTiming(LivingEntity target, Owner owner, float progress, long now) {
        float rate = owner.prevTick == now - 1 ? progress - owner.prev : 0f;
        owner.prev = progress;
        owner.prevTick = now;
        boolean fresh = owner.sentTick == Long.MIN_VALUE;
        float predicted = fresh ? -1f : net.bullettrain.xenopixelsmod.combat.HakaiSweepTiming.at(owner.sentProgress, owner.sentRate, now - owner.sentTick);
        boolean rateChanged = Math.abs(rate - owner.sentRate) > 0.002f && owner.prevTick == now;
        if (fresh || Math.abs(predicted - progress) > 0.01f || (rateChanged && rate != 0f)) {
            owner.sentProgress = progress; owner.sentRate = rate; owner.sentTick = now;
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToTrackingAndSelf(target,
                    new net.bullettrain.xenopixelsmod.combat.HakaiSweepTiming(target.getId(), progress, rate));
        }
    }

    private static void clearNow(LivingEntity target) {
        net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToTrackingAndSelf(target,
                new net.bullettrain.xenopixelsmod.combat.HakaiSweepTiming(target.getId(), 0f, 0f));
        if (target.removeEffect(ModEffects.HAKAI_DISSOLVE)) {
            broadcast(target, new ClientboundRemoveMobEffectPacket(target.getId(), ModEffects.HAKAI_DISSOLVE));
        }
        sendFade(target, 0);
    }

    private static void sendFade(LivingEntity target, int amplifier) {
        if (target.level() instanceof ServerLevel) {
            ModNetwork.sendToTrackingAndSelf(target, new HakaiFadePacket(target.getId(), amplifier));
        }
    }

    private static void broadcast(LivingEntity target, Packet<?> packet) {
        if (target.level() instanceof ServerLevel level) {
            level.getChunkSource().broadcast(target, packet);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (OWNERS.isEmpty()) return;
        long now = event.getServer().overworld().getGameTime();
        for (var it = OWNERS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Owner owner = entry.getValue();
            if (now - owner.riseTick <= LIVE_TICKS) continue;
            if (owner.pendingClear) {
                for (ServerLevel level : event.getServer().getAllLevels())
                    if (level.getEntity(entry.getKey()) instanceof LivingEntity living) { clearNow(living); break; }
                it.remove();
            } else if (now - owner.riseTick > 20 * 60) {
                it.remove(); // stale bookkeeping only; the entity's own state is untouched
            }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) { OWNERS.clear(); }
}
