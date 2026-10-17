package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Coalesces LevelPlot.addChunkHolder's growing-list scan to the body-builder batch boundary. */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.plot.LevelPlot", remap = false)
public abstract class SablePlotChunkBoundsBatchMixin {
    @Redirect(method = "addChunkHolder(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/plot/PlotChunkHolder;Z)V",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/plot/LevelPlot;updateBoundingBox()V"),
            require = 1)
    private void xenopixels$coalesceBuildBounds(LevelPlot plot) {
        if (!PlotBodyBuilder.deferChunkHolderBounds()) plot.updateBoundingBox();
    }
}
