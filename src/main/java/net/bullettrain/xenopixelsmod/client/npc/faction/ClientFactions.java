package net.bullettrain.xenopixelsmod.client.npc.faction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The faction list as the client knows it.
 *
 * <p>Factions are loaded from a datapack, which is a server-side reload listener - so the client
 * has no idea any of them exist until the server says so. The editor runs on the client, so without
 * this its faction screens would be permanently empty and read as broken rather than as unloaded.
 * The same trap the mark icons and the bubble palettes both hit.
 *
 * <p>Deliberately a thin mirror rather than the real {@code XenoFaction}: the client only needs
 * enough to list, colour and name them. Hostility is decided on the server, which is where the
 * authoritative copy lives.
 *
 * @see net.bullettrain.xenopixelsmod.npc.faction.XenoFactions
 */
public final class ClientFactions {

    /** One faction, as much of it as a client screen needs. */
    public record Entry(String id, String name, int color, List<String> hostileTo,
                        int defaultStanding, boolean attackedByMobs,
                        boolean aggressiveToMobs, List<String> attackableMobs) {
        public Entry(String id, String name, int color, List<String> hostileTo,
                     int defaultStanding) {
            this(id, name, color, hostileTo, defaultStanding, false, false, List.of());
        }

        public Entry {
            id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            name = name == null || name.isBlank() ? id : name;
            color = color & 0xFFFFFF;
            hostileTo = List.copyOf(hostileTo == null ? List.of() : hostileTo);
            attackableMobs = List.copyOf(attackableMobs == null ? List.of() : attackableMobs);
        }
    }

    private static volatile Map<String, Entry> known = Map.of();

    private ClientFactions() {
    }

    /** Replaces the client's view. Called when the server syncs, and on disconnect with nothing. */
    public static void accept(List<Entry> entries) {
        Map<String, Entry> next = new LinkedHashMap<>();
        for (Entry entry : entries == null ? List.<Entry>of() : entries) {
            if (!entry.id().isBlank()) {
                next.put(entry.id(), entry);
            }
        }
        // Not Map.copyOf: that returns an unordered map, which would have silently scrambled the
        // list every screen below reads. The server sends them in pack order and a cycler should
        // walk them in that order too.
        known = Collections.unmodifiableMap(next);
    }

    /** Every faction, in the order the server sent them. */
    public static List<Entry> all() {
        return List.copyOf(known.values());
    }

    /** Ids only, for a cycler. */
    public static List<String> ids() {
        return List.copyOf(known.keySet());
    }

    /** One faction, or null when the server never sent it. */
    public static Entry get(String id) {
        return id == null || id.isBlank() ? null : known.get(id.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Whether the server has sent anything yet.
     *
     * <p>Distinct from "no factions exist": a screen should say which of the two it is, because one
     * means the pack defines none and the other means the sync has not arrived.
     */
    public static boolean isEmpty() {
        return known.isEmpty();
    }
}
