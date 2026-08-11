package net.bullettrain.xenopixelsmod.combat.beam;

/**
 * The surge curve for a sustained ki wave: how far a beam has grown, and how fast it gets there.
 *
 * <p>Deliberately free of Minecraft types so the curve can be unit-tested directly. Everything
 * about <em>applying</em> the result to a live beam lives in {@link BeamSurgeManager}; this class
 * only answers "given that the owner has been feeding it for a while, how big is it now".
 *
 * <p><b>The ramp is asymptotic, not linear.</b> A linear climb has no natural stopping point, so
 * the only way to bound it is an arbitrary cut-off — and a beam that grows steadily then stops
 * dead at a cap reads as a bug. Approaching a ceiling instead means a long hold visibly settles,
 * which is both self-limiting and legible: the player can see they have got everything they are
 * going to get and should let go.
 *
 * <p>Mastery raises the ceiling and the approach rate together, so a practised player reaches a
 * bigger beam sooner rather than merely eventually.
 */
public final class BeamSurgeState {

    /** Fraction of the remaining gap closed per tick at zero mastery. */
    private static final double BASE_RAMP_PER_TICK = 0.020;
    /** Extra gap-closing per tick per mastery level. */
    private static final double RAMP_PER_MASTERY = 0.010;
    /** Ceiling at zero mastery, as a fraction of the configured maximum growth. */
    private static final double BASE_CEILING = 0.45;
    /** Extra ceiling per mastery level. Level 3 reaches the full configured maximum. */
    private static final double CEILING_PER_MASTERY = 0.183;
    /** Fraction of current surge shed per tick once the player lets go. */
    private static final double DECAY_PER_TICK = 0.12;

    /** Current growth, 0 (just fired) to 1 (fully surged). */
    private double surge;
    /** Ticks the owner has been feeding without a break; drives mastery gain. */
    private int sustainedTicks;

    /**
     * Advance one tick.
     *
     * @param fed          the owner is holding the key and could pay for it this tick
     * @param masteryLevel {@code CombatSkills} beam level, 0..3
     */
    public void tick(boolean fed, int masteryLevel) {
        if (fed) {
            double ceiling = ceiling(masteryLevel);
            double rate = BASE_RAMP_PER_TICK + RAMP_PER_MASTERY * Math.max(0, masteryLevel);
            // Close a fraction of the remaining gap: fast at first, tapering as it fills.
            surge += (ceiling - surge) * rate;
            if (surge > ceiling) surge = ceiling;
            sustainedTicks++;
        } else {
            surge -= surge * DECAY_PER_TICK;
            if (surge < 1.0e-4) surge = 0.0;
            sustainedTicks = 0;
        }
    }

    /**
     * The most this player's beam can grow to, 0..1.
     *
     * <p>Clamped at 1 so a future mastery cap above 3 cannot silently exceed the configured
     * maxima the manager scales against.
     */
    public static double ceiling(int masteryLevel) {
        double raw = BASE_CEILING + CEILING_PER_MASTERY * Math.max(0, masteryLevel);
        return Math.min(1.0, raw);
    }

    /** Current growth, 0..1. */
    public double surge() {
        return surge;
    }

    /** Unbroken feeding ticks, for mastery accrual. */
    public int sustainedTicks() {
        return sustainedTicks;
    }

    /**
     * Coarse tier, used only to decide when to fire a feedback cue.
     *
     * <p>Cueing on a threshold crossing rather than continuously is what makes growth feel like
     * it has beats — a continuous effect scaled by surge is just a slider the player stops
     * noticing.
     */
    public int tier() {
        if (surge >= 0.75) return 3;
        if (surge >= 0.45) return 2;
        if (surge >= 0.20) return 1;
        return 0;
    }
}
