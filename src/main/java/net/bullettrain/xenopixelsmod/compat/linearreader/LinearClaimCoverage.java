package net.bullettrain.xenopixelsmod.compat.linearreader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Immutable server-thread snapshots; conversion workers never traverse YAWP's mutable data. */
public final class LinearClaimCoverage {
    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static volatile Map<String, Dimension> snapshot = Map.of();
    private static long generation;

    private LinearClaimCoverage() {}

    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean covers(long x, long z, int bottom, int top) {
            return minX <= x && maxX >= x + 511 && minZ <= z && maxZ >= z + 511
                    && minY <= bottom && maxY >= top;
        }
    }

    public record Dimension(int bottom, int top, List<Box> claims) {
        public Dimension { claims = List.copyOf(claims); }
    }

    public static synchronized void invalidate() { generation++; snapshot = Map.of(); }
    public static synchronized long generation() { return generation; }
    public static synchronized void publish(long expected, Map<String, Dimension> dimensions) {
        if (expected == generation) snapshot = Map.copyOf(dimensions);
    }

    public static boolean covers(String dimension, Path path) {
        if (dimension == null || path == null || path.getFileName() == null) return false;
        var match = REGION.matcher(path.getFileName().toString());
        if (!match.matches()) return false;
        try {
            // Long arithmetic avoids wrapping extreme / malformed file coordinates into a claim.
            long x = Math.multiplyExact(Long.parseLong(match.group(1)), 512L);
            long z = Math.multiplyExact(Long.parseLong(match.group(2)), 512L);
            var data = snapshot.get(dimension);
            return data != null && data.bottom <= data.top && data.claims.stream()
                    .anyMatch(box -> box.covers(x, z, data.bottom, data.top));
        } catch (NumberFormatException | ArithmeticException ignored) { return false; }
    }
}
