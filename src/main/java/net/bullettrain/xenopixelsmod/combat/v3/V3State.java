package net.bullettrain.xenopixelsmod.combat.v3;

/** Server state mirrored to a V3 client. Append only. */
public enum V3State {
    IDLE, CHARGING_PUNCH, CHARGING_KICK, TRAVEL, STRIKE, GRAB_HOLD, GRABBED, CINEMATIC;
    public static V3State decode(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) throw new IllegalArgumentException("Unknown V3 state");
        return values()[ordinal];
    }
}
