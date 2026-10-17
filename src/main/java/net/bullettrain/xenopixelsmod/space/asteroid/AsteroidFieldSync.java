package net.bullettrain.xenopixelsmod.space.asteroid;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends the deterministic asteroid field (S11 B1) to players in the Voidsea host: on login, on dimension change, and
 * when the belt set or a belt centre (rounded to a block) changes, checked once a second. Switch
 * {@link XenoSpaceConfig#asteroidBelts()} (default off); while off, players receive an empty field once on entry.
 * Nothing here creates asteroids; that is B3.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class AsteroidFieldSync {
    private static final int INTERVAL_TICKS = 20;
    private static final class Sent { long signature = Long.MIN_VALUE; int revision; long nextTick; }
    private static final Map<MinecraftServer, Sent> SENT = new IdentityHashMap<>();

    private AsteroidFieldSync() {}

    /** Called from the space chart's server tick. */
    public static void tick(MinecraftServer server, SpaceChartService.State state) {
        Sent sent = SENT.computeIfAbsent(server, ignored -> new Sent());
        long now = server.overworld().getGameTime();
        if (now < sent.nextTick) return;
        sent.nextTick = now + INTERVAL_TICKS;
        var host = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, PhysicalEarth.HOST));
        if (host == null) return;
        long signature = signature(state);
        if (signature == sent.signature) return;
        sent.signature = signature;
        sent.revision++;
        var snapshot = snapshot(server, state, sent.revision);
        for (ServerPlayer player : host.players()) AddonNetwork.sendToPlayer(player, snapshot);
        XenoPixelsMod.LOGGER.info("Asteroid field snapshot: revision={}, belts={}", snapshot.revision(), snapshot.belts().size());
    }

    /** The field as clients should see it now. */
    public static AsteroidFieldSnapshot snapshot(MinecraftServer server, SpaceChartService.State state, int revision) {
        if (!XenoSpaceConfig.asteroidBelts()) return AsteroidFieldSnapshot.off(revision);
        return new AsteroidFieldSnapshot(revision, AsteroidFieldSnapshot.fieldSeed(server.overworld().getSeed()), belts(state));
    }

    /** Belts placed on their parents' current chart positions; belts whose parent is not charted are left out. */
    public static List<AsteroidField.Belt> belts(SpaceChartService.State state) {
        var chart = state.runtime.chart;
        var out = new ArrayList<AsteroidField.Belt>();
        for (var definition : state.belts.values()) {
            if (out.size() >= AsteroidFieldSnapshot.MAX_BELTS) break;
            int slot = chart.slotOf(definition.parentId());
            if (slot < 0 || !Double.isFinite(chart.x(slot)) || !Double.isFinite(chart.z(slot))) continue;
            out.add(definition.at(chart.x(slot), chart.z(slot)));
        }
        return out;
    }

    private static long signature(SpaceChartService.State state) {
        long hash = 1_469_598_103_934_665_603L * 31 + state.beltRevision;
        hash = (hash ^ (XenoSpaceConfig.asteroidBelts() ? 1 : 0)) * 1_099_511_628_211L;
        if (!XenoSpaceConfig.asteroidBelts()) return hash;
        var chart = state.runtime.chart;
        for (var definition : state.belts.values()) {
            int slot = chart.slotOf(definition.parentId());
            if (slot < 0) continue;
            hash = (hash ^ Math.round(chart.x(slot))) * 1_099_511_628_211L;
            hash = (hash ^ Math.round(chart.z(slot))) * 1_099_511_628_211L;
        }
        return hash;
    }

    private static void sendTo(ServerPlayer player) {
        if (!PhysicalEarth.HOST.equals(player.level().dimension().location())) return;
        var server = player.getServer();
        Sent sent = SENT.computeIfAbsent(server, ignored -> new Sent());
        AddonNetwork.sendToPlayer(player, snapshot(server, SpaceChartService.state(server), sent.revision));
        AsteroidPhysicalizer.syncPresence(player);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendTo(player);
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendTo(player);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SENT.remove(event.getServer()); }
}
