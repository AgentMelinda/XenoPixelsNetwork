package net.bullettrain.xenopixelsmod.compat.worldgen;

/** Height-range helpers for safely keeping ZFastNoise's optimized section writer enabled. */
public final class FastNoiseHeightCompat {
    private FastNoiseHeightCompat() {
    }

    /**
     * ZFastNoise indexes chunk sections from the supplied section base. The noise range can use
     * its fast writer when every generated block falls within the world's section array.
     */
    public static boolean canUseFastNoise(int worldMinY, int worldHeight,
                                          int noiseMinY, int noiseHeight) {
        if (worldHeight <= 0 || noiseHeight <= 0) return false;
        long worldMaxY = (long) worldMinY + worldHeight;
        long noiseMaxY = (long) noiseMinY + noiseHeight;
        return worldMinY <= noiseMinY && noiseMaxY <= worldMaxY;
    }

    /** ZFastNoise uses this value only to translate absolute block Y into a section-array index. */
    public static int fastNoiseSectionBaseY(int worldMinY) {
        return worldMinY;
    }
}
