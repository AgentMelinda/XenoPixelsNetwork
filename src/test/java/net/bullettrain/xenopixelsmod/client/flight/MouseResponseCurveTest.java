package net.bullettrain.xenopixelsmod.client.flight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MouseResponseCurveTest {

    @Test
    void linearIsConstantOne() {
        assertEquals(1.0, MouseResponseCurve.LINEAR.apply(0.0));
        assertEquals(1.0, MouseResponseCurve.LINEAR.apply(0.5));
        assertEquals(1.0, MouseResponseCurve.LINEAR.apply(1.0));
    }

    @Test
    void exponentialAndSmoothstepAreMonotonicAndBounded() {
        double prevExp = -1.0, prevSmooth = -1.0;
        for (double t = 0.0; t <= 1.0; t += 0.1) {
            double exp = MouseResponseCurve.EXPONENTIAL.apply(t);
            double smooth = MouseResponseCurve.SMOOTHSTEP.apply(t);
            assertTrue(exp >= prevExp - 1.0e-9, "exponential curve must not decrease");
            assertTrue(smooth >= prevSmooth - 1.0e-9, "smoothstep curve must not decrease");
            assertTrue(exp >= 0.0 && exp <= 1.0);
            assertTrue(smooth >= 0.0 && smooth <= 1.0);
            prevExp = exp;
            prevSmooth = smooth;
        }
    }

    @Test
    void curvesClampOutOfRangeInput() {
        assertEquals(MouseResponseCurve.EXPONENTIAL.apply(1.0), MouseResponseCurve.EXPONENTIAL.apply(5.0), 1.0e-9);
        assertEquals(MouseResponseCurve.EXPONENTIAL.apply(0.0), MouseResponseCurve.EXPONENTIAL.apply(-5.0), 1.0e-9);
    }

    @Test
    void byNameParsesCaseInsensitivelyAndFallsBackToLinear() {
        assertEquals(MouseResponseCurve.EXPONENTIAL, MouseResponseCurve.byName("exponential"));
        assertEquals(MouseResponseCurve.SMOOTHSTEP, MouseResponseCurve.byName("SmoothStep"));
        assertEquals(MouseResponseCurve.LINEAR, MouseResponseCurve.byName(null));
        assertEquals(MouseResponseCurve.LINEAR, MouseResponseCurve.byName("not-a-curve"));
    }
}
