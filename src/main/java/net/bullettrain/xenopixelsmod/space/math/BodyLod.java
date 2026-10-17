package net.bullettrain.xenopixelsmod.space.math;

/**
 * The lazy-activation ladder from §21, as a pure decision with hysteresis.
 *
 * <p>A planet nobody is standing on must cost almost nothing yet still orbit, still show in the sky,
 * and still host its content. That works only if each level of detail is paid for exactly when
 * someone is close enough to notice it, and given back promptly when they are not:
 *
 * <pre>
 * ABSENT → BILLBOARD → PROXY → FIELD → ANCHOR → CHUNKS
 * </pre>
 *
 * <p>Billboards are instanced quads sharing one atlas. Proxies are the geodesic spheres, capped
 * globally (the design's tuned default is ~12 simultaneous, enforced by the chart registry, not here).
 * Field is where a body starts contributing gravity. Anchor is where a handoff target chunk gets
 * pre-warmed so the §3 swap has somewhere to land. Chunks is the only stage that touches worldgen, and
 * it is reached only when someone is actually inside the body.
 *
 * <p><b>Why hysteresis.</b> A single threshold per boundary is a trap: a ship holding station right at
 * the boundary promotes and demotes every tick, and each transition is expensive — building or
 * dropping a proxy mesh, warming or releasing an anchor. So promotion and demotion use different
 * distances, and a body only gives a stage back once the ship has moved clearly past it. The margin is
 * a multiplier on the promotion distance, which makes it scale-free: the same numbers work for King
 * Kai's few-hundred-block world and for a gas giant.
 *
 * <p>Thresholds are expressed as multiples of the body's own radius rather than absolute block counts,
 * for the same reason. §19.2 makes every one of them a config key.
 */
public enum BodyLod {
    /** Not rendered and not simulated. Costs one registry row. */
    ABSENT,
    /** An instanced quad from the shared icon atlas. §21's claim is 200 of these are free. */
    BILLBOARD,
    /** A geodesic proxy sphere. Counts against the global proxy budget. */
    PROXY,
    /** Contributes to gravity summation for ships in range. */
    FIELD,
    /** A handoff anchor chunk is pre-warmed and held ready. */
    ANCHOR,
    /** Real chunks are loaded — only because someone is inside the body. */
    CHUNKS;

    /** True when this stage renders a geodesic proxy and so counts against the global budget. */
    public boolean usesProxyBudget() {
        return this == PROXY;
    }

    /** True when this stage costs server simulation work rather than only draw calls. */
    public boolean simulates() {
        return ordinal() >= FIELD.ordinal();
    }

    /**
     * The promotion/demotion distances for one ladder, as radius multipliers.
     *
     * <p><b>The factors must decrease as the stages get cheaper</b> — {@code proxy > field > anchor >
     * chunk} — because {@link #resolve} tests innermost-first and returns the first match. A proxy
     * sphere is the outermost of the four: a body is worth drawing as a sphere long before it is close
     * enough to pull on a ship, closer still before a handoff anchor is worth pre-warming, and closest
     * of all before real chunks are loaded. Getting this order backwards does not throw; it silently
     * makes the outer stages unreachable, which is exactly the kind of quiet wrongness that a
     * "everything still renders" smoke test would not catch.
     *
     * @param proxyFactor      distance in radii inside which a billboard becomes a proxy sphere; the
     *                         largest of the four
     * @param fieldFactor      distance in radii inside which the body starts pulling on ships
     * @param anchorFactor     distance in radii inside which a handoff anchor is pre-warmed
     * @param chunkFactor      distance in radii inside which real chunks are loaded; the smallest
     * @param hysteresisMargin multiplier applied to each threshold on the way <i>down</i>; 0.25 means a
     *                         body gives a stage back only at 1.25× the distance that earned it. Must
     *                         be &gt; 0 or the ladder oscillates at every boundary.
     */
    public record Thresholds(double proxyFactor, double fieldFactor, double anchorFactor,
                             double chunkFactor, double hysteresisMargin) {
        public Thresholds {
            if (!Double.isFinite(proxyFactor) || !Double.isFinite(fieldFactor)
                    || !Double.isFinite(anchorFactor) || !Double.isFinite(chunkFactor)
                    || !Double.isFinite(hysteresisMargin)) {
                throw new IllegalArgumentException("LOD thresholds must be finite");
            }
            if (!(proxyFactor > 0.0)) throw new IllegalArgumentException("proxyFactor must be > 0");
            if (!(proxyFactor > fieldFactor)) {
                throw new IllegalArgumentException(
                        "proxyFactor must exceed fieldFactor — the proxy band is the outermost one");
            }
            if (!(fieldFactor > anchorFactor)) {
                throw new IllegalArgumentException("fieldFactor must exceed anchorFactor");
            }
            if (!(anchorFactor > chunkFactor) || !(chunkFactor > 0.0)) {
                throw new IllegalArgumentException(
                        "anchorFactor must exceed chunkFactor, and chunkFactor must be > 0");
            }
            if (!(hysteresisMargin > 0.0)) {
                throw new IllegalArgumentException("hysteresisMargin must be > 0 or the ladder thrashes");
            }
        }

        /** The design's tuned starting point (§19.1's {@code space.performance.*} defaults). */
        public static Thresholds defaults() {
            return new Thresholds(900.0, 400.0, 60.0, 1.05, 0.25);
        }
    }

    /**
     * Resolves the stage a body should be at, given how far away the nearest interested party is.
     *
     * <p>Deliberately <b>not</b> a pure function of distance: {@code current} is an input, because
     * hysteresis is the whole point. The same distance can leave a body at its existing stage or move
     * it, depending on which side of the boundary it arrived from.
     *
     * @param distance     distance from the body's centre to the nearest ship or player, in the same
     *                     units as {@code radius}
     * @param radius       the body's radius; a non-positive radius means a body with no surface, which
     *                     can never be entered, so it stays a billboard at best
     * @param current      the stage the body is at now
     * @param thresholds   the ladder's distances; null falls back to {@link Thresholds#defaults()}
     * @param inView       false when the body is behind the viewer or beyond the render distance, which
     *                     suppresses visual-only stages; nearby simulation remains relevant even
     *                     when the observer is looking away
     */
    public static BodyLod resolve(double distance, double radius, BodyLod current,
                                  Thresholds thresholds, boolean inView) {
        Thresholds t = thresholds == null ? Thresholds.defaults() : thresholds;
        BodyLod from = current == null ? ABSENT : current;
        if (!(distance >= 0.0) || !Double.isFinite(distance)) return ABSENT;

        boolean canBeEntered = radius > 0.0 && Double.isFinite(radius);
        // Rendering needs the body on screen; simulation does not. A body you are standing inside is
        // by definition relevant, so the view test only gates the two purely visual stages.
        double proxyAt = canBeEntered ? radius * t.proxyFactor() : 0.0;
        double fieldAt = canBeEntered ? radius * t.fieldFactor() : 0.0;
        double anchorAt = canBeEntered ? radius * t.anchorFactor() : 0.0;
        double chunkAt = canBeEntered ? radius * t.chunkFactor() : 0.0;
        double slack = 1.0 + t.hysteresisMargin();

        // Innermost stage first: a ship inside the body needs chunks whatever else is true.
        if (canBeEntered && distance <= promote(chunkAt, from, CHUNKS, slack)) return CHUNKS;

        // Promotion uses the bare threshold; demotion uses the threshold widened by the margin. Which
        // one applies depends on whether we are already at or past the stage being tested.
        boolean wantAnchor = canBeEntered && distance <= promote(anchorAt, from, ANCHOR, slack);
        if (wantAnchor) return ANCHOR;

        boolean wantField = canBeEntered && distance <= promote(fieldAt, from, FIELD, slack);
        if (wantField) return FIELD;

        if (inView && canBeEntered && distance <= promote(proxyAt, from, PROXY, slack)) return PROXY;
        return inView ? BILLBOARD : ABSENT;
    }

    /**
     * The distance to compare against for one stage: the bare threshold when trying to reach a stage we
     * do not have yet, the widened one when trying to keep a stage we already have.
     */
    private static double promote(double threshold, BodyLod from, BodyLod stage, double slack) {
        return from.ordinal() >= stage.ordinal() ? threshold * slack : threshold;
    }
}
