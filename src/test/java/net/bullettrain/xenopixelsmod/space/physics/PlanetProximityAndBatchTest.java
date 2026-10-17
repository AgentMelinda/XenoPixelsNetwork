package net.bullettrain.xenopixelsmod.space.physics;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanetProximityAndBatchTest {
    @Test void activatesInsideRangeReleasesBeyondAndHoldsInBetween() {
        // activate 1500 + r, release 2200 + r, r = 100
        assertEquals(1, PhysicalEarth.proximity(1600, 100, false, 1500, 2200));
        assertEquals(0, PhysicalEarth.proximity(1601, 100, false, 1500, 2200), "just outside activation: stay unbuilt");
        assertEquals(0, PhysicalEarth.proximity(2000, 100, true, 1500, 2200), "hysteresis band keeps a built body");
        assertEquals(-1, PhysicalEarth.proximity(2301, 100, true, 1500, 2200));
        assertEquals(0, PhysicalEarth.proximity(Double.POSITIVE_INFINITY, 100, false, 1500, 2200), "nobody online: nothing to do");
        assertEquals(-1, PhysicalEarth.proximity(Double.POSITIVE_INFINITY, 100, true, 1500, 2200), "nobody online: release");
    }

    @Test void packingRoundTripsTheWholeRange() {
        for (int v : new int[]{-511, -160, -1, 0, 1, 160, 511}) {
            int p = (v + 512) | (-v + 512) << 10 | (v / 2 + 512) << 20;
            assertEquals(v, PlotBodyBuilder.unpackX(p));
            assertEquals(-v, PlotBodyBuilder.unpackY(p));
            assertEquals(v / 2, PlotBodyBuilder.unpackZ(p));
        }
    }

    @Test void columnsCoverEveryVoxelOnceInSweepOrder() {
        int radius = 40;
        var ids = new java.util.ArrayList<Integer>();
        for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++) for (int z = -radius; z <= radius; z++)
            if (PlanetShell.contains(x, y, z, radius)) ids.add((x + 512) | (y + 512) << 10 | (z + 512) << 20);
        int[] packed = ids.stream().mapToInt(Integer::intValue).toArray();
        List<int[]> columns = PlotBodyBuilder.group(packed, 8 * 16 + 8, 8 * 16 + 8);
        var seen = new HashSet<Integer>();
        int previousRing = -1;
        for (int[] column : columns) {
            assertTrue(column[0] >= previousRing, "sweep key must not decrease");
            previousRing = column[0];
            for (int i = 1; i < column.length; i++) assertTrue(seen.add(column[i]), "voxel installed twice");
        }
        assertEquals(packed.length, seen.size(), "every voxel installed");
    }

    @Test void chartedUnbuiltBodyProximityIncludesVerticalDistance() {
        assertEquals(1600.0, PhysicalEarth.distance3d(0, 0, 0, 0, 1600, 0), 1.0e-9,
                "an unbuilt planet's chart Y coordinate must participate in proximity");
        assertEquals(0.0, PhysicalEarth.distance3d(12, 34, 56, 12, 34, 56), 1.0e-9);
    }
}
