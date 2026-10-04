package net.bullettrain.xenopixelsmod.client.npc.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The player's active quests as the client knows them.
 *
 * <p>Quest state lives in {@code QuestBook} on the server and reaches no client today. The log
 * screen runs on the client, so without this it would be permanently empty - which reads as a
 * broken screen rather than as data that has not arrived. The same trap the faction list, the mark
 * icons, the bubble palettes and the dialogue trees each hit in turn.
 *
 * <p>Carries each quest's <em>definition</em> text as well as its progress. Definitions are
 * datapack state, which is also server-side, so a client sent only ids could render nothing but
 * ids.
 *
 * <p>Active quests only. Completed quests stay on the server for {@code MasterPrerequisites} and
 * the availability checks a later spec will add; nothing on the client displays them, and syncing
 * them would send a list that grows without bound over a world's lifetime.
 *
 * @see net.bullettrain.xenopixelsmod.features.progression.QuestBook
 */
public final class ClientQuests {

    /** Matches {@code QuestBook.MAX_ACTIVE}; a server claiming more is not to be believed. */
    public static final int MAX_QUESTS = 32;

    /** One active quest, as much of it as the log screen needs. */
    public record Entry(String id, String title, String category, String logText,
                        int progress, int target, boolean ready, String completerNpc) {
        public Entry {
            id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            // Falls back to the id so a quest whose definition the client never received renders
            // as something readable and selectable rather than as a blank row.
            title = title == null || title.isBlank() ? id : title;
            category = category == null ? "" : category.trim();
            logText = logText == null ? "" : logText;
            target = Math.max(1, target);
            progress = Math.max(0, Math.min(target, progress));
            completerNpc = completerNpc == null ? "" : completerNpc.trim();
        }
    }

    private static volatile List<Entry> active = List.of();
    private static volatile boolean synced;

    private ClientQuests() {
    }

    /** Replaces the client's view. Called when the server syncs. */
    public static void accept(List<Entry> entries) {
        List<Entry> next = new ArrayList<>();
        for (Entry entry : entries == null ? List.<Entry>of() : entries) {
            if (!entry.id().isBlank() && next.size() < MAX_QUESTS) {
                next.add(entry);
            }
        }
        // The toast fires on an id that was not here before - not on every sync, because progress
        // pushes one per kill and a toast that re-fired on each would never leave the screen. The
        // first sync after login only establishes the baseline: announcing it would greet a
        // returning player with a toast for every quest they took hours ago.
        if (synced) {
            for (Entry entry : next) {
                if (!containsId(active, entry.id())) {
                    QuestToastOverlay.show(entry.title());
                }
            }
        }
        // Deliberately a list, in the order the server sent: that is quest start order, and it is
        // the order the log walks. A map would have unordered it.
        active = Collections.unmodifiableList(next);
        synced = true;
    }

    private static boolean containsId(List<Entry> entries, String id) {
        for (Entry entry : entries) {
            if (entry.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Every active quest, in the order the server sent them. */
    public static List<Entry> all() {
        return active;
    }

    /**
     * Whether the server has sent anything yet.
     *
     * <p>Distinct from "you have no quests": one means the log is genuinely empty, the other means
     * the sync has not arrived. A screen should say which, because they are not the same news.
     */
    public static boolean isSynced() {
        return synced;
    }

    /**
     * Forgets everything, including that a sync ever happened.
     *
     * <p>Called on disconnect. This holder is static and outlives a world, so without it a player
     * leaving world A and joining world B would see world A's quests until the new sync landed -
     * and if world B has none, would see them indefinitely.
     */
    public static void clear() {
        active = List.of();
        synced = false;
        QuestToastOverlay.reset();
    }
}
