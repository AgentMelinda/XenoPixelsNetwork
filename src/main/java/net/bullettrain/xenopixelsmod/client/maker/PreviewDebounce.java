package net.bullettrain.xenopixelsmod.client.maker;

/**
 * Coalesces preview rebuilds so widget edits do not rebuild every keystroke.
 *
 * <p>Spec D.3 / PR-D6b: schedule with ≤50 ms delay; {@link #shouldFire(long)} is true once
 * {@code nowMs} reaches the due time. Pure timing — no Minecraft / renderer dependency.
 */
public final class PreviewDebounce {
    /** Default quiet period after the last edit before a preview rebuild (spec ≤50 ms). */
    public static final long DEFAULT_MS = 50L;

    private final long delayMs;
    private long dueAtMs = Long.MIN_VALUE;

    public PreviewDebounce() {
        this(DEFAULT_MS);
    }

    public PreviewDebounce(long delayMs) {
        if (delayMs < 0L) {
            throw new IllegalArgumentException("delayMs must be >= 0");
        }
        this.delayMs = delayMs;
    }

    public long delayMs() {
        return delayMs;
    }

    /** Arms (or restarts) the debounce window from {@code nowMs}. */
    public void schedule(long nowMs) {
        dueAtMs = nowMs + delayMs;
    }

    /** Alias for {@link #schedule(long)} (plan PR-D6b {@code markChanged}). */
    public void markChanged(long nowMs) {
        schedule(nowMs);
    }

    /** True when a rebuild was scheduled and {@code nowMs} is at or past the due time. */
    public boolean shouldFire(long nowMs) {
        return dueAtMs != Long.MIN_VALUE && nowMs >= dueAtMs;
    }

    /** Clears a pending rebuild after it has been applied (or cancelled). */
    public void clear() {
        dueAtMs = Long.MIN_VALUE;
    }

    public boolean pending() {
        return dueAtMs != Long.MIN_VALUE;
    }
}
