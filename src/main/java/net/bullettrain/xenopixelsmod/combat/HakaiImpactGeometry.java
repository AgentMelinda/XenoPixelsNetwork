package net.bullettrain.xenopixelsmod.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pure geometry shared by the server's impact capture and the client's impact-origin dissolve. */
public final class HakaiImpactGeometry {
    private HakaiImpactGeometry() {}

    /** Normalizes a world point to a bounding box, clamped so network data always stays on the target. */
    public static float[] normalize(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                    double x, double y, double z) {
        return new float[]{unit((x - minX) / Math.max(1.0e-6, maxX - minX)),
                unit((y - minY) / Math.max(1.0e-6, maxY - minY)),
                unit((z - minZ) / Math.max(1.0e-6, maxZ - minZ))};
    }

    /**
     * Captures the point where the caster's aim ray meets a target. The nearest-point fallback
     * preserves the forgiving entity-target selection used by Hakai when the aim ray narrowly
     * misses the hit box.
     */
    public static Vec3 impactPoint(Vec3 eye, Vec3 look, AABB target, double pickRadius, double range) {
        if (eye == null || look == null || target == null || !Double.isFinite(range) || range <= 0.0) {
            throw new IllegalArgumentException("eye, look, target, and a positive finite range are required");
        }
        Vec3 direction = look.lengthSqr() < 1.0e-12 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 end = eye.add(direction.scale(range));
        return target.inflate(Math.max(0.0, pickRadius)).clip(eye, end)
                .map(hit -> closestPoint(target, hit))
                .orElseGet(() -> {
                    Vec3 centre = new Vec3((target.minX + target.maxX) * 0.5,
                            (target.minY + target.maxY) * 0.5, (target.minZ + target.maxZ) * 0.5);
                    double along = Math.max(0.0, Math.min(range, centre.subtract(eye).dot(direction)));
                    return closestPoint(target, eye.add(direction.scale(along)));
                });
    }

    /** Smoothly expands the erase radius from zero to the farthest body corner. */
    public static float radius(float maximum, float progress) {
        float p = unit(progress);
        float eased = p * p * (3.0f - 2.0f * p);
        return Math.max(0.0f, maximum) * eased;
    }

    /** Smooth violet boundary intensity on the still-visible surface outside an expanding cut. */
    public static float boundaryGlow(double surfaceDistance, double cutRadius, double width) {
        if (!Double.isFinite(surfaceDistance) || !Double.isFinite(cutRadius)
                || !Double.isFinite(width) || width <= 0.0 || surfaceDistance < cutRadius) return 0.0f;
        double outside = surfaceDistance - cutRadius;
        if (outside >= width) return 0.0f;
        double t = Math.max(0.0, Math.min(1.0, outside / width));
        double smooth = t * t * (3.0 - 2.0 * t);
        return (float) (1.0 - smooth);
    }

    public static boolean erased(float distance, float radius) { return distance <= radius; }

    /** Closest valid point on a target box to the aim ray, including forgiving cone-target fallback. */
    public static Vec3 closestPoint(AABB box, Vec3 point) {
        return new Vec3(clamp(point.x, box.minX, box.maxX), clamp(point.y, box.minY, box.maxY),
                clamp(point.z, box.minZ, box.maxZ));
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    private static float unit(double value) {
        if (!Double.isFinite(value)) return 0.5f;
        return (float) Math.max(0.0, Math.min(1.0, value));
    }
}
