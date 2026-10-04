package net.bullettrain.xenopixelsmod.client.npc.store;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the world store holds, as the client knows it.
 *
 * <p>The store is server-side: it reads and writes files under the world folder, which a client
 * has no access to even in single-player once a dedicated server is involved. So the editor is told
 * what exists, and asks for a particular entry's contents only when somebody opens it. The same
 * split {@code ClientFactions} already uses, and for the same reason - the fourth time this trap
 * has come up here, after the mark icons, the bubble palettes and the faction list.
 *
 * <p>An <em>index</em> rather than the entries themselves. Listing needs a name; editing needs the
 * whole thing, and only for one entry at a time. Broadcasting every dialogue in the world to every
 * client would be paying continuously for something almost nobody opens.
 */
public final class ClientNpcStoreIndex {

    /**
     * One entry, as much as a list needs.
     *
     * @param label what to show - a name where the entry has one, otherwise the id
     * @param revision what the server had when it sent this, so a save can say what it was editing
     */
    public record Entry(XenoNpcStoreCategory category, String group, String id, String label,
                        int revision) {
        public Entry {
            group = group == null ? "" : group;
            id = id == null ? "" : id;
            label = label == null || label.isBlank() ? id : label;
            revision = Math.max(0, revision);
        }

        /** An entry the client is describing rather than echoing, so it claims no revision. */
        public Entry(XenoNpcStoreCategory category, String group, String id, String label) {
            this(category, group, id, label, 0);
        }

        /** How the store addresses it: {@code group/id}, or just {@code id} when ungrouped. */
        public String key() {
            return group.isEmpty() ? id : group + "/" + id;
        }
    }

    private static volatile Map<XenoNpcStoreCategory, List<Entry>> known = emptyIndex();

    private ClientNpcStoreIndex() {
    }

    private static Map<XenoNpcStoreCategory, List<Entry>> emptyIndex() {
        Map<XenoNpcStoreCategory, List<Entry>> map =
                new EnumMap<>(XenoNpcStoreCategory.class);
        for (XenoNpcStoreCategory category : XenoNpcStoreCategory.values()) {
            map.put(category, List.of());
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Replaces the client's view.
     *
     * <p>Replaces rather than merges: an entry the operator deleted has to disappear here too, or
     * the editor keeps offering one the server no longer has.
     */
    public static void accept(List<Entry> entries) {
        Map<XenoNpcStoreCategory, List<Entry>> next =
                new EnumMap<>(XenoNpcStoreCategory.class);
        for (XenoNpcStoreCategory category : XenoNpcStoreCategory.values()) {
            next.put(category, new ArrayList<>());
        }
        for (Entry entry : entries == null ? List.<Entry>of() : entries) {
            if (entry != null && entry.category() != null && !entry.id().isEmpty()) {
                next.get(entry.category()).add(entry);
            }
        }
        Map<XenoNpcStoreCategory, List<Entry>> frozen =
                new EnumMap<>(XenoNpcStoreCategory.class);
        // Order is preserved on purpose: the server sends them sorted, and a list that reshuffled
        // between reloads would be worse than one that is merely long.
        next.forEach((category, list) -> frozen.put(category, List.copyOf(list)));
        known = Collections.unmodifiableMap(frozen);
    }

    /** Clears it. Called on disconnect, so one world's content cannot show up in the next. */
    public static void clear() {
        known = emptyIndex();
    }

    /** Everything stored in a category, in the order the server sent it. */
    public static List<Entry> of(XenoNpcStoreCategory category) {
        return category == null ? List.of() : known.get(category);
    }

    /** Whether a category holds anything. */
    public static boolean isEmpty(XenoNpcStoreCategory category) {
        return of(category).isEmpty();
    }

    /** One entry by the address the store uses, or null. */
    public static Entry find(XenoNpcStoreCategory category, String group, String id) {
        String wanted = new Entry(category, group, id, id).key();
        for (Entry entry : of(category)) {
            if (entry.key().equals(wanted)) {
                return entry;
            }
        }
        return null;
    }
}
