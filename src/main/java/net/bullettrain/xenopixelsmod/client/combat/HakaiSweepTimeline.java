package net.bullettrain.xenopixelsmod.client.combat;

/** Frame-sampled body sweep, following the planet's continuous clock with smooth server corrections. */
final class HakaiSweepTimeline {
    private static final double CORRECTION_TICKS = 5.0;
    private final double anchor;
    private final float from;
    private final float rate;
    private final float correction;
    private final int direction;

    private HakaiSweepTimeline(double anchor, float from, float rate, float correction, int direction) {
        this.anchor = anchor;
        this.from = from;
        this.rate = rate;
        this.correction = correction;
        this.direction = direction;
    }

    static HakaiSweepTimeline start(double now, float progress, float rate) {
        return new HakaiSweepTimeline(now, progress, rate, 0f, (int) Math.signum(rate));
    }

    HakaiSweepTimeline correct(double now, float progress, float nextRate) {
        float shown = HakaiSweepRenderPolicy.reanchorProgress(rate, at(now), progress, false);
        float error = progress - shown;
        // smoothstep's maximum derivative is 1.5. Bound the correction so an erase cannot
        // move upward (or a restore downward) merely because a delayed packet arrived.
        float limit = (float) (Math.abs(nextRate) * CORRECTION_TICKS / 1.5);
        if (nextRate > 0f) error = Math.max(-limit, error);
        else if (nextRate < 0f) error = Math.min(limit, error);
        else if (direction > 0) error = Math.max(0f, error);
        else if (direction < 0) error = Math.min(0f, error);
        int nextDirection = nextRate == 0f ? direction : (int) Math.signum(nextRate);
        return new HakaiSweepTimeline(now, shown, nextRate, error, nextDirection);
    }

    float at(double now) {
        double elapsed = Math.max(0.0, now - anchor);
        double t = Math.min(1.0, elapsed / CORRECTION_TICKS);
        double eased = t * t * (3.0 - 2.0 * t);
        return (float) Math.clamp(from + rate * elapsed + correction * eased, 0.0, 1.0);
    }
}
