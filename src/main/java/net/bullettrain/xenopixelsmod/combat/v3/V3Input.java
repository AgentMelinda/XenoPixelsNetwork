package net.bullettrain.xenopixelsmod.combat.v3;

/** V3 wire intents. Append only; charge timing belongs to the server. */
public enum V3Input {
    LIGHT_TAP, HEAVY_TAP, LIGHT_CHARGE_START, HEAVY_CHARGE_START, CHARGE_RELEASE, CHARGE_CANCEL,
    LOCK_ACQUIRE, LOCK_CLEAR, CHASE_START, CHASE_STOP, DRAGON_DASH, DASH_CROSS, VANISH, GRAB, COUNTER,
    LOCK_CYCLE,
    /** The technique slot key was let go: a ki technique being charged fires. */
    TECHNIQUE_RELEASE,
    /** Held-heavy then dash key: launcher heavy with 15-block knockback that pops the dash window. */
    DRAGON_DASH_POP;
    public static V3Input decode(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) throw new IllegalArgumentException("Unknown V3 input");
        return values()[ordinal];
    }
}
