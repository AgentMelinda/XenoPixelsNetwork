package net.bullettrain.xenopixelsmod.space.event;

/** Monotonic elapsed-tick tracker that survives a client world-time correction. */
public final class StellarClientTickClock {
    private long anchorTick;
    private long lastTick;
    private long elapsedBeforeAnchor;

    public StellarClientTickClock(long initialTick) { reset(initialTick); }

    public void reset(long tick) {
        anchorTick = tick;
        lastTick = tick;
        elapsedBeforeAnchor = 0L;
    }

    public double elapsedTicks(long tick, double partialTick) {
        if (tick < lastTick) {
            long elapsed = Math.max(0L, lastTick - anchorTick);
            elapsedBeforeAnchor = saturatingAdd(elapsedBeforeAnchor, elapsed);
            anchorTick = tick;
        }
        lastTick = tick;
        long sinceAnchor = Math.max(0L, tick - anchorTick);
        double partial = Double.isFinite(partialTick) ? Math.max(0.0, Math.min(1.0, partialTick)) : 0.0;
        return (double) saturatingAdd(elapsedBeforeAnchor, sinceAnchor) + partial;
    }

    private static long saturatingAdd(long a, long b) {
        if (Long.MAX_VALUE - a < b) return Long.MAX_VALUE;
        return a + b;
    }
}
