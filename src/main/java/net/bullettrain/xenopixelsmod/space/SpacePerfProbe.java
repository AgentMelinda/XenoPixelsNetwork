package net.bullettrain.xenopixelsmod.space;

/**
 * Allocation-free per-pass timing for the space scheduler.
 *
 * <p>§21 sets budgets — a Kepler solve under ~0.3 ms, a gravity sum for a hundred ships under 0.5 ms,
 * fifty idle bodies costing under 1 ms/tick overall — and the house rule is that none of those numbers
 * may be quoted without a measurement behind them. This is the measurement. It exists so a budget can
 * be checked in a live world rather than assumed from a design document.
 *
 * <p>An instance per scheduler, not global statics: two integrated servers in one JVM (a client's
 * single-player world plus a lan host) must not fold their timings into one set of counters.
 *
 * <p>Reported as an exponentially weighted moving average rather than a raw last-value, because a
 * single GC pause would otherwise dominate a reading and hide a real regression. The window is short
 * enough that a genuine slowdown still shows within a second.
 */
public final class SpacePerfProbe {

    /** Weight of the newest sample; 1/16 is roughly the last 0.8 s at 20 ticks per second. */
    private static final double SAMPLE_WEIGHT = 1.0 / 16.0;

    private final String[] names;
    private final long[] emaNanos;
    private final long[] lastNanos;
    private final long[] peakNanos;
    private final long[] runs;
    private final long[] totalNanos;
    private final long[][] samples;

    private boolean enabled = true;

    public SpacePerfProbe(int passCount) {
        int count = Math.max(0, passCount);
        this.names = new String[count];
        this.emaNanos = new long[count];
        this.lastNanos = new long[count];
        this.peakNanos = new long[count];
        this.runs = new long[count];
        this.totalNanos = new long[count];
        this.samples = new long[count][2048];
    }

    public void name(int slot, String name) {
        if (slot >= 0 && slot < names.length) names[slot] = name;
    }

    /**
     * Records one sample. Cheap enough to leave on: two long writes and one multiply per pass per
     * tick, against a pass that just did real work.
     */
    public void record(int slot, long nanos) {
        if (!enabled || slot < 0 || slot >= runs.length || nanos < 0) return;
        lastNanos[slot] = nanos;
        totalNanos[slot] += nanos;
        samples[slot][(int) (runs[slot] % samples[slot].length)] = nanos;
        runs[slot]++;
        if (nanos > peakNanos[slot]) peakNanos[slot] = nanos;
        long previous = emaNanos[slot];
        emaNanos[slot] = previous == 0
                ? nanos
                : (long) (previous + (nanos - previous) * SAMPLE_WEIGHT);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void reset() {
        java.util.Arrays.fill(emaNanos, 0L);
        java.util.Arrays.fill(lastNanos, 0L);
        java.util.Arrays.fill(peakNanos, 0L);
        java.util.Arrays.fill(runs, 0L);
        java.util.Arrays.fill(totalNanos, 0L);
        for (long[] sample : samples) java.util.Arrays.fill(sample, 0L);
    }

    /** Smoothed cost of one pass in microseconds, for budget assertions. */
    public double emaMicros(int slot) {
        return slot >= 0 && slot < emaNanos.length ? emaNanos[slot] / 1000.0 : 0.0;
    }

    public double peakMicros(int slot) {
        return slot >= 0 && slot < peakNanos.length ? peakNanos[slot] / 1000.0 : 0.0;
    }

    /** Percentile of the last 2048 raw samples. Allocates only when explicitly queried. */
    public double percentileMicros(int slot, double percentile) {
        if (slot < 0 || slot >= samples.length || runs[slot] == 0) return 0;
        int size = (int) Math.min(runs[slot], samples[slot].length);
        long[] sorted = java.util.Arrays.copyOf(samples[slot], size);
        java.util.Arrays.sort(sorted);
        int index = Math.max(0, Math.min(size - 1, (int) Math.ceil(percentile * size) - 1));
        return sorted[index] / 1000.0;
    }

    public long runs(int slot) {
        return slot >= 0 && slot < runs.length ? runs[slot] : 0L;
    }

    /** Every pass's smoothed cost, summed — the scheduler's total contribution to the physics tick. */
    public double totalEmaMicros() {
        long sum = 0;
        for (long value : emaNanos) sum += value;
        return sum / 1000.0;
    }

    /** One line per pass for {@code /xenospace perf}. Allocates, so it is a command path only. */
    public String report() {
        StringBuilder out = new StringBuilder(64 + names.length * 48);
        out.append("space passes=").append(names.length)
                .append(" probe=").append(enabled ? "on" : "off")
                .append(" total=").append(format(totalEmaMicros())).append("us");
        for (int i = 0; i < names.length; i++) {
            out.append("\n  ").append(names[i] == null ? "#" + i : names[i])
                    .append(" ema=").append(format(emaMicros(i))).append("us")
                    .append(" peak=").append(format(peakMicros(i))).append("us")
                    .append(" p50=").append(format(percentileMicros(i, .50))).append("us")
                    .append(" p95=").append(format(percentileMicros(i, .95))).append("us")
                    .append(" p99=").append(format(percentileMicros(i, .99))).append("us")
                    .append(" runs=").append(runs[i]);
        }
        return out.toString();
    }

    /** Three decimals without allocating a formatter on a path that only runs for a command. */
    private static String format(double micros) {
        return String.format(java.util.Locale.ROOT, "%.3f", micros);
    }
}
