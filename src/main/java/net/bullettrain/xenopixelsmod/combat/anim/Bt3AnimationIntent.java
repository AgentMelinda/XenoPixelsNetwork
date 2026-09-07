package net.bullettrain.xenopixelsmod.combat.anim;

/**
 * What a combat beat should <em>look</em> like, named independently of any animation library.
 *
 * <p>This is a XenoPixels concept, not a PlayerAnimationLibrary one. It is deliberately common
 * code with no client or PAL imports so the server can decide which pose a strike is and put that
 * decision on the wire, while the client alone decides how to render it — see
 * {@code Bt3AnimationBinding} for the PAL clip / DragonMineZ fallback each one maps to.
 *
 * <p>Ordinals are sent over the network by {@code Bt3AnimIntentPacket}, so <b>append new values at
 * the end</b> and bump the network protocol rather than inserting in the middle.
 */
public enum Bt3AnimationIntent {
    /** Closing step into the target. Pose only — actual movement stays server-owned. */
    STEP_IN_DASH,
    JAB_LEFT,
    JAB_RIGHT,
    CROSS_LEFT,
    CROSS_RIGHT,
    BODY_PUNCH_LEFT,
    BODY_PUNCH_RIGHT,
    HOOK_LEFT,
    HOOK_RIGHT,
    UPPERCUT_LEFT,
    UPPERCUT_RIGHT,
    LOW_KICK_LEFT,
    LOW_KICK_RIGHT,
    MID_KICK_LEFT,
    MID_KICK_RIGHT,
    HIGH_ROUNDHOUSE,
    SPINNING_BACK_KICK,
    KNEE_LEFT,
    KNEE_RIGHT,
    /** Jumping kick that launches. Pose only. */
    FLYING_KICK,
    /** Pursuit dash pose. Pose only. */
    RUSH_IN_CHASE,
    /** Combo-ending heavy blow. */
    HEAVY_FINISH,
    /** Full 360 in place, then a hook / uppercut / kick. Appended — do not reorder. */
    SPIN_HOOK_LEFT,
    SPIN_HOOK_RIGHT,
    SPIN_UPPERCUT_LEFT,
    SPIN_KICK_RIGHT;

    private static final Bt3AnimationIntent[] VALUES = values();

    /** Decodes a wire ordinal, or null when a peer sent one this build does not know. */
    public static Bt3AnimationIntent byOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length ? null : VALUES[ordinal];
    }

    /** True for beats whose real-world counterpart moves the fighter across the ground. */
    public boolean isTravelPose() {
        return this == STEP_IN_DASH || this == RUSH_IN_CHASE || this == FLYING_KICK;
    }

    /**
     * True for leg strikes. Gameplay reads this to give kicks their lifting knockback, which the
     * combo previously derived from step parity — every even step was a kick regardless of what
     * was actually thrown.
     */
    public boolean isKick() {
        return switch (this) {
            case LOW_KICK_LEFT, LOW_KICK_RIGHT, MID_KICK_LEFT, MID_KICK_RIGHT,
                 HIGH_ROUNDHOUSE, SPINNING_BACK_KICK, KNEE_LEFT, KNEE_RIGHT, FLYING_KICK,
                 SPIN_KICK_RIGHT -> true;
            default -> false;
        };
    }

    /** Beats whose clip yaws {@code root}; head look must follow the body, not the camera. */
    public boolean isBodyYawPose() {
        return this == FLYING_KICK
                || this == SPINNING_BACK_KICK
                || this == SPIN_HOOK_LEFT || this == SPIN_HOOK_RIGHT
                || this == SPIN_UPPERCUT_LEFT || this == SPIN_KICK_RIGHT;
    }
}
