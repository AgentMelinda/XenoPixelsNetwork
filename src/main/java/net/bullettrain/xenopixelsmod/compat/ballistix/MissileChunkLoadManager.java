package net.bullettrain.xenopixelsmod.compat.ballistix;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary forced chunks for guided missiles / VLS (keeps impact chunks loaded mid-flight).
 * Uses {@link ServerLevel#setChunkForced} (simple, no TicketType generics issues).
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class MissileChunkLoadManager {
    /** key = dim|x,z → expire server tick */
    private static final Map<String, Integer> ACTIVE = new ConcurrentHashMap<>();

    private MissileChunkLoadManager() {}

    public static void forceNear(ServerLevel level, BlockPos pos, int radiusChunks, int durationTicks) {
        if (level == null || pos == null) return;
        ChunkPos center = new ChunkPos(pos);
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                forceChunk(level, center.x + dx, center.z + dz, durationTicks);
            }
        }
    }

    public static void forceNear(ServerLevel level, Vec3 pos, int radiusChunks, int durationTicks) {
        if (pos == null) return;
        forceNear(level, BlockPos.containing(pos), radiusChunks, durationTicks);
    }

    public static void forceChunk(ServerLevel level, int chunkX, int chunkZ, int durationTicks) {
        if (level == null) return;
        String key = level.dimension().location() + "|" + chunkX + "," + chunkZ;
        int until = level.getServer().getTickCount() + Math.max(40, durationTicks);
        Integer prev = ACTIVE.get(key);
        if (prev == null || prev < until) {
            try {
                level.setChunkForced(chunkX, chunkZ, true);
            } catch (Throwable t) {
                XenoPixelsMod.LOGGER.debug("setChunkForced failed: {}", t.toString());
            }
            ACTIVE.put(key, until);
        }
    }

    public static void trackMissile(ServerLevel level, Vec3 position, BlockPos target) {
        if (level == null || position == null) return;
        if (level.getServer().getTickCount() % 10 != 0) return;
        forceNear(level, position, 1, 20 * 15);
        if (target != null) {
            forceNear(level, target, 1, 20 * 15);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % 40 != 0) return;
        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<String, Integer>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> e = it.next();
            if (e.getValue() >= now) continue;
            // release force
            try {
                String[] parts = e.getKey().split("\\|", 2);
                if (parts.length == 2) {
                    String[] xz = parts[1].split(",");
                    int cx = Integer.parseInt(xz[0]);
                    int cz = Integer.parseInt(xz[1]);
                    for (ServerLevel level : event.getServer().getAllLevels()) {
                        if (level.dimension().location().toString().equals(parts[0])) {
                            level.setChunkForced(cx, cz, false);
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            it.remove();
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) {
        ACTIVE.clear();
    }
}
