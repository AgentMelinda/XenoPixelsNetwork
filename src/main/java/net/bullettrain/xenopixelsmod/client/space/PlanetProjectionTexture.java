package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallIdentity;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * Which texture a far chart projection samples for one body, and the check that it is that body's own. The
 * legacy path binds the shared {@link PlanetSkyTexture} strip (three globes: Earth, Moon, Namek) and picks a
 * cell by the body's {@code sky_style}, so every body sharing a style shows the same globe. The per-body path
 * binds a far copy of the HD map {@link PhysicalPlanetRenderer} uses for that id.
 */
final class PlanetProjectionTexture {
    private PlanetProjectionTexture() {}

    /** Map for a non-SDB far projection; SDB slots keep their own slot-specific projection texture. */
    static ResourceLocation forProjection(String bodyId, boolean perBodyMaps) {
        return perBodyMaps ? PlanetSurfaceTexture.forBody(bodyId) : PlanetSkyTexture.LOCATION;
    }

    /** Whether a non-SDB projection whose map source is {@code texture} is showing {@code bodyId}'s own map. */
    static boolean belongsTo(String bodyId, ResourceLocation texture) {
        if (bodyId == null || texture == null) return false;
        if (DragonBallIdentity.slotFromPlanetKey(bodyId) > 0) return false;
        return texture.equals(PlanetSurfaceTexture.forBody(bodyId));
    }

    /** Which render branch drew a projection. */
    enum Path {
        /** Downsampled far copy of a body map; the recorded texture is the source map it was built from. */
        BODY_MAP,
        /** Legacy shared style strip cell. */
        STYLE_STRIP,
        /** Super Dragon Ball slot projection texture. */
        SDB_SLOT,
        /** Planet Hakai burning projection, which binds the full HD map. */
        BURNING_MAP
    }

    /** What the renderer last bound for one body. */
    record Rendered(Path path, ResourceLocation texture) {}

    /** Outcome of the identity check for one projected body. */
    enum Status {
        /** The body's own packaged map (or, for an SDB, its own slot texture). */
        OWN_MAP,
        /** The body's own map path, but no such resource: the renderer draws a flat fallback colour. */
        MISSING_MAP,
        /** The legacy shared style strip: a globe that is not this body's map. */
        SHARED_STYLE_GLOBE,
        /** A map that belongs to another body or another SDB slot. */
        WRONG_MAP,
        /** The id does not parse, so the map resolver would fall back to Earth's map. */
        UNKNOWN_ID,
        /** The same id appears more than once in the projected list. */
        DUPLICATE_ID
    }

    /**
     * Render passes a recorded bind stays "drawn now" for. The renderer stamps a body with the pass counter each
     * time it draws it; a body still in the snapshot that stopped being far-drawn (a live Sable body took over, it
     * came within the handoff distance, a draw policy skipped it, or the pass stopped running) keeps its old record,
     * which is then reported as not drawn now instead of being judged as what is on screen. Two passes of slack:
     * the counter advances once per AFTER_LEVEL stage, which a shader pack's shadow pass can fire as well.
     */
    static final long CURRENT_FRAMES = 2;

    /**
     * @param drawn            true when {@code path}/{@code texture} are what the renderer bound for this body within
     *                         {@link #CURRENT_FRAMES} far passes. False means it was never bound or its last bind is
     *                         older; the values are then what the current mode selects
     * @param sharedWith       other projected bodies whose projection uses the same texture
     * @param framesSinceDrawn far passes since the last recorded bind, or -1 when this body was never bound
     */
    record Check(String bodyId, int style, Path path, ResourceLocation texture, boolean drawn, Status status,
                 List<String> sharedWith, long framesSinceDrawn) {
        boolean correct() { return status == Status.OWN_MAP; }
    }

    /** One projected body as the renderer sees it: chart id and its legacy style cell. */
    record Projected(String id, int style) {}

    /** The branch and texture the renderer would take for a non-burning body in the given mode. */
    static Rendered expected(String bodyId, boolean perBodyMaps, IntFunction<ResourceLocation> sdbTexture) {
        int slot = DragonBallIdentity.slotFromPlanetKey(bodyId);
        if (slot > 0) return new Rendered(Path.SDB_SLOT, sdbTexture.apply(slot));
        return perBodyMaps ? new Rendered(Path.BODY_MAP, PlanetSurfaceTexture.forBody(bodyId))
                : new Rendered(Path.STYLE_STRIP, PlanetSkyTexture.LOCATION);
    }

    /** {@link #check(List, boolean, Map, Map, long, IntFunction, IntFunction, Predicate)} with every record current. */
    static List<Check> check(List<Projected> bodies, boolean perBodyMaps, Map<String, Rendered> rendered,
                             IntFunction<ResourceLocation> sdbTexture, IntFunction<ResourceLocation> sdbFullTexture,
                             Predicate<ResourceLocation> exists) {
        Map<String, Long> now = new HashMap<>();
        for (String id : rendered.keySet()) now.put(id, 0L);
        return check(bodies, perBodyMaps, rendered, now, 0L, sdbTexture, sdbFullTexture, exists);
    }

    /**
     * Checks every projected body. A body bound within {@link #CURRENT_FRAMES} far passes is judged by what was
     * actually bound (so a cache fallback to the strip shows up); any other body, including one whose record is
     * older, by what the current mode selects. Bounded by the snapshot size; an on-demand report, never per frame.
     *
     * @param drawnAt far pass number of each body's last recorded bind
     * @param frame   the latest far pass number
     */
    static List<Check> check(List<Projected> bodies, boolean perBodyMaps, Map<String, Rendered> rendered,
                             Map<String, Long> drawnAt, long frame,
                             IntFunction<ResourceLocation> sdbTexture, IntFunction<ResourceLocation> sdbFullTexture,
                             Predicate<ResourceLocation> exists) {
        Map<String, Integer> idCounts = new HashMap<>();
        for (var body : bodies) idCounts.merge(body.id(), 1, Integer::sum);
        List<Rendered> selections = new ArrayList<>(bodies.size());
        long[] ages = new long[bodies.size()];
        Map<ResourceLocation, List<String>> users = new HashMap<>();
        for (int i = 0; i < bodies.size(); i++) {
            var body = bodies.get(i);
            Rendered recorded = rendered.get(body.id());
            Long at = drawnAt.get(body.id());
            ages[i] = recorded == null || at == null ? -1 : Math.max(0, frame - at);
            boolean current = ages[i] >= 0 && ages[i] <= CURRENT_FRAMES;
            Rendered selection = current ? recorded : expected(body.id(), perBodyMaps, sdbTexture);
            selections.add(selection);
            users.computeIfAbsent(selection.texture(), ignored -> new ArrayList<>()).add(body.id());
        }
        List<Check> checks = new ArrayList<>(bodies.size());
        for (int i = 0; i < bodies.size(); i++) {
            var body = bodies.get(i);
            Rendered selection = selections.get(i);
            List<String> shared = new ArrayList<>();
            for (String other : users.get(selection.texture())) if (!other.equals(body.id())) shared.add(other);
            boolean current = ages[i] >= 0 && ages[i] <= CURRENT_FRAMES;
            checks.add(new Check(body.id(), body.style(), selection.path(), selection.texture(),
                    current, status(body.id(), idCounts.get(body.id()), selection, sdbTexture, sdbFullTexture, exists),
                    List.copyOf(shared), ages[i]));
        }
        return checks;
    }

    /**
     * @param sdbTexture     an SDB slot's far proxy texture
     * @param sdbFullTexture an SDB slot's full texture, which the burning branch binds; only asked for a body
     *                       recorded on that branch
     */
    private static Status status(String id, int occurrences, Rendered selection, IntFunction<ResourceLocation> sdbTexture,
                                 IntFunction<ResourceLocation> sdbFullTexture, Predicate<ResourceLocation> exists) {
        if (occurrences > 1) return Status.DUPLICATE_ID;
        if (ResourceLocation.tryParse(id) == null) return Status.UNKNOWN_ID;
        int slot = DragonBallIdentity.slotFromPlanetKey(id);
        if (slot > 0) {
            // The burning branch binds the slot's full-size texture; the normal branch binds its proxy.
            boolean own = switch (selection.path()) {
                case SDB_SLOT -> selection.texture().equals(sdbTexture.apply(slot));
                case BURNING_MAP -> selection.texture().equals(sdbFullTexture.apply(slot));
                default -> false;
            };
            return own ? Status.OWN_MAP : Status.WRONG_MAP;
        }
        if (selection.path() == Path.STYLE_STRIP) return Status.SHARED_STYLE_GLOBE;
        if (selection.path() == Path.SDB_SLOT || !belongsTo(id, selection.texture())) return Status.WRONG_MAP;
        return exists.test(selection.texture()) ? Status.OWN_MAP : Status.MISSING_MAP;
    }

    /** One chat/log line per body: id, style cell, branch, texture, verdict and bodies sharing the texture. */
    /**
     * One line per projected body: {@code body=<id>}, the branch, and {@code bound=<exact texture key>}. On the
     * per-body branch the bound key is the downsampled far copy, so its source map is printed too. Identity only:
     * it names keys, not the pixels on screen.
     */
    static String describe(Check check) {
        String shared = check.sharedWith().isEmpty() ? "" : " (shared with " + String.join(", ", check.sharedWith()) + ")";
        return (check.correct() ? "OK " : "WRONG ") + "body=" + check.bodyId() + " style=" + check.style() + " "
                + check.path() + drawnLabel(check)
                + " " + boundTexture(check) + " : " + check.status() + shared;
    }

    private static String drawnLabel(Check check) {
        if (check.drawn()) return " [bound now]";
        if (check.framesSinceDrawn() < 0) return " [never bound; current-mode prediction]";
        return " [not drawn now, last bound " + check.framesSinceDrawn() + " passes ago; current-mode prediction]";
    }

    /** The exact key the renderer binds for this selection, plus the source map when the bound key is a copy. */
    static String boundTexture(Check check) {
        if (check.texture() == null) return "bound=<none>";
        if (check.path() == Path.BODY_MAP)
            return "bound=" + PlanetFarProjectionCache.farKey(check.texture()) + " source=" + check.texture();
        return "bound=" + check.texture();
    }
}
