package net.bullettrain.xenopixelsmod.space.smallplanet;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.HandoffAnchorMath;
import net.bullettrain.xenopixelsmod.space.HandoffTransitionNotice;
import net.bullettrain.xenopixelsmod.space.PlanetTravelMemory;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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

/** Touch entry and high-altitude exit for large deterministic small planets in the shared cell dimension. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SmallPlanetSurfaceTravel {
    private static final ResourceLocation SURFACE_ID = ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "planet_surfaces");
    private static final ResourceKey<Level> SURFACE_KEY = ResourceKey.create(Registries.DIMENSION, SURFACE_ID);
    private static final ResourceKey<Level> HOST_KEY = ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST);
    private static final String TRIP_TAG = "PlanetSurfaceTrip";
    private static final int ARRIVAL_TICKET_DISTANCE = 1;
    private static final int PENDING_TIMEOUT_TICKS = 200;
    private static final int TRANSFER_COOLDOWN_TICKS = 20;
    private static final double TOUCH_MARGIN = 2.0;
    private static final TicketType<UUID> ARRIVAL_TICKET = TicketType.create(
            "xenopixelsmod_small_planet_surface", Comparator.comparing(UUID::toString));

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, TicketLease> TICKETS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    private record Pending(SmallPlanetField.Candidate candidate, ResourceLocation source,
                           ServerLevel destination, Vec3 fixedPosition, boolean entering, long startedAt) {}
    private record TicketLease(ServerLevel level, ChunkPos chunk, long expiresAt) {}

    private SmallPlanetSurfaceTravel() {}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        expireTickets();
        ServerLevel host = server.getLevel(HOST_KEY);
        ServerLevel surfaces = server.getLevel(SURFACE_KEY);
        if (!enabled() || host == null || surfaces == null) {
            cancelAllPending();
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            Pending pending = PENDING.get(playerId);
            if (pending != null) {
                finishPending(player, pending, now);
                continue;
            }
            if (now < COOLDOWN_UNTIL.getOrDefault(playerId, Long.MIN_VALUE)) continue;

            if (player.serverLevel() == host) {
                SmallPlanetField.Candidate candidate = touchingEnterablePlanet(host, player);
                if (candidate != null) beginEntry(server, player, candidate, surfaces, now);
            } else if (player.serverLevel() == surfaces && player.getY() >= exitThreshold(surfaces)) {
                SmallPlanetField.Candidate candidate = readTrip(player);
                if (candidate == null || !insideAssignedCell(player, candidate)) continue;
                Vec3 destination = new Vec3(candidate.x(), SmallPlanetSurfaceTravelMath.voidseaExitY(
                        candidate.y(), candidate.radius()), candidate.z());
                if (host.getWorldBorder().isWithinBounds(BlockPos.containing(destination)))
                    PENDING.put(playerId, new Pending(candidate, SURFACE_ID, host, destination, false, now));
            }
        }
    }

    private static boolean enabled() {
        return XenoSpaceConfig.enabled() && XenoSpaceConfig.handoffEnabled()
                && XenoSpaceConfig.smallPlanetPhysical()
                && XenoSpaceConfig.smallPlanetBuildIncrementalSections();
    }

    private static double exitThreshold(ServerLevel level) {
        return level.getMaxBuildHeight() - 16.0;
    }

    private static SmallPlanetField.Candidate touchingEnterablePlanet(ServerLevel host, ServerPlayer player) {
        SmallPlanetField.Candidate best = null;
        double bestGap = Double.POSITIVE_INFINITY;
        for (ServerSubLevel body : SmallPlanetPhysicalizer.bodies(host).values()) {
            if (body.isRemoved() || body.getUserDataTag() == null
                    || !body.getUserDataTag().contains(SmallPlanetPhysicalizer.TAG, Tag.TAG_COMPOUND)) continue;
            SmallPlanetField.Candidate candidate = readCandidate(body.getUserDataTag().getCompound(SmallPlanetPhysicalizer.TAG));
            if (candidate == null || candidate.surfaceMode() != SmallPlanetField.SurfaceMode.ENTERABLE) continue;
            var center = body.logicalPose().position();
            double cx = center.x(), cy = center.y(), cz = center.z();
            double gap = Math.sqrt(square(player.getX() - cx) + square(player.getY() - cy)
                    + square(player.getZ() - cz)) - candidate.radius();
            if (gap <= TOUCH_MARGIN && gap < bestGap) {
                best = new SmallPlanetField.Candidate(candidate.id(), candidate.seed(), cx, cy, cz,
                        candidate.radius(), candidate.profile(), candidate.oreRichness(), false);
                bestGap = gap;
            }
        }
        return best;
    }

    private static double square(double value) { return value * value; }

    private static void beginEntry(MinecraftServer server, ServerPlayer player,
                                   SmallPlanetField.Candidate candidate, ServerLevel surfaces, long now) {
        PlanetSurfaceCells.Cell cell = PlanetSurfaceCells.forPlanet(candidate.id().systemCell(),
                candidate.id().slot(), candidate.profile());
        double x = cell.centerBlockX() + 0.5;
        double z = cell.centerBlockZ() + 0.5;
        Vec3 remembered = PlanetTravelMemory.lastLeaving(PlanetTravelMemory.persisted(player), candidate.id().bodyId());
        if (remembered == null)
            remembered = PlanetTravelMemory.lastEntrance(PlanetTravelMemory.persisted(player), candidate.id().bodyId());
        if (remembered != null && inCell(remembered, cell)) {
            x = remembered.x;
            z = remembered.z;
        }
        Vec3 target = new Vec3(x, surfaces.getMinBuildHeight() + 1.0, z);
        if (!surfaces.getWorldBorder().isWithinBounds(BlockPos.containing(target))) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Planet surface entry refused: assigned cell is outside this dimension's border"), true);
            return;
        }
        PENDING.put(player.getUUID(), new Pending(candidate, player.level().dimension().location(),
                surfaces, target, true, now));
    }

    private static void finishPending(ServerPlayer player, Pending pending, long now) {
        UUID playerId = player.getUUID();
        if (!player.isAlive() || !player.level().dimension().location().equals(pending.source())) {
            cancel(playerId);
            return;
        }
        if (now - pending.startedAt() >= PENDING_TIMEOUT_TICKS) {
            cancel(playerId);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Planet surface transfer cancelled: destination chunk did not become ready"), true);
            return;
        }
        if (pending.entering()) {
            double gap = Math.sqrt(square(player.getX() - pending.candidate().x())
                    + square(player.getY() - pending.candidate().y())
                    + square(player.getZ() - pending.candidate().z())) - pending.candidate().radius();
            if (gap > TOUCH_MARGIN + 8.0) { cancel(playerId); return; }
        } else if (player.getY() < exitThreshold(player.serverLevel()) - 4.0) {
            cancel(playerId);
            return;
        }

        Vec3 destination = pending.fixedPosition();
        if (pending.entering()) {
            int x = BlockPos.containing(destination).getX();
            int z = BlockPos.containing(destination).getZ();
            var fullChunk = pending.destination().getChunkSource().getChunkFuture(
                    new ChunkPos(x >> 4, z >> 4).x, new ChunkPos(x >> 4, z >> 4).z, ChunkStatus.FULL, false);
            if (!arrivalChunkReady(playerId, pending.destination(), destination, now, fullChunk)) return;
            int surfaceY = pending.destination().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            destination = new Vec3(x + 0.5, surfaceY + 0.05, z + 0.5);
        } else {
            ChunkPos chunk = new ChunkPos(BlockPos.containing(destination));
            var fullChunk = pending.destination().getChunkSource().getChunkFuture(
                    chunk.x, chunk.z, ChunkStatus.FULL, false);
            if (!arrivalChunkReady(playerId, pending.destination(), destination, now, fullChunk)) return;
        }
        if (!safeArrival(player, pending.destination(), destination)) {
            cancel(playerId);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Planet surface transfer refused: destination is obstructed or outside the world border"), true);
            return;
        }

        if (!PENDING.remove(playerId, pending)) return;
        releaseTicket(playerId);
        Vec3 departure = player.position();
        AddonNetwork.sendToPlayer(player, new HandoffTransitionNotice());
        Entity moved = player.changeDimension(new DimensionTransition(pending.destination(), destination, Vec3.ZERO,
                player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
        if (!(moved instanceof ServerPlayer arrived) || arrived.serverLevel() != pending.destination()) return;
        String bodyId = pending.candidate().id().bodyId();
        if (pending.entering()) {
            PlanetTravelMemory.commit(arrived, null, null, bodyId);
            writeTrip(arrived, pending.candidate());
        } else {
            PlanetTravelMemory.commit(arrived, bodyId, departure, null);
            clearTrip(arrived);
        }
        COOLDOWN_UNTIL.put(playerId, now + TRANSFER_COOLDOWN_TICKS);
    }

    private static boolean arrivalChunkReady(UUID playerId, ServerLevel level, Vec3 position, long now,
                                             java.util.concurrent.CompletableFuture<? extends net.minecraft.server.level.ChunkResult<?>> future) {
        ChunkPos chunk = new ChunkPos(BlockPos.containing(position));
        TicketLease previous = TICKETS.get(playerId);
        if (previous != null && (previous.level() != level || !previous.chunk().equals(chunk))) releaseTicket(playerId);
        level.getChunkSource().addRegionTicket(ARRIVAL_TICKET, chunk, ARRIVAL_TICKET_DISTANCE, playerId);
        TICKETS.put(playerId, new TicketLease(level, chunk, now + PENDING_TIMEOUT_TICKS));
        return HandoffAnchorMath.fullChunkFutureReady(future);
    }

    private static boolean safeArrival(ServerPlayer player, ServerLevel target, Vec3 position) {
        if (!target.getWorldBorder().isWithinBounds(BlockPos.containing(position))) return false;
        return target.noCollision(player, player.getBoundingBox().move(position.subtract(player.position())));
    }

    private static boolean insideAssignedCell(ServerPlayer player, SmallPlanetField.Candidate candidate) {
        var expected = PlanetSurfaceCells.forPlanet(candidate.id().systemCell(), candidate.id().slot(), candidate.profile());
        var actual = PlanetSurfaceCells.addressAtBlock(BlockPos.containing(player.position()).getX(),
                BlockPos.containing(player.position()).getZ());
        return actual.isPresent() && actual.get().surfaceCell().equals(expected);
    }

    private static boolean inCell(Vec3 position, PlanetSurfaceCells.Cell cell) {
        return position.x >= cell.minBlockX() && position.x <= cell.maxBlockX()
                && position.z >= cell.minBlockZ() && position.z <= cell.maxBlockZ();
    }

    private static SmallPlanetField.Candidate readTrip(Player player) {
        CompoundTag root = PlanetTravelMemory.persisted(player).getCompound(PlanetTravelMemory.ROOT);
        return readCandidate(root.getCompound(TRIP_TAG));
    }

    private static void writeTrip(Player player, SmallPlanetField.Candidate candidate) {
        CompoundTag persisted = PlanetTravelMemory.persisted(player);
        CompoundTag root = persisted.getCompound(PlanetTravelMemory.ROOT);
        root.put(TRIP_TAG, candidateTag(candidate));
        persisted.put(PlanetTravelMemory.ROOT, root);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static void clearTrip(Player player) {
        CompoundTag persisted = PlanetTravelMemory.persisted(player);
        CompoundTag root = persisted.getCompound(PlanetTravelMemory.ROOT);
        root.remove(TRIP_TAG);
        persisted.put(PlanetTravelMemory.ROOT, root);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static CompoundTag candidateTag(SmallPlanetField.Candidate candidate) {
        var tag = new CompoundTag();
        var id = candidate.id();
        tag.putLongArray("key", new long[]{id.systemCell().x(), id.systemCell().z(), id.slot(), id.sectorX(), id.sectorZ()});
        tag.putLong("seed", candidate.seed());
        tag.putDouble("x", candidate.x()); tag.putDouble("y", candidate.y()); tag.putDouble("z", candidate.z());
        tag.putInt("radius", candidate.radius()); tag.putInt("profile", candidate.profile().ordinal());
        tag.putDouble("oreRichness", candidate.oreRichness()); tag.putBoolean("nativeLife", false);
        return tag;
    }

    private static SmallPlanetField.Candidate readCandidate(CompoundTag tag) {
        long[] key = tag.getLongArray("key");
        int profile = tag.getInt("profile");
        if (key.length != 5
                || key[0] < -net.bullettrain.xenopixelsmod.space.universe.UniverseField.MAX_CELL
                || key[0] > net.bullettrain.xenopixelsmod.space.universe.UniverseField.MAX_CELL
                || key[1] < -net.bullettrain.xenopixelsmod.space.universe.UniverseField.MAX_CELL
                || key[1] > net.bullettrain.xenopixelsmod.space.universe.UniverseField.MAX_CELL
                || key[2] < 0 || key[2] >= SmallPlanetField.MAX_PLANETS_PER_SYSTEM
                || key[3] < -SmallPlanetField.MAX_SECTOR_OFFSET || key[3] > SmallPlanetField.MAX_SECTOR_OFFSET
                || key[4] < -SmallPlanetField.MAX_SECTOR_OFFSET || key[4] > SmallPlanetField.MAX_SECTOR_OFFSET
                || profile < 0 || profile >= SmallPlanetField.Profile.values().length) return null;
        try {
            return new SmallPlanetField.Candidate(new SmallPlanetField.ObjectId(
                    new net.bullettrain.xenopixelsmod.space.universe.UniverseField.Cell((int) key[0], (int) key[1]),
                    (int) key[2], (int) key[3], (int) key[4]), tag.getLong("seed"),
                    tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getInt("radius"),
                    SmallPlanetField.Profile.values()[profile], tag.getDouble("oreRichness"), false);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static void cancel(UUID playerId) {
        PENDING.remove(playerId);
        releaseTicket(playerId);
    }

    private static void cancelAllPending() {
        for (UUID id : List.copyOf(PENDING.keySet())) cancel(id);
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

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) { cancelAllPending(); }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        cancelAllPending();
        COOLDOWN_UNTIL.clear();
    }
}
