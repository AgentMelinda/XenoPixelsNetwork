package net.bullettrain.xenopixelsmod.space.pod;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodRiderBinding;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferTransaction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-owned Classic Egg launch/build/bind path. The only active build advances on the shared body budget. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SpacePodLifecycle {
    public enum Request { STARTED, SPACE_DISABLED, PODS_DISABLED, ALREADY_IN_POD, BUILD_PENDING }
    private record Pending(UUID podId, UUID playerId, ServerPlayer player, ServerLevel level, Vector3d start,
                           ClassicEggPodHull.BuildJob job) {}

    private static final SpaceBuildWorkBudget.Owner OWNER = SpaceBuildWorkBudget.Owner.SPACE_POD;
    private static final Map<MinecraftServer, Map<UUID, Pending>> PENDING = new WeakHashMap<>();
    private static final Map<MinecraftServer, Long> LAST_TICK = new WeakHashMap<>();

    private SpacePodLifecycle() {}

    /** Starts an incremental hull build; no world blocks are staged and no physics body is built in one call. */
    public static Request requestLaunch(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!XenoSpaceConfig.enabled()) return Request.SPACE_DISABLED;
        if (!XenoSpaceConfig.spacePodsEnabled()) return Request.PODS_DISABLED;
        return queueLaunch(player);
    }

    /** Enqueues a launch after policy checks; package-visible so GameTests can test the build without enabling all space systems. */
    static Request queueLaunch(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (PodTransferTransaction.podId(PodRiderBinding.trackedBody(player)) != null)
            return Request.ALREADY_IN_POD;
        Map<UUID, Pending> builds = PENDING.computeIfAbsent(server, ignored -> new LinkedHashMap<>());
        if (builds.containsKey(player.getUUID())) return Request.BUILD_PENDING;

        UUID podId = UUID.randomUUID();
        Vector3d start = new Vector3d(player.getX(), player.getY(), player.getZ());
        ClassicEggPodHull.BuildJob job = ClassicEggPodHull.start(player.serverLevel(), start, podId);
        builds.put(player.getUUID(), new Pending(podId, player.getUUID(), player, player.serverLevel(), start, job));
        player.displayClientMessage(Component.literal("Classic Egg pod assembly queued"), true);
        return Request.STARTED;
    }

    static boolean pendingLaunch(MinecraftServer server, UUID playerId) {
        Map<UUID, Pending> builds = PENDING.get(server);
        return builds != null && builds.containsKey(playerId);
    }

    static String pendingStatus(MinecraftServer server, UUID playerId) {
        Map<UUID, Pending> builds = PENDING.get(server);
        Pending pending = builds == null ? null : builds.get(playerId);
        if (pending == null) return "none";
        var job = pending.job();
        var work = job.workJob();
        return "tick=" + server.getTickCount() + ",fillReady=" + job.fillReady()
                + ",prepared=" + (work.maxColumnVoxels() > 0) + ",body=" + (job.body() != null)
                + ",done=" + job.done() + ",failed=" + job.failed()
                + ",installed=" + job.installed() + ",total=" + work.total()
                + ",budgetVoxels=" + XenoSpaceConfig.generatedBodyBuildVoxelsPerTick()
                + ",budgetMs=" + XenoSpaceConfig.generatedBodyBuildMillisPerTick()
                + ",ownerUsed=" + SpaceBuildWorkBudget.used(server, server.getTickCount(), OWNER)
                + ",ownerMs=" + String.format(java.util.Locale.ROOT, "%.3f",
                SpaceBuildWorkBudget.usedMillis(server, server.getTickCount(), OWNER));
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancelPending(player);
    }

    /** Package-visible for dedicated GameTests; repeated calls in one server tick are ignored. */
    static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (LAST_TICK.getOrDefault(server, Long.MIN_VALUE) == tick) return;
        LAST_TICK.put(server, tick);
        Map<UUID, Pending> builds = PENDING.get(server);
        if (builds == null || builds.isEmpty()) return;

        Iterator<Map.Entry<UUID, Pending>> iterator = builds.entrySet().iterator();
        if (!iterator.hasNext()) return;
        Pending pending = iterator.next().getValue();
        ServerPlayer player = pending.player();
        if (player.isRemoved() || player.serverLevel() != pending.level()
                || player.position().distanceToSqr(pending.start().x, pending.start().y, pending.start().z)
                > Math.pow(ClassicEggPodHull.EXTENT + 2.0, 2.0)) {
            pending.job().cancel();
            iterator.remove();
            if (player != null) player.displayClientMessage(
                    Component.literal("Classic Egg pod assembly cancelled: stay near the launch point"), true);
            return;
        }

        try {
            int localLimit = Math.max(1, XenoSpaceConfig.generatedBodyBuildVoxelsPerTick());
            if (pending.job().workJob().body() == null) {
                if (!SpaceBuildWorkBudget.mayPrepare(server, tick, OWNER, localLimit)) return;
                if (!SpaceBuildWorkBudget.prepare(server, tick, OWNER, pending.job().workJob())) {
                    if (!pending.job().failed()) return;
                    throw new IllegalStateException("Sable could not allocate a pod plot");
                }
            }

            int allowance = SpaceBuildWorkBudget.allowance(server, tick, OWNER,
                    pending.job().workJob(), localLimit);
            if (allowance <= 0) return;
            double millis = SpaceBuildWorkBudget.remainingMillis(server, tick, OWNER);
            SpaceBuildWorkBudget.step(server, tick, OWNER, pending.job().workJob(), allowance, millis);
            if (!pending.job().done()) return;

            var body = pending.job().body();
            if (body == null || body.isRemoved()) throw new IllegalStateException("the built pod body was removed");
            if (!PodRiderBinding.restore(player, body, PodRiderBinding.capture(player, body)))
                throw new IllegalStateException("Sable did not bind the launching player to the new pod");
            pending.job().releaseHandoffTicket();
            player.displayClientMessage(Component.literal("Classic Egg pod ready — you are aboard"), true);
            XenoPixelsMod.LOGGER.info("Classic Egg pod launched: player={} pod={} dimension={} centre={} voxels={}",
                    player.getScoreboardName(), pending.podId(), pending.level().dimension().location(),
                    body.logicalPose().position(), pending.job().installed());
            iterator.remove();
        } catch (RuntimeException | Error failure) {
            pending.job().cancel();
            iterator.remove();
            player.displayClientMessage(Component.literal("Classic Egg pod assembly failed; see server log"), true);
            XenoPixelsMod.LOGGER.error("Classic Egg pod assembly failed: player={} pod={} dimension={}",
                    player.getScoreboardName(), pending.podId(), pending.level().dimension().location(), failure);
        }
    }

    static void cancelPending(ServerPlayer player) {
        Map<UUID, Pending> builds = PENDING.get(player.server);
        Pending pending = builds == null ? null : builds.remove(player.getUUID());
        if (pending != null) pending.job().cancel();
        if (builds != null && builds.isEmpty()) PENDING.remove(player.server);
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        MinecraftServer server = event.getServer();
        Map<UUID, Pending> builds = PENDING.remove(server);
        if (builds != null) for (Pending pending : builds.values()) pending.job().cancel();
        LAST_TICK.remove(server);
        SpaceBuildWorkBudget.forget(server);
    }
}
