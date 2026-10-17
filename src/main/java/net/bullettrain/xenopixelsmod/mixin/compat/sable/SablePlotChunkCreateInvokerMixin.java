package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.bullettrain.xenopixelsmod.space.physics.SableIncrementalPlotAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Uses Sable's own once-per-key chunk constructor; repeated `load()` keys are rejected by Sable 2.0.3. */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot", remap = false)
public abstract class SablePlotChunkCreateInvokerMixin implements SableIncrementalPlotAccess {
    @Invoker("newNonLitChunk")
    protected abstract void xenopixels$invokeCreateNonLitChunk(ChunkPos globalChunk);

    @Invoker("lightChunk")
    protected abstract void xenopixels$invokeLightChunk(LevelChunk chunk);

    @Override
    public void xenopixels$createNonLitChunk(ChunkPos globalChunk) {
        xenopixels$invokeCreateNonLitChunk(globalChunk);
    }

    @Override
    public void xenopixels$lightChunk(LevelChunk chunk) {
        xenopixels$invokeLightChunk(chunk);
    }
}
