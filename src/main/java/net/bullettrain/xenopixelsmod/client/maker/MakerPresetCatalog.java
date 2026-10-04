package net.bullettrain.xenopixelsmod.client.maker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Display labels for Race Character Maker preset cyclers.
 *
 * <p><b>Live catalog when READY:</b> labels derive from {@link RaceMakerParts#partIds} (Body /
 * Eyes / Mouth / Extra via {@code NpcAppearanceParts}/{@code TextureCounter}; Hair via
 * {@code NpcHairBridge.presetCount}/{@code HairManager}).
 *
 * <p><b>Hardcoded FALLBACK when counts are 0</b> (unit tests, missing client resources, Aura /
 * Clothes citation gaps stay empty — no invented enumerators):
 * <ul>
 *   <li>Body 1–8</li>
 *   <li>Eyes 1–6</li>
 *   <li>Mouth 1–4</li>
 *   <li>Hair: Default / Soft / Spiky / Wild / Long / Short / SSJ-style / SSJ2-style / SSJ3-style
 *       (documented stand-ins; DMZ creation stores numbered presets without public names)</li>
 *   <li>Extra: Nose 1–3 + Tattoo 1–4</li>
 * </ul>
 */
public final class MakerPresetCatalog {
    /** Documented Body fallback when TextureCounter returns 0. */
    public static final List<String> FALLBACK_BODY = List.of(
            "Body 1", "Body 2", "Body 3", "Body 4",
            "Body 5", "Body 6", "Body 7", "Body 8");

    /** Documented Eyes fallback when TextureCounter returns 0. */
    public static final List<String> FALLBACK_EYES = List.of(
            "Eyes 1", "Eyes 2", "Eyes 3", "Eyes 4", "Eyes 5", "Eyes 6");

    /** Documented Mouth fallback when TextureCounter returns 0. */
    public static final List<String> FALLBACK_MOUTH = List.of(
            "Mouth 1", "Mouth 2", "Mouth 3", "Mouth 4");

    /**
     * Documented Hair fallback when {@code HairManager.getPresetCount()} is 0.
     * Names are stand-ins matching DMZ creation form-family vocabulary where known.
     */
    public static final List<String> FALLBACK_HAIR = List.of(
            "Default", "Soft", "Spiky", "Wild", "Long", "Short",
            "SSJ-style", "SSJ2-style", "SSJ3-style");

    /** Documented Extra (nose + tattoo) fallback when TextureCounter returns 0. */
    public static final List<String> FALLBACK_EXTRA = List.of(
            "Nose 1", "Nose 2", "Nose 3",
            "Tattoo 1", "Tattoo 2", "Tattoo 3", "Tattoo 4");

    private MakerPresetCatalog() {
    }

    /**
     * Cycler display labels for {@code category}. Live from {@link RaceMakerParts} when that list
     * is non-empty; otherwise the documented FALLBACK for Body/Eyes/Mouth/Hair/Extra. Aura and
     * Clothes stay empty (citation gap — do not invent).
     */
    public static List<String> labels(RaceMakerParts.Category category, String race, String gender) {
        RaceMakerParts.Category cat = category == null ? RaceMakerParts.Category.BODY : category;
        List<String> liveIds = RaceMakerParts.partIds(cat, race, gender);
        if (!liveIds.isEmpty()) {
            List<String> out = new ArrayList<>(liveIds.size());
            for (String id : liveIds) {
                out.add(labelForPartId(id));
            }
            return List.copyOf(out);
        }
        return fallbackLabels(cat);
    }

    /**
     * Part ids parallel to {@link #labels}. Live ids when READY; synthetic {@code kind:index}
     * fallback ids when labels fall back. Empty for Aura / Clothes.
     */
    public static List<String> partIds(RaceMakerParts.Category category, String race, String gender) {
        RaceMakerParts.Category cat = category == null ? RaceMakerParts.Category.BODY : category;
        List<String> liveIds = RaceMakerParts.partIds(cat, race, gender);
        if (!liveIds.isEmpty()) {
            return liveIds;
        }
        return fallbackPartIds(cat);
    }

    /** True when labels/partIds came from live TextureCounter / HairManager counts. */
    public static boolean isLive(RaceMakerParts.Category category, String race, String gender) {
        return !RaceMakerParts.partIds(category, race, gender).isEmpty();
    }

    /** Category cycler labels (Body … Extra). */
    public static List<String> categoryLabels() {
        RaceMakerParts.Category[] cats = RaceMakerParts.Category.values();
        List<String> out = new ArrayList<>(cats.length);
        for (RaceMakerParts.Category cat : cats) {
            out.add(cat.label());
        }
        return List.copyOf(out);
    }

    static String labelForPartId(String partId) {
        if (partId == null || partId.isBlank()) {
            return "?";
        }
        String id = partId.trim().toLowerCase(Locale.ROOT);
        if (id.startsWith("extra:nose:")) {
            return "Nose " + id.substring("extra:nose:".length());
        }
        if (id.startsWith("extra:tattoo:")) {
            return "Tattoo " + id.substring("extra:tattoo:".length());
        }
        if (id.startsWith("body:")) {
            return "Body " + id.substring("body:".length());
        }
        if (id.startsWith("eyes:")) {
            return "Eyes " + id.substring("eyes:".length());
        }
        if (id.startsWith("mouth:")) {
            return "Mouth " + id.substring("mouth:".length());
        }
        if (id.startsWith("hair:")) {
            return "Hair " + id.substring("hair:".length());
        }
        int colon = id.lastIndexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static List<String> fallbackLabels(RaceMakerParts.Category cat) {
        return switch (cat) {
            case BODY -> FALLBACK_BODY;
            case EYES -> FALLBACK_EYES;
            case MOUTH -> FALLBACK_MOUTH;
            case HAIR -> FALLBACK_HAIR;
            case EXTRA -> FALLBACK_EXTRA;
            case AURA, CLOTHES -> List.of();
        };
    }

    private static List<String> fallbackPartIds(RaceMakerParts.Category cat) {
        return switch (cat) {
            case BODY -> numberedIds("body", 1, FALLBACK_BODY.size());
            case EYES -> numberedIds("eyes", 1, FALLBACK_EYES.size());
            case MOUTH -> numberedIds("mouth", 1, FALLBACK_MOUTH.size());
            case HAIR -> numberedIds("hair", 1, FALLBACK_HAIR.size());
            case EXTRA -> {
                List<String> out = new ArrayList<>();
                out.addAll(numberedIds("extra:nose", 1, 3));
                out.addAll(numberedIds("extra:tattoo", 1, 4));
                yield List.copyOf(out);
            }
            case AURA, CLOTHES -> List.of();
        };
    }

    private static List<String> numberedIds(String prefix, int fromInclusive, int count) {
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(prefix + ":" + (fromInclusive + i));
        }
        return List.copyOf(out);
    }
}
