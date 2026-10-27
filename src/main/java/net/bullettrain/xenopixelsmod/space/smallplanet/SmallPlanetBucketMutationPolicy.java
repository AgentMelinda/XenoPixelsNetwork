package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/** Potential target positions for BucketItem's replace-clicked-block or place-adjacent behavior. */
final class SmallPlanetBucketMutationPolicy {
    private SmallPlanetBucketMutationPolicy() {}

    static List<BlockPos> candidatePositions(BlockPos clicked, Direction face) {
        if (clicked == null || face == null) return List.of();
        BlockPos stableClicked = clicked.immutable();
        BlockPos adjacent = stableClicked.relative(face).immutable();
        return List.of(stableClicked, adjacent);
    }
}
