package net.bullettrain.xenopixelsmod.space.event;

import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.math.EclipseShadowMath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure eclipse event logic: which star/planet/moon triples can eclipse, how much of the star a moon hides from the
 * planet's sub-moon surface point, and edge-triggered start/end with a per-triple announcement cooldown.
 * No chart, network or tick state lives here, so it is fully unit-testable.
 */
public final class EclipseEvents {
    /**
     * One server game day: at most one chat announcement per triple in this window. The active record itself is NOT
     * rate limited: on the shipped chart a qualifying eclipse recurs about every 5 minutes, so tying the record to
     * the announcement cooldown would hide most real eclipses from the Atlas.
     */
    public static final long START_COOLDOWN_TICKS = 24_000L;
    /** Bounded walk to the star ancestor; deeper or cyclic parent chains are rejected. */
    private static final int MAX_PARENT_DEPTH = 8;

    private EclipseEvents() {}

    /** Chart body as this logic needs it; position is the chart's current evaluated position. */
    public record Body(String id, CelestialBody.BodyKind kind, String parentId, double x, double y, double z,
                       double radius, boolean enterable) {}

    public record Triple(String starId, String planetId, String moonId) {
        String key() { return starId + "|" + planetId + "|" + moonId; }
    }

    /**
     * Measured state of one triple at one sample. {@code shadowRadius} is the surface footprint at the threshold:
     * the mean arc length (blocks) from {@code shadowCentre} to where coverage drops below the threshold.
     */
    public record Sample(Triple triple, double coverage, double[] shadowCentre, double shadowRadius) {}

    /** An active eclipse, as published to clients. */
    public record Active(Triple triple, double coverage, double x, double y, double z, double radius, long startTick) {}

    public enum Edge { STARTED, ENDED }

    /** {@code announce} is true only for a start that is outside the triple's announcement cooldown. */
    public record Transition(Edge edge, Active eclipse, boolean announce) {}

    /**
     * Every enterable planet with a direct charted moon child and a charted star ancestor. Order follows the body
     * list, so results are deterministic.
     */
    public static List<Triple> triples(List<Body> bodies) {
        Map<String, Body> byId = new HashMap<>();
        for (Body body : bodies) byId.put(body.id(), body);
        List<Triple> out = new ArrayList<>();
        for (Body planet : bodies) {
            if (planet.kind() != CelestialBody.BodyKind.PLANET || !planet.enterable()) continue;
            Body star = starAncestor(planet, byId);
            if (star == null) continue;
            for (Body moon : bodies)
                if (moon.kind() == CelestialBody.BodyKind.MOON && planet.id().equals(moon.parentId()))
                    out.add(new Triple(star.id(), planet.id(), moon.id()));
        }
        return out;
    }

    private static Body starAncestor(Body body, Map<String, Body> byId) {
        Body current = body;
        for (int depth = 0; depth < MAX_PARENT_DEPTH && current != null && current.parentId() != null; depth++) {
            current = byId.get(current.parentId());
            if (current != null && current.kind() == CelestialBody.BodyKind.STAR) return current;
        }
        return null;
    }

    /** Tangent directions sampled for the footprint; four gives an average radius that tolerates elongation. */
    private static final int FOOTPRINT_DIRECTIONS = 4;
    /** Bisection steps per direction: 2^-20 of a quarter turn is far below one block at chart planet radii. */
    private static final int FOOTPRINT_STEPS = 20;

    /**
     * Coverage at the planet's sub-moon surface point (planet centre plus radius toward the moon), which is where a
     * moon's shadow falls when it sits between the planet and its star, plus the footprint radius: the mean
     * surface arc length, along {@value #FOOTPRINT_DIRECTIONS} tangent directions, out to where coverage falls
     * below {@code thresholdFraction}. The footprint is zero when the centre itself is below the threshold.
     * Returns a zero-coverage sample when the geometry is degenerate (overlapping spheres).
     */
    public static Sample sample(Triple triple, Map<String, Body> byId, double thresholdFraction) {
        Body star = byId.get(triple.starId()), planet = byId.get(triple.planetId()), moon = byId.get(triple.moonId());
        if (star == null || planet == null || moon == null) return new Sample(triple, 0.0, null, 0.0);
        double dx = moon.x() - planet.x(), dy = moon.y() - planet.y(), dz = moon.z() - planet.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length > planet.radius())) return new Sample(triple, 0.0, null, 0.0);
        double[] normal = {dx / length, dy / length, dz / length};
        double[] surface = pointAt(planet, normal, null, 0.0);
        double coverage = coverageAt(star, moon, surface);
        double footprint = coverage >= thresholdFraction ? footprintRadius(star, planet, moon, normal, thresholdFraction) : 0.0;
        return new Sample(triple, coverage, surface, footprint);
    }

    /** Mean arc length (blocks) from the sub-moon point to the threshold contour, bisected per tangent direction. */
    static double footprintRadius(Body star, Body planet, Body moon, double[] normal, double thresholdFraction) {
        double[] u = perpendicular(normal);
        double[] v = cross(normal, u);
        double total = 0.0;
        for (int k = 0; k < FOOTPRINT_DIRECTIONS; k++) {
            double phi = 2.0 * Math.PI * k / FOOTPRINT_DIRECTIONS;
            double[] tangent = {u[0] * Math.cos(phi) + v[0] * Math.sin(phi), u[1] * Math.cos(phi) + v[1] * Math.sin(phi),
                    u[2] * Math.cos(phi) + v[2] * Math.sin(phi)};
            double inside = 0.0, outside = Math.PI / 2.0;
            if (coverageAt(star, moon, pointAt(planet, normal, tangent, outside)) >= thresholdFraction) {
                total += planet.radius() * outside;
                continue;
            }
            for (int step = 0; step < FOOTPRINT_STEPS; step++) {
                double mid = 0.5 * (inside + outside);
                if (coverageAt(star, moon, pointAt(planet, normal, tangent, mid)) >= thresholdFraction) inside = mid;
                else outside = mid;
            }
            total += planet.radius() * inside;
        }
        return total / FOOTPRINT_DIRECTIONS;
    }

    /** Surface point at {@code angle} radians from the normal toward {@code tangent} (angle 0 is the normal point). */
    private static double[] pointAt(Body planet, double[] normal, double[] tangent, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        double nx = normal[0] * c, ny = normal[1] * c, nz = normal[2] * c;
        if (tangent != null) { nx += tangent[0] * s; ny += tangent[1] * s; nz += tangent[2] * s; }
        return new double[]{planet.x() + nx * planet.radius(), planet.y() + ny * planet.radius(), planet.z() + nz * planet.radius()};
    }

    private static double coverageAt(Body star, Body moon, double[] surface) {
        try {
            return EclipseShadowMath.coverage(new double[]{star.x(), star.y(), star.z()}, star.radius(),
                    new double[]{moon.x(), moon.y(), moon.z()}, moon.radius(), surface);
        } catch (IllegalArgumentException degenerate) {
            return 0.0;
        }
    }

    private static double[] perpendicular(double[] n) {
        double[] axis = Math.abs(n[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] p = cross(n, axis);
        double length = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        return new double[]{p[0] / length, p[1] / length, p[2] / length};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** Edge tracker. Not thread-safe; owned by the server tick. */
    public static final class Tracker {
        private final Map<String, Active> active = new HashMap<>();
        private final Map<String, Long> lastAnnounced = new HashMap<>();

        /**
         * Applies one sample. A record starts whenever coverage reaches the threshold and ends as soon as it falls
         * below it. The start is announced only if the triple's last announcement is at least
         * {@link #START_COOLDOWN_TICKS} old.
         */
        public Transition update(Sample sample, double thresholdFraction, long tick) {
            String key = sample.triple().key();
            boolean above = sample.coverage() >= thresholdFraction && sample.shadowCentre() != null;
            Active current = active.get(key);
            if (current != null) {
                if (above) {
                    active.put(key, new Active(current.triple(), sample.coverage(), sample.shadowCentre()[0],
                            sample.shadowCentre()[1], sample.shadowCentre()[2], sample.shadowRadius(), current.startTick()));
                    return null;
                }
                active.remove(key);
                return new Transition(Edge.ENDED, current, false);
            }
            if (!above) return null;
            Long previous = lastAnnounced.get(key);
            boolean announce = previous == null || tick - previous >= START_COOLDOWN_TICKS;
            var started = new Active(sample.triple(), sample.coverage(), sample.shadowCentre()[0],
                    sample.shadowCentre()[1], sample.shadowCentre()[2], sample.shadowRadius(), tick);
            active.put(key, started);
            if (announce) lastAnnounced.put(key, tick);
            return new Transition(Edge.STARTED, started, announce);
        }

        /** Drops triples that no longer exist in the chart, ending their active eclipse. */
        public List<Transition> retain(List<Triple> current) {
            var keys = new java.util.HashSet<String>();
            for (Triple triple : current) keys.add(triple.key());
            List<Transition> ended = new ArrayList<>();
            for (var iterator = active.entrySet().iterator(); iterator.hasNext(); ) {
                var entry = iterator.next();
                if (keys.contains(entry.getKey())) continue;
                ended.add(new Transition(Edge.ENDED, entry.getValue(), false));
                iterator.remove();
            }
            lastAnnounced.keySet().retainAll(keys);
            return ended;
        }

        public List<Active> active() { return List.copyOf(active.values()); }

        public void clear() { active.clear(); lastAnnounced.clear(); }
    }
}
