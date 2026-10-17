package net.bullettrain.xenopixelsmod.space.asteroid;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Resumable deterministic cursor over a rectangular asteroid-field cell volume. A caller can bound
 * cell visits per server pass without allocating a list proportional to activation radius.
 */
final class AsteroidCellScanCursor {
    record Bounds(long minX, long maxX, long minY, long maxY, long minZ, long maxZ) {
        Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ)
                throw new IllegalArgumentException("cell bounds must be ordered");
            // Validate the complete volume now so positionAt() can use checked dimensions safely.
            Math.multiplyExact(Math.multiplyExact(width(minX, maxX), width(minY, maxY)), width(minZ, maxZ));
        }

        private static long width(long min, long max) {
            return Math.addExact(Math.subtractExact(max, min), 1L);
        }

        long size() {
            return Math.multiplyExact(Math.multiplyExact(width(minX, maxX), width(minY, maxY)), width(minZ, maxZ));
        }

        AsteroidField.Cell positionAt(long index) {
            long size = size();
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException("cell index " + index + " / " + size);
            long widthX = width(minX, maxX), widthY = width(minY, maxY);
            long x = index % widthX;
            long y = index / widthX % widthY;
            long z = index / Math.multiplyExact(widthX, widthY);
            return new AsteroidField.Cell(minX + x, minY + y, minZ + z);
        }
    }

    record Result(int visited, boolean completedSweep, long nextIndex) {}

    private final Bounds bounds;
    private long nextIndex;

    AsteroidCellScanCursor(Bounds bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    Result visit(int budget, Consumer<AsteroidField.Cell> visitor) {
        if (budget < 1) throw new IllegalArgumentException("scan budget must be positive");
        Objects.requireNonNull(visitor, "visitor");
        long remaining = bounds.size() - nextIndex;
        int visited = (int) Math.min((long) budget, remaining);
        for (int i = 0; i < visited; i++) visitor.accept(bounds.positionAt(nextIndex++));
        boolean completed = nextIndex == bounds.size();
        if (completed) nextIndex = 0;
        return new Result(visited, completed, nextIndex);
    }

    long nextIndex() { return nextIndex; }

    Bounds bounds() { return bounds; }
}
