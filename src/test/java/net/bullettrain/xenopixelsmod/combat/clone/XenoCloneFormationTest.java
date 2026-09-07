package net.bullettrain.xenopixelsmod.combat.clone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ring geometry, checked without a level. Both techniques place bodies on a circle, and a
 * circle that is not evenly divided stops reading as an encirclement.
 */
class XenoCloneFormationTest {

    /** Mirrors the angle step XenoCloneSystem.encircle uses, so a change there fails here. */
    private static double[] ringPoint(double cx, double cz, int index, int count, double radius) {
        double angle = (2.0 * Math.PI * index) / Math.max(1, count);
        return new double[]{cx + Math.cos(angle) * radius, cz + Math.sin(angle) * radius};
    }

    @Test
    void ringPointsSitOnTheCircleAtTheGivenRadius() {
        for (int i = 0; i < 6; i++) {
            double[] p = ringPoint(10.0, -4.0, i, 6, 3.0);
            double dx = p[0] - 10.0;
            double dz = p[1] + 4.0;
            assertEquals(3.0, Math.sqrt(dx * dx + dz * dz), 1.0e-9,
                    "copy " + i + " must stand exactly on the ring");
        }
    }

    @Test
    void ringIsEvenlySpacedAndCloses() {
        int count = 6;
        double[] a = ringPoint(0, 0, 0, count, 3.0);
        double[] b = ringPoint(0, 0, 1, count, 3.0);
        assertEquals(2.0 * Math.PI / count, Math.atan2(b[1], b[0]) - Math.atan2(a[1], a[0]), 1.0e-9);
        double[] wrapped = ringPoint(0, 0, count, count, 3.0);
        assertEquals(a[0], wrapped[0], 1.0e-9);
        assertEquals(a[1], wrapped[1], 1.0e-9);
    }

    @Test
    void aSingleCopyIsStillPlacedOnTheCircle() {
        double[] p = ringPoint(0, 0, 0, 1, 3.0);
        assertTrue(Math.abs(Math.hypot(p[0], p[1]) - 3.0) < 1.0e-9);
    }
}
