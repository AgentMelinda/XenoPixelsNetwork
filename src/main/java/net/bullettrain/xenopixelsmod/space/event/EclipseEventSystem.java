package net.bullettrain.xenopixelsmod.space.event;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-side P4 eclipse markers. Every {@link #SAMPLE_INTERVAL_TICKS} it samples each chart star/planet/moon
 * triple, keeps clients' Galaxy Atlas list of active eclipses current (every qualifying eclipse), and announces in
 * chat at most once per triple per game day. It never changes gamerules or PvP rules: the "tournament zone" is an
 * announced marker only.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class EclipseEventSystem {
    public static final int SAMPLE_INTERVAL_TICKS = 20;
    private static final Map<MinecraftServer, EclipseEvents.Tracker> TRACKERS = new WeakHashMap<>();

    private EclipseEventSystem() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long tick = server.overworld().getGameTime();
        if (tick % SAMPLE_INTERVAL_TICKS != 0) return;
        EclipseEvents.Tracker tracker = TRACKERS.get(server);
        if (!XenoSpaceConfig.eclipseEventsEnabled()) {
            if (tracker != null) {
                boolean hadActive = !tracker.active().isEmpty();
                TRACKERS.remove(server);
                if (hadActive) broadcast(server, EclipseEventSnapshot.of(List.of()));
            }
            return;
        }
        if (tracker == null) {
            tracker = new EclipseEvents.Tracker();
            TRACKERS.put(server, tracker);
        }
        List<EclipseEvents.Body> bodies = bodies(server);
        Map<String, EclipseEvents.Body> byId = new HashMap<>();
        for (var body : bodies) byId.put(body.id(), body);
        List<EclipseEvents.Triple> triples = EclipseEvents.triples(bodies);
        double threshold = XenoSpaceConfig.eclipseCoverageThresholdPercent() / 100.0;
        boolean changed = !tracker.retain(triples).isEmpty();
        for (var triple : triples) {
            var transition = tracker.update(EclipseEvents.sample(triple, byId, threshold), threshold, tick);
            if (transition == null) continue;
            changed = true;
            if (transition.announce()) announce(server, transition.eclipse(), byId);
        }
        if (changed) broadcast(server, EclipseEventSnapshot.of(tracker.active()));
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var tracker = TRACKERS.get(player.getServer());
        AddonNetwork.sendToPlayer(player, EclipseEventSnapshot.of(tracker == null ? List.of() : tracker.active()));
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { TRACKERS.remove(event.getServer()); }

    /** The chart's current evaluated bodies; at most the chart size. */
    private static List<EclipseEvents.Body> bodies(MinecraftServer server) {
        var chart = SpaceChartService.state(server).runtime.chart;
        List<EclipseEvents.Body> out = new ArrayList<>(chart.size());
        for (int slot = 0; slot < chart.size(); slot++) {
            var body = chart.body(slot);
            double x = chart.x(slot), y = chart.y(slot), z = chart.z(slot);
            if (body == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !(body.radiusBlocks() > 0)) continue;
            out.add(new EclipseEvents.Body(body.id(), body.kind(), body.parentId(), x, y, z, body.radiusBlocks(),
                    body.enterable()));
        }
        return out;
    }

    private static void announce(MinecraftServer server, EclipseEvents.Active eclipse, Map<String, EclipseEvents.Body> byId) {
        var chart = SpaceChartService.state(server).runtime.chart;
        String text = String.format(Locale.ROOT,
                "Eclipse: %s's shadow is crossing %s (%.0f%% of %s hidden) - tournament zone marked on the Galaxy Atlas",
                name(chart, eclipse.triple().moonId()), name(chart, eclipse.triple().planetId()),
                eclipse.coverage() * 100.0, name(chart, eclipse.triple().starId()));
        XenoPixelsMod.LOGGER.info("Space event: {}", text);
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GOLD), false);
    }

    private static String name(net.bullettrain.xenopixelsmod.space.chart.ChartRegistry chart, String id) {
        int slot = chart.slotOf(id);
        var body = slot < 0 ? null : chart.body(slot);
        return body == null || body.displayName() == null || body.displayName().isBlank() ? id : body.displayName();
    }

    private static void broadcast(MinecraftServer server, EclipseEventSnapshot snapshot) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) AddonNetwork.sendToPlayer(player, snapshot);
    }
}
