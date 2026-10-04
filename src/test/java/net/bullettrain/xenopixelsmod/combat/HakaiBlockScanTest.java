package net.bullettrain.xenopixelsmod.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-09-29 owner: "remove limit of /xenoset hakaiAreaRadius 32 ... i want to be able to set it to
 * 500". A radius-500 sphere is ~500 million positions; listing them up front froze the server. The
 * scan is lazy, chunk by chunk (nearest first), never loads a chunk, and skips empty sections.
 */
class HakaiBlockScanTest {

    /** Everything loaded; blocks at y <= 64; nothing above is anything but air. */
    private static final HakaiBlockScan.World FLAT = new HakaiBlockScan.World() {
        public boolean loaded(int cx, int cz) { return true; }
        public boolean sectionEmpty(int cx, int sy, int cz) { return sy > 4; } // sections above y=79
        public boolean ground(BlockPos p) { return p.getY() <= 60; }
        public boolean air(BlockPos p) { return p.getY() > 64; }
    };

    private static List<BlockPos> drain(HakaiBlockScan scan, int max) {
        List<BlockPos> out = new ArrayList<>();
        while (scan.hasNext() && out.size() < max) out.add(scan.next());
        return out;
    }

    @Test
    void aSmallSphereCoversExactlyTheSphere() {
        Vec3 c = new Vec3(0.5, 64.5, 0.5);
        Set<BlockPos> got = new HashSet<>(drain(HakaiBlockScan.sphere(c, 3.0, -64, 320, FLAT, null),
                Integer.MAX_VALUE));
        Set<BlockPos> want = new HashSet<>(HakaiAreaRules.sphereTopDown(c, 3.0, Integer.MAX_VALUE));
        assertEquals(want, got);
    }

    /**
     * 2026-09-29 owner: "its not always clean all of the first layer after layer". Chunk by chunk
     * left steps at every chunk border; each layer now goes across the whole area before the next.
     */
    @Test
    void theWholeAreaGoesOneLayerAtATime() {
        List<BlockPos> all = drain(HakaiBlockScan.sphere(new Vec3(0.5, 64.5, 0.5), 40.0, -64, 320, FLAT, null),
                Integer.MAX_VALUE);
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i - 1).getY() >= all.get(i).getY(), "layer after layer at " + i);
        }
        List<BlockPos> raze = drain(HakaiBlockScan.raze(new Vec3(0.5, 64.5, 0.5), 40.0, 100, 0, FLAT, null),
                Integer.MAX_VALUE);
        for (int i = 1; i < raze.size(); i++) {
            assertTrue(raze.get(i - 1).getY() >= raze.get(i).getY(), "raze layer after layer at " + i);
        }
    }

    @Test
    void aRadiusOf500IsLazyAndStartsNearTheCentre() {
        long t0 = System.nanoTime();
        HakaiBlockScan scan = HakaiBlockScan.sphere(new Vec3(0.5, 64.5, 0.5), 500.0, -64, 320, FLAT, null);
        List<BlockPos> first = drain(scan, 5000);
        assertEquals(5000, first.size());
        assertTrue((System.nanoTime() - t0) / 1_000_000L < 3000, "no up-front listing");
        for (BlockPos p : first) {
            // Layer after layer: the first 5000 are the top layer's nearest ~20 chunks.
            assertEquals(79, p.getY(), "the top non-empty layer first");
            assertTrue(Math.abs(p.getX()) < 64 && Math.abs(p.getZ()) < 64, "nearest chunks first: " + p);
        }
    }

    @Test
    void anUnloadedChunkIsSkippedNotLoaded() {
        HakaiBlockScan.World half = new HakaiBlockScan.World() {
            public boolean loaded(int cx, int cz) { return cx >= 0; }
            public boolean sectionEmpty(int cx, int sy, int cz) { return false; }
            public boolean ground(BlockPos p) { return false; }
            public boolean air(BlockPos p) { return false; }
        };
        List<BlockPos> all = drain(HakaiBlockScan.sphere(new Vec3(0, 64, 0), 20.0, -64, 320, half, null),
                Integer.MAX_VALUE);
        assertFalse(all.isEmpty());
        assertTrue(all.stream().allMatch(p -> p.getX() >= 0), "never a position in an unloaded chunk");
    }

    @Test
    void emptySectionsAreSkippedAndTheWorldHeightIsRespected() {
        List<BlockPos> all = drain(HakaiBlockScan.sphere(new Vec3(0.5, 64.5, 0.5), 40.0, 0, 128, FLAT, null),
                Integer.MAX_VALUE);
        assertTrue(all.stream().allMatch(p -> p.getY() < 80), "sections above y=79 are empty");
        assertTrue(all.stream().allMatch(p -> p.getY() >= 0), "not below the world");
    }

    @Test
    void razeTakesEachColumnDownToTheGround() {
        List<BlockPos> all = drain(HakaiBlockScan.raze(new Vec3(0.5, 64.5, 0.5), 2.0, 100, 0, FLAT, null),
                Integer.MAX_VALUE);
        assertTrue(all.contains(new BlockPos(0, 64, 0)));
        assertTrue(all.contains(new BlockPos(0, 61, 0)));
        assertFalse(all.contains(new BlockPos(0, 60, 0)), "the ground stays");
        assertFalse(all.contains(new BlockPos(0, 70, 0)), "air is not a block");
        assertFalse(all.contains(new BlockPos(3, 64, 0)), "outside the radius");
    }

    @Test
    void aPlotIsScannedOnlyWithinItsChunks() {
        HakaiBlockScan.Bounds plot = new HakaiBlockScan.Bounds(10, 10, 10, 10);
        List<BlockPos> all = drain(HakaiBlockScan.sphere(new Vec3(168, 64, 168), 30.0, -64, 320, FLAT, plot),
                Integer.MAX_VALUE);
        assertFalse(all.isEmpty());
        assertTrue(all.stream().allMatch(p -> (p.getX() >> 4) == 10 && (p.getZ() >> 4) == 10));
    }

    @Test
    void theRadiusCanBeSetTo500AndTheBlockLimitRaised() {
        net.bullettrain.xenopixelsmod.config.XenoServerConfig.Data saved =
                net.bullettrain.xenopixelsmod.config.XenoServerConfig.snapshot();
        try {
            assertEquals(500.0, net.bullettrain.xenopixelsmod.config.XenoServerConfig.clampHakaiAreaRadius(500.0), 1e-9);
            assertEquals(4096.0, net.bullettrain.xenopixelsmod.config.XenoServerConfig.clampHakaiAreaRadius(1e9), 1e-9,
                    "past every loaded chunk there is nothing to erase");
            var d = new net.bullettrain.xenopixelsmod.config.XenoServerConfig.Data();
            d.hakaiBlockLimit = 2_000_000;
            net.bullettrain.xenopixelsmod.config.XenoServerConfig.apply(d);
            assertEquals(2_000_000, net.bullettrain.xenopixelsmod.config.XenoServerConfig.hakaiBlockLimit);
        } finally {
            net.bullettrain.xenopixelsmod.config.XenoServerConfig.apply(saved);
        }
    }
}
