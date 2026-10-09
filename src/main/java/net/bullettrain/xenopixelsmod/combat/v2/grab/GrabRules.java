package net.bullettrain.xenopixelsmod.combat.v2.grab;

/**
 * The timing and eligibility of a grab. Minecraft-free.
 *
 * <p>A grab is the answer to guard: it ignores a block entirely, but it has startup, so anyone
 * who is swinging instead of blocking hits the grabber out of it. Once it connects the victim is
 * held for a fixed time and then thrown; a player victim may break free by pressing grab back
 * inside the tech window.
 */
public final class GrabRules {

    public enum Phase {
        STARTUP,
        /** Startup has ended this tick: test range and connect or whiff. */
        CONNECT,
        HOLD,
        /** The hold has ended this tick: throw. */
        THROW
    }

    private GrabRules() {}

    /** Where a grab is, {@code ticks} after it started. */
    public static Phase phase(int ticks, int startupTicks, int holdTicks) {
        int startup = Math.max(1, startupTicks);
        int hold = Math.max(1, holdTicks);
        if (ticks < startup) return Phase.STARTUP;
        if (ticks == startup) return Phase.CONNECT;
        if (ticks < startup + hold) return Phase.HOLD;
        return Phase.THROW;
    }

    /**
     * Whether a grab may start or connect.
     *
     * @param facingDot attacker look direction dotted with the unit vector to the victim
     */
    public static boolean inReach(double distance, double range, double facingDot, double minFacingDot) {
        return distance <= Math.max(0.5, range) && facingDot >= minFacingDot;
    }

    /** Whether the victim's own grab press breaks the hold. */
    public static boolean techs(int ticksSinceConnect, int techWindowTicks, boolean victimIsPlayer) {
        return victimIsPlayer && techWindowTicks > 0
                && ticksSinceConnect >= 0 && ticksSinceConnect <= techWindowTicks;
    }

    /**
     * Whether a hit on the grabber cancels the grab. Only startup is vulnerable: once the hold
     * has begun the grab is committed, or a third fighter could free anyone by tapping the
     * grabber.
     */
    public static boolean interruptedByHit(Phase phase, float damage) {
        return phase == Phase.STARTUP && damage > 0.05f;
    }

    /** Where the victim is held, as a distance in front of the grabber. */
    public static double holdDistance(double grabberWidth, double victimWidth) {
        return Math.max(0.6, (grabberWidth + victimWidth) * 0.5 + 0.15);
    }

    /** How many ticks apart the two keys of the grab may go down and still be one press. */
    public static final int CHORD_TICKS = 3;

    /**
     * Whether guard and punch, as read this tick, are the grab, for the controllers that read it
     * off two keys of their own.
     *
     * <p>Two fingers never land on the same tick, and which lands first is chance. A punch into a
     * raised guard is the grab; so is a guard raised within a few ticks of the punch. A guard
     * raised into a punch that has been held longer than that is only a guard, as it always was:
     * a fighter holding the punch key to keep a string going and then blocking must not grab.
     *
     * @param guardRaised     the guard came up this tick
     * @param attackPressed   the punch key went down this tick
     * @param attackHeldTicks ticks the punch key has been down, counting this one
     */
    public static boolean chord(boolean guardUp, boolean guardRaised, boolean attackDown,
                                boolean attackPressed, int attackHeldTicks) {
        if (!guardUp || !attackDown) return false;
        return attackPressed || (guardRaised && attackHeldTicks <= CHORD_TICKS);
    }

    /**
     * Twice the longest any one grab state can last on any server: the hold, which is also how
     * long a victim stays held, is capped at 100 ticks where the setting is read, and the startup
     * at 40. Doubled so that no amount of ordinary lag reaches it.
     */
    public static final int MAX_STATE_TICKS = 200;

    /**
     * Whether a client has been shown the same grab state for longer than any server could keep
     * it there. Then the report of its end was lost, and the client stops waiting for it rather
     * than holding the fighter's keys for a grab that is over.
     */
    public static boolean outstays(boolean inGrab, int ticksInState) {
        return inGrab && ticksInState > MAX_STATE_TICKS;
    }
}
