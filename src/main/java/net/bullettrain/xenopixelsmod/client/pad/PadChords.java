package net.bullettrain.xenopixelsmod.client.pad;

/**
 * Which layer a gamepad face button is acting on, given the two held modifiers.
 *
 * <p>Budokai Tenkaichi 3 gets far more than four moves out of four face buttons by layering them
 * under two held shoulders: ki charge and lock-on. A gamepad has roughly sixteen inputs and this
 * mod has forty key bindings, so the same trick is the only way the pad reaches more than a
 * fraction of the moveset.
 *
 * <p>Minecraft-free so the precedence rules can be tested. They are small but genuinely easy to
 * get wrong: the failure mode is a button that fires two moves at once, which in play reads as the
 * game doing something random.
 */
public final class PadChords {

    /**
     * The three meanings a face button can carry.
     *
     * <p>{@link #BASE} is the unmodified press, {@link #CHARGE} is under held ki charge (left
     * trigger) and {@link #LOCK} is under held lock-on (left bumper).
     */
    public enum Layer {
        BASE,
        CHARGE,
        LOCK
    }

    private PadChords() {
    }

    /**
     * The layer currently in force.
     *
     * <p>Charge wins when both modifiers are held. It is the trigger, held for seconds at a time
     * while powering up, so it is the one a player is most likely to still be holding by accident;
     * resolving in its favour makes the ambiguous case predictable rather than order-dependent.
     */
    public static Layer active(boolean chargeHeld, boolean lockHeld) {
        if (chargeHeld) return Layer.CHARGE;
        if (lockHeld) return Layer.LOCK;
        return Layer.BASE;
    }

    /**
     * Whether a binding belonging to {@code wanted} should be allowed to press right now.
     *
     * <p>Every face button carries up to three bindings — one per layer — sharing one physical
     * input, and each is gated on this. Because exactly one layer is active at a time, exactly one
     * of them can fire, so the modifier both enables its own move and withholds the base move
     * without either binding knowing the other exists.
     */
    public static boolean allows(Layer wanted, boolean chargeHeld, boolean lockHeld) {
        return active(chargeHeld, lockHeld) == wanted;
    }
}
