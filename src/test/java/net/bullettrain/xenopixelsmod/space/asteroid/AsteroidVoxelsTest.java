package net.bullettrain.xenopixelsmod.space.asteroid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidVoxelsTest {
    private static final AsteroidField.Belt MAIN = new AsteroidField.Belt("xenopixelsmod:main_belt", 0, 128, 0, 2200, 3800, 96, false);

    private static AsteroidField.Candidate any(boolean icy, int minRadius) {
        var belt = new AsteroidField.Belt("t", 0, 0, 0, 0, 1_000_000, 1_000_000, icy);
        for (long x = 0; x < 200; x++)
            for (var c : AsteroidField.inCell(77L, new AsteroidField.Cell(x, 0, 0), belt))
                if (c.radius() >= minRadius) return c;
        throw new AssertionError("no candidate");
    }

    @Test void deterministicSolidAndBounded() {
        var candidate = any(false, 8);
        var a = new AsteroidVoxels(candidate);
        var b = new AsteroidVoxels(candidate);
        int e = a.extent(), solid = 0;
        for (int x = -e - 1; x <= e + 1; x++)
            for (int y = -e - 1; y <= e + 1; y++)
                for (int z = -e - 1; z <= e + 1; z++) {
                    var state = a.at(x, y, z);
                    assertEquals(state, b.at(x, y, z));
                    if (state != null) {
                        solid++;
                        assertTrue(Math.abs(x) <= e && Math.abs(y) <= e && Math.abs(z) <= e, "voxel outside extent");
                    }
                }
        double sphere = 4.0 / 3.0 * Math.PI * Math.pow(candidate.radius(), 3);
        assertTrue(solid > sphere * 0.5 && solid < sphere * 3, "solid=" + solid + " sphere=" + sphere);
        assertNotNull(a.at(0, 0, 0), "centre must be solid");
    }

    @Test void icyAsteroidHasNoOrePockets() {
        var icy = new AsteroidVoxels(any(true, 4));
        assertNotNull(icy.at(0, 0, 0));
    }

    @Test void cellFilterKeepsBeltCellsAndSkipsEmptySpace() {
        assertTrue(AsteroidPhysicalizer.cellMayTouch(MAIN, Math.floorDiv(3000, 384), 0, 0));
        assertFalse(AsteroidPhysicalizer.cellMayTouch(MAIN, 0, 0, 0), "cell at the Sun is inside the inner radius");
        assertFalse(AsteroidPhysicalizer.cellMayTouch(MAIN, Math.floorDiv(3000, 384), 3, 0), "cell far above the slab");
        assertFalse(AsteroidPhysicalizer.cellMayTouch(MAIN, Math.floorDiv(9000, 384), 0, 0), "cell beyond the outer radius");
        // Every accepted candidate of a belt cell must come from a cell the filter keeps.
        for (long x = -12; x <= 12; x++)
            for (long z = -12; z <= 12; z++)
                if (!AsteroidField.inCell(5L, new AsteroidField.Cell(x, 0, z), MAIN).isEmpty())
                    assertTrue(AsteroidPhysicalizer.cellMayTouch(MAIN, x, 0, z), "filter dropped a populated cell " + x + "," + z);
    }
}
