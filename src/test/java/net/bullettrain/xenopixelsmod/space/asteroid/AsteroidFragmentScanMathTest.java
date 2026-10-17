package net.bullettrain.xenopixelsmod.space.asteroid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidFragmentScanMathTest {
    @Test void inclusiveBoxVolumeAndCursorOrderCoverEveryPositionOnce() {
        assertEquals(8, AsteroidFragmentScanMath.volume(2, 3, 4, 3, 4, 5));
        assertArrayEquals(new int[]{2, 3, 4}, AsteroidFragmentScanMath.positionAt(2, 3, 4, 3, 4, 5, 0));
        assertArrayEquals(new int[]{2, 3, 5}, AsteroidFragmentScanMath.positionAt(2, 3, 4, 3, 4, 5, 1));
        assertArrayEquals(new int[]{2, 4, 4}, AsteroidFragmentScanMath.positionAt(2, 3, 4, 3, 4, 5, 2));
        assertArrayEquals(new int[]{3, 3, 4}, AsteroidFragmentScanMath.positionAt(2, 3, 4, 3, 4, 5, 4));
        assertArrayEquals(new int[]{3, 4, 5}, AsteroidFragmentScanMath.positionAt(2, 3, 4, 3, 4, 5, 7));
    }

    @Test void emptyOrReversedInclusiveBoundsHaveNoPositions() {
        assertEquals(0, AsteroidFragmentScanMath.volume(1, 0, 0, 0, 0, 0));
        assertEquals(0, AsteroidFragmentScanMath.volume(0, 0, 0, 0, -1, 0));
        assertThrows(IndexOutOfBoundsException.class,
                () -> AsteroidFragmentScanMath.positionAt(0, 0, 0, 0, 0, 0, 1));
    }

    @Test void volumeArithmeticSaturatesInsteadOfWrappingNegative() {
        assertEquals(Long.MAX_VALUE,
                AsteroidFragmentScanMath.volume(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
