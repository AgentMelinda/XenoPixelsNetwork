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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
        java.util.UUID target;
        long startedTick, revision;
        float last; long riseTick; boolean pendingClear;
        // Sweep timing (HakaiSweepTiming): previous accepted value/tick, and the line last sent to clients.
        float prev = -1f; long prevTick = Long.MIN_VALUE;
        float sentProgress; float sentRate; long sentTick = Long.MIN_VALUE;
        long lastFadeTick = Long.MIN_VALUE;
        float impactX = 0.5f, impactY = 0.5f, impactZ = 0.5f;
        boolean impactCaptured;
        SweepKind originKind;
        java.util.UUID originSource;
    }

    /** Which sweep captured the origin. Cross-kind handover must not move an in-flight ring (Codex Q1, 2026-10-19). */
    public enum SweepKind { CHANNEL, AREA, NPC }
    /** Runtime entity ids are level-local; including the dimension prevents two simultaneous targets cross-wiring. */
    private record OwnerKey(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int entityId) {}
    private static final java.util.Map<OwnerKey, Owner> OWNERS = new java.util.HashMap<>();
    private static long nextRevision;

    private static Owner ownerFor(LivingEntity target) {
        OwnerKey key = ownerKey(target);
        Owner owner = OWNERS.get(key);
        if (owner == null || !target.getUUID().equals(owner.target)) {
            owner = new Owner();
            owner.target = target.getUUID();
            owner.startedTick = target.level().getGameTime();
            OWNERS.put(key, owner);
        }
        return owner;
    }
    private static final long LIVE_TICKS = 3;
    /** Re-anchor the client clock periodically so dedicated-server tick drift cannot accumulate into a visible jump. */
    private static final int TIMING_REFRESH_TICKS = 5;
    /** Match the client's two-tick interpolation window so each sample reaches its target before the next arrives. */
    private static final int FADE_SYNC_INTERVAL_TICKS = 2;

    static boolean fadeSyncDue(long previousTick, long now) {
        return previousTick == Long.MIN_VALUE || now - previousTick >= FADE_SYNC_INTERVAL_TICKS;
    }

    private NpcDissolve() {}

    static OwnerKey ownerKey(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int entityId) {
        return new OwnerKey(dimension, entityId);
    }

    static boolean sameOwnerKey(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> firstDimension,
                                int firstEntityId,
                                net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> secondDimension,
                                int secondEntityId) {
        return ownerKey(firstDimension, firstEntityId).equals(ownerKey(secondDimension, secondEntityId));
    }

    private static OwnerKey ownerKey(LivingEntity target) {
        return ownerKey(target.level().dimension(), target.getId());
    }

    public static int amplifierForProgress(float eraseProgress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, eraseProgress));
        return Math.max(0, Math.min(255, Math.round(clamped * 255.0f)));
    }

    /**
     * Whether a source may anchor this target's dissolve origin. The sweep already erasing the target keeps its
     * point: re-anchoring under an ongoing erase moves the client's dissolve line, which is the same interleaving
     * the live-channel guard in {@link #apply} already refuses for progress. A fresh cast of the same channel
     * (re-press on the same target) is deliberately allowed to re-aim; a different caster, or the same caster
     * crossing between single-target and area, is not. Once nothing has raised the target for
     * {@link #LIVE_TICKS}, any source may capture.
     */
    static boolean originCaptureAllowed(boolean alreadyCaptured, boolean sameSource, long riseTick, long now) {
        return !alreadyCaptured || sameSource || riseTick == Long.MIN_VALUE || now - riseTick > LIVE_TICKS;
    }

    /** The arbitration law, or unconditional capture when {@code hakaiOriginArbitration} is off (pre-0.5.23 behaviour). */
    static boolean originCaptureAllowedWithArbitration(boolean arbitration, boolean alreadyCaptured, boolean sameSource,
                                                       long riseTick, long now) {
        return !arbitration || originCaptureAllowed(alreadyCaptured, sameSource, riseTick, now);
    }

    /**
     * The same live sweep, identified by caster and kind. A target with no stored source is not the same sweep as
     * any request, so the very first capture always goes through {@code !alreadyCaptured} instead.
     */
    static boolean sameSweepSource(java.util.UUID stored, SweepKind storedKind, java.util.UUID request,
                                   SweepKind requestKind) {
        return stored != null && request != null && storedKind == requestKind && stored.equals(request);
    }

    /** Capture the origin for a fresh cast of {@code kind} by {@code source}. */
    public static void setImpactOrigin(LivingEntity target, Vec3 worldImpact, java.util.UUID source, SweepKind kind) {
        if (target == null || worldImpact == null || !(target.level() instanceof ServerLevel level)) return;
        Owner owner = ownerFor(target);
        boolean sameSource = sameSweepSource(owner.originSource, owner.originKind, source, kind);
        if (!originCaptureAllowedWithArbitration(net.bullettrain.xenopixelsmod.config.XenoServerConfig.hakaiOriginArbitration,
                owner.impactCaptured, sameSource, owner.riseTick, level.getGameTime())) return;
        capture(owner, target, worldImpact);
        owner.originSource = source;
        owner.originKind = kind;
    }

    /**
     * Capture this channel's hit point before progress packets begin, unless a live channel already owns it. Used by
     * the progress path, which must never re-anchor an existing origin.
     */
    public static void setImpactOrigin(LivingEntity target, Vec3 worldImpact) {
        if (target == null || worldImpact == null || !(target.level() instanceof ServerLevel level)) return;
        Owner owner = ownerFor(target);
        if (!originCaptureAllowedWithArbitration(net.bullettrain.xenopixelsmod.config.XenoServerConfig.hakaiOriginArbitration,
                owner.impactCaptured, false, owner.riseTick, level.getGameTime())) return;
        capture(owner, target, worldImpact);
    }

    private static void capture(Owner owner, LivingEntity target, Vec3 worldImpact) {
        AABB box = target.getBoundingBox();
        float[] point = net.bullettrain.xenopixelsmod.combat.HakaiImpactGeometry.normalize(
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                worldImpact.x, worldImpact.y, worldImpact.z);
        owner.impactX = point[0]; owner.impactY = point[1]; owner.impactZ = point[2];
        owner.impactCaptured = true;
    }

    public static void apply(LivingEntity target, float eraseProgress) {
        apply(target, eraseProgress, null);
    }

    /** Apply progress with the server-captured crosshair point from the channel start. */
    public static void apply(LivingEntity target, float eraseProgress, Vec3 worldImpact) {
        if (target == null) return;
        if (target.level() instanceof ServerLevel level) {
            long now = level.getGameTime();
            Owner owner = ownerFor(target);
            if (!owner.impactCaptured && worldImpact != null) setImpactOrigin(target, worldImpact);
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
        if (target.level() instanceof ServerLevel level) {
            Owner owner = OWNERS.get(ownerKey(target));
            if (owner == null || fadeSyncDue(owner.lastFadeTick, level.getGameTime())) {
                if (owner != null) owner.lastFadeTick = level.getGameTime();
                target.forceAddEffect(instance, null);
                broadcast(target, new ClientboundUpdateMobEffectPacket(target.getId(), instance, false));
                sendFade(target, amplifier);
            }
        } else {
            target.forceAddEffect(instance, null);
        }
    }

    public static void clear(LivingEntity target) {
        if (target == null) return;
        if (target.level() instanceof ServerLevel level) {
            Owner owner = OWNERS.get(ownerKey(target));
            if (owner != null && !target.getUUID().equals(owner.target)) {
                OWNERS.remove(ownerKey(target), owner);
                owner = null;
            }
            if (owner != null && level.getGameTime() - owner.riseTick <= LIVE_TICKS && owner.last < 0.95f) {
                owner.pendingClear = true; // another channel is still erasing it; clear once that one stops
                return;
            }
            owner = OWNERS.remove(ownerKey(target));
            clearNow(target, owner);
            return;
        }
        clearNow(target, null);
    }

    /** The progress slope per server tick measured over the real gap since the last accepted write. */
    static float measuredRate(float previous, long previousTick, float progress, long now) {
        return previousTick == Long.MIN_VALUE ? 0f : (progress - previous) / Math.max(1L, now - previousTick);
    }

    static boolean timingSyncDue(boolean fresh, float predicted, float progress, float rate, float sentRate,
                                 long sentTick, long now) {
        boolean rateChanged = Math.abs(rate - sentRate) > 0.002f && rate != 0f;
        // The client may still be easing a delayed correction. Send the authoritative endpoint
        // before clear/removal even when the server's last line already predicts completion.
        return fresh || progress >= 1f || Math.abs(predicted - progress) > 0.01f || rateChanged
                || now - sentTick >= TIMING_REFRESH_TICKS;
    }

    /**
     * Server ticks are exact, but an accepted write is not always on the next tick: the live-channel guard in
     * {@link #apply} returns before reaching here, so a restore ramp is recorded with gaps. A new line goes to clients
     * only when the real value leaves the line already sent (first tick, cancel, restore, another source).
     */
    private static void sendTiming(LivingEntity target, Owner owner, float progress, long now) {
        float rate = measuredRate(owner.prev, owner.prevTick, progress, now);
        boolean fresh = owner.sentTick == Long.MIN_VALUE;
        float predicted = fresh ? -1f : net.bullettrain.xenopixelsmod.combat.HakaiSweepTiming.at(owner.sentProgress, owner.sentRate, now - owner.sentTick);
        boolean due = timingSyncDue(fresh, predicted, progress, rate, owner.sentRate, owner.sentTick, now);
        owner.prev = progress;
        owner.prevTick = now;
        if (due) {
            owner.sentProgress = progress; owner.sentRate = rate; owner.sentTick = now;
            owner.revision = ++nextRevision;
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToTrackingAndSelf(target,
                    snapshot(target, owner, progress, rate, now));
        }
    }

    private static void clearNow(LivingEntity target, Owner owner) {
        if (target.level() instanceof ServerLevel) {
            if (owner == null) owner = ownerFor(target);
            owner.revision = ++nextRevision;
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToTrackingAndSelf(target,
                    snapshot(target, owner, 0f, 0f, target.level().getGameTime()));
            OWNERS.remove(ownerKey(target), owner);
        }
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

    private static net.bullettrain.xenopixelsmod.combat.HakaiSweepSync snapshot(
            LivingEntity target, Owner owner, float progress, float rate, long now) {
        return new net.bullettrain.xenopixelsmod.combat.HakaiSweepSync(target.level().dimension().location(),
                target.getUUID(), owner.revision, owner.startedTick, now,
                new net.bullettrain.xenopixelsmod.combat.HakaiSweepTiming(target.getId(), progress, rate,
                        owner.impactX, owner.impactY, owner.impactZ));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void tracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !(event.getTarget() instanceof LivingEntity target)) return;
        Owner owner = OWNERS.get(ownerKey(target));
        if (owner == null || !target.getUUID().equals(owner.target) || owner.sentTick == Long.MIN_VALUE) return;
        // Replay the actual last accepted sample, never restart a late observer at zero.
        owner.revision = ++nextRevision;
        net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player,
                snapshot(target, owner, owner.prev, owner.sentRate, owner.prevTick));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (OWNERS.isEmpty()) return;
        for (var it = OWNERS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Owner owner = entry.getValue();
            ServerLevel level = event.getServer().getLevel(entry.getKey().dimension());
            if (level == null) { it.remove(); continue; }
            long now = level.getGameTime();
            if (now - owner.riseTick <= LIVE_TICKS) continue;
            if (owner.pendingClear) {
                if (level.getEntity(entry.getKey().entityId()) instanceof LivingEntity living
                        && living.getUUID().equals(owner.target)) {
                    owner.revision = ++nextRevision;
                    net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToTrackingAndSelf(living,
                            snapshot(living, owner, 0f, 0f, now));
                    if (living.removeEffect(ModEffects.HAKAI_DISSOLVE))
                        broadcast(living, new ClientboundRemoveMobEffectPacket(living.getId(), ModEffects.HAKAI_DISSOLVE));
                    sendFade(living, 0);
                }
                it.remove();
            } else if (now - owner.riseTick > 20 * 60) {
                it.remove(); // stale bookkeeping only; the entity's own state is untouched
            }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) { OWNERS.clear(); }
}
