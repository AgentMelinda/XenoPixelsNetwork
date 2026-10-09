package net.bullettrain.xenopixelsmod.combat.v3;

/** What the fighter's one timed window is for. Mirrored to the client. Append only. */
public enum V3Window {
    NONE, DASH_CROSS, CHASE, COUNTER, GRAB;
    public static V3Window decode(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) throw new IllegalArgumentException("Unknown V3 window");
        return values()[ordinal];
    }
}
