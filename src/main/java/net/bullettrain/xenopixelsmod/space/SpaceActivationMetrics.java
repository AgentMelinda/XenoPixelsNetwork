package net.bullettrain.xenopixelsmod.space;

import java.util.Locale;

/**
 * Bounded, command-path observability for generated Sable-body activations.
 *
 * <p>Events are recorded only when a body is successfully built or released, never from the per-tick
 * measurement path. Last-minute counts use wall time rather than game ticks so low TPS does not make
 * the reported rate misleading.
 */
public final class SpaceActivationMetrics {
    private static final long WINDOW_NANOS = 60_000_000_000L;
    private static final int EVENT_CAPACITY = 8192;

    public enum Owner { CHART_PLANET, ASTEROID, SMALL_PLANET, SUPER_DRAGON_BALL }

    private record Event(long atNanos, Owner owner, boolean activation) {}
    private record Build(double fillMillis, double installMillis, double maxServerSliceMillis, int voxels) {}

    public record Snapshot(long activationsLastMinute, long releasesLastMinute, int lastBuildVoxels,
                           double lastFillMillis, double lastInstallMillis, double lastMaxServerSliceMillis,
                           boolean minuteWindowComplete) {}

    private final Event[] events = new Event[EVENT_CAPACITY];
    private final Build[] lastBuild = new Build[Owner.values().length];
    private int nextEvent;
    private int eventCount;
    private long mostRecentlyOverwrittenNanos = Long.MIN_VALUE;

    public void recordBuild(Owner owner, long nowNanos, int voxels, double fillMillis,
                            double installMillis, double maxServerSliceMillis) {
        if (owner == null || voxels < 0 || !finiteNonNegative(fillMillis)
                || !finiteNonNegative(installMillis) || !finiteNonNegative(maxServerSliceMillis)) return;
        append(new Event(nowNanos, owner, true));
        lastBuild[owner.ordinal()] = new Build(fillMillis, installMillis, maxServerSliceMillis, voxels);
    }

    public void recordRelease(Owner owner, long nowNanos) {
        if (owner == null) return;
        append(new Event(nowNanos, owner, false));
    }

    private void append(Event event) {
        if (eventCount == events.length) {
            Event overwritten = events[nextEvent];
            if (overwritten != null) mostRecentlyOverwrittenNanos = overwritten.atNanos();
        } else {
            eventCount++;
        }
        events[nextEvent] = event;
        nextEvent = (nextEvent + 1) % events.length;
    }

    /** Snapshot is intended for the explicit `/xenospace perf` command, not a tick callback. */
    public Snapshot snapshot(Owner owner, long nowNanos) {
        if (owner == null) throw new IllegalArgumentException("owner is required");
        long activations = 0, releases = 0;
        int oldest = Math.floorMod(nextEvent - eventCount, events.length);
        for (int i = 0; i < eventCount; i++) {
            Event event = events[(oldest + i) % events.length];
            if (event == null || event.owner() != owner) continue;
            long age = nowNanos - event.atNanos();
            if (age < 0 || age > WINDOW_NANOS) continue;
            if (event.activation()) activations++; else releases++;
        }
        Build build = lastBuild[owner.ordinal()];
        long overwrittenAge = nowNanos - mostRecentlyOverwrittenNanos;
        boolean complete = mostRecentlyOverwrittenNanos == Long.MIN_VALUE || overwrittenAge < 0 || overwrittenAge > WINDOW_NANOS;
        return new Snapshot(activations, releases, build == null ? 0 : build.voxels(),
                build == null ? 0 : build.fillMillis(), build == null ? 0 : build.installMillis(),
                build == null ? 0 : build.maxServerSliceMillis(), complete);
    }

    public String report(long nowNanos) {
        StringBuilder out = new StringBuilder("generated bodies, last 60s (wall clock):");
        for (Owner owner : Owner.values()) {
            Snapshot s = snapshot(owner, nowNanos);
            out.append("\n  ").append(owner.name().toLowerCase(Locale.ROOT))
                    .append(" activations=").append(s.activationsLastMinute())
                    .append(" releases=").append(s.releasesLastMinute())
                    .append(" lastBuildVoxels=").append(s.lastBuildVoxels())
                    .append(" fillBackgroundMs=").append(format(s.lastFillMillis()))
                    .append(" installTotalMs=").append(format(s.lastInstallMillis()))
                    .append(" installMaxServerSliceMs=").append(format(s.lastMaxServerSliceMillis()))
                    .append(" window=").append(s.minuteWindowComplete() ? "complete" : "truncated");
        }
        return out.toString();
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
