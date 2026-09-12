package net.bullettrain.xenopixelsmod.client.anim.studio;

import java.util.List;
import java.util.Locale;

/**
 * The easing ids the studio can write into a keyframe, and the preview curve for each.
 *
 * <p>The ids are exactly the ones GeckoLib 4.9.2 registers in {@code EasingType.EASING_TYPES}
 * (verified with {@code javap -c} on the pinned jar: lowercase, no separators - {@code linear},
 * {@code step}, {@code easeinoutsine}, {@code catmullrom} and the rest). GeckoLib's
 * {@code BakedAnimationsAdapter} reads them from the {@code "easing"} key of a keyframe object, so
 * a clip saved here eases the same way once it is baked.
 *
 * <p>{@code catmullrom} is a spline through the neighbouring keys rather than a curve over one
 * segment, so it is exported faithfully but previewed as linear. That is called out in
 * {@code docs/xeno-anim-studio.md} rather than papered over.
 */
public final class AnimEasing {
    public static final String LINEAR = "linear";

    /** The cycle offered by the studio's EASE button, in order. */
    public static final List<String> CYCLE = List.of(
            LINEAR, "step", "easeinsine", "easeoutsine", "easeinoutsine", "easeinoutcubic",
            "catmullrom");

    private AnimEasing() {}

    public static String sanitize(String id) {
        if (id == null || id.isBlank()) return LINEAR;
        String lower = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return CYCLE.contains(lower) ? lower : LINEAR;
    }

    public static String next(String id) {
        int index = CYCLE.indexOf(sanitize(id));
        return CYCLE.get((index + 1) % CYCLE.size());
    }

    /** Short label for the inspector; the full id is too wide for the dock. */
    public static String label(String id) {
        return switch (sanitize(id)) {
            case "step" -> "step";
            case "easeinsine" -> "in";
            case "easeoutsine" -> "out";
            case "easeinoutsine" -> "in/out";
            case "easeinoutcubic" -> "in/out³";
            case "catmullrom" -> "spline";
            default -> "linear";
        };
    }

    /** Remaps a 0..1 segment position. Used for the in-studio preview and by {@code AnimTimeline}. */
    public static float apply(String id, float t) {
        float u = Math.max(0f, Math.min(1f, t));
        return switch (sanitize(id)) {
            case "step" -> u < 1f ? 0f : 1f;
            case "easeinsine" -> 1f - (float) Math.cos((u * Math.PI) / 2.0);
            case "easeoutsine" -> (float) Math.sin((u * Math.PI) / 2.0);
            case "easeinoutsine" -> (float) (-(Math.cos(Math.PI * u) - 1.0) / 2.0);
            case "easeinoutcubic" -> u < 0.5f
                    ? 4f * u * u * u
                    : 1f - (float) Math.pow(-2.0 * u + 2.0, 3) / 2f;
            default -> u;
        };
    }
}
