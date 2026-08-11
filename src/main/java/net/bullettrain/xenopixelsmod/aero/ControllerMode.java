package net.bullettrain.xenopixelsmod.aero;

/**
 * Operating mode of a guidance computer.
 *
 * <p>The block began life as a missile guidance computer and that behavior is unchanged and
 * default. {@link #FLIGHT} enables the Aero flight-controller stack (avionics bus, link
 * manager, stabilization) on the same block. Every Aero code path is gated on this value so
 * an existing world keeps byte-identical missile semantics until an operator opts in.
 */
public enum ControllerMode {
    /** Ballistic missile guidance — the original, default behavior. */
    MISSILE,
    /** Crewed / autopilot ship flight control. */
    FLIGHT;

    public static ControllerMode byName(String name) {
        if (name == null) return MISSILE;
        for (ControllerMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) return mode;
        }
        return MISSILE;
    }
}
