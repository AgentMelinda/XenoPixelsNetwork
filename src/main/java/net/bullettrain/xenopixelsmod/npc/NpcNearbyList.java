package net.bullettrain.xenopixelsmod.npc;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The Nearby NPCs list (NPC wand, right-click the air): one row per native Xeno NPC in range, as
 * MyNPCs lays it out - "distance : name", nearest first, filtered by the search box.
 */
public final class NpcNearbyList {
    /** How far the list reaches; the same radius the editor, delete and profile saves allow. */
    public static final double RANGE = 64.0;
    public static final int MAX_ENTRIES = 256;
    static final int MAX_LABEL = 40;

    /** One NPC row. {@code revision} guards Delete against a changed NPC. */
    public record Entry(int entityId, String name, String role, double distance, int revision, boolean frozen) {}

    private NpcNearbyList() {}

    public static List<Entry> filterSort(List<Entry> entries, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return entries.stream()
                .filter(e -> q.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparingDouble(Entry::distance))
                .toList();
    }

    public static String label(Entry entry) {
        String label = String.format(Locale.ROOT, "%04.1f : %s", entry.distance(), entry.name());
        return label.length() <= MAX_LABEL ? label : label.substring(0, MAX_LABEL - 3) + "...";
    }
}
