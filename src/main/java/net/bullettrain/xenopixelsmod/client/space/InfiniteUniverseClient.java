package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.space.universe.InfiniteUniverseField;
import net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageRequest;
import net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageSnapshot;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.ArrayList;
import java.util.List;

/** Client cache for one bounded logical-universe Atlas viewport. */
@EventBusSubscriber(modid = "xenopixelsmod", value = Dist.CLIENT)
public final class InfiniteUniverseClient {
    public record Known(InfiniteUniverseField.Cell cell, InfiniteUniverseField.StarSystem system, boolean revealed) {}

    private static volatile InfiniteUniversePageSnapshot page;
    private static volatile List<Known> systems = List.of();
    private static ArrayList<Known> building;
    private static int buildCursor;
    private static long requestedOriginX;
    private static long requestedOriginZ;
    private static long lastRequestTick = Long.MIN_VALUE;
    private static volatile long revision;
    private static boolean hasRequest;

    private InfiniteUniverseClient() {}

    /** Requests or re-requests the page containing a logical cell; the server applies a global work budget. */
    public static void requestAt(long cellX, long cellZ, boolean retryUnavailable) {
        var request = InfiniteUniversePageRequest.viewportAt(cellX, cellZ);
        var mc = Minecraft.getInstance();
        if (mc.player == null || !AddonNetwork.isReady()) return;
        long tick = mc.level == null ? 0 : mc.level.getGameTime();
        boolean same = hasRequest && requestedOriginX == request.originX() && requestedOriginZ == request.originZ();
        boolean pageReady = page != null && page.originX() == request.originX() && page.originZ() == request.originZ();
        boolean retryThrottled = InfiniteUniverseRequestThrottle.isActive(tick, lastRequestTick, 100);
        if (same && pageReady && (page.available() || !retryUnavailable || retryThrottled)) return;
        if (same && !pageReady && retryThrottled) return;
        requestedOriginX = request.originX();
        requestedOriginZ = request.originZ();
        lastRequestTick = tick;
        hasRequest = true;
        AddonNetwork.sendToServer(request);
    }

    public static void apply(InfiniteUniversePageSnapshot incoming) {
        if (!hasRequest || incoming.originX() != requestedOriginX || incoming.originZ() != requestedOriginZ
                || incoming.width() != InfiniteUniversePageRequest.VIEWPORT_EDGE
                || incoming.height() != InfiniteUniversePageRequest.VIEWPORT_EDGE) return;
        page = incoming;
        if (!incoming.available()) {
            systems = List.of();
            building = null;
            buildCursor = 0;
            revision++;
            return;
        }
        systems = List.of();
        building = new ArrayList<>();
        buildCursor = 0;
        revision++;
    }

    public static void apply(net.bullettrain.xenopixelsmod.space.universe.InfiniteUniverseCellReveal reveal) {
        var current = page;
        if (current == null || !current.available() || current.fieldSeed() != reveal.fieldSeed()) return;
        var updated = current.withRevealedCell(reveal.x(), reveal.z());
        if (updated == current) return;
        page = updated;
        var next = new ArrayList<Known>(systems.size());
        for (Known known : systems) {
            boolean matches = known.cell().x() == reveal.x() && known.cell().z() == reveal.z();
            next.add(matches ? new Known(known.cell(), known.system(), true) : known);
        }
        systems = List.copyOf(next);
        if (building != null) {
            for (int i = 0; i < building.size(); i++) {
                Known known = building.get(i);
                if (known.cell().x() == reveal.x() && known.cell().z() == reveal.z())
                    building.set(i, new Known(known.cell(), known.system(), true));
            }
        }
        revision++;
    }

    public static InfiniteUniversePageSnapshot page() { return page; }
    public static List<Known> systems() { return systems; }
    public static boolean isGenerating() { return building != null; }
    public static long revision() { return revision; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var snapshot = page;
        var accumulator = building;
        if (snapshot == null || !snapshot.available() || accumulator == null) return;
        int total = snapshot.width() * snapshot.height();
        int end = buildCursor + Math.min(512, total - buildCursor);
        while (buildCursor < end) {
            int index = buildCursor++;
            if (!snapshot.hasSystemAt(index)) continue;
            int dx = index / snapshot.height();
            int dz = index % snapshot.height();
            var cell = new InfiniteUniverseField.Cell(snapshot.originX() + dx, snapshot.originZ() + dz);
            var system = InfiniteUniverseField.systemAt(snapshot.fieldSeed(), cell);
            if (system != null) accumulator.add(new Known(cell, system, snapshot.isRevealedAt(index)));
        }
        if (buildCursor == total) {
            systems = List.copyOf(accumulator);
            building = null;
            revision++;
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        page = null;
        systems = List.of();
        building = null;
        buildCursor = 0;
        requestedOriginX = 0;
        requestedOriginZ = 0;
        lastRequestTick = Long.MIN_VALUE;
        hasRequest = false;
        revision++;
    }
}
