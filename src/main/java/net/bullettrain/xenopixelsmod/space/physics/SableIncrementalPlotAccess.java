package net.bullettrain.xenopixelsmod.space.physics;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** Narrow bridge for Sable's one-time non-lit chunk allocation used by the opt-in incremental asteroid writer. */
public interface SableIncrementalPlotAccess {
    void xenopixels$createNonLitChunk(ChunkPos globalChunk);

    /** Initialize Sable's private light-source, status, and propagation lifecycle for a completed plot chunk. */
    void xenopixels$lightChunk(LevelChunk chunk);
}
