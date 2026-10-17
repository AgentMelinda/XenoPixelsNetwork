package net.bullettrain.xenopixelsmod.space.asteroid;

/** Pure indexing helpers for the bounded fragment expiry scan. Bounds are inclusive. */
final class AsteroidFragmentScanMath {
    private AsteroidFragmentScanMath() {}

    static long volume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long sx = (long) maxX - minX + 1;
        long sy = (long) maxY - minY + 1;
        long sz = (long) maxZ - minZ + 1;
        if (sx <= 0 || sy <= 0 || sz <= 0) return 0;
        try {
            return Math.multiplyExact(Math.multiplyExact(sx, sy), sz);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /** x-major, then y, then z, matching the box-walk order in other Sable host-plot scans. */
    static int[] positionAt(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, long index) {
        long volume = volume(minX, minY, minZ, maxX, maxY, maxZ);
        if (index < 0 || index >= volume) throw new IndexOutOfBoundsException("index=" + index + ", volume=" + volume);
        long sy = (long) maxY - minY + 1;
        long sz = (long) maxZ - minZ + 1;
        long yz = sy * sz;
        int x = (int) (minX + index / yz);
        long remainder = index % yz;
        int y = (int) (minY + remainder / sz);
        int z = (int) (minZ + remainder % sz);
        return new int[]{x, y, z};
    }
}
