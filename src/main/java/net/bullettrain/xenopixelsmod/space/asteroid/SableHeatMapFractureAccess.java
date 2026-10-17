package net.bullettrain.xenopixelsmod.space.asteroid;

import net.minecraft.core.BlockPos;

/** Internal bridge implemented by the Sable heat-map mixin; kept outside the mixin package for runtime casts. */
public interface SableHeatMapFractureAccess {
    void xenopixels$beginFractureHeatMapRebuild();
    void xenopixels$removeFractureHeatMapBlock(BlockPos hostBlock);
    void xenopixels$addFractureHeatMapBlock(BlockPos hostBlock);
    void xenopixels$finishFractureHeatMapRebuild();
    void xenopixels$abortFractureHeatMapRebuild();
    boolean xenopixels$fractureHeatMapReady(int expectedSolidCount);
}
