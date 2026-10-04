package net.bullettrain.xenopixelsmod.item.custom;

/**
 * Shared apply-mode for the ship target tool and panel configurator.
 * Shift-right-click in the air advances the mode; a later block click applies it.
 */
public enum ShipToolMode {
    LINKER("Linker", "Link or unlink panels and thrusters"),
    ROLE("Flap role", "Cycle NONE / FLAP / PITCH / ROLL / YAW / BRAKE"),
    DEFLECT("Deflect direction", "Flip invert / swing sign"),
    FACING("Flap facing", "Cycle north / east / south / west / up / down"),
    ORIENT("Flap orient", "Spin the small hinge 90°"),
    AXIS("Mount axis", "Cycle panel AXIS X / Y / Z"),
    LIT("Lit mode", "Toggle copycat wing light on or off");

    private final String title;
    private final String hint;

    ShipToolMode(String title, String hint) {
        this.title = title;
        this.hint = hint;
    }

    public String title() {
        return title;
    }

    public String hint() {
        return hint;
    }

    public ShipToolMode next() {
        ShipToolMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static ShipToolMode byName(String name) {
        if (name == null || name.isBlank()) {
            return LINKER;
        }
        String key = name.trim();
        if (key.equalsIgnoreCase("facing")
                || key.equalsIgnoreCase("facing_x")
                || key.equalsIgnoreCase("facing_y")
                || key.equalsIgnoreCase("facing_z")) {
            return FACING;
        }
        if (key.equalsIgnoreCase("orient") || key.equalsIgnoreCase("orientation")) {
            return ORIENT;
        }
        for (ShipToolMode mode : values()) {
            if (mode.name().equalsIgnoreCase(key)) {
                return mode;
            }
        }
        return LINKER;
    }
}
