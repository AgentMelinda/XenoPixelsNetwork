package net.bullettrain.xenopixelsmod.client.space;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Identity checks for the two renderers besides the far chart projection: replicated Sable bodies drawn by
 * {@link PhysicalPlanetRenderer}, and seeded small planets drawn by {@link SmallPlanetFarRenderer}. Same verdicts
 * and last-bound/prediction distinction as {@link PlanetProjectionTexture#check}; on demand only.
 */
final class PlanetTextureChecks {
    private PlanetTextureChecks() {}

    /** One body to check: its id and the texture its renderer last bound, or null if it has not drawn it yet. */
    record Observed(String id, ResourceLocation bound) {}

    enum Verdict {
        /** Bound (or would bind) the texture that belongs to this id. */
        OWN,
        /** Bound a texture that belongs to another id, or that another checked id also bound. */
        WRONG,
        /** Its own map path has no packaged resource, so a flat fallback colour is drawn. */
        MISSING,
        /** Not drawn yet and nothing exists to predict from (a small planet whose texture was not generated). */
        UNKNOWN,
        /** The same id was listed more than once; current sources cannot produce this, so it marks a broken invariant. */
        DUPLICATE_ID
    }

    record Result(String id, ResourceLocation texture, boolean drawn, Verdict verdict, List<String> sharedWith) {}

    /**
     * @param expected the texture that belongs to an id (near bodies: its HD map or SDB full texture; small
     *                 planets: its generated texture, or null when none was generated)
     * @param packaged whether a texture resource exists; generated (dynamic) textures should answer true
     */
    static List<Result> check(List<Observed> observed, Function<String, ResourceLocation> expected,
                              Predicate<ResourceLocation> packaged) {
        Map<String, Integer> idCounts = new HashMap<>();
        for (var body : observed) idCounts.merge(body.id(), 1, Integer::sum);
        Map<ResourceLocation, List<String>> users = new HashMap<>();
        List<ResourceLocation> textures = new ArrayList<>(observed.size());
        for (var body : observed) {
            ResourceLocation texture = body.bound() != null ? body.bound() : expected.apply(body.id());
            textures.add(texture);
            if (texture != null) users.computeIfAbsent(texture, ignored -> new ArrayList<>()).add(body.id());
        }
        List<Result> results = new ArrayList<>(observed.size());
        for (int i = 0; i < observed.size(); i++) {
            var body = observed.get(i);
            ResourceLocation texture = textures.get(i);
            List<String> shared = new ArrayList<>();
            if (texture != null) for (String other : users.get(texture)) if (!other.equals(body.id())) shared.add(other);
            Verdict verdict;
            ResourceLocation own = expected.apply(body.id());
            if (idCounts.get(body.id()) > 1) verdict = Verdict.DUPLICATE_ID;
            else if (texture == null || own == null) verdict = body.bound() == null ? Verdict.UNKNOWN : Verdict.WRONG;
            else if (!texture.equals(own) || !shared.isEmpty()) verdict = Verdict.WRONG;
            else if (!packaged.test(texture)) verdict = Verdict.MISSING;
            else verdict = Verdict.OWN;
            results.add(new Result(body.id(), texture, body.bound() != null, verdict, List.copyOf(shared)));
        }
        return results;
    }

    /**
     * Angle in degrees between the view direction and the direction from the camera to a target; 0 is on the
     * crosshair, 180 behind. Used to name the globe nearest the screen centre.
     */
    static double offAxisDegrees(double cameraX, double cameraY, double cameraZ, double lookX, double lookY, double lookZ,
                                 double targetX, double targetY, double targetZ) {
        double dx = targetX - cameraX, dy = targetY - cameraY, dz = targetZ - cameraZ;
        double toTarget = Math.sqrt(dx * dx + dy * dy + dz * dz), look = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        if (toTarget == 0 || look == 0) return 0;
        double cos = (dx * lookX + dy * lookY + dz * lookZ) / (toTarget * look);
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }

    /** What is actually registered under a bound texture key, as opposed to which key was bound. */
    enum Upload {
        /** A planet map object built from the expected source, decoded from its file. */
        DECODED,
        /** The expected source, but its file was absent, so a flat fallback colour was uploaded. */
        FALLBACK_COLOUR,
        /** Vanilla's purple/black missing-texture placeholder: the texture's load() threw. */
        MISSING_PLACEHOLDER,
        /** Nothing is registered under the key. */
        NOT_REGISTERED,
        /** A planet map object built from a DIFFERENT source sits under this key. */
        OTHER_SOURCE,
        /** Some other texture kind (strip, SDB dynamic); its source cannot be checked here. */
        UNVERIFIED
    }

    /**
     * Classifies the object the texture manager holds for a key. {@code placeholder} is vanilla's shared
     * missing-texture instance, which TextureManager registers when a texture's load() fails.
     */
    static Upload upload(Object registered, Object placeholder, ResourceLocation expectedSource) {
        if (registered == null) return Upload.NOT_REGISTERED;
        if (registered == placeholder) return Upload.MISSING_PLACEHOLDER;
        ResourceLocation source;
        boolean decoded;
        if (registered instanceof PlanetSurfaceTexture surface) { source = surface.source(); decoded = surface.decoded(); }
        else if (registered instanceof PlanetFarProjectionTexture far) { source = far.source(); decoded = far.decoded(); }
        else return Upload.UNVERIFIED;
        if (expectedSource == null || !expectedSource.equals(source)) return Upload.OTHER_SOURCE;
        return decoded ? Upload.DECODED : Upload.FALLBACK_COLOUR;
    }

    static boolean uploadAcceptable(Upload upload) {
        return upload == Upload.DECODED;
    }

    /** A known load/source failure; UNVERIFIED means the report cannot judge the registered object. */
    static boolean uploadKnownWrong(Upload upload) {
        return switch (upload) {
            case NOT_REGISTERED, MISSING_PLACEHOLDER, OTHER_SOURCE, FALLBACK_COLOUR -> true;
            case DECODED, UNVERIFIED -> false;
        };
    }

    static String describe(String renderer, Result result) {
        String shared = result.sharedWith().isEmpty() ? "" : " (shared with " + String.join(", ", result.sharedWith()) + ")";
        return (result.verdict() == Verdict.OWN ? "OK " : "WRONG ") + renderer + " body=" + result.id()
                + (result.drawn() ? " [last bound]" : " [never bound; prediction]") + " bound="
                + (result.texture() == null ? "<none generated>" : result.texture()) + " : " + result.verdict() + shared;
    }
}
