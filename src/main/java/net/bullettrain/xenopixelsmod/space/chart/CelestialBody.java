package net.bullettrain.xenopixelsmod.space.chart;

import net.bullettrain.xenopixelsmod.space.math.GravityField;
import net.bullettrain.xenopixelsmod.space.math.OrbitalElements;

/**
 * One row of the Galaxy Atlas: the authored, immutable description of a body on the chart.
 *
 * <p>This is the §21 promise made concrete — "its existence while empty is one registry row plus one
 * proxy texture plus its orbital elements". Everything that costs anything per tick lives in the
 * registry's scratch buffers, not here, so 200 idle bodies are 200 of these and nothing else.
 *
 * <p><b>No Minecraft types, deliberately.</b> The dimension binding is a plain namespaced
 * {@code String}, exactly as {@code OrbitalGravityConfig} already does it. That keeps this record
 * testable without booting the game, and it means a space mod, a DMZ addon, or Northstar can rename,
 * move or repackage anything it owns without breaking the chart — the failure mode that took ship
 * gravity down once already. Resolution from the string to a live level happens in one adapter class
 * on the Minecraft side, and an id that does not resolve degrades to a showcase body rather than
 * crashing (§17's validation-first rule).
 *
 * @param id            namespaced resource-derived id, {@code "namespace:path"}; bundled JSON and
 *                      custom datapacks use the same loader.
 * @param kind          what the body is, which decides whether it can be entered at all
 * @param displayName   the name shown on the chart and in handoff toasts
 * @param orbit         the orbital elements, or null for a body pinned at a fixed chart position —
 *                      the cheap REV 2 behaviour, and what §20's {@code orbital_motion: false} and
 *                      the {@code space.gravity.real_orbits} toggle both fall back to
 * @param pinnedX       parent-relative position when {@code orbit} is null; ignored otherwise
 * @param pinnedY       parent-relative position when {@code orbit} is null; ignored otherwise
 * @param pinnedZ       parent-relative position when {@code orbit} is null; ignored otherwise
 * @param parentId      the id of the body this one orbits, or null for a system root such as a star.
 *                      Moons name their planet; planets name their star (§20's hierarchy).
 * @param radiusBlocks  the body's radius in blocks. Also the unit every LOD threshold is measured in,
 *                      so a few-hundred-block gag world and a gas giant share one ladder config.
 * @param mu            gravitational parameter {@code GM} for inverse-square falloff, read as surface
 *                      gravity for the linear and constant modes. One authored number either way.
 * @param falloff       how the pull decays with distance
 * @param dimensionId   the dimension this body hands off to, or null for a pure Voidsea showcase body
 *                      that can be flown around but never entered (§24's {@code kind: "showcase"})
 * @param handoffAnchorY the Y the handoff drops arrivals at inside {@code dimensionId}
 * @param exitAltitude  the Y at which leaving the bound dimension triggers the handoff. §19.2's
 *                      headline knob: per-body, zero code, and the reason Earth gets 700 while King
 *                      Kai's world gets ~180.
 * @param warmUpAltitude the Y at which the server starts warming the opposite-side anchor chunk. When
 *                      omitted from chart data, the loader derives it as {@code exitAltitude - 300}.
 * @param orbitalMotion per-body opt-out of live Kepler motion (§20's {@code orbital_motion} key)
 * @param erasable      whether hakai or a crack event may remove this body (§22's policy flag)
 * @param dangerRating  chart-display rating; gates nothing on its own
 * @param voidDamage    per-dimension override of {@code space.voidDamage}: {@code true} keeps the
 *                      {@code out_of_world} damage, {@code false} cancels it and returns the faller to
 *                      {@code handoff_anchor_y}, and null inherits the global switch. Only consulted
 *                      for a dimension bound to this body — an unbound dimension keeps vanilla void
 *                      damage (§19.1).
 * @param skyStyle      which cell of the client's proxy atlas this body draws from, 0-based. The shipped
 *                      art is three cells — Earth, Moon, Namek — so this is an index into existing
 *                      artwork rather than a free-form look, and a pack that adds cells to that texture
 *                      raises {@link #MAX_VISUAL_STYLES} in the same change. Bodies an author leaves
 *                      unset draw as the first cell: wrong-colouring one custom planet beats dropping it
 *                      out of the sky, which is the failure this whole slice exists to fix.
 */
public record CelestialBody(
        String id,
        BodyKind kind,
        String displayName,
        OrbitalElements orbit,
        double pinnedX,
        double pinnedY,
        double pinnedZ,
        String parentId,
        double radiusBlocks,
        double mu,
        GravityField.Falloff falloff,
        String dimensionId,
        double handoffAnchorY,
        double exitAltitude,
        double warmUpAltitude,
        boolean orbitalMotion,
        boolean erasable,
        int dangerRating,
        Boolean voidDamage,
        int skyStyle,
        double spatialAnchorX,
        double spatialAnchorZ,
        SurfaceAnchor surfaceAnchor,
        Double rotationPeriodSeconds) {

    /** A dimension-local arrival point and cardinal facing authored by §24 chart entries. */
    public record SurfaceAnchor(double x, double y, double z, String facing) {
        private static final java.util.Set<String> FACINGS = java.util.Set.of(
                "up", "down", "north", "south", "east", "west");
        public SurfaceAnchor {
            if (!supported(x) || !supported(y) || !supported(z))
                throw new IllegalArgumentException("surface_anchor coordinates must be finite and within +/-30000000");
            facing = facing == null ? "up" : facing.toLowerCase(java.util.Locale.ROOT);
            if (!FACINGS.contains(facing))
                throw new IllegalArgumentException("surface_anchor facing must be up, down, north, south, east, or west");
        }
        private static boolean supported(double value) {
            return Double.isFinite(value) && Math.abs(value) < 30_000_000;
        }
        /** Minecraft yaw for horizontal facings; vertical facing keeps the player's current view. */
        public Float yawDegrees() {
            return switch (facing) {
                case "south" -> 0.0f;
                case "west" -> 90.0f;
                case "north" -> 180.0f;
                case "east" -> -90.0f;
                default -> null;
            };
        }
    }

    /** Source-compatible constructor for chart definitions with §24 anchors but no authored spin period. */
    public CelestialBody(String id, BodyKind kind, String displayName, OrbitalElements orbit,
                         double pinnedX, double pinnedY, double pinnedZ, String parentId,
                         double radiusBlocks, double mu, GravityField.Falloff falloff, String dimensionId,
                         double handoffAnchorY, double exitAltitude, double warmUpAltitude,
                         boolean orbitalMotion, boolean erasable, int dangerRating,
                         Boolean voidDamage, int skyStyle, double spatialAnchorX, double spatialAnchorZ,
                         SurfaceAnchor surfaceAnchor) {
        this(id, kind, displayName, orbit, pinnedX, pinnedY, pinnedZ, parentId, radiusBlocks, mu,
                falloff, dimensionId, handoffAnchorY, exitAltitude, warmUpAltitude, orbitalMotion,
                erasable, dangerRating, voidDamage, skyStyle, spatialAnchorX, spatialAnchorZ, surfaceAnchor, null);
    }

    /** Source-compatible constructor for chart definitions predating §24 surface anchors. */
    public CelestialBody(String id, BodyKind kind, String displayName, OrbitalElements orbit,
                         double pinnedX, double pinnedY, double pinnedZ, String parentId,
                         double radiusBlocks, double mu, GravityField.Falloff falloff, String dimensionId,
                         double handoffAnchorY, double exitAltitude, double warmUpAltitude,
                         boolean orbitalMotion, boolean erasable, int dangerRating,
                         Boolean voidDamage, int skyStyle, double spatialAnchorX, double spatialAnchorZ) {
        this(id, kind, displayName, orbit, pinnedX, pinnedY, pinnedZ, parentId, radiusBlocks, mu,
                falloff, dimensionId, handoffAnchorY, exitAltitude, warmUpAltitude, orbitalMotion,
                erasable, dangerRating, voidDamage, skyStyle, spatialAnchorX, spatialAnchorZ, null, null);
    }

    /** Source-compatible constructor for chart definitions without a configured spatial anchor. */
    public CelestialBody(String id, BodyKind kind, String displayName, OrbitalElements orbit,
                         double pinnedX, double pinnedY, double pinnedZ, String parentId,
                         double radiusBlocks, double mu, GravityField.Falloff falloff, String dimensionId,
                         double handoffAnchorY, double exitAltitude, double warmUpAltitude,
                         boolean orbitalMotion, boolean erasable, int dangerRating,
                         Boolean voidDamage, int skyStyle) {
        this(id, kind, displayName, orbit, pinnedX, pinnedY, pinnedZ, parentId, radiusBlocks, mu,
                falloff, dimensionId, handoffAnchorY, exitAltitude, warmUpAltitude, orbitalMotion,
                erasable, dangerRating, voidDamage, skyStyle, 0.0, 0.0);
    }

    /** Source-compatible constructor for callers predating per-body anchor warm-up. */
    public CelestialBody(String id, BodyKind kind, String displayName, OrbitalElements orbit,
                         double pinnedX, double pinnedY, double pinnedZ, String parentId,
                         double radiusBlocks, double mu, GravityField.Falloff falloff, String dimensionId,
                         double handoffAnchorY, double exitAltitude, boolean orbitalMotion,
                         boolean erasable, int dangerRating, Boolean voidDamage, int skyStyle) {
        this(id, kind, displayName, orbit, pinnedX, pinnedY, pinnedZ, parentId, radiusBlocks, mu,
                falloff, dimensionId, handoffAnchorY, exitAltitude, defaultWarmUpAltitude(exitAltitude),
                orbitalMotion, erasable, dangerRating, voidDamage, skyStyle, 0.0, 0.0);
    }

    /** The cells the shipped proxy atlas holds. See {@code skyStyle}. */
    public static final int MAX_VISUAL_STYLES = 3;

    public CelestialBody {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (id.indexOf(':') < 0) {
            throw new IllegalArgumentException("id must be namespaced 'namespace:path', got " + id);
        }
        if (kind == null) throw new IllegalArgumentException("kind must not be null for " + id);
        if (!Double.isFinite(exitAltitude)) throw new IllegalArgumentException("exitAltitude must be finite for " + id);
        if (!Double.isFinite(warmUpAltitude) || warmUpAltitude >= exitAltitude) {
            throw new IllegalArgumentException("warm_up_altitude must be finite and below exit_altitude for " + id);
        }
        if (!Double.isFinite(spatialAnchorX) || !Double.isFinite(spatialAnchorZ)) {
            throw new IllegalArgumentException("spatial_anchor x/z must be finite for " + id);
        }
        if (rotationPeriodSeconds != null && (!Double.isFinite(rotationPeriodSeconds)
                || Math.abs(rotationPeriodSeconds) < 0.05 || Math.abs(rotationPeriodSeconds) > 1.0e12)) {
            throw new IllegalArgumentException("rotation_period_seconds magnitude must be in [0.05, 1e12] for " + id);
        }
        if (skyStyle < 0 || skyStyle >= MAX_VISUAL_STYLES) {
            throw new IllegalArgumentException("sky_style must be a proxy atlas cell in [0, "
                    + MAX_VISUAL_STYLES + ") for " + id);
        }
        if (!(radiusBlocks > 0.0) || !Double.isFinite(radiusBlocks)) {
            throw new IllegalArgumentException("radiusBlocks must be finite and > 0 for " + id);
        }
        if (!(mu >= 0.0) || !Double.isFinite(mu)) {
            throw new IllegalArgumentException("mu must be finite and >= 0 for " + id);
        }
        // A null dimension is legal — that is a showcase body — but a blank one is a typo in a pack.
        if (dimensionId != null && dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId must be null or non-blank for " + id);
        }
        // Without elements the pinned offset is the body's only source of position, so it has to be
        // usable rather than merely present.
        if (orbit == null && !(Double.isFinite(pinnedX) && Double.isFinite(pinnedY)
                && Double.isFinite(pinnedZ))) {
            throw new IllegalArgumentException(
                    "a body with no orbit needs finite pinned coordinates: " + id);
        }
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        falloff = falloff == null ? GravityField.Falloff.INVERSE_SQUARE : falloff;
    }

    /** Default authored-data offset; the design's Earth example uses warm-up 400 / exit 700. */
    public static double defaultWarmUpAltitude(double exitAltitude) {
        return exitAltitude - 300.0;
    }

    /** True when this body has no orbital elements and sits at its pinned offset instead. */
    public boolean pinned() {
        return orbit == null;
    }

    /** What a chart entry is, which decides whether it can be entered and how it is drawn. */
    public enum BodyKind {
        /** A star: the system root its planets orbit. */
        STAR,
        /** A full planet, enterable when it has a dimension binding. */
        PLANET,
        /** A moon; orbits a planet rather than the star directly. */
        MOON,
        /** An artificial body — a station or dock, usually small and always enterable. */
        STATION,
        /** A black hole: tidal bands, disposal exits, slingshot racing (§15). Never landed on. */
        BLACK_HOLE,
        /** Visible sphere with no entry. Beyond-God's-Domain style, or an unresolvable binding. */
        SHOWCASE,
        /** A pocket dimension presented as a body — a derelict, a wreckyard, a trial arena. */
        POCKET,
        /** A point of interest on someone else's world: "a city on the moon" (§24). */
        RUINS
    }

    /** True when a player or ship can be handed off into this body at all. */
    public boolean enterable() {
        return dimensionId != null && kind != BodyKind.SHOWCASE && kind != BodyKind.BLACK_HOLE;
    }

    /**
     * True when this body's position comes from live Kepler math rather than a pinned chart slot.
     * Both §20's per-body flag and the global {@code real_orbits} toggle have to agree.
     */
    public boolean moves() {
        return orbitalMotion && orbit != null;
    }

    /** True when this body orbits {@code candidateParentId} directly. */
    public boolean orbits(String candidateParentId) {
        return parentId != null && parentId.equals(candidateParentId);
    }

    /** The namespace half of {@link #id}. */
    public String namespace() {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(0, colon);
    }

    /** The path half of {@link #id}. */
    public String path() {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }
}
