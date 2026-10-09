package net.bullettrain.xenopixelsmod.combat.v3;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Loaded-only look-ahead for V3 travel.
 *
 * <p>Combat never loads or generates terrain, so this holds no chunk tickets at all: a region
 * ticket also raises the level of its neighbours and can generate them. A step is allowed only
 * when every chunk the fighter's box would touch there is already fully loaded.
 */
public final class V3ChunkWindow {
    /** Tickets owned per fighter. Zero by design; see the class note. */
    public static final int HELD_TICKETS = 0;

    private V3ChunkWindow() {}

    static int chunkCount(double minX, double minZ, double maxX, double maxZ) {
        int x = (Mth.floor(maxX) >> 4) - (Mth.floor(minX) >> 4) + 1;
        int z = (Mth.floor(maxZ) >> 4) - (Mth.floor(minZ) >> 4) + 1;
        return Math.max(0, x) * Math.max(0, z);
    }

    /** Whether the world the fighter would occupy at {@code nextPosition} is loaded right now. */
    public static boolean update(ServerPlayer player, Vec3 nextPosition) {
        if (nextPosition == null || !V3TargetingRules.finite(nextPosition)) return false;
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().move(nextPosition.subtract(player.position())).inflate(1.0);
        if (!level.getWorldBorder().isWithinBounds(box)) return false;
        return loaded(level, box);
    }

    /** Loaded chunks and world-border coverage for a moved entity box; never takes a ticket. */
    public static boolean loaded(ServerLevel level, AABB box) {
        if (level == null || box == null || !level.getWorldBorder().isWithinBounds(box)) return false;
        int minX = Mth.floor(box.minX) >> 4, maxX = Mth.floor(box.maxX) >> 4;
        int minZ = Mth.floor(box.minZ) >> 4, maxZ = Mth.floor(box.maxZ) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Non-generating: null unless the chunk is already a full loaded chunk.
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    /** Nothing is held, so there is nothing to give back; kept so every exit path can call it. */
    public static void release(ServerPlayer player) {
    }
}
