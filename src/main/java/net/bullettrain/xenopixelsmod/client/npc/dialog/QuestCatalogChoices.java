package net.bullettrain.xenopixelsmod.client.npc.dialog;

import net.bullettrain.xenopixelsmod.client.npc.store.ClientNpcStoreIndex;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Quest choices for the dialogue editor, backed by the server-sent global catalog. */
public final class QuestCatalogChoices {

    public record Choice(String id, String title) {
        public Choice {
            id = id == null ? "" : id;
            title = title == null || title.isBlank() ? id : title;
        }

        public String label() {
            if (title.equals("Unavailable: " + id)) {
                return title;
            }
            return title.equals(id) ? id : title + " (" + id + ")";
        }
    }

    private QuestCatalogChoices() {
    }

    public static List<Choice> fromIndex(List<ClientNpcStoreIndex.Entry> entries,
                                         String selectedId) {
        Map<String, Choice> byId = new LinkedHashMap<>();
        for (ClientNpcStoreIndex.Entry entry : entries == null
                ? List.<ClientNpcStoreIndex.Entry>of() : entries) {
            if (entry == null || entry.category() != XenoNpcStoreCategory.QUESTS
                    || entry.id().isBlank()) {
                continue;
            }
            byId.putIfAbsent(entry.id(), new Choice(entry.id(), entry.label()));
        }
        if (selectedId != null && !selectedId.isBlank()) {
            byId.putIfAbsent(selectedId, new Choice(selectedId, "Unavailable: " + selectedId));
        }
        return List.copyOf(new ArrayList<>(byId.values()));
    }

    public static int selectedIndex(List<Choice> choices, String selectedId) {
        if (choices == null || selectedId == null) {
            return 0;
        }
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).id().equals(selectedId)) {
                return i;
            }
        }
        return 0;
    }
}
