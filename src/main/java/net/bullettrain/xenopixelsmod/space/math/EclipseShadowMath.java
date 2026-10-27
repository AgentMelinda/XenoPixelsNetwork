package net.bullettrain.xenopixelsmod.space.math;

/** Pure apparent-disc eclipse classification for a point on a planet surface. */
public final class EclipseShadowMath {
    public enum Shadow { NONE, PARTIAL, TOTAL }

    private EclipseShadowMath() {}

    /**
     * Classifies how much of a spherical light source is hidden from a surface point by a nearer spherical occluder.
     * All positions and radii must use the same units. This method does not schedule events or mutate chart state.
     */
    public static Shadow classify(double[] light, double lightRadius,
                                  double[] occluder, double occluderRadius, double[] surfacePoint) {
        requirePoint(light, "light");
        requirePoint(occluder, "occluder");
        requirePoint(surfacePoint, "surfacePoint");
        if (!(lightRadius > 0.0) || !Double.isFinite(lightRadius)
                || !(occluderRadius > 0.0) || !Double.isFinite(occluderRadius))
            throw new IllegalArgumentException("light and occluder radii must be finite and positive");

        double lx = light[0] - surfacePoint[0], ly = light[1] - surfacePoint[1], lz = light[2] - surfacePoint[2];
        double ox = occluder[0] - surfacePoint[0], oy = occluder[1] - surfacePoint[1], oz = occluder[2] - surfacePoint[2];
        double lightDistance = Math.sqrt(lx * lx + ly * ly + lz * lz);
        double occluderDistance = Math.sqrt(ox * ox + oy * oy + oz * oz);
        if (!(lightDistance > lightRadius) || !(occluderDistance > occluderRadius))
            throw new IllegalArgumentException("surface point must be outside both spheres");
        if (occluderDistance >= lightDistance) return Shadow.NONE;

        double cosine = (lx * ox + ly * oy + lz * oz) / (lightDistance * occluderDistance);
        double separation = Math.acos(Math.max(-1.0, Math.min(1.0, cosine)));
        double lightAngularRadius = Math.asin(lightRadius / lightDistance);
        double occluderAngularRadius = Math.asin(occluderRadius / occluderDistance);
        if (separation >= lightAngularRadius + occluderAngularRadius) return Shadow.NONE;
        if (occluderAngularRadius >= lightAngularRadius
                && separation <= occluderAngularRadius - lightAngularRadius) return Shadow.TOTAL;
        return Shadow.PARTIAL;
    }

    /**
     * Fraction (0..1) of the light source's apparent disc area hidden by the occluder, seen from a surface point.
     * Uses the angular radii and separation as plane circles, which is accurate for the small discs in the chart.
     * Same preconditions as {@link #classify}; an occluder at or beyond the light hides nothing.
     */
    public static double coverage(double[] light, double lightRadius,
                                  double[] occluder, double occluderRadius, double[] surfacePoint) {
        if (classify(light, lightRadius, occluder, occluderRadius, surfacePoint) == Shadow.NONE) return 0.0;
        double lx = light[0] - surfacePoint[0], ly = light[1] - surfacePoint[1], lz = light[2] - surfacePoint[2];
        double ox = occluder[0] - surfacePoint[0], oy = occluder[1] - surfacePoint[1], oz = occluder[2] - surfacePoint[2];
        double lightDistance = Math.sqrt(lx * lx + ly * ly + lz * lz);
        double occluderDistance = Math.sqrt(ox * ox + oy * oy + oz * oz);
        double cosine = (lx * ox + ly * oy + lz * oz) / (lightDistance * occluderDistance);
        double d = Math.acos(Math.max(-1.0, Math.min(1.0, cosine)));
        double a = Math.asin(lightRadius / lightDistance);
        double b = Math.asin(occluderRadius / occluderDistance);
        return discOverlapFraction(a, b, d);
    }

    /** Area of disc b (radius b) inside disc a (radius a), centres d apart, as a fraction of disc a's area. */
    static double discOverlapFraction(double a, double b, double d) {
        if (!(a > 0.0) || !(b > 0.0) || !(d >= 0.0)) return 0.0;
        if (d >= a + b) return 0.0;
        if (d <= Math.abs(a - b)) return b >= a ? 1.0 : (b * b) / (a * a);
        double alpha = Math.acos(Math.max(-1.0, Math.min(1.0, (d * d + a * a - b * b) / (2.0 * d * a))));
        double beta = Math.acos(Math.max(-1.0, Math.min(1.0, (d * d + b * b - a * a) / (2.0 * d * b))));
        double kite = 0.5 * Math.sqrt(Math.max(0.0, (-d + a + b) * (d + a - b) * (d - a + b) * (d + a + b)));
        double lens = a * a * alpha + b * b * beta - kite;
        return Math.max(0.0, Math.min(1.0, lens / (Math.PI * a * a)));
    }

    private static void requirePoint(double[] point, String name) {
        if (point == null || point.length != 3
                || !Double.isFinite(point[0]) || !Double.isFinite(point[1]) || !Double.isFinite(point[2]))
            throw new IllegalArgumentException(name + " must contain three finite coordinates");
    }
}
