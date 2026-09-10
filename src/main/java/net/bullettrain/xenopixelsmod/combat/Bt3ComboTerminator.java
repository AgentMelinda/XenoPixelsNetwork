package net.bullettrain.xenopixelsmod.combat;

/**
 * What a combo beat turns into instead of an ordinary hit.
 *
 * <p>Budokai Tenkaichi 3 does not give its signature moves buttons of their own; they come out of a
 * string. This is the rule that decides which one a beat becomes, so those moves can stop occupying
 * gamepad chords.
 *
 * <p><b>Pure and Minecraft-free</b>, like {@link Bt3ComboChoreography} beside it and for the same
 * reason: the client runs it to predict the beat and the server runs it to decide, and they have to
 * agree without a round trip. Nothing here reads state, spends a resource or stamps a cooldown —
 * every gate on {@link Bt3CombatLimiter} is checked by the caller, which matters because
 * {@code canUltimate} stamps its own cooldown when asked.
 */
public final class Bt3ComboTerminator {

    /** The move a beat resolves into, or {@link #NONE} for an ordinary hit. */
    public enum Kind {
        NONE,
        ULTIMATE,
        Z_BURST
    }

    private Bt3ComboTerminator() {
    }

    /**
     * Which terminator this beat is.
     *
     * <p><b>Z-Burst closes the gap.</b> It fires when you swing at a locked target you cannot reach,
     * which is the situation the move exists for and the one where the swing would otherwise be a
     * whiff. It deliberately does not read a held direction: forward already means the launcher
     * here ({@code launcher = verticalBias > 0} in the combo handler), so putting Z-Burst on
     * forward as well would have one press fire both.
     *
     * <p><b>Ultimate finishes.</b> It takes the finisher beat, and only in range and only when that
     * beat is not already committed to launching the target.
     *
     * <p>Out of range wins over the finisher: you cannot finish someone you cannot touch.
     *
     * @param finisher      whether this beat is the string's finisher
     * @param launcher      whether this beat is already launching the target
     * @param hasLock       whether a live locked target exists
     * @param inMeleeRange  whether that target is close enough to hit
     */
    public static Kind resolve(boolean finisher, boolean launcher, boolean hasLock,
                               boolean inMeleeRange) {
        if (hasLock && !inMeleeRange) return Kind.Z_BURST;
        if (finisher && !launcher && hasLock) return Kind.ULTIMATE;
        return Kind.NONE;
    }
}
