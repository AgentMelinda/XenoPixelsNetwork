package net.bullettrain.xenopixelsmod.combat.v3;

/** Server-tick recovery; only distinct admitted presses accelerate the natural timer. */
public final class RecoveryProgress {
    private final long started;
    private final int total;
    private int bonus;
    private long lastPress = Long.MIN_VALUE;
    public RecoveryProgress(long started, int total) {
        this.started = started;
        this.total = Math.clamp(total, 1, 1200);
    }
    public int total() { return total; }
    public int completed(long now) { return (int) Math.clamp(now-started+bonus, 0, total); }
    public int remaining(long now) { return total-completed(now); }
    public boolean press(long now) {
        if (now < started || remaining(now) == 0
                || lastPress != Long.MIN_VALUE && now-lastPress < 2) return false;
        lastPress = now;
        bonus = Math.min(total, bonus+4);
        return true;
    }
}
