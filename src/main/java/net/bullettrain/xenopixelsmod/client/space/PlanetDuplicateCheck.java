package net.bullettrain.xenopixelsmod.client.space;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Pure overlap rule for the projection check: the benchmark SDB spheres ({@code bench poly}, drawn by
 * {@link DragonBallPlanetClient#render} with no check against production) duplicate any SDB slot that a production
 * path (chart SDB projection or a bound physical SDB body) also draws.
 */
final class PlanetDuplicateCheck {
    private PlanetDuplicateCheck() {}

    /** SDB slots (1..7) drawn both as a benchmark sphere and by a production path; sorted and unique. */
    static List<Integer> benchmarkOverlaps(Collection<Integer> benchmarkStars, Collection<Integer> productionSlots) {
        var overlap = new TreeSet<Integer>();
        for (Integer star : benchmarkStars)
            if (star != null && star > 0 && productionSlots.contains(star)) overlap.add(star);
        return List.copyOf(overlap);
    }

    /**
     * Whether the far projection pass would draw this body now: the distance band {@link OrbitPlanetProxies#wouldDraw}
     * reports, narrowed by the same Hakai rule the render loop applies, so a burning body whose live Sable body is
     * bound (drawn by the near renderer instead) is not reported as projected.
     */
    static boolean farProjectedNow(boolean inDistanceBand, boolean burning, boolean liveBodyBound) {
        return PlanetHakaiRenderPolicy.drawProjection(inDistanceBand, burning, liveBodyBound);
    }

    /**
     * Whether to look for an unbound live sub-level inside a far body's sphere: only when the far pass both bound it
     * recently ({@code drawnRecently}, a two-pass window) and would still draw it now, and no live body is bound. A
     * body that has since left the distance band is not a duplicate, even if its last bind is still in the window.
     */
    static boolean shouldCheckUnboundOverlap(boolean drawnRecently, boolean projectedNow, boolean liveBodyBound) {
        return drawnRecently && projectedNow && !liveBodyBound;
    }

    /**
     * Whether a projection sphere (centre, radius) intersects an axis-aligned box, such as a live Sable sub-level's
     * bounds. Non-finite input or a negative radius never overlaps.
     */
    static boolean sphereOverlapsBox(double cx, double cy, double cz, double radius,
                                     double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!finite(cx, cy, cz, radius, minX, minY, minZ, maxX, maxY, maxZ) || radius < 0) return false;
        double dx = Math.max(Math.max(minX - cx, 0.0), cx - maxX);
        double dy = Math.max(Math.max(minY - cy, 0.0), cy - maxY);
        double dz = Math.max(Math.max(minZ - cz, 0.0), cz - maxZ);
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    /** One report line per benchmark sphere. */
    static String describe(int stars, boolean alsoProduction) {
        return (alsoProduction ? "DUPLICATE " : "OK ") + "benchmark sphere sdb=" + stars
                + (alsoProduction ? " is also drawn by a production SDB projection or body (clear the bench to remove it)"
                : " (no production SDB drawn for this slot)");
    }
}
