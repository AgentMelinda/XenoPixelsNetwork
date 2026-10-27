package net.bullettrain.xenopixelsmod.space.hakai;

import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** Sends integrity snapshots on login and after persisted damage/restoration changes. */
@EventBusSubscriber(modid = "xenopixelsmod")
public final class PlanetIntegritySync {
    private static final Map<MinecraftServer, Long> REVISIONS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Boolean> WARNED_OVERCAP = new WeakHashMap<>();
    private static final Map<MinecraftServer, PlanetIntegritySyncQueue> PENDING = new WeakHashMap<>();

    private PlanetIntegritySync() {}

    public static void broadcast(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        var pages = snapshot(server);
        if (pages == null) {
            clearPending(server);
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) enqueueSnapshot(server, player, pages);
    }

    public static void send(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) return;
        var pages = snapshot(server);
        if (pages == null) {
            PlanetIntegritySyncQueue queue = PENDING.get(server);
            if (queue != null) {
                queue.remove(player.getUUID());
                if (queue.isEmpty()) PENDING.remove(server);
            }
            return;
        }
        enqueueSnapshot(server, player, pages);
    }

    public static void broadcastBody(MinecraftServer server, String bodyId, double integrity) {
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        var update = new PlanetIntegrityUpdate(nextRevision(server), bodyId, integrity,
                XenoSpaceConfig.planetIntegrityScarredAt(), XenoSpaceConfig.planetIntegrityCriticalAt());
        PlanetIntegritySyncQueue queue = PENDING.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var admission = queue == null ? PlanetIntegritySyncQueue.DeltaAdmission.DIRECT
                    : queue.enqueueUpdateIfSnapshotPending(player.getUUID(), update);
            if (admission == PlanetIntegritySyncQueue.DeltaAdmission.DIRECT)
                AddonNetwork.sendToPlayer(player, update);
            else if (admission == PlanetIntegritySyncQueue.DeltaAdmission.SNAPSHOT_REFRESH_REQUIRED)
                send(player); // Rebase from current persisted state instead of growing an unbounded deferred-delta queue.
        }
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        PlanetIntegritySyncQueue queue = PENDING.get(server);
        if (queue != null) {
            queue.remove(player.getUUID());
            if (queue.isEmpty()) PENDING.remove(server);
        }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        PlanetIntegritySyncQueue queue = PENDING.get(server);
        if (queue == null) return;
        for (var delivery : queue.drainServerTick()) {
            ServerPlayer player = server.getPlayerList().getPlayer(delivery.playerId());
            if (player == null) {
                queue.remove(delivery.playerId());
                continue;
            }
            AddonNetwork.sendToPlayer(player, delivery.packet());
        }
        if (queue.isEmpty()) PENDING.remove(server);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        REVISIONS.remove(event.getServer());
        WARNED_OVERCAP.remove(event.getServer());
        PENDING.remove(event.getServer());
    }

    private static java.util.List<PlanetIntegritySnapshot> snapshot(MinecraftServer server) {
        var saved = PlanetIntegritySavedData.get(server);
        try {
            return PlanetIntegritySnapshot.fromSaved(saved, nextRevision(server),
                    XenoSpaceConfig.planetIntegrityScarredAt(), XenoSpaceConfig.planetIntegrityCriticalAt());
        } catch (IllegalStateException refused) {
            if (WARNED_OVERCAP.putIfAbsent(server, Boolean.TRUE) == null)
                XenoPixelsMod.LOGGER.error("Planet-integrity client snapshot refused: {}; saved integrity is preserved, but clients will lack initial integrity visuals until the data is repaired",
                        refused.getMessage());
            return null;
        }
    }

    private static void enqueueSnapshot(MinecraftServer server, ServerPlayer player,
                                        java.util.List<PlanetIntegritySnapshot> pages) {
        pending(server).enqueueSnapshot(player.getUUID(), pages);
    }

    private static PlanetIntegritySyncQueue pending(MinecraftServer server) {
        return PENDING.computeIfAbsent(server, ignored -> new PlanetIntegritySyncQueue());
    }

    private static void clearPending(MinecraftServer server) {
        PENDING.remove(server);
    }

    private static long nextRevision(MinecraftServer server) {
        long previous = REVISIONS.getOrDefault(server, 0L);
        long next = previous == Long.MAX_VALUE ? Long.MAX_VALUE : previous + 1;
        REVISIONS.put(server, next);
        return next;
    }
}
