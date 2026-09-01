package net.bullettrain.xenopixelsmod.combat.targeting;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.TargetLockStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one place a seated pilot's target lock is created, progressed, broken and synced.
 *
 * <p>A client only ever asks to lock an entity id; every rule about whether that succeeds lives
 * in {@link LockOnValidator}, and it is re-applied every server tick against a held lock exactly
 * as it was against the original request — a lock cannot survive under conditions a fresh
 * request would be refused for, only ride out the configured grace window.
 *
 * <p>All state here is per-locker and keyed by their UUID; nothing is broadcast. Bounded by the
 * number of players who currently hold a lock, and cleaned up on logout, death and seat exit so a
 * disconnected or dismounted pilot's entry does not linger.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class TargetLockManager {

    private static final Map<UUID, LockOnState> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_REQUEST_TICK = new HashMap<>();
    private static final Map<UUID, Integer> LAST_CYCLE_TICK = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWN_UNTIL_TICK = new HashMap<>();
    /** Ceiling before the rate-limit bookkeeping maps prune stale entries. */
    private static final int RATE_MAP_PRUNE_SIZE = 256;
    private static final int RATE_MAP_PRUNE_AGE_TICKS = 1200;
    /**
     * Longest a held lock may go without any packet at all.
     *
     * <p>{@link net.bullettrain.xenopixelsmod.client.combat.ClientLockState} treats a snapshot
     * older than two seconds as no data, so a purely change-driven sync had a hole in it: once
     * progress saturates at 100% with the same target and the same quality, nothing changes
     * again and the client stops hearing about a lock the server is still holding. The brackets
     * and lead marker simply vanished after two seconds of a perfectly good hard lock. One
     * keepalive per second is well inside that window and costs about twenty bytes.
     */
    private static final int RESYNC_INTERVAL_TICKS = 20;
    /** Floor on velocity-driven resends, so a jinking target cannot become a packet stream. */
    private static final int MIN_SYNC_INTERVAL_TICKS = 2;
    /**
     * Velocity change, in blocks per second, worth re-sending for the lead marker.
     *
     * <p>Velocity rides on the same packet but took no part in deciding whether to send it, so
     * the client's lead solution was frozen at whatever the target's velocity happened to be the
     * last time progress crossed a two-percent step — for a hard lock, permanently. The marker
     * pointed confidently at where the target was going a second ago.
     */
    private static final double VELOCITY_RESYNC_EPSILON = 0.75;

    private TargetLockManager() {
    }

    /** Client asked to lock a specific entity id. Silently ignored if ineligible or rate-limited. */
    public static void requestLock(ServerPlayer locker, int targetEntityId) {
        if (rateLimited(locker, LAST_REQUEST_TICK, LockOnConfig.requestRateLimitTicks)) return;
        if (onCooldown(locker)) return;

        Entity target = resolveEntity(locker, targetEntityId);
        LockOnValidator.Reason reason = LockOnValidator.checkEligible(locker, target);
        if (reason != LockOnValidator.Reason.OK) {
            ModNetwork.sendToPlayer(locker, new net.bullettrain.xenopixelsmod.network.packet.TargetLockErrorPacket(reason));
            return;
        }

        LockOnState state = new LockOnState(targetEntityId);
        STATES.put(locker.getUUID(), state);
        sync(locker, state, target);
    }

    /**
     * The pilot let a lock go on purpose — pressed clear, or stood up.
     *
     * <p>No re-acquire cooldown. That cooldown exists to stop a target being re-grabbed the
     * instant it breaks a lock by manoeuvring; punishing the pilot for their own deliberate
     * release just made the clear key feel broken, and made stepping out of the seat and back
     * in cost a second of dead controls.
     */
    public static void clearLock(ServerPlayer locker, String reason) {
        if (STATES.remove(locker.getUUID()) == null) return;
        ModNetwork.sendToPlayer(locker, TargetLockStatePacket.none());
    }

    /** The lock was taken away — conditions failed, the pilot died. Costs a re-acquire cooldown. */
    private static void breakLock(ServerPlayer locker, String reason) {
        if (STATES.remove(locker.getUUID()) == null) return;
        markCooldown(locker);
        ModNetwork.sendToPlayer(locker, TargetLockStatePacket.none());
    }

    /** Client asked to switch to the next nearest eligible target, replacing any current lock. */
    public static void cycleTarget(ServerPlayer locker) {
        if (rateLimited(locker, LAST_CYCLE_TICK, LockOnConfig.cycleRateLimitTicks)) return;
        LockOnState current = STATES.get(locker.getUUID());
        int exclude = current == null ? -1 : current.targetEntityId;
        ServerPlayer best = findNearestEligible(locker, exclude);
        if (best == null) return;
        LockOnState state = new LockOnState(best.getId());
        STATES.put(locker.getUUID(), state);
        sync(locker, state, best);
    }

    /** The seat calls this on dismount — leaving the cockpit must not leave a stale lock. */
    public static void clearForSeatExit(ServerPlayer locker) {
        clearLock(locker, "left seat");
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player.getUUID());
    }

    /**
     * Every map here is static, so in single player they outlive the world: leaving to the title
     * screen and opening a different save would carry the previous session's locks and cooldowns
     * across, keyed by a UUID that is still the same player.
     */
    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        STATES.clear();
        LAST_REQUEST_TICK.clear();
        LAST_CYCLE_TICK.clear();
        COOLDOWN_UNTIL_TICK.clear();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) breakLock(player, "died");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (STATES.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        double progressPerTick = 0.05 / Math.max(0.05, LockOnConfig.lockTimeSeconds);
        int grace = graceTicks();

        Iterator<Map.Entry<UUID, LockOnState>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LockOnState> entry = it.next();
            ServerPlayer locker = server.getPlayerList().getPlayer(entry.getKey());
            if (locker == null) {
                it.remove();
                continue;
            }
            LockOnState state = entry.getValue();
            Entity target = resolveEntity(locker, state.targetEntityId);
            LockOnValidator.Reason reason = LockOnValidator.checkStillValid(locker, target);

            if (reason == LockOnValidator.Reason.OK) {
                state.graceTicksLeft = grace;
                state.progress = Math.min(1.0, state.progress + progressPerTick);
            } else {
                state.graceTicksLeft--;
                if (state.graceTicksLeft <= 0) {
                    it.remove();
                    markCooldown(locker);
                    ModNetwork.sendToPlayer(locker, TargetLockStatePacket.none());
                    continue;
                }
                // Held in grace: progress neither advances nor resets, so a lock that recovers
                // before the grace window closes resumes exactly where it left off.
            }
            sync(locker, state, target);
        }
    }

    private static int graceTicks() {
        return Math.max(0, (int) Math.round(LockOnConfig.graceSeconds * 20.0));
    }

    /**
     * Push the client sync when something the HUD draws actually changed — and, regardless,
     * often enough that the client never decides a live lock has gone stale.
     *
     * <p>Three things can trigger a send: a structural change (different target, different
     * quality, a two-percent step of progress, lead assist turning on or off), the target's
     * velocity drifting far enough to move the lead marker, or the keepalive falling due. The
     * velocity path carries its own minimum spacing so a violently manoeuvring target produces
     * at most ten updates a second rather than twenty.
     */
    private static void sync(ServerPlayer locker, LockOnState state, @Nullable Entity target) {
        state.ticksSinceSync++;
        LockOnQuality quality = state.quality();
        int progressPercent = (int) Math.round(state.progress * 100.0);

        // Velocity only rides along once a lock is fully hard — a weak or soft lock has no lead
        // assist yet, so there is nothing for the client to do with it.
        boolean sendVelocity = LockOnConfig.sendVelocityForLead
                && quality == LockOnQuality.HARD && target != null;
        Vec3 velocity = sendVelocity
                ? TargetMotionEstimator.velocityOf(locker.serverLevel(), target) : Vec3.ZERO;

        boolean structural = state.lastSyncedTargetId != state.targetEntityId
                || state.lastSyncedQuality != quality
                || Math.abs(state.lastSyncedProgressPercent - progressPercent) >= 2
                || state.lastSyncedHadVelocity != sendVelocity;
        boolean velocityDrifted = sendVelocity
                && velocity.distanceToSqr(state.lastSyncedVelocity)
                        > VELOCITY_RESYNC_EPSILON * VELOCITY_RESYNC_EPSILON
                && state.ticksSinceSync >= MIN_SYNC_INTERVAL_TICKS;
        boolean keepalive = state.ticksSinceSync >= RESYNC_INTERVAL_TICKS;
        if (!structural && !velocityDrifted && !keepalive) return;

        state.lastSyncedTargetId = state.targetEntityId;
        state.lastSyncedQuality = quality;
        state.lastSyncedProgressPercent = progressPercent;
        state.lastSyncedHadVelocity = sendVelocity;
        state.lastSyncedVelocity = velocity;
        state.ticksSinceSync = 0;

        if (sendVelocity) {
            ModNetwork.sendToPlayer(locker, new TargetLockStatePacket(state.targetEntityId, quality,
                    progressPercent, true, velocity.x, velocity.y, velocity.z));
        } else {
            ModNetwork.sendToPlayer(locker,
                    new TargetLockStatePacket(state.targetEntityId, quality, progressPercent));
        }
    }

    private static @Nullable Entity resolveEntity(ServerPlayer locker, int entityId) {
        return entityId < 0 ? null : locker.serverLevel().getEntity(entityId);
    }

    /**
     * Nearest eligible player, bounded by the online player list rather than a spatial query —
     * on any real server that list is already small, and this only runs on an explicit cycle
     * request, never every tick.
     */
    private static @Nullable ServerPlayer findNearestEligible(ServerPlayer locker, int excludeEntityId) {
        ServerPlayer best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (ServerPlayer candidate : locker.getServer().getPlayerList().getPlayers()) {
            if (candidate.getId() == excludeEntityId) continue;
            if (candidate.level() != locker.level()) continue;
            if (LockOnValidator.checkEligible(locker, candidate) != LockOnValidator.Reason.OK) continue;
            double distSqr = locker.distanceToSqr(candidate);
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean rateLimited(ServerPlayer locker, Map<UUID, Integer> lastTick, int minIntervalTicks) {
        int now = locker.getServer().getTickCount();
        Integer previous = lastTick.put(locker.getUUID(), now);
        if (lastTick.size() > RATE_MAP_PRUNE_SIZE) {
            lastTick.entrySet().removeIf(e -> now - e.getValue() > RATE_MAP_PRUNE_AGE_TICKS);
        }
        return previous != null && now - previous < minIntervalTicks;
    }

    private static boolean onCooldown(ServerPlayer locker) {
        Integer until = COOLDOWN_UNTIL_TICK.get(locker.getUUID());
        return until != null && locker.getServer().getTickCount() < until;
    }

    private static void markCooldown(ServerPlayer locker) {
        int now = locker.getServer().getTickCount();
        COOLDOWN_UNTIL_TICK.put(locker.getUUID(), now + LockOnConfig.reacquireCooldownTicks);
        if (COOLDOWN_UNTIL_TICK.size() > RATE_MAP_PRUNE_SIZE) {
            COOLDOWN_UNTIL_TICK.entrySet().removeIf(e -> now > e.getValue());
        }
    }

    private static void forget(UUID lockerId) {
        STATES.remove(lockerId);
        LAST_REQUEST_TICK.remove(lockerId);
        LAST_CYCLE_TICK.remove(lockerId);
        COOLDOWN_UNTIL_TICK.remove(lockerId);
    }
}
