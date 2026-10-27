package net.bullettrain.xenopixelsmod.space.pod;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.v3.V3Resources;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceTickScheduler;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodRiderBinding;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferTransaction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/** Server-authoritative two-tier P15 pod cruise/boost, folded into the shared space physics pass. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SpacePodFlightPass implements SpaceTickScheduler.Pass {
    private static final SpacePodFlightPass INSTANCE = new SpacePodFlightPass();
    private static final int BOOST_INPUT_LIFETIME_TICKS = 8;
    private static final Map<UUID, FlightState> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> BOOST_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> BOOST_NEXT_PAYMENT_TICK = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> BOOST_BLOCKED_UNTIL_RELEASE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> LAST_BOOST_PACKET_TICK = new ConcurrentHashMap<>();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private static final BiPredicate<ServerPlayer, Float> PRODUCTION_KI_SPENDER =
            (player, cost) -> V3Resources.spend(player, cost, 0.0f);
    private static volatile BiPredicate<ServerPlayer, Float> kiSpender = PRODUCTION_KI_SPENDER;

    private record FlightState(ServerLevel level, ServerSubLevel body, UUID riderId,
                               double dx, double dy, double dz, boolean boost, long refreshedTick) {}
    private static final class Scratch {
        final Vector3d velocity = new Vector3d();
        final Vector3d direction = new Vector3d();
        final Vector3d acceleration = new Vector3d();
        final Vector3d impulse = new Vector3d();
    }

    private SpacePodFlightPass() {}

    @SubscribeEvent
    public static void starting(ServerStartingEvent event) {
        SpaceTickScheduler.register(INSTANCE);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        BOOST_UNTIL.clear();
        BOOST_NEXT_PAYMENT_TICK.clear();
        BOOST_BLOCKED_UNTIL_RELEASE.clear();
        LAST_BOOST_PACKET_TICK.clear();
        SpaceTickScheduler.unregister(INSTANCE);
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BOOST_UNTIL.remove(player.getUUID());
        BOOST_NEXT_PAYMENT_TICK.remove(player.getUUID());
        BOOST_BLOCKED_UNTIL_RELEASE.remove(player.getUUID());
        LAST_BOOST_PACKET_TICK.remove(player.getUUID());
        ACTIVE.entrySet().removeIf(entry -> entry.getValue().riderId().equals(player.getUUID()));
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        refreshPilots(event.getServer());
    }

    /** Called by the C2S packet; a boost request is accepted only for the sender's currently tracked pod. */
    public static void setBoost(ServerPlayer player, boolean boost) {
        UUID riderId = player.getUUID();
        int tick = player.server.getTickCount();
        if (!boost) {
            boolean wasBoosting = BOOST_UNTIL.getOrDefault(riderId, Long.MIN_VALUE) >= tick;
            LAST_BOOST_PACKET_TICK.put(riderId, tick);
            BOOST_UNTIL.remove(riderId);
            BOOST_NEXT_PAYMENT_TICK.remove(riderId);
            BOOST_BLOCKED_UNTIL_RELEASE.remove(riderId);
            if (wasBoosting) XenoPixelsMod.LOGGER.info("Space pod boost released: player={}",
                    player.getScoreboardName());
            return;
        }
        Integer previous = LAST_BOOST_PACKET_TICK.get(riderId);
        if (previous != null && tick >= previous && (long) tick - previous < 2) return;
        LAST_BOOST_PACKET_TICK.put(riderId, tick);
        if (!XenoSpaceConfig.enabled() || !XenoSpaceConfig.spacePodsEnabled()) return;
        if (BOOST_BLOCKED_UNTIL_RELEASE.contains(riderId)) return;
        ServerSubLevel body = PodRiderBinding.trackedBody(player);
        UUID podId = PodTransferTransaction.podId(body);
        if (podId == null) return;
        boolean wasBoosting = BOOST_UNTIL.getOrDefault(riderId, Long.MIN_VALUE) >= tick;
        BOOST_UNTIL.put(riderId, (long) tick + BOOST_INPUT_LIFETIME_TICKS);
        if (!wasBoosting) XenoPixelsMod.LOGGER.info("Space pod boost accepted: player={} pod={}",
                player.getScoreboardName(), podId);
    }

    private static void refreshPilots(MinecraftServer server) {
        long tick = server.getTickCount();
        BOOST_UNTIL.entrySet().removeIf(entry -> entry.getValue() < tick);
        LAST_BOOST_PACKET_TICK.entrySet().removeIf(entry -> tick >= entry.getValue()
                && tick - entry.getValue() > 200);
        if (!XenoSpaceConfig.enabled() || !XenoSpaceConfig.spacePodsEnabled()) {
            ACTIVE.clear();
            BOOST_UNTIL.clear();
            BOOST_NEXT_PAYMENT_TICK.clear();
            BOOST_BLOCKED_UNTIL_RELEASE.clear();
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) refreshPilot(player, tick);
        ACTIVE.entrySet().removeIf(entry -> tick - entry.getValue().refreshedTick() > 2);
    }

    static void refreshPilot(ServerPlayer player, long tick) {
        if (player == null || !XenoSpaceConfig.enabled() || !XenoSpaceConfig.spacePodsEnabled()) return;
        ServerSubLevel body = PodRiderBinding.trackedBody(player);
        if (PodTransferTransaction.podId(body) == null) return;
        UUID bodyId = body.getUniqueId();
        FlightState alreadyRefreshed = ACTIVE.get(bodyId);
        if (alreadyRefreshed != null && alreadyRefreshed.refreshedTick() == tick) return;
        var look = player.getLookAngle();
        if (!Double.isFinite(look.x) || !Double.isFinite(look.y) || !Double.isFinite(look.z)
                || look.lengthSqr() < 1.0e-12) return;
        boolean boost = BOOST_UNTIL.getOrDefault(player.getUUID(), Long.MIN_VALUE) >= tick;
        if (boost && !payBoostKiIfDue(player, tick)) {
            BOOST_UNTIL.remove(player.getUUID());
            BOOST_BLOCKED_UNTIL_RELEASE.add(player.getUUID());
            boost = false;
        }
        // Refresh a pod only once when more than one player is attached; its first tracked rider is pilot.
        ACTIVE.put(bodyId, new FlightState(player.serverLevel(), body, player.getUUID(),
                look.x, look.y, look.z, boost, tick));
    }

    private static boolean payBoostKiIfDue(ServerPlayer player, long tick) {
        double configured = XenoSpaceConfig.podBoostKiPerSecond();
        if (!Double.isFinite(configured) || configured <= 0.0) return true;
        if (!SpacePodBoostPaymentPolicy.due(tick,
                BOOST_NEXT_PAYMENT_TICK.getOrDefault(player.getUUID(), Long.MIN_VALUE))) return true;
        float cost = (float) configured;
        if (!Float.isFinite(cost) || cost <= 0.0f || !kiSpender.test(player, cost)) return false;
        BOOST_NEXT_PAYMENT_TICK.put(player.getUUID(), SpacePodBoostPaymentPolicy.nextPaymentTick(tick));
        return true;
    }

    static void clearForTest() {
        ACTIVE.clear();
        BOOST_UNTIL.clear();
        BOOST_NEXT_PAYMENT_TICK.clear();
        BOOST_BLOCKED_UNTIL_RELEASE.clear();
        LAST_BOOST_PACKET_TICK.clear();
        kiSpender = PRODUCTION_KI_SPENDER;
    }

    static void setKiSpenderForTest(BiPredicate<ServerPlayer, Float> testSpender) {
        kiSpender = testSpender == null ? PRODUCTION_KI_SPENDER : testSpender;
    }

    static boolean boostActiveForTest(UUID riderId) {
        return ACTIVE.values().stream().anyMatch(state -> state.riderId().equals(riderId) && state.boost());
    }

    @Override public String name() { return "pod-flight"; }

    @Override
    public boolean appliesTo(SubLevelPhysicsSystem system) {
        if (!XenoSpaceConfig.enabled() || !XenoSpaceConfig.spacePodsEnabled()) return false;
        ServerLevel level = system.getLevel();
        if (level == null) return false;
        for (FlightState state : ACTIVE.values()) if (state.level() == level) return true;
        return false;
    }

    @Override
    public int run(SubLevelPhysicsSystem system, double deltaSeconds) {
        if (!XenoSpaceConfig.enabled() || !XenoSpaceConfig.spacePodsEnabled()) return 0;
        double step = Math.max(0.0, Math.min(deltaSeconds, 0.1));
        if (step <= 0.0) return 0;
        ServerLevel level = system.getLevel();
        if (level == null) return 0;
        double response = XenoSpaceConfig.podFlightResponsePerSecond();
        Scratch scratch = SCRATCH.get();
        int applied = 0;
        for (FlightState state : ACTIVE.values()) {
            if (state.level() != level || state.body().isRemoved()) continue;
            RigidBodyHandle handle = system.getPhysicsHandle(state.body());
            if (handle == null || !handle.isValid()) continue;
            double targetSpeed = state.boost() ? XenoSpaceConfig.podBoostSpeed()
                    : XenoSpaceConfig.podCruiseSpeed();
            double maxAcceleration = state.boost() ? XenoSpaceConfig.podBoostAcceleration()
                    : XenoSpaceConfig.podCruiseAcceleration();
            scratch.velocity.set(handle.getLinearVelocity(scratch.velocity));
            scratch.direction.set(state.dx(), state.dy(), state.dz());
            SpacePodFlightPolicy.acceleration(scratch.velocity, scratch.direction, targetSpeed,
                    maxAcceleration, response, scratch.acceleration);
            if (scratch.acceleration.lengthSquared() < 1.0e-12) continue;

            // The scheduler contract and Sable 2.0.3 impulse call both require body-space impulse.
            scratch.impulse.set(scratch.acceleration);
            state.body().logicalPose().orientation().transformInverse(scratch.impulse);
            double mass = state.body().getMassTracker().getMass();
            if (!Double.isFinite(mass) || mass <= 0.0) mass = 1.0;
            scratch.impulse.mul(mass * step);
            if (!scratch.impulse.isFinite()) continue;
            handle.applyLinearImpulse(scratch.impulse);
            applied++;
        }
        return applied;
    }
}
