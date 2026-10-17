package net.bullettrain.xenopixelsmod.space.universe;

/** Pure bounded sampling math for map/sky presentations of a much larger known-system set. */
public final class UniverseMapLod {
    private UniverseMapLod() {}

    /**
     * Linear stride whose count never exceeds {@code maxSamples}, adjusted to avoid repeatedly sampling the same
     * column when traversing a row-major rectangular grid.
     */
    public static int stride(int width, int height, int maxSamples) {
        if (width <= 0 || height <= 0 || maxSamples <= 0
                || width > UniverseField.GRID_DIAMETER || height > UniverseField.GRID_DIAMETER)
            throw new IllegalArgumentException("invalid sampling bounds");
        return stride((long) width * height, width, maxSamples);
    }

    public static int stride1d(int size, int maxSamples) {
        if (size <= 0 || maxSamples <= 0 || size > UniverseField.GRID_CELL_COUNT)
            throw new IllegalArgumentException("invalid sampling bounds");
        return stride((long) size, size, maxSamples);
    }

    private static int stride(long cells, int cycleWidth, int maxSamples) {
        int stride = (int) Math.max(1L, (cells + maxSamples - 1L) / maxSamples);
        while (gcd(stride, cycleWidth) != 1) stride++;
        return stride;
    }

    private static int gcd(int a, int b) {
        while (b != 0) { int r = a % b; a = b; b = r; }
        return Math.abs(a);
    }
}
