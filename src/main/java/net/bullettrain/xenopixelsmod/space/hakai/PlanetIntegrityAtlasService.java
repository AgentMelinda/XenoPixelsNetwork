package net.bullettrain.xenopixelsmod.space.hakai;

import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.WeakHashMap;

/** Serves a bounded, rate-limited Atlas position view for modified chart bodies. */
@EventBusSubscriber(modid = "xenopixelsmod")
public final class PlanetIntegrityAtlasService {
    private static final int REQUEST_INTERVAL_TICKS = 40;
    private static final Map<MinecraftServer, Runtime> RUNTIMES = new WeakHashMap<>();
    private static final Comparator<Damage> DAMAGE_ORDER = Comparator.comparingDouble(Damage::integrity)
            .thenComparing(Damage::id);

    private PlanetIntegrityAtlasService() {}

    public static void request(ServerPlayer player, PlanetIntegrityAtlasRequest request) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Runtime runtime = RUNTIMES.computeIfAbsent(server, ignored -> new Runtime());
        long now = server.getTickCount();
        long next = runtime.nextRequestTick.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now < next) return;
        runtime.nextRequestTick.put(player.getUUID(), now > Long.MAX_VALUE - REQUEST_INTERVAL_TICKS
                ? Long.MAX_VALUE : now + REQUEST_INTERVAL_TICKS);

        ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
        PlanetIntegritySavedData integrity = PlanetIntegritySavedData.get(server);
        ErasedBodiesSavedData erased = ErasedBodiesSavedData.get(server);
        Cache cache = runtime.cache;
        if (cache == null || cache.integrityRevision != integrity.mutationRevision()
                || cache.erasedRevision != erased.mutationRevision() || cache.chartVersion != chart.version()) {
            cache = Cache.build(integrity, erased, chart);
            runtime.cache = cache;
        }
        AddonNetwork.sendToPlayer(player, cache.snapshot(request.requestId(), erased, chart));
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        MinecraftServer server = event.getEntity().getServer();
        Runtime runtime = server == null ? null : RUNTIMES.get(server);
        if (runtime != null) runtime.nextRequestTick.remove(id);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        RUNTIMES.remove(event.getServer());
    }

    private record Damage(String id, double integrity) {}

    private static final class Runtime {
        private final Map<UUID, Long> nextRequestTick = new HashMap<>();
        private Cache cache;
    }

    /** Package-visible so bounded selection and its displayed totals can be tested without a live server. */
    static final class Cache {
        private final long integrityRevision;
        private final long erasedRevision;
        private final int chartVersion;
        private final List<String> erasedIds;
        private final List<Damage> damaged;
        private final int totalEntries;
        private final int unmappedErased;

        private Cache(long integrityRevision, long erasedRevision, int chartVersion, List<String> erasedIds,
                      List<Damage> damaged, int totalEntries, int unmappedErased) {
            this.integrityRevision = integrityRevision;
            this.erasedRevision = erasedRevision;
            this.chartVersion = chartVersion;
            this.erasedIds = List.copyOf(erasedIds);
            this.damaged = List.copyOf(damaged);
            this.totalEntries = totalEntries;
            this.unmappedErased = unmappedErased;
        }

        static Cache build(PlanetIntegritySavedData integrity, ErasedBodiesSavedData erased,
                           ChartRegistry chart) {
            var erasedRecords = erased.erased();
            var erasedIds = new HashSet<>(erasedRecords.keySet());
            var locatedErased = new ArrayList<Map.Entry<String, ErasedBodiesSavedData.Entry>>();
            int unmapped = 0;
            for (var entry : erasedRecords.entrySet()) {
                if (entry.getValue().hasPosition()) locatedErased.add(entry);
                else unmapped++;
            }
            locatedErased.sort(Comparator.<Map.Entry<String, ErasedBodiesSavedData.Entry>>
                    comparingLong(entry -> entry.getValue().erasedAtMillis()).reversed()
                    .thenComparing(Map.Entry::getKey));
            int maximum = PlanetIntegrityAtlasSnapshot.MAX_ENTRIES;
            var worstFirst = new PriorityQueue<Damage>(maximum, DAMAGE_ORDER.reversed());
            int damagedTotal = 0;
            for (var entry : integrity.entries().entrySet()) {
                if (erasedIds.contains(entry.getKey())) continue;
                double value = entry.getValue();
                int slot = chart.slotOf(entry.getKey());
                if (value >= 100 || slot < 0 || !validCoordinate(chart.x(slot)) || !validCoordinate(chart.z(slot))) continue;
                damagedTotal++;
                Damage candidate = new Damage(entry.getKey(), value);
                if (worstFirst.size() < maximum) worstFirst.add(candidate);
                else if (DAMAGE_ORDER.compare(candidate, worstFirst.peek()) < 0) {
                    worstFirst.remove();
                    worstFirst.add(candidate);
                }
            }

            var limits = PlanetIntegrityAtlasMarkerBudget.allocate(locatedErased.size(), damagedTotal);
            var selectedErased = new ArrayList<String>(limits.erased());
            for (int i = 0; i < limits.erased(); i++) selectedErased.add(locatedErased.get(i).getKey());
            var selectedDamage = new ArrayList<>(worstFirst);
            selectedDamage.sort(DAMAGE_ORDER);
            if (selectedDamage.size() > limits.damaged())
                selectedDamage.subList(limits.damaged(), selectedDamage.size()).clear();
            return new Cache(integrity.mutationRevision(), erased.mutationRevision(), chart.version(),
                    selectedErased, selectedDamage, locatedErased.size() + damagedTotal, unmapped);
        }

        PlanetIntegrityAtlasSnapshot snapshot(long requestId, ErasedBodiesSavedData erased,
                                              ChartRegistry chart) {
            var markers = new ArrayList<PlanetIntegrityAtlasSnapshot.Entry>(
                    Math.min(PlanetIntegrityAtlasSnapshot.MAX_ENTRIES, totalEntries));
            var erasedRecords = erased.erased();
            for (int i = 0; i < erasedIds.size() && markers.size() < PlanetIntegrityAtlasSnapshot.MAX_ENTRIES; i++) {
                String id = erasedIds.get(i);
                var saved = erasedRecords.get(id);
                if (saved != null && saved.hasPosition()) markers.add(new PlanetIntegrityAtlasSnapshot.Entry(
                        id, saved.x(), saved.z(), 0.0, true));
            }
            for (Damage damage : damaged) {
                if (markers.size() >= PlanetIntegrityAtlasSnapshot.MAX_ENTRIES) break;
                int slot = chart.slotOf(damage.id());
                if (slot < 0) continue;
                markers.add(new PlanetIntegrityAtlasSnapshot.Entry(damage.id(), chart.x(slot), chart.z(slot),
                        damage.integrity(), false));
            }
            return new PlanetIntegrityAtlasSnapshot(requestId, totalEntries, unmappedErased, markers);
        }
    }

    private static boolean validCoordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 30_000_000.0;
    }
}
