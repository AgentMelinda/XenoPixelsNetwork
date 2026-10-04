package net.bullettrain.xenopixelsmod.ui;

import java.util.EnumMap;
import java.util.Map;

/**
 * Local mapping of a DMZ V-page to an authored document id.
 *
 * <p>Missing assignments return null so the caller keeps DragonMineZ's own screen.
 */
public final class DmzMenuAssignments {
    private final EnumMap<DmzMenuPage, String> map = new EnumMap<>(DmzMenuPage.class);

    public String documentId(DmzMenuPage page) {
        if (page == null) return null;
        String id = map.get(page);
        return id == null || id.isBlank() ? null : id;
    }

    public void set(DmzMenuPage page, String documentId) {
        if (page == null) return;
        if (documentId == null || documentId.isBlank()) {
            map.remove(page);
        } else {
            map.put(page, documentId.trim());
        }
    }

    public Map<DmzMenuPage, String> view() {
        return Map.copyOf(map);
    }
}
