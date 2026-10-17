package net.bullettrain.xenopixelsmod.space.smallplanet;

import org.junit.jupiter.api.Test;
import org.joml.Vector4d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallPlanetRadialGravityTest {
    @Test void pullsTowardTheBodyWithVanillaGravityAtTheSurface() {
        var acceleration = new Vector4d();

        assertTrue(SmallPlanetRadialGravity.accelerationAt(0, 24, 0, 0, 0, 0, 24, 0.08, acceleration));
        assertEquals(0.0, acceleration.x, 1.0e-12);
        assertEquals(-0.08, acceleration.y, 1.0e-12);
        assertEquals(0.0, acceleration.z, 1.0e-12);
        assertEquals(1.0, acceleration.w, 1.0e-12);
    }

    @Test void usesClampedInverseSquareFalloffWithContinuousCutoffAtTwiceTheRadius() {
        var acceleration = new Vector4d();

        assertTrue(SmallPlanetRadialGravity.accelerationAt(36, 0, 0, 0, 0, 0, 24, 0.08, acceleration));
        assertEquals(-0.08 * (24.0 / 36.0) * (24.0 / 36.0) * 0.5, acceleration.x, 1.0e-12);
        assertEquals(0.0, acceleration.y, 1.0e-12);
        assertEquals(0.0, acceleration.z, 1.0e-12);
        assertEquals(2.0 / 9.0, acceleration.w, 1.0e-12);
    }

    @Test void normalizesNonAxisAlignedPullToTheBodyCentre() {
        var acceleration = new Vector4d();

        assertTrue(SmallPlanetRadialGravity.accelerationAt(19.2, 14.4, 0, 0, 0, 0, 24, 0.08, acceleration));
        assertEquals(-0.064, acceleration.x, 1.0e-12);
        assertEquals(-0.048, acceleration.y, 1.0e-12);
        assertEquals(0.0, acceleration.z, 1.0e-12);
        assertEquals(1.0, acceleration.w, 1.0e-12);
        assertEquals(0.08, Math.sqrt(acceleration.x * acceleration.x + acceleration.y * acceleration.y
                + acceleration.z * acceleration.z), 1.0e-12);
    }

    @Test void hasNoInfluenceAtOrBeyondTwiceTheRadius() {
        var output = new Vector4d();
        assertFalse(SmallPlanetRadialGravity.accelerationAt(48, 0, 0, 0, 0, 0, 24, 0.08, output));
        assertFalse(SmallPlanetRadialGravity.accelerationAt(49, 0, 0, 0, 0, 0, 24, 0.08, output));
    }

    @Test void radialAndVanillaComponentsBlendWithoutAnOuterEdgeStep() {
        var surface = new Vector4d();
        var midShell = new Vector4d();
        assertTrue(SmallPlanetRadialGravity.accelerationAt(24, 0, 0, 0, 0, 0, 24, 0.08, surface));
        assertTrue(SmallPlanetRadialGravity.accelerationAt(36, 0, 0, 0, 0, 0, 24, 0.08, midShell));
        assertEquals(0.0, SmallPlanetRadialGravity.vanillaComplement(surface.w), 1.0e-12);
        assertEquals(7.0 / 9.0, SmallPlanetRadialGravity.vanillaComplement(midShell.w), 1.0e-12);
        assertEquals(1.0, SmallPlanetRadialGravity.vanillaComplement(0.0), 1.0e-12);
        assertEquals(1.0, SmallPlanetRadialGravity.vanillaComplement(Double.NaN), 1.0e-12);
    }

    @Test void rejectsInvalidInputsAndUndefinedDirectionAtTheCenter() {
        var output = new Vector4d(2, 3, 4, 5);
        assertFalse(SmallPlanetRadialGravity.accelerationAt(0, 0, 0, 0, 0, 0, 24, 0.08, output));
        assertFalse(SmallPlanetRadialGravity.accelerationAt(Double.NaN, 0, 0, 0, 0, 0, 24, 0.08, output));
        assertFalse(SmallPlanetRadialGravity.accelerationAt(0, 24, 0, 0, 0, 0, 0, 0.08, output));
        assertFalse(SmallPlanetRadialGravity.accelerationAt(0, 24, 0, 0, 0, 0, 24, Double.NaN, output));
        assertEquals(2.0, output.x, 0.0);
        assertEquals(3.0, output.y, 0.0);
        assertEquals(4.0, output.z, 0.0);
        assertEquals(5.0, output.w, 0.0);
    }
}
