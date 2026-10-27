package net.bullettrain.xenopixelsmod.space.dragonball;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.HandoffAnchorMath;
import net.bullettrain.xenopixelsmod.space.HandoffTransitionNotice;
import net.bullettrain.xenopixelsmod.space.PlanetTravelMemory;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Shared-cell entry and above-hitbox return for the seven persistent physical SDB bodies. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DragonBallSurfaceTravel {
    private static final ResourceLocation SURFACE_ID = ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "planet_surfaces");
    private static final ResourceKey<Level> SURFACE_KEY = ResourceKey.create(Registries.DIMENSION, SURFACE_ID);
    private static final ResourceKey<Level> HOST_KEY = ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST);
    private static final String TRIP_TAG = "SdbPlanetSurfaceTrip";
    private static final String TRIP_VERSION_KEY = "version";
    private static final int TRIP_VERSION = 1;
    private static final int ARRIVAL_TICKET_DISTANCE = 1;
    private static final int PENDING_TIMEOUT_TICKS = 200;
    private static final int TRANSFER_COOLDOWN_TICKS = 20;
    private static final double TOUCH_MARGIN = 2.0;
    private static final double EXIT_CLEARANCE = 64.0;
    private static final TicketType<UUID> ARRIVAL_TICKET = TicketType.create(
            "xenopixelsmod_sdb_surface", Comparator.comparing(UUID::toString));

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, TicketLease> TICKETS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    private record Body(int slot, UUID id, String planetKey, double x, double y, double z, int radius) {}
    private record Trip(int slot, UUID bodyId, String planetKey, PlanetSurfaceCells.Cell cell) {}
    private record Pending(Body body, ResourceLocation source, ServerLevel destination,
                           Vec3 target, boolean entering, long startedAt) {}
    private record TicketLease(ServerLevel level, ChunkPos chunk, long expiresAt) {}

    private DragonBallSurfaceTravel() {}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        expireTickets();
        ServerLevel host = server.getLevel(HOST_KEY);
        ServerLevel surfaces = server.getLevel(SURFACE_KEY);
        if (!enabled(server) || host == null || surfaces == null) {
            cancelAllPending();
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            Pending pending = PENDING.get(playerId);
            if (pending != null) {
                finishPending(server, player, pending, now);
                continue;
            }
            if (now < COOLDOWN_UNTIL.getOrDefault(playerId, Long.MIN_VALUE)) continue;

            if (player.serverLevel() == host) {
                Body body = touchingBody(server, player);
                if (body != null) beginEntry(player, body, surfaces, now);
            } else if (player.serverLevel() == surfaces && player.getY() >= exitThreshold(surfaces)) {
                Trip trip = readTrip(player);
                if (trip == null || !insideAssignedCell(player, trip)) continue;
                Body body = resolveBody(server, trip);
                if (body == null) {
                    refuse(player, now, "SDB surface transfer unavailable: the planet is not currently bound");
                    continue;
                }
                if (!trip.bodyId().equals(body.id())) writeTrip(player, trip.slot(), body.id());
                Vec3 target = orbitExit(body, host);
                if (target != null && host.getWorldBorder().isWithinBounds(BlockPos.containing(target)))
                    PENDING.put(playerId, new Pending(body, SURFACE_ID, host, target, false, now));
                else refuse(player, now, "SDB surface return refused: the planet exit point is unavailable");
            }
        }
    }

    private static boolean enabled(MinecraftServer server) {
        return XenoSpaceConfig.enabled() && XenoSpaceConfig.handoffEnabled()
                && DragonBallModeGuard.planetaryEnabled(server);
    }

    private static double exitThreshold(ServerLevel level) {
        return DragonBallSurfaceTravelMath.surfaceExitAltitude(level.getMaxBuildHeight());
    }

    /** True only when the player's persisted trip still identifies this SDB's reserved surface cell. */
    public static boolean ownsSurfaceCell(ServerPlayer player, PlanetSurfaceCells.Cell cell) {
        Trip trip = readTrip(player);
        return trip != null && trip.cell().equals(cell) && insideAssignedCell(player, trip);
    }

    /** Atmosphere end in planet_surfaces, matching this dimension's actual altitude handoff. */
    public static double atmosphereExitAltitude(ServerPlayer player) {
        Trip trip = readTrip(player);
        if (trip == null || !player.level().dimension().location().equals(SURFACE_ID)) return Double.NaN;
        return DragonBallSurfaceTravelMath.surfaceExitAltitude(player.serverLevel().getMaxBuildHeight());
    }

    private static Body touchingBody(MinecraftServer server, ServerPlayer player) {
        ServerLevel host = player.serverLevel();
        SubLevelContainer container;
        try { container = SubLevelContainer.getContainer(host); }
        catch (RuntimeException unavailable) { return null; }
        DragonBallSavedData saved = DragonBallSavedData.get(server);
        if (!saved.isGameplayStateValid()) return null;
        Body best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int slot = 1; slot <= CosmicDragonBalls.COUNT; slot++) {
            UUID id = saved.planetBodyUuid(slot);
            if (id == null || !(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved()) continue;
            String planetKey = DragonBallBodyScan.taggedPlanetKey(body, CosmicDragonBalls.COUNT);
            if (!DragonBallIdentity.forSlot(slot).planetKey().equals(planetKey)) continue;
            var pose = body.logicalPose().position();
            int radius = DragonBallBodyManager.PROTOTYPE_RADIUS;
            double dx = player.getX() - pose.x(), dy = player.getY() - pose.y(), dz = player.getZ() - pose.z();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            double reach = radius + TOUCH_MARGIN;
            if (distanceSquared <= reach * reach && distanceSquared < bestDistance) {
                best = new Body(slot, id, planetKey, pose.x(), pose.y(), pose.z(), radius);
                bestDistance = distanceSquared;
            }
        }
        return best;
    }

    private static Body resolveBody(MinecraftServer server, Trip trip) {
        if (trip == null || trip.slot() < 1 || trip.slot() > CosmicDragonBalls.COUNT) return null;
        ServerLevel host = server.getLevel(HOST_KEY);
        if (host == null) return null;
        DragonBallSavedData saved = DragonBallSavedData.get(server);
        if (!saved.isGameplayStateValid()) return null;
        int slot = trip.slot();
        UUID boundId = saved.planetBodyUuid(slot);
        if (boundId == null || !DragonBallSurfaceTravelMath.sameSlotPlanetIdentity(
                slot, trip.planetKey(), DragonBallIdentity.forSlot(slot).planetKey())) return null;
        // The persisted UUID is the last incarnation; after a validated slot rebind, follow only its
        // current body. The tagged stable planet key below prevents crossing into another slot.
        UUID currentId = boundId;
        try {
            if (!(SubLevelContainer.getContainer(host).getSubLevel(currentId) instanceof ServerSubLevel body)
                    || body.isRemoved()) return null;
            String planetKey = DragonBallBodyScan.taggedPlanetKey(body, CosmicDragonBalls.COUNT);
            if (!DragonBallSurfaceTravelMath.sameSlotPlanetIdentity(slot, trip.planetKey(), planetKey)) return null;
            var pose = body.logicalPose().position();
            return new Body(slot, currentId, planetKey, pose.x(), pose.y(), pose.z(),
                    DragonBallBodyManager.PROTOTYPE_RADIUS);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static void beginEntry(ServerPlayer player, Body body, ServerLevel surfaces, long now) {
        PlanetSurfaceCells.Cell cell = PlanetSurfaceCells.forDragonBallKey(
                DragonBallIdentity.forSlot(body.slot()).surfaceCellKey());
        Vec3 remembered = PlanetTravelMemory.lastLeaving(PlanetTravelMemory.persisted(player), body.planetKey());
        if (remembered == null) remembered = PlanetTravelMemory.lastEntrance(
                PlanetTravelMemory.persisted(player), body.planetKey());
        double x = cell.centerBlockX() + 0.5, z = cell.centerBlockZ() + 0.5;
        if (remembered != null && inCell(remembered, cell)) { x = remembered.x; z = remembered.z; }
        Vec3 target = new Vec3(x, surfaces.getMinBuildHeight() + 1.0, z);
        if (!surfaces.getWorldBorder().isWithinBounds(BlockPos.containing(target))) {
            refuse(player, now, "SDB surface entry refused: assigned cell is outside this dimension's border");
            return;
        }
        PENDING.put(player.getUUID(), new Pending(body, player.level().dimension().location(), surfaces, target, true, now));
    }

    private static Vec3 orbitExit(Body body, ServerLevel host) {
        if (body == null) return null;
        double exitY = DragonBallSurfaceTravelMath.exitAltitude(body.y(), body.radius(), EXIT_CLEARANCE);
        if (!Double.isFinite(exitY)) return null;
        double arrivalY = PlanetTravelMemory.reentryY(exitY, host.getMinBuildHeight());
        if (!Double.isFinite(arrivalY)) return null;
        return new Vec3(body.x(), arrivalY, body.z());
    }

    private static void finishPending(MinecraftServer server, ServerPlayer player, Pending pending, long now) {
        UUID playerId = player.getUUID();
        if (!player.isAlive() || !player.level().dimension().location().equals(pending.source())) {
            cancel(playerId); return;
        }
        if (now - pending.startedAt() >= PENDING_TIMEOUT_TICKS) {
            refuse(player, now, "SDB surface transfer cancelled: destination chunk did not become ready");
            return;
        }
        Body live = resolveBody(server, new Trip(pending.body().slot(), pending.body().id(),
                pending.body().planetKey(), PlanetSurfaceCells.forDragonBallKey(
                        DragonBallIdentity.forSlot(pending.body().slot()).surfaceCellKey())));
        if (live == null) {
            refuse(player, now, "SDB surface transfer cancelled: the planet is not currently bound");
            return;
        }
        Vec3 destination = pending.target();
        if (pending.entering()) {
            if (!DragonBallSurfaceTravelMath.touches(player.getX(), player.getY(), player.getZ(),
                    live.x(), live.y(), live.z(), live.radius(), TOUCH_MARGIN + 8.0)) {
                cancelTransfer(player, now); return;
            }
            int x = BlockPos.containing(destination).getX(), z = BlockPos.containing(destination).getZ();
            ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
            CompletableFuture<? extends net.minecraft.server.level.ChunkResult<?>> future =
                    pending.destination().getChunkSource().getChunkFuture(chunk.x, chunk.z, ChunkStatus.FULL, false);
            if (!arrivalChunkReady(playerId, pending.destination(), destination, now, future)) return;
            int surfaceY = pending.destination().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!DragonBallSurfaceTravelMath.hasSafeSurfaceY(surfaceY, pending.destination().getMinBuildHeight())) {
                PlanetSurfaceCells.Cell cell = PlanetSurfaceCells.forDragonBallKey(
                        DragonBallIdentity.forSlot(pending.body().slot()).surfaceCellKey());
                long centerX = cell.centerBlockX(), centerZ = cell.centerBlockZ();
                if (x != centerX || z != centerZ) {
                    Vec3 centerTarget = new Vec3(centerX + 0.5,
                            pending.destination().getMinBuildHeight() + 1.0, centerZ + 0.5);
                    if (pending.destination().getWorldBorder().isWithinBounds(BlockPos.containing(centerTarget))) {
                        PENDING.put(playerId, new Pending(pending.body(), pending.source(), pending.destination(),
                                centerTarget, true, pending.startedAt()));
                        return;
                    }
                }
                refuse(player, now, "SDB surface entry refused: no solid surface is available in the assigned cell");
                return;
            }
            destination = new Vec3(x + 0.5, surfaceY + 0.05, z + 0.5);
        } else {
            if (player.getY() < exitThreshold(player.serverLevel()) - 4.0) { cancelTransfer(player, now); return; }
            if (!insideAssignedCell(player, readTrip(player))) { cancelTransfer(player, now); return; }
            ServerLevel host = pending.destination();
            destination = orbitExit(live, host);
            if (destination == null || !host.getWorldBorder().isWithinBounds(BlockPos.containing(destination))) {
                refuse(player, now, "SDB surface return refused: the planet exit point is unavailable"); return;
            }
            ChunkPos chunk = new ChunkPos(BlockPos.containing(destination));
            CompletableFuture<? extends net.minecraft.server.level.ChunkResult<?>> future =
                    host.getChunkSource().getChunkFuture(chunk.x, chunk.z, ChunkStatus.FULL, false);
            if (!arrivalChunkReady(playerId, host, destination, now, future)) return;
        }
        if (!safeArrival(player, pending.destination(), destination)) {
            refuse(player, now, "SDB surface transfer refused: destination is obstructed or outside the world border");
            return;
        }
        if (!PENDING.remove(playerId, pending)) return;
        releaseTicket(playerId);
        Vec3 departure = player.position();
        AddonNetwork.sendToPlayer(player, new HandoffTransitionNotice());
        Entity moved = player.changeDimension(new DimensionTransition(pending.destination(), destination, Vec3.ZERO,
                player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
        if (!(moved instanceof ServerPlayer arrived) || arrived.serverLevel() != pending.destination()) return;
        if (pending.entering()) {
            writeTrip(arrived, live.slot(), live.id());
            PlanetTravelMemory.commit(arrived, null, null, live.planetKey());
        } else {
            PlanetTravelMemory.commit(arrived, live.planetKey(), departure, null);
            clearTrip(arrived);
        }
        net.bullettrain.xenopixelsmod.space.SpaceAtmosphereSync.send(arrived);
        COOLDOWN_UNTIL.put(playerId, now + TRANSFER_COOLDOWN_TICKS);
    }

    private static boolean arrivalChunkReady(UUID playerId, ServerLevel level, Vec3 position, long now,
            CompletableFuture<? extends net.minecraft.server.level.ChunkResult<?>> future) {
        ChunkPos chunk = new ChunkPos(BlockPos.containing(position));
        TicketLease previous = TICKETS.get(playerId);
        if (previous != null && previous.level() == level && previous.chunk().equals(chunk)) {
            TICKETS.put(playerId, new TicketLease(level, chunk, now + PENDING_TIMEOUT_TICKS));
            return HandoffAnchorMath.fullChunkFutureReady(future);
        }
        // Acquire the new target before releasing the old one. The body may cross a chunk boundary
        // while orbiting; a release-first swap can leave the player with no destination ticket.
        level.getChunkSource().addRegionTicket(ARRIVAL_TICKET, chunk, ARRIVAL_TICKET_DISTANCE, playerId);
        TICKETS.put(playerId, new TicketLease(level, chunk, now + PENDING_TIMEOUT_TICKS));
        if (previous != null) previous.level().getChunkSource().removeRegionTicket(
                ARRIVAL_TICKET, previous.chunk(), ARRIVAL_TICKET_DISTANCE, playerId);
        return HandoffAnchorMath.fullChunkFutureReady(future);
    }

    private static void cancelTransfer(ServerPlayer player, long now) {
        cancel(player.getUUID());
        COOLDOWN_UNTIL.put(player.getUUID(), now + TRANSFER_COOLDOWN_TICKS);
    }

    private static void refuse(ServerPlayer player, long now, String message) {
        cancelTransfer(player, now);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
    }

    private static boolean safeArrival(ServerPlayer player, ServerLevel target, Vec3 position) {
        if (position == null || !target.getWorldBorder().isWithinBounds(BlockPos.containing(position))) return false;
        return target.noCollision(player, player.getBoundingBox().move(position.subtract(player.position())));
    }

    private static boolean insideAssignedCell(ServerPlayer player, Trip trip) {
        if (trip == null) return false;
        var actual = PlanetSurfaceCells.addressAtBlock(player.blockPosition().getX(), player.blockPosition().getZ());
        return actual.isPresent() && actual.get().surfaceCell().equals(trip.cell());
    }

    private static boolean inCell(Vec3 position, PlanetSurfaceCells.Cell cell) {
        return position.x >= cell.minBlockX() && position.x <= cell.maxBlockX()
                && position.z >= cell.minBlockZ() && position.z <= cell.maxBlockZ();
    }

    private static Trip readTrip(ServerPlayer player) {
        CompoundTag root = PlanetTravelMemory.persisted(player).getCompound(PlanetTravelMemory.ROOT);
        if (!root.contains(TRIP_TAG, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(TRIP_TAG);
        if (!tag.contains(TRIP_VERSION_KEY, Tag.TAG_INT) || tag.getInt(TRIP_VERSION_KEY) != TRIP_VERSION
                || !tag.contains("Slot", Tag.TAG_INT) || !tag.hasUUID("BodyId")
                || !tag.contains("PlanetKey", Tag.TAG_STRING)) return null;
        int slot = tag.getInt("Slot");
        if (slot < 1 || slot > CosmicDragonBalls.COUNT) return null;
        DragonBallIdentity identity = DragonBallIdentity.forSlot(slot);
        if (!identity.planetKey().equals(tag.getString("PlanetKey"))) return null;
        return new Trip(slot, tag.getUUID("BodyId"), identity.planetKey(),
                PlanetSurfaceCells.forDragonBallKey(identity.surfaceCellKey()));
    }

    private static void writeTrip(ServerPlayer player, int slot, UUID bodyId) {
        DragonBallIdentity identity = DragonBallIdentity.forSlot(slot);
        CompoundTag persisted = PlanetTravelMemory.persisted(player);
        CompoundTag root = persisted.getCompound(PlanetTravelMemory.ROOT);
        CompoundTag tag = new CompoundTag();
        tag.putInt(TRIP_VERSION_KEY, TRIP_VERSION);
        tag.putInt("Slot", slot);
        tag.putUUID("BodyId", bodyId);
        tag.putString("PlanetKey", identity.planetKey());
        root.put(TRIP_TAG, tag);
        persisted.put(PlanetTravelMemory.ROOT, root);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static void clearTrip(ServerPlayer player) {
        CompoundTag persisted = PlanetTravelMemory.persisted(player);
        CompoundTag root = persisted.getCompound(PlanetTravelMemory.ROOT);
        root.remove(TRIP_TAG);
        persisted.put(PlanetTravelMemory.ROOT, root);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static void cancel(UUID playerId) { PENDING.remove(playerId); releaseTicket(playerId); }

    private static void cancelAllPending() {
        for (UUID playerId : List.copyOf(PENDING.keySet())) cancel(playerId);
    }

    private static void releaseTicket(UUID playerId) {
        TicketLease lease = TICKETS.remove(playerId);
        if (lease != null) lease.level().getChunkSource().removeRegionTicket(
                ARRIVAL_TICKET, lease.chunk(), ARRIVAL_TICKET_DISTANCE, playerId);
    }

    private static void expireTickets() {
        for (var entry : List.copyOf(TICKETS.entrySet()))
            if (entry.getValue().level().getGameTime() >= entry.getValue().expiresAt()) cancel(entry.getKey());
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        cancel(event.getEntity().getUUID());
        COOLDOWN_UNTIL.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { cancelAllPending(); }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        cancelAllPending();
        COOLDOWN_UNTIL.clear();
    }
}
