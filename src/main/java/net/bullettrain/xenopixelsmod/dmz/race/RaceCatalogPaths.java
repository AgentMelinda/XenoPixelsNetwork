package net.bullettrain.xenopixelsmod.dmz.race;

import java.util.Locale;

/**
 * TextureCounter custom-layered body paths (DMZ 2.1.3).
 *
 * <p>When {@code customModel} is empty, {@code hasGender} is true, and {@code isLayered} is true,
 * counted files are
 * {@code textures/entity/races/<race>/<race>_<male|female>_<i>_layer1.png} starting at index 1
 * when {@code useVanillaSkin} is true. Rendering uses the same prefix.
 */
public final class RaceCatalogPaths {
    private RaceCatalogPaths() {
    }

    public static String layeredLayer1(String raceId, String gender, int index) {
        return layeredLayer1(raceId, gender, index, true);
    }

    public static String layeredLayer1(String raceId, String gender, int index, boolean hasGender) {
        String race = normal(raceId);
        String gen = "female".equalsIgnoreCase(gender) ? "female" : "male";
        int i = Math.max(0, index);
        return "textures/entity/races/" + race + "/" + race + (hasGender ? "_" + gen : "") + "_" + i + "_layer1.png";
    }

    public static String resourcePackRelative(String raceId, String gender, int index) {
        return "assets/dragonminez/" + layeredLayer1(raceId, gender, index);
    }

    static String normal(String raceId) {
        return raceId == null ? "" : raceId.trim().toLowerCase(Locale.ROOT);
    }
}
