package net.bullettrain.xenopixelsmod.space.dragonball;

/** Last and peak whole-container census cost, measured without retaining any Sable objects. */
public final class DragonBallBodyScanMetrics {
    public record Snapshot(long scans, int listedBodies, int visitedBodies, int skippedInFlight,
                           long lastNanos, long peakNanos, boolean complete) {
        public boolean hasSample() { return scans > 0; }
    }

    private long scans;
    private int listedBodies = -1;
    private int visitedBodies;
    private int skippedInFlight;
    private long lastNanos;
    private long peakNanos;
    private boolean complete;

    /** Records one census. listedBodies=-1 means enumeration failed before a list was obtained. */
    public void record(int listedBodies, int visitedBodies, int skippedInFlight, long elapsedNanos, boolean complete) {
        if (listedBodies < -1 || visitedBodies < 0 || skippedInFlight < 0 || skippedInFlight > visitedBodies || elapsedNanos < 0
                || (listedBodies < 0 && visitedBodies != 0)
                || (listedBodies >= 0 && visitedBodies > listedBodies)
                || (complete && (listedBodies < 0 || visitedBodies != listedBodies)))
            throw new IllegalArgumentException("invalid SDB body-scan metrics");
        scans++;
        this.listedBodies = listedBodies;
        this.visitedBodies = visitedBodies;
        this.skippedInFlight = skippedInFlight;
        this.lastNanos = elapsedNanos;
        this.peakNanos = Math.max(peakNanos, elapsedNanos);
        this.complete = complete;
    }

    public Snapshot snapshot() {
        return new Snapshot(scans, listedBodies, visitedBodies, skippedInFlight, lastNanos, peakNanos, complete);
    }

    /** Command-only formatting; metric writes remain allocation-free per scan. */
    public String report() {
        Snapshot value = snapshot();
        if (!value.hasSample()) return "SDB reconcile scan=not-run";
        String census = value.listedBodies() < 0 ? "bodies=unavailable"
                : "bodies=" + value.visitedBodies() + "/" + value.listedBodies();
        return "SDB reconcile scan last=" + millis(value.lastNanos()) + "ms"
                + " peak=" + millis(value.peakNanos()) + "ms " + census
                + " skipped-in-flight=" + value.skippedInFlight()
                + " status=" + (value.complete() ? "complete" : "incomplete")
                + " runs=" + value.scans();
    }

    private static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
