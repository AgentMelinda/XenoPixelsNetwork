package net.bullettrain.xenopixelsmod.missile;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Temporary forced chunks for the addon's native missiles and VLS target areas. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class MissileChunkLoadManager {
    public enum Role { TARGET, PAD, VEHICLE }

    private record TicketKey(ResourceKey<Level> dimension, int chunkX, int chunkZ) {}

    private static final Map<TicketKey, Integer> ACTIVE = new HashMap<>();

    private MissileChunkLoadManager() {}

    public static void forceNearTarget(ServerLevel level, BlockPos pos, int radiusChunks, int durationTicks) {
        forceNear(level, pos, radiusChunks, durationTicks, Role.TARGET);
    }

    public static void forceNearTarget(ServerLevel level, Vec3 pos, int radiusChunks, int durationTicks) {
        if (pos != null) forceNearTarget(level, BlockPos.containing(pos), radiusChunks, durationTicks);
    }

    public static void forceNear(ServerLevel level, Vec3 pos, int radiusChunks, int durationTicks, Role role) {
        if (pos != null) forceNear(level, BlockPos.containing(pos), radiusChunks, durationTicks, role);
    }

    public static void forceNear(ServerLevel level, BlockPos pos, int radiusChunks, int durationTicks, Role role) {
        if (level == null || pos == null || !XenoPerfConfig.perfEnabled || !XenoPerfConfig.forceChunksEnabled) return;
        if (XenoPerfConfig.forceChunksTargetOnly && role != Role.TARGET) return;
        if (XenoPerfConfig.forceChunksPlayerRange > 0.0
                && !playerNear(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                XenoPerfConfig.forceChunksPlayerRange)) return;

        int radius = Math.min(Math.max(0, radiusChunks), XenoPerfConfig.forceChunksRadius);
        int duration = Math.max(40, Math.min(durationTicks, XenoPerfConfig.forceChunksDurationTicks));
        ChunkPos center = new ChunkPos(pos);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                forceChunk(level, center.x + dx, center.z + dz, duration);
            }
        }
    }

    public static void forceChunk(ServerLevel level, int chunkX, int chunkZ, int durationTicks) {
        TicketKey key = new TicketKey(level.dimension(), chunkX, chunkZ);
        int until = level.getServer().getTickCount() + Math.max(40, durationTicks);
        Integer previous = ACTIVE.get(key);
        if (previous == null) {
            try {
                level.setChunkForced(chunkX, chunkZ, true);
            } catch (RuntimeException exception) {
                XenoPixelsMod.LOGGER.debug("Unable to force missile chunk {}, {}", chunkX, chunkZ, exception);
            }
        }
        if (previous == null || previous < until) ACTIVE.put(key, until);
    }

    public static void trackMissile(ServerLevel level, Vec3 position, BlockPos target) {
        if (level == null || position == null || level.getServer().getTickCount() % 20 != 0) return;
        if (target != null) {
            forceNear(level, target, XenoPerfConfig.forceChunksRadius,
                    XenoPerfConfig.forceChunksDurationTicks, Role.TARGET);
        }
        forceNear(level, position, XenoPerfConfig.forceChunksRadius,
                XenoPerfConfig.forceChunksDurationTicks, Role.VEHICLE);
    }

    private static boolean playerNear(ServerLevel level, double x, double y, double z, double range) {
        double rangeSquared = range * range;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= rangeSquared) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty() || event.getServer().getTickCount() % 40 != 0) return;
        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<TicketKey, Integer>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TicketKey, Integer> entry = iterator.next();
            if (entry.getValue() >= now) continue;
            TicketKey key = entry.getKey();
            ServerLevel level = event.getServer().getLevel(key.dimension());
            if (level != null) level.setChunkForced(key.chunkX(), key.chunkZ(), false);
            iterator.remove();
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) {
        for (TicketKey key : ACTIVE.keySet()) {
            ServerLevel level = event.getServer().getLevel(key.dimension());
            if (level != null) level.setChunkForced(key.chunkX(), key.chunkZ(), false);
        }
        ACTIVE.clear();
    }
}
