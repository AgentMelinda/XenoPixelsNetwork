package net.bullettrain.xenopixelsmod.space.smallplanet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmallPlanetSurfaceTravelMathTest {
    @Test
    void surfaceExitLandsJustAboveThePlanetHitbox() {
        assertEquals(94.0, SmallPlanetSurfaceTravelMath.voidseaExitY(20.0, 50));
    }
}
