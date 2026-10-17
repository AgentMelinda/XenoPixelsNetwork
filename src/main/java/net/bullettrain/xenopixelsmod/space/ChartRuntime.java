package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;

/** Mutable state owned by one logical server thread; no static chart or Sable callback. */
public final class ChartRuntime {
    /** Slot indices for this runtime's probe. Kept named so /xenospace perf output stays readable. */
    public static final int SLOT_CHART_EVALUATION = 0;
    public static final int SLOT_SERVER_TICK_SPAN = 1;

    public ChartRegistry chart = new ChartRegistry();
    public final SpacePerfProbe probe = new SpacePerfProbe(2);
    public final SpaceActivationMetrics activationMetrics = new SpaceActivationMetrics();
    public long nextEvaluation;
    public int revision;
    public int solved;
    public ChartRuntime() {
        probe.name(SLOT_CHART_EVALUATION, "chart evaluation");
        probe.name(SLOT_SERVER_TICK_SPAN, "server tick span");
    }
    public void tick(long gameTime, boolean enabled, int interval, boolean measure) {
        probe.setEnabled(measure);
        if (!enabled || gameTime < nextEvaluation) return;
        nextEvaluation = gameTime + Math.max(1, interval);
        long start = System.nanoTime();
        solved = chart.evaluate(gameTime / 20.0 + net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.orbitEpochOffsetSeconds());
        probe.record(SLOT_CHART_EVALUATION, System.nanoTime() - start);
    }
    /** Records the ServerTickEvent.Pre→Post wall span; pure instrumentation, no behavior. */
    public void recordServerTickSpan(long nanos) {
        probe.record(SLOT_SERVER_TICK_SPAN, nanos);
    }
}
