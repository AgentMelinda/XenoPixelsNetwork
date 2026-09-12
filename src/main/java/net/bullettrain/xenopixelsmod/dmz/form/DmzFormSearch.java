package net.bullettrain.xenopixelsmod.dmz.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Substring search over the form-studio group and form lists. */
public final class DmzFormSearch {
    private DmzFormSearch() {
    }

    /**
     * Case-insensitive substring filter preserving the source order. A blank query keeps the
     * whole list, so an empty search box behaves exactly like no search box at all.
     */
    public static List<String> filter(List<String> values, String query) {
        if (values == null) return List.of();
        if (query == null || query.isBlank()) return values;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(needle)) matches.add(value);
        }
        return matches;
    }
}
