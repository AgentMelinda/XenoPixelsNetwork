package net.bullettrain.xenopixelsmod.aero;

/**
 * Server-wide guidance stack. {@link #V1} is the current Aero / ballistic computer.
 * {@link #V2} is the rewrite (colorful HUD, facing flaps, flaps-only mode).
 * {@link #V3} keeps V1's flight controller, HUD and planner, and flies missiles (ship and tube)
 * with the V3 guidance that keeps correcting after the motor cuts off.
 */
public enum GuidanceVersion {
    V1,
    V2,
    V3;

    public static GuidanceVersion byName(String name) {
        if (name == null || name.isBlank()) {
            return V1;
        }
        String key = name.trim();
        if (key.startsWith("v") || key.startsWith("V")) {
            key = key.substring(1);
        }
        return switch (key) {
            case "2" -> V2;
            case "3" -> V3;
            default -> V1;
        };
    }

    public static GuidanceVersion active() {
        return GuidanceConfig.version();
    }

    /** The V2 flight stack: colorful HUD, facing flaps, flaps-only mode, V2 planner screen. */
    public boolean isV2() {
        return this == V2;
    }

    /** Missiles launched now fly the V3 guidance (a snapshot is taken at launch). */
    public boolean usesV3Missiles() {
        return this == V3;
    }
}
