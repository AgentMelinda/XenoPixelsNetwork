package net.bullettrain.xenopixelsmod.client.npc.spawn;

import net.bullettrain.xenopixelsmod.npc.spawn.NpcNaturalSpawn;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The natural spawn rules as the client knows them.
 *
 * <p>The rules live in the world store, which only the server reads, so without this the editor's
 * Natural Spawns page could list nothing and could not show what a stored rule actually holds. The
 * same gap factions, banks and the store index each hit, and the same fix — deliberately shaped like
 * {@code ClientBanks} so there is one pattern here rather than two.
 *
 * <p>Ordered by arrival, because the page numbers its rows and a scrambled list makes "Remove rule
 * 3" mean something different every time the server speaks.
 */
public final class ClientNaturalSpawns {

    private static volatile Map<String, NpcNaturalSpawn> known = Map.of();

    private ClientNaturalSpawns() {
    }

    /** Replaces the client's view. Called when the server syncs, and on disconnect with nothing. */
    public static void accept(List<NpcNaturalSpawn> entries) {
        Map<String, NpcNaturalSpawn> next = new LinkedHashMap<>();
        for (NpcNaturalSpawn entry : entries == null ? List.<NpcNaturalSpawn>of() : entries) {
            if (!entry.id().isBlank()) {
                next.put(entry.id(), entry);
            }
        }
        known = Collections.unmodifiableMap(next);
    }

    /** Every rule, in the order the server sent them. */
    public static List<NpcNaturalSpawn> all() {
        return List.copyOf(known.values());
    }

    /** One rule, or null when the server never sent it. */
    public static NpcNaturalSpawn get(String id) {
        return id == null || id.isBlank() ? null : known.get(id.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** Whether the server has sent anything yet. */
    public static boolean isEmpty() {
        return known.isEmpty();
    }

    /** Forgets everything. Called on disconnect so one world's rules are not shown in another. */
    public static void clear() {
        accept(List.of());
    }
}
