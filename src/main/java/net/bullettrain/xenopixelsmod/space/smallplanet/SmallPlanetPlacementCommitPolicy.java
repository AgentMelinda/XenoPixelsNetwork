package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.minecraft.world.level.block.state.BlockState;

/** Pure final-state check for Forge snapshot-captured block placements. */
final class SmallPlanetPlacementCommitPolicy {
    private SmallPlanetPlacementCommitPolicy() {}

    static boolean survived(BlockState before, BlockState requested, BlockState current) {
        return before != null && requested != null && current != null
                && !before.equals(requested) && current.equals(requested);
    }

    /** Snapshot-captured placements must have a saved before-state so overflow cannot silently skip pinning. */
    static boolean allowWithoutCapture(boolean snapshotCaptureActive) {
        return !snapshotCaptureActive;
    }

    static boolean needsDeferredReservation(boolean alreadyPinned) {
        return !alreadyPinned;
    }
}
