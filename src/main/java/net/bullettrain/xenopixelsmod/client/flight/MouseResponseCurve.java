package net.bullettrain.xenopixelsmod.client.flight;

/**
 * Shapes how quickly the commanded heading tracks the raw look angle in mouse-aim flight.
 *
 * <p>{@code t} is how far off-boresight the look angle is, normalized 0..1 against a fixed
 * reference span ({@link XenoFlightControls}'s full-authority angle); the curve returns a
 * multiplier on the base tracking rate. This is a client feel setting only — it changes how fast
 * the <i>setpoint sent to the server</i> approaches your look direction, never anything the
 * server trusts blindly, so a bad value can only make flight feel sluggish or twitchy, not break
 * anything server-side.
 */
public enum MouseResponseCurve {
    /** Tracking rate does not depend on how far off-center you are looking. */
    LINEAR {
        @Override
        public double apply(double t) {
            return 1.0;
        }
    },
    /** Gentle near boresight, sharp at the edges — small corrections stay precise. */
    EXPONENTIAL {
        @Override
        public double apply(double t) {
            double c = clamp01(t);
            return 0.25 + 0.75 * c * c;
        }
    },
    /** Eases in and out smoothly; a middle ground between the other two. */
    SMOOTHSTEP {
        @Override
        public double apply(double t) {
            double c = clamp01(t);
            double s = c * c * (3.0 - 2.0 * c);
            return 0.4 + 0.6 * s;
        }
    };

    public abstract double apply(double t);

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    public static MouseResponseCurve byName(String name) {
        if (name != null) {
            for (MouseResponseCurve curve : values()) {
                if (curve.name().equalsIgnoreCase(name)) return curve;
            }
        }
        return LINEAR;
    }
}
