package net.bullettrain.xenopixelsmod.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

/** Pure coordinate conversion shared by the handoff's ticket readiness gate and its tests. */
public final class HandoffAnchorMath {
    private HandoffAnchorMath() {}

    public static ChunkPos chunkAt(Vec3 position) {
        return new ChunkPos(BlockPos.containing(position));
    }

    /** A non-blocking gate for a future requested at ChunkStatus.FULL. */
    public static boolean fullChunkFutureReady(CompletableFuture<? extends ChunkResult<?>> future) {
        if (future == null || !future.isDone() || future.isCompletedExceptionally() || future.isCancelled()) return false;
        ChunkResult<?> result = future.getNow(null);
        return result != null && result.isSuccess();
    }
}
