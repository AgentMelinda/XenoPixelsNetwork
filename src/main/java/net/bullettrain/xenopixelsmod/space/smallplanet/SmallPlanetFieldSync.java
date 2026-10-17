package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidField;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidFieldSync;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sends bounded, location-relevant small-planet field inputs; clients never receive the raw world seed. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SmallPlanetFieldSync {
    private static final int INTERVAL_TICKS = 20;
    private static final class PlayerState { long signature = Long.MIN_VALUE; int revision; }
    private static final class ServerState {
        long nextTick;
        final Map<UUID, PlayerState> players = new HashMap<>();
    }
    private static final Map<MinecraftServer, ServerState> SERVERS = new IdentityHashMap<>();

    private SmallPlanetFieldSync() {}

    public static void tick(MinecraftServer server, SpaceChartService.State chartState) {
        ServerState sync = SERVERS.computeIfAbsent(server, ignored -> new ServerState());
        long now = server.overworld().getGameTime();
        if (now < sync.nextTick) return;
        sync.nextTick = now + INTERVAL_TICKS;
        var host = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, PhysicalEarth.HOST));
        if (host == null) return;
        var present = new java.util.HashSet<UUID>();
        for (ServerPlayer player : host.players()) {
            present.add(player.getUUID());
            sendIfChanged(player, chartState, sync.players.computeIfAbsent(player.getUUID(), ignored -> new PlayerState()));
        }
        sync.players.keySet().removeIf(id -> !present.contains(id));
    }

    private static void sendIfChanged(ServerPlayer player, SpaceChartService.State state, PlayerState sent) {
        var packet = snapshot(player.getServer(), state, player.getX(), player.getY(), player.getZ(), sent.revision + 1);
        long signature = signature(packet);
        if (signature == sent.signature) return;
        sent.signature = signature;
        sent.revision = packet.revision();
        AddonNetwork.sendToPlayer(player, packet);
    }

    /** Produces only exclusions that could affect a candidate inside the client's bounded LOD query. */
    public static SmallPlanetFieldSnapshot snapshot(MinecraftServer server, SpaceChartService.State state,
                                                     double x, double y, double z, int revision) {
        boolean enabled = XenoSpaceConfig.enabled() && XenoSpaceConfig.smallPlanetFieldEnabled();
        if (!enabled || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            return SmallPlanetFieldSnapshot.disabled(revision);

        var chart = state.runtime.chart;
        var spheres = new ArrayList<SmallPlanetField.Sphere>();
        for (int slot = 0; slot < chart.size(); slot++) {
            double cx = chart.x(slot), cy = chart.y(slot), cz = chart.z(slot);
            double radius = chart.body(slot).radiusBlocks();
            if (!Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(cz)
                    || !Double.isFinite(radius) || radius <= 0) continue;
            var sphere = new SmallPlanetField.Sphere(cx, cy, cz, radius);
            if (SmallPlanetFieldSnapshot.chartSphereMayAffectQuery(x, y, z, sphere)) {
                if (spheres.size() == SmallPlanetFieldSnapshot.MAX_CHARTED_BODIES)
                    return SmallPlanetFieldSnapshot.disabled(revision);
                spheres.add(sphere);
            }
        }
        List<AsteroidField.Belt> belts = AsteroidFieldSync.belts(state);
        return new SmallPlanetFieldSnapshot(revision,
                net.bullettrain.xenopixelsmod.space.universe.UniverseService.fieldSeed(server), true, spheres, belts);
    }

    private static double distanceSquared(double x, double y, double z) { return x * x + y * y + z * z; }

    private static long signature(SmallPlanetFieldSnapshot packet) {
        long hash = 0xCBF29CE484222325L ^ packet.fieldSeed() ^ (packet.enabled() ? 1L : 0L);
        for (var sphere : packet.chartedBodies()) {
            hash = mix(hash ^ Double.doubleToLongBits(sphere.x()));
            hash = mix(hash ^ Double.doubleToLongBits(sphere.y()));
            hash = mix(hash ^ Double.doubleToLongBits(sphere.z()));
            hash = mix(hash ^ Double.doubleToLongBits(sphere.radius()));
        }
        for (var belt : packet.asteroidBelts()) {
            hash = mix(hash ^ belt.id().hashCode());
            hash = mix(hash ^ Double.doubleToLongBits(belt.centerX()));
            hash = mix(hash ^ Double.doubleToLongBits(belt.centerY()));
            hash = mix(hash ^ Double.doubleToLongBits(belt.centerZ()));
            hash = mix(hash ^ Double.doubleToLongBits(belt.innerRadius()));
            hash = mix(hash ^ Double.doubleToLongBits(belt.outerRadius()));
            hash = mix(hash ^ Double.doubleToLongBits(belt.halfThickness()));
        }
        return hash;
    }

    private static long mix(long value) { value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L; value ^= value >>> 27; value *= 0x94D049BB133111EBL; return value ^ (value >>> 31); }

    private static void send(ServerPlayer player) {
        if (!PhysicalEarth.HOST.equals(player.level().dimension().location())) return;
        MinecraftServer server = player.getServer();
        ServerState sync = SERVERS.computeIfAbsent(server, ignored -> new ServerState());
        PlayerState sent = sync.players.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
        sendIfChanged(player, SpaceChartService.state(server), sent);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }
}
