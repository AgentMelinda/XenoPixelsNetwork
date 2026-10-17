package net.bullettrain.xenopixelsmod.space;

import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ChunkResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandoffAnchorMathTest {
    @Test void mapsFractionalArrivalCoordinatesToContainingChunkIncludingNegativeEdges() {
        var negative = HandoffAnchorMath.chunkAt(new Vec3(-0.01, 700, -16.01));
        assertEquals(-1, negative.x);
        assertEquals(-2, negative.z);

        var positiveBoundary = HandoffAnchorMath.chunkAt(new Vec3(16, 700, 31.999));
        assertEquals(1, positiveBoundary.x);
        assertEquals(1, positiveBoundary.z);
    }

    @Test void fullChunkReadinessRequiresACompletedSuccessfulResult() {
        var pending = new CompletableFuture<ChunkResult<Object>>();
        assertFalse(HandoffAnchorMath.fullChunkFutureReady(pending));
        assertFalse(HandoffAnchorMath.fullChunkFutureReady(
                CompletableFuture.completedFuture(ChunkResult.error("chunk is not ready"))));
        assertTrue(HandoffAnchorMath.fullChunkFutureReady(
                CompletableFuture.completedFuture(ChunkResult.of(new Object()))));
        var exceptional = new CompletableFuture<ChunkResult<Object>>();
        exceptional.completeExceptionally(new IllegalStateException("load failed"));
        assertFalse(HandoffAnchorMath.fullChunkFutureReady(exceptional));
    }
}
