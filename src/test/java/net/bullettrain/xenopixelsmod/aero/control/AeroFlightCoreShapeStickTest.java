package net.bullettrain.xenopixelsmod.aero.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the keyboard stick shaping curve.
 *
 * <p>This exists because the shaping was once dropped entirely while {@code stickDeadzone} and
 * {@code stickExpo} stayed in the config and in {@code /xenoaerotune} — the knobs read as working
 * while the sticks were in fact raw and every key press jumped to full deflection. A missing
 * transform is invisible in a config dump, so it is asserted here instead.
 */
class AeroFlightCoreShapeStickTest {

    @Test
    void insideDeadzoneReadsCentered() {
        assertEquals(0.0, AeroFlightCore.shapeStick(0.0, 0.1, 0.5));
        assertEquals(0.0, AeroFlightCore.shapeStick(0.1, 0.1, 0.5));
        assertEquals(0.0, AeroFlightCore.shapeStick(-0.09, 0.1, 0.5));
    }

    @Test
    void fullDeflectionReachesFullOutput() {
        assertEquals(1.0, AeroFlightCore.shapeStick(1.0, 0.1, 0.5), 1.0e-9);
        assertEquals(-1.0, AeroFlightCore.shapeStick(-1.0, 0.1, 1.0), 1.0e-9);
        assertEquals(1.0, AeroFlightCore.shapeStick(1.0, 0.0, 0.0), 1.0e-9);
    }

    @Test
    void zeroExpoIsLinearPastTheDeadzone() {
        assertEquals(0.4, AeroFlightCore.shapeStick(0.4, 0.0, 0.0), 1.0e-9);
        assertEquals(0.75, AeroFlightCore.shapeStick(0.75, 0.0, 0.0), 1.0e-9);
    }

    @Test
    void expoSoftensSmallInputs() {
        double linear = AeroFlightCore.shapeStick(0.3, 0.0, 0.0);
        double curved = AeroFlightCore.shapeStick(0.3, 0.0, 1.0);
        assertTrue(curved < linear, "expo should reduce a mid-travel input");
        assertTrue(curved > 0.0);
    }

    @Test
    void monotonicAndSignPreserving() {
        double prev = -1.0;
        for (double raw = -1.0; raw <= 1.0; raw += 0.05) {
            double out = AeroFlightCore.shapeStick(raw, 0.06, 0.5);
            assertTrue(out >= prev - 1.0e-9, "shaping must be monotonic non-decreasing");
            assertTrue(Math.signum(out) == 0.0 || Math.signum(out) == Math.signum(raw));
            prev = out;
        }
    }
}
