package net.bullettrain.xenopixelsmod.aero.v2;

/** How a v2 host turns the hull. */
public enum GuidanceV2SurfaceMode {
    /** Existing mix: thrusters plus control surfaces. */
    THRUST_AND_FLAPS,
    /** Steer only with linked flaps / panels. Thruster force stays zero. */
    FLAPS_ONLY;

    public static GuidanceV2SurfaceMode byName(String name) {
        if (name == null) {
            return THRUST_AND_FLAPS;
        }
        for (GuidanceV2SurfaceMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name.trim())) {
                return mode;
            }
        }
        return THRUST_AND_FLAPS;
    }

    public boolean flapsOnly() {
        return this == FLAPS_ONLY;
    }
}
