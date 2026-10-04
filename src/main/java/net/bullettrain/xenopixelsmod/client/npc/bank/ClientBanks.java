package net.bullettrain.xenopixelsmod.client.npc.bank;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The bank list as the client knows it.
 *
 * <p>Banks live in the world store, which only the server reads, so without this the editor's
 * Banks page would be permanently empty and read as broken rather than as unloaded. The same trap
 * the factions, the mark icons and the bubble palettes all hit — and the same fix, deliberately
 * shaped the same way as {@code ClientFactions} so there is one pattern here rather than two.
 *
 * <p>Carries the tab costs because the editor's page shows them. It does <b>not</b> carry anybody's
 * account: what a player has in a vault never reaches another player's client, and never needs to
 * reach their own this way — the container menu syncs the slots it opens.
 */
public final class ClientBanks {

    /** One bank's tab, as much of it as an editor row needs. */
    public record Tab(String name, String costItem, int costCount, int startSlots,
                      boolean upgradable) {
        public Tab {
            name = name == null || name.isBlank() ? "Vault" : name;
            costItem = costItem == null ? "" : costItem;
        }

        /** What the cost row shows. Blank cost reads as free rather than as an empty field. */
        public String costLabel() {
            return costItem.isEmpty() ? "Free" : costCount + "x " + costItem;
        }
    }

    /** One bank, as much of it as a client screen needs. */
    public record Entry(String id, String name, int withdrawFeePercent, List<Tab> tabs) {
        public Entry {
            id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            name = name == null || name.isBlank() ? id : name;
            tabs = List.copyOf(tabs == null ? List.of() : tabs);
        }
    }

    private static volatile Map<String, Entry> known = Map.of();

    private ClientBanks() {
    }

    /** Replaces the client's view. Called when the server syncs, and on disconnect with nothing. */
    public static void accept(List<Entry> entries) {
        Map<String, Entry> next = new LinkedHashMap<>();
        for (Entry entry : entries == null ? List.<Entry>of() : entries) {
            if (!entry.id().isBlank()) {
                next.put(entry.id(), entry);
            }
        }
        // Not Map.copyOf: that returns an unordered map, which would silently scramble the list
        // every screen below reads. The same mistake this codebase has now made four times.
        known = Collections.unmodifiableMap(next);
    }

    /** Every bank, in the order the server sent them. */
    public static List<Entry> all() {
        return List.copyOf(known.values());
    }

    /** One bank, or null when the server never sent it. */
    public static Entry get(String id) {
        return id == null || id.isBlank() ? null : known.get(id.trim().toLowerCase(Locale.ROOT));
    }

    /** Whether the server has sent anything yet. */
    public static boolean isEmpty() {
        return known.isEmpty();
    }
}
