package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidField;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidFieldSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Pure, bounded client selection for the distant asteroid renderer. */
public final class AsteroidRenderPlan {
    public static final int MAX_VISIBLE = 512;
    public static final int MAX_CELL_QUERIES = 65_536;
    private static final double CELL_HALF_DIAGONAL = AsteroidField.CELL_SIZE * Math.sqrt(3.0) / 2.0;

    private AsteroidRenderPlan() {}

    /**
     * Returns nearest deterministic candidates in the camera sphere. Generation touches no Minecraft or GL
     * state and is safe to run on a worker. The query ceiling bounds even a malformed/custom belt set.
     */
    public static java.util.List<AsteroidField.Candidate> visible(AsteroidFieldSnapshot field,
            Set<AsteroidField.ObjectId> physicallyPresent,
            double cameraX, double cameraY, double cameraZ, double drawRange, int visibleCap) {
        if (field == null || physicallyPresent == null) throw new IllegalArgumentException("field and presence set are required");
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraY) || !Double.isFinite(cameraZ)
                || !Double.isFinite(drawRange) || drawRange <= 0 || drawRange > 4096
                || visibleCap < 0 || visibleCap > MAX_VISIBLE)
            throw new IllegalArgumentException("invalid asteroid render bounds");
        if (visibleCap == 0 || field.belts().isEmpty()) return java.util.List.of();

        long minX = cell(cameraX - drawRange), maxX = cell(cameraX + drawRange);
        long minY = cell(cameraY - drawRange), maxY = cell(cameraY + drawRange);
        long minZ = cell(cameraZ - drawRange), maxZ = cell(cameraZ + drawRange);
        long perBelt = boundedVolume(minX, maxX, minY, maxY, minZ, maxZ);
        long queries = perBelt * field.belts().size();
        if (queries > MAX_CELL_QUERIES)
            throw new IllegalArgumentException("asteroid render cell-query budget exceeded: " + queries);

        double rangeSq = drawRange * drawRange;
        Map<AsteroidField.ObjectId, AsteroidField.Candidate> candidates = new HashMap<>();
        for (AsteroidField.Belt belt : field.belts()) {
            for (long cy = minY; cy <= maxY; cy++) {
                for (long cz = minZ; cz <= maxZ; cz++) {
                    for (long cx = minX; cx <= maxX; cx++) {
                        if (!cellTouchesBelt(cx, cy, cz, belt)) continue;
                        for (AsteroidField.Candidate candidate : AsteroidField.inCell(field.fieldSeed(),
                                new AsteroidField.Cell(cx, cy, cz), belt)) {
                            if (physicallyPresent.contains(candidate.id())) continue;
                            double dx = candidate.x() - cameraX;
                            double dy = candidate.y() - cameraY;
                            double dz = candidate.z() - cameraZ;
                            if (dx * dx + dy * dy + dz * dz <= rangeSq)
                                candidates.putIfAbsent(candidate.id(), candidate);
                        }
                    }
                }
            }
        }

        var ordered = new ArrayList<>(candidates.values());
        ordered.sort(Comparator.comparingDouble((AsteroidField.Candidate c) -> distanceSq(c, cameraX, cameraY, cameraZ))
                .thenComparing(AsteroidField.Candidate::id));
        if (ordered.size() > visibleCap) ordered.subList(visibleCap, ordered.size()).clear();
        return java.util.List.copyOf(ordered);
    }

    private static long cell(double coordinate) {
        return (long) Math.floor(coordinate / AsteroidField.CELL_SIZE);
    }

    private static long boundedVolume(long minX, long maxX, long minY, long maxY, long minZ, long maxZ) {
        long x = maxX - minX + 1, y = maxY - minY + 1, z = maxZ - minZ + 1;
        if (x <= 0 || y <= 0 || z <= 0 || x > MAX_CELL_QUERIES || y > MAX_CELL_QUERIES || z > MAX_CELL_QUERIES)
            return MAX_CELL_QUERIES + 1L;
        if (x > MAX_CELL_QUERIES / y || x * y > MAX_CELL_QUERIES / z) return MAX_CELL_QUERIES + 1L;
        return x * y * z;
    }

    private static boolean cellTouchesBelt(long cellX, long cellY, long cellZ, AsteroidField.Belt belt) {
        double minX = cellX * (double) AsteroidField.CELL_SIZE;
        double minY = cellY * (double) AsteroidField.CELL_SIZE;
        double minZ = cellZ * (double) AsteroidField.CELL_SIZE;
        double maxX = minX + AsteroidField.CELL_SIZE;
        double maxY = minY + AsteroidField.CELL_SIZE;
        double maxZ = minZ + AsteroidField.CELL_SIZE;
        if (maxY < belt.centerY() - belt.halfThickness() - CELL_HALF_DIAGONAL
                || minY > belt.centerY() + belt.halfThickness() + CELL_HALF_DIAGONAL) return false;

        double nearestX = belt.centerX() < minX ? minX - belt.centerX()
                : belt.centerX() > maxX ? belt.centerX() - maxX : 0;
        double nearestZ = belt.centerZ() < minZ ? minZ - belt.centerZ()
                : belt.centerZ() > maxZ ? belt.centerZ() - maxZ : 0;
        double farX = Math.max(Math.abs(minX - belt.centerX()), Math.abs(maxX - belt.centerX()));
        double farZ = Math.max(Math.abs(minZ - belt.centerZ()), Math.abs(maxZ - belt.centerZ()));
        double minRadius = Math.hypot(nearestX, nearestZ);
        double maxRadius = Math.hypot(farX, farZ);
        return minRadius <= belt.outerRadius() + CELL_HALF_DIAGONAL
                && maxRadius + CELL_HALF_DIAGONAL >= belt.innerRadius();
    }

    private static double distanceSq(AsteroidField.Candidate candidate, double x, double y, double z) {
        double dx = candidate.x() - x, dy = candidate.y() - y, dz = candidate.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
