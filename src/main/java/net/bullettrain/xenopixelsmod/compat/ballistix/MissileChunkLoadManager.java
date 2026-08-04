package net.bullettrain.xenopixelsmod.compat.ballistix;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Temporary forced chunks for XenoPixels missiles / VLS target areas.
 * Gated by {@link XenoPerfConfig} (target-only, radius, player range).
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class MissileChunkLoadManager {
    /** What kind of position is being force-loaded. */
    public enum Role {
        /** Impact / aim point — always allowed when force-chunks on. */
        TARGET,
        /** Launch pad / silo. */
        PAD,
        /** Moving vehicle / missile body. */
        VEHICLE
    }

    /** Typed key avoids per-refresh strings and split/parse work during expiry sweeps. */
    private record TicketKey(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
    }

    private static final Map<TicketKey, Integer> ACTIVE = new HashMap<>();

    private MissileChunkLoadManager() {}

    /** Convenience: force around a target (respects config). */
    public static void forceNearTarget(ServerLevel level, BlockPos pos, int radiusChunks, int durationTicks) {
        forceNear(level, pos, radiusChunks, durationTicks, Role.TARGET);
    }

    public static void forceNearTarget(ServerLevel level, Vec3 pos, int radiusChunks, int durationTicks) {
        if (pos == null) return;
        forceNearTarget(level, BlockPos.containing(pos), radiusChunks, durationTicks);
    }

    /**
     * @deprecated Prefer {@link #forceNear(ServerLevel, BlockPos, int, int, Role)} so target-only mode works.
     * Defaults to {@link Role#VEHICLE} (skipped when target-only).
     */
    @Deprecated
    public static void forceNear(ServerLevel level, BlockPos pos, int radiusChunks, int durationTicks) {
        forceNear(level, pos, radiusChunks, durationTicks, Role.VEHICLE);
    }

    @Deprecated
    public static void forceNear(ServerLevel level, Vec3 pos, int radiusChunks, int durationTicks) {
        if (pos == null) return;
        forceNear(level, BlockPos.containing(pos), radiusChunks, durationTicks, Role.VEHICLE);
    }

    public static void forceNear(ServerLevel level, Vec3 pos, int radiusChunks, int durationTicks, Role role) {
        if (pos == null) return;
        forceNear(level, BlockPos.containing(pos), radiusChunks, durationTicks, role);
    }

    public static void forceNear(ServerLevel level, BlockPos pos, int radiusChunks, int durationTicks, Role role) {
        if (level == null || pos == null) return;
        if (!XenoPerfConfig.perfEnabled || !XenoPerfConfig.forceChunksEnabled) return;

        // Target-only: skip pad / mid-flight vehicle tickets
        if (XenoPerfConfig.forceChunksTargetOnly && role != Role.TARGET) return;

        if (XenoPerfConfig.forceChunksPlayerRange > 0.0
                && !playerNear(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                XenoPerfConfig.forceChunksPlayerRange)) {
            return;
        }

        int r = Math.min(Math.max(0, radiusChunks), XenoPerfConfig.forceChunksRadius);
        if (r < 0) return;
        int dur = Math.max(40, Math.min(durationTicks, XenoPerfConfig.forceChunksDurationTicks));

        ChunkPos center = new ChunkPos(pos);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                forceChunk(level, center.x + dx, center.z + dz, dur);
            }
        }
    }

    public static void forceChunk(ServerLevel level, int chunkX, int chunkZ, int durationTicks) {
        if (level == null) return;
        TicketKey key = new TicketKey(level.dimension(), chunkX, chunkZ);
        int until = level.getServer().getTickCount() + Math.max(40, durationTicks);
        Integer prev = ACTIVE.get(key);
        if (prev == null) {
            try {
                level.setChunkForced(chunkX, chunkZ, true);
            } catch (Throwable t) {
                XenoPixelsMod.LOGGER.debug("setChunkForced failed: {}", t.toString());
            }
        }
        if (prev == null || prev < until) {
            ACTIVE.put(key, until);
        }
    }

    /** Track entity missile: target always; vehicle only if not target-only. */
    public static void trackMissile(ServerLevel level, Vec3 position, BlockPos target) {
        if (level == null || position == null) return;
        if (level.getServer().getTickCount() % 20 != 0) return;
        int r = XenoPerfConfig.forceChunksRadius;
        int d = XenoPerfConfig.forceChunksDurationTicks;
        if (target != null) {
            forceNear(level, target, r, d, Role.TARGET);
        }
        forceNear(level, BlockPos.containing(position), r, d, Role.VEHICLE);
    }

    private static boolean playerNear(ServerLevel level, double x, double y, double z, double range) {
        double r2 = range * range;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) <= r2) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ACTIVE.isEmpty()) return;
        if (event.getServer().getTickCount() % 40 != 0) return;
        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<TicketKey, Integer>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TicketKey, Integer> e = it.next();
            if (e.getValue() >= now) continue;
            try {
                TicketKey key = e.getKey();
                ServerLevel level = event.getServer().getLevel(key.dimension);
                if (level != null) {
                    level.setChunkForced(key.chunkX, key.chunkZ, false);
                }
            } catch (Throwable ignored) {
            }
            it.remove();
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) {
        for (TicketKey key : ACTIVE.keySet()) {
            try {
                ServerLevel level = event.getServer().getLevel(key.dimension);
                if (level != null) level.setChunkForced(key.chunkX, key.chunkZ, false);
            } catch (Throwable ignored) {
            }
        }
        ACTIVE.clear();
    }
}
