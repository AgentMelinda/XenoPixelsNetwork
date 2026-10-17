package net.bullettrain.xenopixelsmod.combat;

/**
 * Whether a DragonMineZ lock-on that just dropped should be re-pinned. Minecraft-free.
 *
 * <p>Owner request 2026-10-09: an attack that fails to execute must not cost the fighter's
 * lock. DragonMineZ drops the lock client-side within a quarter of a second when its own tick
 * rules fail (death, range, line of sight), which a launched or repositioning target can trip
 * while a move is still resolving. The keeper restores such a drop — but only inside those same
 * rules, so a target that is truly gone, out of range, or out of sight stays unlocked, and an
 * explicit unlock through the lock key is never overridden.
 */
public final class StickyLockRules {

    /** Milliseconds after an explicit lock-key press in which a drop is never restored. */
    public static final long SUPPRESS_MS = 600;

    private StickyLockRules() {}

    /**
     * @param lockPresent  a lock is held right now
     * @param targetValid  the remembered target still passes DragonMineZ's own lock rules
     * @param sinceSeenMs  milliseconds since the lock was last seen on the remembered target
     * @param graceMs      how long a drop may be undone; 0 disables the keeper
     * @param suppressActive within the window after an explicit lock-key press
     */
    public static boolean restore(boolean lockPresent, boolean targetValid,
                                  long sinceSeenMs, long graceMs, boolean suppressActive) {
        if (lockPresent || !targetValid || suppressActive) return false;
        return graceMs > 0 && sinceSeenMs <= graceMs;
    }
}
