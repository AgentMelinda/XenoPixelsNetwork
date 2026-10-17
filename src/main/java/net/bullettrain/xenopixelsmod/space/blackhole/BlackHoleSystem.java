package net.bullettrain.xenopixelsmod.space.blackhole;

import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.SpaceFlightGuard;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of design-doc §15 black holes (switch {@link XenoSpaceConfig#blackHolesEnabled()}, default off). Holes are
 * chart entries of kind {@code black_hole} with a {@code black_hole} block; they are never Sable bodies. Every tick, for
 * survival players in the Voidsea: in the TIDAL band a pull toward the centre is added to their motion; crossing the
 * EVENT HORIZON flushes them to the hole's exit (default: the overworld spawn) - disposal, not death. The escape meter is
 * sent to the client, and {@link #oxygenFactor} makes oxygen run out faster in the tidal band. Ships (Sable bodies) are
 * not touched in this version. Cost: holes x Voidsea players distance checks per tick.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class BlackHoleSystem {
    record Exit(ResourceLocation dimension, Vec3 position) {}

    private static List<BlackHoleRules.Hole> holes = List.of();
    private static Map<String, Exit> exits = Map.of();
    private static long nextRefresh;
    private static int revision;
    private static final Map<UUID, BlackHoleStatus> LAST_SENT = new HashMap<>();

    private BlackHoleSystem() {}

    public static List<BlackHoleRules.Hole> holes() { return holes; }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now >= nextRefresh) { nextRefresh = now + 20; refresh(server); }
        if (!XenoSpaceConfig.blackHolesEnabled() || holes.isEmpty()) return;
        ServerLevel voidsea = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (voidsea == null) return;
        for (ServerPlayer player : new ArrayList<>(voidsea.players())) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) { sendStatus(player, null, 0); continue; }
            BlackHoleRules.Hole nearest = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (var hole : holes) {
                double d = BlackHoleRules.distance(hole, player.getX(), player.getY(), player.getZ());
                if (d < nearestDistance) { nearest = hole; nearestDistance = d; }
            }
            var band = nearest == null ? BlackHoleRules.Band.OUTSIDE : BlackHoleRules.band(nearest, nearestDistance);
            switch (band) {
                case HORIZON -> { flush(server, player, nearest); sendStatus(player, null, 0); }
                case TIDAL -> {
                    double pull = BlackHoleRules.pullPerTick(nearest, nearestDistance);
                    Vec3 towards = new Vec3(nearest.x() - player.getX(), nearest.y() - player.getY(), nearest.z() - player.getZ()).normalize();
                    player.setDeltaMovement(player.getDeltaMovement().add(towards.scale(pull)));
                    player.hurtMarked = true;
                    SpaceFlightGuard.grant(player);
                    sendStatus(player, nearest, BlackHoleRules.escapeFraction(nearest, nearestDistance));
                }
                case LENS -> sendStatus(player, nearest, 1);
                default -> sendStatus(player, null, 0);
            }
        }
    }

    /** Oxygen drain multiplier for this player right now (1 outside the tidal band or with holes off). */
    public static int oxygenFactor(ServerPlayer player) {
        if (!XenoSpaceConfig.blackHolesEnabled() || holes.isEmpty() || !PhysicalEarth.HOST.equals(player.level().dimension().location())) return 1;
        int factor = 1;
        for (var hole : holes)
            factor = Math.max(factor, BlackHoleRules.oxygenFactor(BlackHoleRules.band(hole,
                    BlackHoleRules.distance(hole, player.getX(), player.getY(), player.getZ())), XenoSpaceConfig.blackHoleOxygenFactor()));
        return factor;
    }

    private static void flush(MinecraftServer server, ServerPlayer player, BlackHoleRules.Hole hole) {
        Exit exit = exits.get(hole.id());
        ResourceLocation dimension = exit == null || exit.dimension() == null ? ResourceLocation.withDefaultNamespace("overworld") : exit.dimension();
        ServerLevel target = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (target == null) target = server.overworld();
        Vec3 position = exit != null && exit.position() != null ? exit.position() : spawnOf(target);
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(target, position.x, position.y, position.z, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.translatable("message.xenopixelsmod.black_hole_flushed"), false);
        XenoPixelsMod.LOGGER.info("Black hole {} flushed {} to {} {}", hole.id(), player.getScoreboardName(), dimension, position);
    }

    private static Vec3 spawnOf(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        return new Vec3(spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);
    }

    private static void sendStatus(ServerPlayer player, BlackHoleRules.Hole hole, double escape) {
        var status = hole == null ? BlackHoleStatus.NONE : new BlackHoleStatus(hole.id(), (float) escape);
        if (status.equals(LAST_SENT.get(player.getUUID()))) return;
        LAST_SENT.put(player.getUUID(), status);
        AddonNetwork.sendToPlayer(player, status);
    }

    /** Rebuilds the hole list from the chart once a second; positions follow the chart (planet plane y = chart y + 128). */
    private static void refresh(MinecraftServer server) {
        var state = SpaceChartService.state(server);
        var chart = state.runtime.chart;
        var next = new ArrayList<BlackHoleRules.Hole>();
        var nextExits = new HashMap<String, Exit>();
        for (int slot = 0; slot < chart.size(); slot++) {
            CelestialBody body = chart.body(slot);
            if (body.kind() != CelestialBody.BodyKind.BLACK_HOLE) continue;
            JsonObject definition = state.definitions.get(body.id());
            if (definition == null || !definition.has("black_hole")) continue;
            try {
                JsonObject block = definition.getAsJsonObject("black_hole");
                double pull = block.has("pull") ? block.get("pull").getAsDouble() : 0.04;
                next.add(new BlackHoleRules.Hole(body.id(), chart.x(slot), chart.y(slot) + 128, chart.z(slot),
                        block.get("horizon_radius").getAsDouble(), block.get("tidal_radius").getAsDouble(),
                        block.get("lens_radius").getAsDouble(), pull));
                if (block.has("exit")) {
                    JsonObject exit = block.getAsJsonObject("exit");
                    ResourceLocation dimension = exit.has("dimension") ? ResourceLocation.tryParse(exit.get("dimension").getAsString()) : null;
                    Vec3 position = exit.has("x") && exit.has("y") && exit.has("z")
                            ? new Vec3(exit.get("x").getAsDouble(), exit.get("y").getAsDouble(), exit.get("z").getAsDouble()) : null;
                    nextExits.put(body.id(), new Exit(dimension, position));
                }
            } catch (RuntimeException invalid) {
                XenoPixelsMod.LOGGER.warn("Black hole {} skipped: {}", body.id(), invalid.getMessage());
            }
        }
        // Exits are server-only, so an exit-only edit (Codex review of CL-130) updates the map without a client resync.
        if (!nextExits.equals(exits)) exits = Map.copyOf(nextExits);
        if (!next.equals(holes)) {
            holes = List.copyOf(next);
            revision++;
            syncAll(server);
        }
    }

    public static BlackHoleSnapshot snapshot() {
        return new BlackHoleSnapshot(revision, XenoSpaceConfig.blackHolesEnabled() ? holes : List.of());
    }

    static void syncAll(MinecraftServer server) {
        var packet = snapshot();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) AddonNetwork.sendToPlayer(player, packet);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) AddonNetwork.sendToPlayer(player, snapshot());
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { LAST_SENT.remove(event.getEntity().getUUID()); }
}
