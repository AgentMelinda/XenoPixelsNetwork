package net.bullettrain.xenopixelsmod.space.smallplanet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SmallPlanetSurfaceTravelMathTest {
    @Test
    void surfaceExitLandsJustAboveThePlanetHitbox() {
        assertEquals(94.0, SmallPlanetSurfaceTravelMath.voidseaExitY(20.0, 50));
    }

    @Test void exitMustFitTheEffectiveSableBandAndPlayerBoundingHeight() {
        assertTrue(SmallPlanetSurfaceTravelMath.arrivalFitsRemovalBand(2032.0, 1.8,
                -30_000_000.0, 30_000_000.0, 0, true));
        assertFalse(SmallPlanetSurfaceTravelMath.arrivalFitsRemovalBand(-64.5, 1.8,
                -10_000.0, 100_000.0, 0, false));
        assertFalse(SmallPlanetSurfaceTravelMath.arrivalFitsRemovalBand(99_999.0, 1.8,
                -10_000.0, 100_000.0, 0, false));
        assertFalse(SmallPlanetSurfaceTravelMath.arrivalFitsRemovalBand(Double.NaN, 1.8,
                -30_000_000.0, 30_000_000.0, 0, true));
    }
}
