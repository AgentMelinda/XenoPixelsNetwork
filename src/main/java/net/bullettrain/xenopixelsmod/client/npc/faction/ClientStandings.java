package net.bullettrain.xenopixelsmod.client.npc.faction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * This player's faction standings, as the client knows them.
 *
 * <p>{@link ClientFactions} carries what the factions <em>are</em>, including each one's default
 * standing. That default is the faction's, not the player's - reading it as the player's would show
 * everyone on a server identical numbers, and would look entirely right while doing so.
 *
 * <p>Standings live in {@code XenoPlayerData} on the server and reach no client on their own, which
 * is the same trap the faction list itself hit.
 *
 * <p>A map looked up by id, never iterated for display: the server-side source is a
 * {@code Map.copyOf}, which is unordered. The Factions tab walks {@link ClientFactions#all()} -
 * which is ordered - and asks this holder one faction at a time.
 */
public final class ClientStandings {

    /** Matches {@code SyncFactionsPacket.MAX_FACTIONS}: a player cannot stand with more. */
    public static final int MAX_STANDINGS = 256;

    private static volatile Map<String, Integer> standings = Map.of();

    private ClientStandings() {
    }

    /** Replaces the client's view. Called when the server syncs. */
    public static void accept(Map<String, Integer> next) {
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry
                : (next == null ? Map.<String, Integer>of() : next).entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || copy.size() >= MAX_STANDINGS) {
                continue;
            }
            copy.put(entry.getKey().trim().toLowerCase(Locale.ROOT),
                    entry.getValue() == null ? 0 : entry.getValue());
        }
        standings = Collections.unmodifiableMap(copy);
    }

    /**
     * This player's standing with {@code factionId}, or {@code fallback} when they have none.
     *
     * <p>The fallback is the faction's own default, which is what
     * {@code XenoPlayerData.getFactionStanding} returns server-side for a faction the player has
     * never interacted with. Defaulting to zero instead would read as "neutral" for a faction whose
     * default is hostile.
     */
    public static int of(String factionId, int fallback) {
        if (factionId == null || factionId.isBlank()) {
            return fallback;
        }
        Integer stored = standings.get(factionId.trim().toLowerCase(Locale.ROOT));
        return stored == null ? fallback : stored;
    }

    /** How many standings are held. For tests, and for a bounds check. */
    public static int size() {
        return standings.size();
    }

    /** Forgets them. Called on disconnect, so world A's standings do not outlive world A. */
    public static void clear() {
        standings = Map.of();
    }
}
