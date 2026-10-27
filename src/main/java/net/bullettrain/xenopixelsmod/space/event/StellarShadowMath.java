package net.bullettrain.xenopixelsmod.space.event;

import java.util.List;

/**
 * Finite-star umbra geometry for §23.1 host-space shelter of players and ship centers.
 * Eclipse surface coverage uses its separate apparent-disc model.
 */
final class StellarShadowMath {
    record Occluder(double x, double y, double z, double radius) {
        Occluder {
            if (!coordinate(x) || !coordinate(y) || !coordinate(z)
                    || !Double.isFinite(radius) || radius <= 0.0)
                throw new IllegalArgumentException("invalid stellar shadow occluder");
        }
    }

    private StellarShadowMath() {}

    /** True only when the expanding wave currently crosses a point that is inside a planet's umbra. */
    static boolean blocksWave(StellarBlastWaveMath.Wave wave, long nowTick,
            double starRadius, double pointX, double pointY, double pointZ, List<Occluder> occluders) {
        return wave != null && blocksWave(StellarBlastWaveMath.exposes(wave, pointX, pointY, pointZ, nowTick),
                wave.originX(), wave.originY(), wave.originZ(), starRadius,
                pointX, pointY, pointZ, occluders);
    }

    /** Shared production predicate when callers already computed whether the wave crosses the point. */
    static boolean blocksWave(boolean crossing, double starX, double starY, double starZ, double starRadius,
            double pointX, double pointY, double pointZ, List<Occluder> occluders) {
        return crossing && sheltered(starX, starY, starZ, starRadius,
                pointX, pointY, pointZ, occluders);
    }

    /** True when a spherical chart body lies between the star and point, inside its finite-source umbra. */
    static boolean sheltered(double starX, double starY, double starZ, double starRadius,
            double pointX, double pointY, double pointZ, List<Occluder> occluders) {
        if (!coordinate(starX) || !coordinate(starY) || !coordinate(starZ)
                || !coordinate(pointX) || !coordinate(pointY) || !coordinate(pointZ)
                || !Double.isFinite(starRadius) || starRadius <= 0.0 || occluders == null || occluders.isEmpty())
            return false;

        double rayX = pointX - starX;
        double rayY = pointY - starY;
        double rayZ = pointZ - starZ;
        double rayLength = Math.hypot(Math.hypot(rayX, rayY), rayZ);
        if (!(rayLength > 0.0) || !Double.isFinite(rayLength)) return false;
        double rayLengthSquared = rayLength * rayLength;
        if (!Double.isFinite(rayLengthSquared)) return false;

        for (Occluder body : occluders) {
            if (body == null) continue;
            double fromStarX = body.x() - starX;
            double fromStarY = body.y() - starY;
            double fromStarZ = body.z() - starZ;
            double projection = (fromStarX * rayX + fromStarY * rayY + fromStarZ * rayZ) / rayLengthSquared;
            if (!(projection > 0.0 && projection < 1.0)) continue;

            double axisX = projection * rayX;
            double axisY = projection * rayY;
            double axisZ = projection * rayZ;
            double offsetX = fromStarX - axisX;
            double offsetY = fromStarY - axisY;
            double offsetZ = fromStarZ - axisZ;
            double perpendicular = Math.hypot(Math.hypot(offsetX, offsetY), offsetZ);
            double starToBody = projection * rayLength;
            double bodyToPoint = (1.0 - projection) * rayLength;
            if (!(starToBody > 0.0) || !Double.isFinite(perpendicular)) continue;

            // Similar triangles: the umbra narrows when the star is larger than the planet and widens
            // when the occluding world is larger than the emitting star. No arbitrary padding is added.
            double shadowRadius = body.radius()
                    + bodyToPoint * (body.radius() - starRadius) / starToBody;
            if (shadowRadius > 0.0 && perpendicular <= shadowRadius) return true;
        }
        return false;
    }

    private static boolean coordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= StellarBlastWaveMath.MAX_RADIUS_BLOCKS;
    }
}
