package net.bullettrain.xenopixelsmod.dmz.form;

import java.util.List;
import java.util.Locale;

/**
 * DragonMineZ {@code FormData.hairType} values actually present in the pinned 2.1.3 form
 * files: {@code base}, {@code ssj}, {@code ssj2}, {@code ssj3}.
 *
 * <p>The form studio pager shows {@code hairType} as a text field. The cycle button next to it
 * walks this list so an operator can pick a stock character hair option without typing.
 */
public final class DmzHairTypes {
    /** Verified against every {@code "hairType"} in this repository's DMZ form JSON. */
    public static final List<String> VALUES = List.of("base", "ssj", "ssj2", "ssj3");

    private DmzHairTypes() {
    }

    public static String cycle(String current, int delta) {
        if (VALUES.isEmpty()) return current == null ? "" : current;
        int index = indexOf(current);
        int next = Math.floorMod(index + delta, VALUES.size());
        return VALUES.get(next);
    }

    public static int indexOf(String current) {
        if (current == null || current.isBlank()) return 0;
        String key = current.trim().toLowerCase(Locale.ROOT);
        int found = VALUES.indexOf(key);
        return found < 0 ? 0 : found;
    }
}
