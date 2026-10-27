package net.bullettrain.xenopixelsmod.space.event;

/** Bounded retry schedule for a scripted nova whose chart position cannot be represented as a wave origin. */
public final class StellarOriginRetryPolicy {
    public record Retry(int chartRevision, long retryAtTick) {}

    private StellarOriginRetryPolicy() {}

    public static boolean shouldAttempt(Retry retry, int chartRevision, long nowTick) {
        return retry == null || retry.chartRevision() != chartRevision || nowTick >= retry.retryAtTick();
    }

    public static Retry failed(int chartRevision, long nowTick, long backoffTicks) {
        if (nowTick < 0L || backoffTicks < 1L) throw new IllegalArgumentException("invalid origin retry timing");
        long retryAt = nowTick > Long.MAX_VALUE - backoffTicks ? Long.MAX_VALUE : nowTick + backoffTicks;
        return new Retry(chartRevision, retryAt);
    }
}
