package net.bullettrain.xenopixelsmod.client.maker;

import net.bullettrain.xenopixelsmod.client.npc.NpcAppearanceParts;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;
import net.bullettrain.xenopixelsmod.compat.npc.NpcHairBridge;
import net.bullettrain.xenopixelsmod.dmz.race.RaceAppearanceCatalog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Appearance part indices for {@link RaceCharacterMakerScreen}.
 *
 * <p>Cited sources only — do not invent part ids:
 * <ul>
 *   <li>Body / Eyes / Mouth / Nose / Tattoo → {@link NpcAppearanceParts} → DMZ
 *       {@code TextureCounter}</li>
 *   <li>Hair presets → {@link NpcHairBridge#presetCount()} → {@code HairManager.getPresetCount()}</li>
 *   <li>Aura / Clothes → <b>citation gap</b>: no TextureCounter / NpcAppearanceParts enumerator
 *       found for clothes outfits or selectable aura-type grids (race {@code auraType} is a single
 *       config string; aura class tabs use {@code RaceStatsConfig} classes, not aura textures)</li>
 * </ul>
 */
public final class RaceMakerParts {
    public enum Category {
        BODY("Body"),
        EYES("Eyes"),
        MOUTH("Mouth"),
        HAIR("Hair"),
        TATTOO("Tattoo"),
        AURA("Aura"),
        CLOTHES("Clothes"),
        EXTRA("Extra");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static Category fromLabel(String label) {
            if (label == null) {
                return BODY;
            }
            for (Category c : values()) {
                if (c.label.equalsIgnoreCase(label.trim())) {
                    return c;
                }
            }
            return BODY;
        }
    }

    /** Stable empty-grid reason for Aura / Clothes (unit-testable). */
    public static final String AURA_CITATION_GAP =
            "Aura grid empty: no TextureCounter aura-type enumerator; race auraType is a single "
                    + "RaceCharacterConfig string (see RacePackService / ConfigManager).";
    public static final String CLOTHES_CITATION_GAP =
            "Clothes grid empty: no TextureCounter / NpcAppearanceParts clothes enumerator cited.";

    private RaceMakerParts() {
    }

    /**
     * Part tile ids for {@code category} + race + gender. Ids are {@code kind:index} from verified
     * counters (e.g. {@code body:0}, {@code hair:1}, {@code extra:tattoo:0}).
     */
    public static List<String> partIds(Category category, String raceId, String gender) {
        Category cat = category == null ? Category.BODY : category;
        String race = raceId == null ? "human" : raceId.trim().toLowerCase(Locale.ROOT);
        String gen = gender == null || gender.isBlank() ? "male" : gender.trim().toLowerCase(Locale.ROOT);
        RaceAppearanceCatalog catalog = catalogFor(race);
        return switch (cat) {
            case BODY -> bodyParts(race, gen, catalog);
            case EYES -> indexParts("eyes", NpcAppearanceParts.maxEyesType(race));
            case MOUTH -> indexParts("mouth", NpcAppearanceParts.maxMouthType(race));
            case HAIR -> hairParts(catalog);
            case TATTOO -> tattooParts(race);
            case AURA, CLOTHES -> List.of();
            case EXTRA -> extraParts(race);
        };
    }

    /** Trailing integer in {@code kind:index} ids ({@code body:2}, {@code extra:nose:1}, {@code hair:4}). */
    public static int parseIndex(String partId) {
        if (partId == null || partId.isBlank()) {
            return -1;
        }
        int colon = partId.lastIndexOf(':');
        String raw = colon < 0 ? partId.trim() : partId.substring(colon + 1).trim();
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String emptyReason(Category category) {
        if (category == Category.AURA) {
            return AURA_CITATION_GAP;
        }
        if (category == Category.CLOTHES) {
            return CLOTHES_CITATION_GAP;
        }
        return "No parts counted for this race/category (TextureCounter returned 0).";
    }

    private static int maxBody(String race, String gender) {
        NpcDmzAppearance appearance = new NpcDmzAppearance();
        appearance.gender = gender;
        return NpcAppearanceParts.maxBodyType(race, appearance);
    }

    public static final String TAOTTO_PART = "tattoo:taotto";

    private static RaceAppearanceCatalog catalogFor(String race) {
        try {
            return RaceAppearanceCatalog.loadLive(race);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<String> bodyParts(String race, String gender, RaceAppearanceCatalog catalog) {
        List<String> live = indexParts("body", maxBody(race, gender));
        if (catalog == null || catalog.bodiesFor(gender).isEmpty()) {
            return live;
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add("body:0");
        for (RaceAppearanceCatalog.BodyType body : catalog.bodiesFor(gender)) {
            ids.add("body:" + body.index());
        }
        ids.addAll(live);
        return List.copyOf(ids);
    }

    private static List<String> hairParts(RaceAppearanceCatalog catalog) {
        if (catalog != null && !catalog.hairs().isEmpty()) {
            List<String> out = new ArrayList<>(catalog.hairs().size());
            for (RaceAppearanceCatalog.HairStyle style : catalog.hairs()) {
                out.add("hair:catalog:" + style.id());
            }
            return List.copyOf(out);
        }
        int count = NpcHairBridge.presetCount();
        if (count <= 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            out.add("hair:" + i);
        }
        return List.copyOf(out);
    }

    private static List<String> tattooParts(String race) {
        List<String> out = new ArrayList<>();
        out.add(TAOTTO_PART);
        out.addAll(indexParts("tattoo", NpcAppearanceParts.maxTattooType(race)));
        return List.copyOf(out);
    }

    private static List<String> extraParts(String race) {
        return indexParts("extra:nose", NpcAppearanceParts.maxNoseType(race));
    }

    private static List<String> indexParts(String prefix, int max) {
        // Same empty rule as NpcAppearanceParts.indices: max <= 0 → no cycle/grid entries.
        List<String> indices = NpcAppearanceParts.indices(max);
        if (indices.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(indices.size());
        for (String index : indices) {
            out.add(prefix + ":" + index);
        }
        return List.copyOf(out);
    }
}
