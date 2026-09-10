package net.bullettrain.xenopixelsmod.client.pad;

/**
 * Which layer a gamepad face button is acting on, given the three held modifiers.
 *
 * <p>Budokai Tenkaichi 3 gets far more than four moves out of four face buttons by layering them
 * under held shoulders: ki charge, lock-on and descend. A gamepad has roughly sixteen inputs and
 * this mod has forty key bindings, so the same trick is the only way the pad reaches more than a
 * fraction of the moveset.
 *
 * <p>Minecraft-free so the precedence rules can be tested. They are small but genuinely easy to
 * get wrong: the failure mode is a button that fires two moves at once, which in play reads as the
 * game doing something random.
 */
public final class PadChords {

    /**
     * The four meanings a face button can carry.
     *
     * <p>{@link #BASE} is the unmodified press, {@link #CHARGE} is under held ki charge (left
     * trigger), {@link #LOCK} is under held lock-on (left bumper) and {@link #DESCEND} is under
     * held descend (right trigger).
     */
    public enum Layer {
        BASE,
        CHARGE,
        LOCK,
        DESCEND
    }

    private PadChords() {
    }

    /**
     * The layer currently in force.
     *
     * <p>Charge wins first. It is the trigger, held for seconds at a time while powering up, so it
     * is the one a player is most likely to still be holding by accident; resolving in its favour
     * makes the ambiguous case predictable rather than order-dependent.
     *
     * <p>Descend comes last for the same reason read the other way round: the right trigger is
     * held continuously all the way down a descent, so a descending player who reaches for a
     * lock-on chord means the lock-on move, not the descend one. The two overlaps that exist today
     * are harmless either way — charge+Y and descend+Y are both the charged kick.
     */
    public static Layer active(boolean chargeHeld, boolean lockHeld, boolean descendHeld) {
        if (chargeHeld) return Layer.CHARGE;
        if (lockHeld) return Layer.LOCK;
        if (descendHeld) return Layer.DESCEND;
        return Layer.BASE;
    }

    /**
     * Whether a binding belonging to {@code wanted} should be allowed to press right now.
     *
     * <p>Every face button carries up to four bindings — one per layer — sharing one physical
     * input, and each is gated on this. Because exactly one layer is active at a time, exactly one
     * of them can fire, so the modifier both enables its own move and withholds the base move
     * without either binding knowing the other exists.
     */
    public static boolean allows(Layer wanted, boolean chargeHeld, boolean lockHeld,
                                 boolean descendHeld) {
        return active(chargeHeld, lockHeld, descendHeld) == wanted;
    }
}
