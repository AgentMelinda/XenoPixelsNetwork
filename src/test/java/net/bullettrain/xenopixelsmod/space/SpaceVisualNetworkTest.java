package net.bullettrain.xenopixelsmod.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpaceVisualNetworkTest {
    @Test void expandedUniverseSnapshotUsesItsOwnWireVersion() {
        assertEquals("universe_snapshot_v3", SpaceVisualNetwork.universeSnapshotPacketId().getPath());
    }

    @Test void logicalPageDiscoveryBitsUseASeparateWireVersion() {
        assertEquals("infinite_universe_page_v2", SpaceVisualNetwork.infiniteUniversePagePacketId().getPath());
    }

    @Test void logicalCellRevealUsesAStableDeltaPacketId() {
        assertEquals("infinite_universe_cell_reveal_v1", SpaceVisualNetwork.infiniteUniverseCellRevealPacketId().getPath());
    }
}
