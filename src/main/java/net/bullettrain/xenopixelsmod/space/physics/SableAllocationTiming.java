package net.bullettrain.xenopixelsmod.space.physics;

/** Scoped timings for Sable's synchronous allocation observer callbacks. */
public final class SableAllocationTiming {
    private static final ThreadLocal<Sample> ACTIVE = new ThreadLocal<>();

    private SableAllocationTiming() {}

    public static Sample begin() {
        if (ACTIVE.get() != null) throw new IllegalStateException("Sable allocation timing already active on this thread");
        Sample sample = new Sample();
        ACTIVE.set(sample);
        return sample;
    }

    public static boolean isActive() { return ACTIVE.get() != null; }

    public static void recordMassTracker(long nanos) {
        Sample sample = ACTIVE.get();
        if (sample != null && nanos > 0) sample.massTrackerNanos += nanos;
    }

    public static void recordPipelineAdd(long nanos) {
        Sample sample = ACTIVE.get();
        if (sample != null && nanos > 0) sample.pipelineAddNanos += nanos;
    }

    public static void recordSubLevelCreate(long nanos) {
        Sample sample = ACTIVE.get();
        if (sample != null && nanos > 0) sample.subLevelCreateNanos += nanos;
    }

    public static void recordPlotConstruction(long nanos) {
        Sample sample = ACTIVE.get();
        if (sample != null && nanos > 0) sample.plotConstructionNanos += nanos;
    }

    public static void end(Sample expected) {
        if (ACTIVE.get() != expected) throw new IllegalStateException("Sable allocation timing scope mismatch");
        ACTIVE.remove();
    }

    public static final class Sample {
        private long massTrackerNanos;
        private long pipelineAddNanos;
        private long subLevelCreateNanos;
        private long plotConstructionNanos;

        public double subLevelCreateMillis() { return subLevelCreateNanos / 1_000_000.0; }
        public double plotConstructionMillis() { return plotConstructionNanos / 1_000_000.0; }
        public double massTrackerMillis() { return massTrackerNanos / 1_000_000.0; }
        public double pipelineAddMillis() { return pipelineAddNanos / 1_000_000.0; }
    }
}
