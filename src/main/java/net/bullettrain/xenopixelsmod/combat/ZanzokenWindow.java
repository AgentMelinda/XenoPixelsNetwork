package net.bullettrain.xenopixelsmod.combat;

/**
 * The timing rules behind Zanzoken, kept Minecraft-free so they can be tested.
 *
 * <p>Zanzoken is a read, not a stance. Pressing it opens a brief window; only a hit that arrives
 * inside that window is dodged. Pressing it early or late spends the ki and leaves you standing
 * there, which is what makes landing it worth anything.
 */
public final class ZanzokenWindow {

    private ZanzokenWindow() {
    }

    /**
     * A stamp further ahead than this cannot be a real deadline — it is a leftover from a clock
     * that has since restarted, and trusting it silently disables the technique.
     *
     * <p>Both stamps come from {@code MinecraftServer.getTickCount()}, which restarts at zero on
     * every world load. Half an hour of play before the first press once left a cooldown stamped
     * around tick 36000; reloading the world reset the clock to zero and Zanzoken then refused for
     * the next half hour. Cleanup on server stop is the real fix, but a missed cleanup path should
     * cost one wasted press, never a move that is quietly dead.
     */
    static final int MAX_PLAUSIBLE_HORIZON_TICKS = 20 * 60 * 5;

    /**
     * How long a fighter counts as standing behind their images, in ticks.
     *
     * <p>Two durations describe the same disguise from different ends: {@code pressTicks} covers a
     * press that has not resolved into anything yet, and {@code ringTicks} is how long the ring of
     * bodies actually stands once a read lands. Whichever is longer is how long the fighter is
     * genuinely hidden, so that is what AI confusion has to run for.
     *
     * <p>This exists because the two were not reconciled. The press marked the images for
     * {@code pressTicks} and nothing re-marked them when the ring went up, so for a default
     * configuration — a 40-tick press mark against a 200-tick ring — the mark expired four fifths of
     * the way <i>before</i> the ring did. AI went on tracking the real body through a ring that was
     * still plainly on screen, which is exactly the case the images exist for.
     */
    public static int afterimageTicks(int pressTicks, int ringTicks) {
        return Math.max(1, Math.max(pressTicks, ringTicks));
    }

    /** Is a press still live at {@code now}? */
    public static boolean armed(int now, Integer windowEndTick) {
        if (windowEndTick == null || stale(now, windowEndTick)) return false;
        return now <= windowEndTick;
    }

    /** Has the cooldown from the previous press elapsed? */
    public static boolean ready(int now, Integer cooldownUntilTick) {
        if (cooldownUntilTick == null || stale(now, cooldownUntilTick)) return true;
        return now >= cooldownUntilTick;
    }

    /** A deadline impossibly far ahead of the current clock, i.e. left over from an older one. */
    static boolean stale(int now, int stamp) {
        return stamp - now > MAX_PLAUSIBLE_HORIZON_TICKS;
    }

    /**
     * Does this incoming hit get dodged?
     *
     * @param enabled       the feature is on for this server
     * @param armed         a press is still inside its window, per {@link #armed}
     * @param fromLiving    the damage came from a living attacker — there is nothing to vanish
     *                      behind for fall damage, drowning or a falling anvil
     * @param realDamage    the hit would actually have hurt; a zero-damage event is not worth a dodge
     */
    public static boolean dodges(boolean enabled, boolean armed, boolean fromLiving, boolean realDamage) {
        return enabled && armed && fromLiving && realDamage;
    }
}
