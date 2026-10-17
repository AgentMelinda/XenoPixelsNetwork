package net.bullettrain.xenopixelsmod.space.universe;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Server-authoritative logical-page work queue. It evaluates at most 512 cell candidates per server tick globally. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class InfiniteUniversePageService {
    public static final int CELLS_PER_TICK = 512;
    public static final int MAX_PENDING_PLAYERS = 16;
    private static final int PLAYER_REQUEST_INTERVAL_TICKS = 2;
    private static final LinkedHashMap<UUID, Pending> PENDING = new LinkedHashMap<>();
    private static final Map<UUID, Long> NEXT_REQUEST_TICK = new java.util.HashMap<>();
    private static int lastCellsProcessed;
    private static long lastProcessingNanos;

    private InfiniteUniversePageService() {}

    public static void request(ServerPlayer player, InfiniteUniversePageRequest request) {
        UUID id = player.getUUID();
        long tick = player.getServer().getTickCount();
        if (tick < NEXT_REQUEST_TICK.getOrDefault(id, Long.MIN_VALUE)) {
            return;
        }
        NEXT_REQUEST_TICK.put(id, tick > Long.MAX_VALUE - PLAYER_REQUEST_INTERVAL_TICKS
                ? Long.MAX_VALUE : tick + PLAYER_REQUEST_INTERVAL_TICKS);
        if (!XenoSpaceConfig.logicalUniverseEnabled()) {
            unavailable(player, request);
            return;
        }
        Pending current = PENDING.get(id);
        if (current != null) {
            // Never restart an in-flight page on every pan. Keep only the newest follow-up viewport so the current
            // bounded job completes fairly even if a client rapidly changes pages.
            current.nextRequest = InfiniteUniversePageQueuePolicy.followUp(current.request(), current.nextRequest, request);
            return;
        }
        if (PENDING.size() >= MAX_PENDING_PLAYERS) {
            unavailable(player, request);
            return;
        }
        long seed = UniverseService.fieldSeed(player.getServer());
        var job = InfiniteUniverseField.occupancyPageJob(seed,
                request.originX(), request.originZ(), request.width(), request.height(),
                revealedCells(player.getServer()));
        // Replacing one player's old viewport request prevents pan-spam from growing the queue.
        PENDING.remove(id);
        PENDING.put(id, new Pending(seed, request, job));
    }

    /**
     * Folds a just-persisted reveal into active page jobs so packet ordering cannot lose a delta
     * that arrives before the corresponding page snapshot.
     */
    public static void markRevealed(long fieldSeed, long x, long z) {
        for (Pending pending : PENDING.values()) {
            if (pending.fieldSeed == fieldSeed) pending.job().markRevealed(x, z);
        }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        long started = System.nanoTime();
        lastCellsProcessed = 0;
        try {
            if (PENDING.isEmpty()) return;
            Iterator<Map.Entry<UUID, Pending>> iterator = PENDING.entrySet().iterator();
            var entry = iterator.next();
            UUID id = entry.getKey();
            Pending pending = entry.getValue();
            var server = event.getServer();
            ServerPlayer current = server.getPlayerList().getPlayer(id);
            if (current == null) {
                iterator.remove();
                return;
            }
            if (!XenoSpaceConfig.logicalUniverseEnabled()) {
                iterator.remove();
                unavailable(current, pending.latestRequest());
                return;
            }
            lastCellsProcessed = pending.job().step(CELLS_PER_TICK);
            if (pending.job().complete()) {
                iterator.remove();
                if (pending.nextRequest != null) {
                    InfiniteUniversePageRequest next = pending.nextRequest;
                    pending.request = next;
                    pending.nextRequest = null;
                    pending.fieldSeed = UniverseService.fieldSeed(server);
                    pending.job = InfiniteUniverseField.occupancyPageJob(pending.fieldSeed,
                            next.originX(), next.originZ(), next.width(), next.height(), revealedCells(server));
                    PENDING.put(id, pending);
                } else {
                    var response = InfiniteUniversePageSnapshot.occupancy(
                            UniverseService.fieldSeed(server), false, pending.job());
                    AddonNetwork.sendToPlayer(current, response);
                }
            } else {
                // Round-robin across players; work remains one bounded slice per game tick.
                iterator.remove();
                PENDING.put(id, pending);
            }
        } finally {
            lastProcessingNanos = System.nanoTime() - started;
        }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        NEXT_REQUEST_TICK.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        PENDING.clear();
        NEXT_REQUEST_TICK.clear();
        lastCellsProcessed = 0;
        lastProcessingNanos = 0;
    }

    public static int pendingPlayers() { return PENDING.size(); }
    public static int lastCellsProcessed() { return lastCellsProcessed; }
    public static long lastProcessingNanos() { return lastProcessingNanos; }

    public static String report() {
        return "logical-page queue pending=" + PENDING.size() + "/" + MAX_PENDING_PLAYERS
                + " cellsLastTick=" + lastCellsProcessed + "/" + CELLS_PER_TICK
                + " lastCost=" + String.format(java.util.Locale.ROOT, "%.3f", lastProcessingNanos / 1_000.0) + "us";
    }

    private static void unavailable(ServerPlayer player, InfiniteUniversePageRequest request) {
        AddonNetwork.sendToPlayer(player, InfiniteUniversePageSnapshot.unavailable(
                UniverseService.fieldSeed(player.getServer()), request));
    }

    /** Reads persisted logical discoveries while the page job visits each candidate under the shared work cap. */
    private static Predicate<InfiniteUniverseField.Cell> revealedCells(MinecraftServer server) {
        ServerLevel level = UniverseService.voidsea(server);
        if (level == null) return null;
        var logical = InfiniteUniverseSavedData.get(level);
        var finite = UniverseSavedData.get(level);
        return cell -> logical.isRevealed(cell)
                || (UniverseField.Cell.inRange(cell.x()) && UniverseField.Cell.inRange(cell.z())
                && finite.isRevealed(new UniverseField.Cell((int) cell.x(), (int) cell.z())));
    }

    private static final class Pending {
        private long fieldSeed;
        private InfiniteUniversePageRequest request;
        private InfiniteUniversePageRequest nextRequest;
        private InfiniteUniverseField.PageJob job;

        private Pending(long fieldSeed, InfiniteUniversePageRequest request, InfiniteUniverseField.PageJob job) {
            this.fieldSeed = fieldSeed;
            this.request = request;
            this.job = job;
        }

        private InfiniteUniversePageRequest request() { return request; }
        private InfiniteUniversePageRequest latestRequest() { return nextRequest == null ? request : nextRequest; }
        private InfiniteUniverseField.PageJob job() { return job; }
    }
}
