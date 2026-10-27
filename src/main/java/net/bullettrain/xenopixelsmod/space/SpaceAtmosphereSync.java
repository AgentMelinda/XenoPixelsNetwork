package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallSurfaceTravel;
import net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetSurfaceTravel;
import net.bullettrain.xenopixelsmod.space.math.SkyTransition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sends only the current bound dimension's resolved sky transition, never the sidecar YAML. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SpaceAtmosphereSync {
    public static final double DEFAULT_START_ALTITUDE = 450.0;
    private static final Set<ResourceLocation> WARNED_INVALID_EXPLICIT_START = ConcurrentHashMap.newKeySet();
    private static final ResourceLocation PLANET_SURFACES = ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "planet_surfaces");
    private static final Map<UUID, PlanetSurfaceCells.Cell> LAST_SURFACE_CELL = new HashMap<>();

    private SpaceAtmosphereSync() {}

    public static void send(ServerPlayer player) {
        var state = SpaceChartService.state(player.getServer());
        ChartRegistry chart = state.runtime.chart;
        ResourceLocation dimension = player.level().dimension().location();
        if (dimension.equals(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "planet_surfaces"))) {
            var address = PlanetSurfaceCells.addressAtBlock(player.blockPosition().getX(), player.blockPosition().getZ());
            if (address.isEmpty()) {
                sendDisabled(player, dimension);
                return;
            }
            var surfaceCell = address.get().surfaceCell();
            if (PlanetSurfaceCells.dragonBallSlotAt(surfaceCell).isPresent()) {
                double end = DragonBallSurfaceTravel.atmosphereExitAltitude(player);
                if (!DragonBallSurfaceTravel.ownsSurfaceCell(player, surfaceCell) || !Double.isFinite(end)) {
                    sendDisabled(player, dimension);
                    return;
                }
                AddonNetwork.sendToPlayer(player, new SpaceAtmosphereSettingsPacket(dimension, true,
                        defaultStart(end, player.level().getMinBuildHeight()), end,
                        true, surfaceCell.x(), surfaceCell.z()));
                return;
            }
            int chartSlot = ChartSurfaceBodyResolver.slotAt(chart, dimension.toString(),
                    player.blockPosition().getX(), player.blockPosition().getZ());
            if (chartSlot >= 0) {
                var settings = sidecarForDimension(state.sidecarSettings, dimension);
                if (settings != null && Boolean.FALSE.equals(settings.enabled())) {
                    sendDisabled(player, dimension);
                    return;
                }
                double end = chart.exitAltitude(chartSlot);
                double start = resolvedStart(settings, dimension, end, player.level().getMinBuildHeight());
                AddonNetwork.sendToPlayer(player, new SpaceAtmosphereSettingsPacket(dimension, true, start,
                        end, true, surfaceCell.x(), surfaceCell.z()));
                return;
            }
            var candidate = SmallPlanetSurfaceTravel.atmosphereCandidate(player);
            if (candidate == null || !PlanetSurfaceCells.forPlanet(candidate.id().systemCell(),
                    candidate.id().slot(), candidate.profile()).equals(surfaceCell)) {
                sendDisabled(player, dimension);
                return;
            }
            double end = SmallPlanetSurfaceTravel.atmosphereExitAltitude(player);
            AddonNetwork.sendToPlayer(player, new SpaceAtmosphereSettingsPacket(dimension, true,
                    defaultStart(end, player.level().getMinBuildHeight()), end,
                    true, surfaceCell.x(), surfaceCell.z()));
            return;
        }
        int slot = chart.slotFor(dimension);
        if (slot < 0 || !chart.body(slot).enterable()) {
            sendDisabled(player, dimension);
            return;
        }
        var settings = sidecarForDimension(state.sidecarSettings, dimension);
        if (settings != null && Boolean.FALSE.equals(settings.enabled())) {
            sendDisabled(player, dimension);
            return;
        }
        double end = chart.exitAltitude(slot);
        double start = resolvedStart(settings, dimension, end, player.level().getMinBuildHeight());
        AddonNetwork.sendToPlayer(player, new SpaceAtmosphereSettingsPacket(dimension, true, start, end));
    }

    private static double resolvedStart(net.bullettrain.xenopixelsmod.space.chart.DimensionSpaceSettings settings,
                                        ResourceLocation dimension, double end, double minBuildHeight) {
        if (settings != null && settings.skyTransitionAltitude() != null) {
            double explicit = settings.skyTransitionAltitude();
            if (explicit >= end && WARNED_INVALID_EXPLICIT_START.add(dimension))
                XenoPixelsMod.LOGGER.warn("Space sky_transition_altitude {} for {} is at/above exit altitude {}; transition may not be visible",
                        explicit, dimension, end);
            return explicit;
        }
        return defaultStart(end, minBuildHeight);
    }

    private static double defaultStart(double end, double minBuildHeight) {
        return SkyTransition.defaultStartAltitude(end, minBuildHeight, DEFAULT_START_ALTITUDE);
    }

    private static void sendDisabled(ServerPlayer player, ResourceLocation dimension) {
        AddonNetwork.sendToPlayer(player, new SpaceAtmosphereSettingsPacket(dimension, false, 0.0, 0.0));
    }

    public static void sendAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player);
    }

    static net.bullettrain.xenopixelsmod.space.chart.DimensionSpaceSettings sidecarForDimension(
            Map<String, net.bullettrain.xenopixelsmod.space.chart.DimensionSpaceSettings> settings,
            ResourceLocation dimension) {
        return settings.get(dimension.toString());
    }

    static boolean surfaceCellChanged(ResourceLocation dimension, PlanetSurfaceCells.Cell previous,
                                      PlanetSurfaceCells.Cell current) {
        return PLANET_SURFACES.equals(dimension) && !java.util.Objects.equals(previous, current);
    }

    /** Refreshes the cell-scoped client atmosphere after walking or teleporting within planet_surfaces. */
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 != 0) return;
        var online = event.getServer().getPlayerList().getPlayers();
        var onlineIds = new java.util.HashSet<UUID>();
        for (ServerPlayer player : online) {
            UUID id = player.getUUID();
            onlineIds.add(id);
            ResourceLocation dimension = player.level().dimension().location();
            if (!PLANET_SURFACES.equals(dimension)) {
                LAST_SURFACE_CELL.remove(id);
                continue;
            }
            PlanetSurfaceCells.Cell current = PlanetSurfaceCells.addressAtBlock(
                    player.blockPosition().getX(), player.blockPosition().getZ())
                    .map(PlanetSurfaceCells.Address::surfaceCell).orElse(null);
            PlanetSurfaceCells.Cell previous = LAST_SURFACE_CELL.put(id, current);
            if (surfaceCellChanged(dimension, previous, current)) send(player);
        }
        LAST_SURFACE_CELL.keySet().retainAll(onlineIds);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SURFACE_CELL.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        LAST_SURFACE_CELL.clear();
    }
}
