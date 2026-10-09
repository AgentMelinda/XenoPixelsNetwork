package net.bullettrain.xenopixelsmod.combat.v2;

/** What a v2 fighter is doing right now. One value at a time. Wire ordinal: append only. */
public enum V2State {
    NEUTRAL,
    /** Inside a combo node: startup, hit, or the window where the next input is read. */
    ATTACK,
    /** Retired step slot, retained only for wire ordinal compatibility. */
    STEP,
    /** Chase, dragon homing, Z-Burst or Dragon Dash travel. */
    TRAVEL,
    RUSH,
    GRAB_STARTUP,
    GRAB_HOLD,
    /** Held by someone else's grab. */
    GRABBED,
    /**
     * Inside a DragonMineZ strike technique (the Xeno rush strikes). DragonMineZ holds both
     * fighters still and aims the camera for the length of the strike; v2 only waits for it to
     * end. Appended.
     */
    STRIKE,
    /** Holding a server-timed punch or kick; release commits one strike. */
    CHARGE;

    private static final V2State[] VALUES = values();

    public static V2State byOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length || VALUES[ordinal] == STEP ? NEUTRAL : VALUES[ordinal];
    }

    /** States that refuse a new attack or travel until they end. */
    public boolean committed() {
        return this == RUSH || this == GRAB_STARTUP || this == GRAB_HOLD || this == GRABBED
                || this == STRIKE;
    }
}
