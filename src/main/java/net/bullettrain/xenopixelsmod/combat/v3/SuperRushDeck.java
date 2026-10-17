package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.RandomSource;

/** Per-player shuffled catalog: no attack repeats until the whole current catalog was used. */
final class SuperRushDeck {
    private static final class Deck { final Deque<String> ids = new ArrayDeque<>(); String last; }
    private static final Map<UUID, Deck> DECKS = new HashMap<>();
    private SuperRushDeck() {}

    static String next(UUID player, List<String> catalog, RandomSource random) {
        if (player == null || catalog == null || catalog.isEmpty() || random == null) return null;
        Deck deck = DECKS.computeIfAbsent(player, ignored -> new Deck());
        if (deck.ids.isEmpty()) {
            List<String> shuffled = new ArrayList<>(catalog);
            Collections.shuffle(shuffled, new java.util.Random(random.nextLong()));
            if (shuffled.size() > 1 && shuffled.getFirst().equals(deck.last))
                Collections.swap(shuffled, 0, 1);
            deck.ids.addAll(shuffled);
        }
        deck.last = deck.ids.removeFirst();
        return deck.last;
    }

    static boolean ultimate(RandomSource random) { return random != null && isUltimateRoll(random.nextInt(4)); }
    static boolean isUltimateRoll(int draw) { return draw >= 0 && draw < 3; }
    static void clear(UUID player) { if (player != null) DECKS.remove(player); }
    static void clearAll() { DECKS.clear(); }
}
