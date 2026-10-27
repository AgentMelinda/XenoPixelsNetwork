package net.bullettrain.xenopixelsmod.space;

/** Pure conversion from the vanilla-synchronized game tick to the shared orbital-seconds coordinate. */
public final class SpaceOrbitClockSample {
    private SpaceOrbitClockSample() {}

    public static double secondsAt(long gameTick, double partialTick, double epochOffsetSeconds) {
        if (gameTick < 0) throw new IllegalArgumentException("game tick must not be negative");
        if (!Double.isFinite(partialTick) || partialTick < 0.0 || partialTick > 1.0)
            throw new IllegalArgumentException("partial tick must be in [0,1]");
        if (!Double.isFinite(epochOffsetSeconds) || Math.abs(epochOffsetSeconds) > 1.0e12)
            throw new IllegalArgumentException("invalid orbital epoch offset");
        return (gameTick + partialTick) / 20.0 + epochOffsetSeconds;
    }
}
