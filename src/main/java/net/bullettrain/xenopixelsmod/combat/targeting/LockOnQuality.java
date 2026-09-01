package net.bullettrain.xenopixelsmod.combat.targeting;

/** How solid a lock is, driven by {@link LockOnState}'s progress. */
public enum LockOnQuality {
    /** Progress just started; conditions are valid but not held long enough to trust yet. */
    WEAK,
    /** Progress is advancing steadily inside the hard cone. */
    SOFT,
    /** Progress is complete: a full lock, eligible for lead assist. */
    HARD;

    public static LockOnQuality fromProgress(double progress) {
        if (progress >= 1.0) return HARD;
        if (progress >= 0.35) return SOFT;
        return WEAK;
    }
}
