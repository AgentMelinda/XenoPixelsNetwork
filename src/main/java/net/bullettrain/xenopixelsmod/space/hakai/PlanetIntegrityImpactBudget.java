package net.bullettrain.xenopixelsmod.space.hakai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded transient per-player/per-body rate limit for projectile callbacks that can repeat each tick. */
public final class PlanetIntegrityImpactBudget {
    private record Key(UUID player, String bodyId) {}

    private final int capacity;
    private final LinkedHashMap<Key, Long> lastAcceptedTick = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> lastBodyAcceptedTick = new LinkedHashMap<>();
    private long lastObservedTick = Long.MIN_VALUE;

    public PlanetIntegrityImpactBudget(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    /** Uses one shared interval for the player/body pair and the aggregate body ceiling. */
    public boolean acquire(UUID player, String bodyId, long gameTick, int cooldownTicks) {
        return acquire(player, bodyId, gameTick, cooldownTicks, cooldownTicks);
    }

    /** Also enforces a global per-body interval, preventing damage rate from scaling with online attackers. */
    public boolean acquire(UUID player, String bodyId, long gameTick, int playerCooldownTicks, int bodyCooldownTicks) {
        if (player == null || !validBodyId(bodyId) || gameTick < 0 || playerCooldownTicks < 1 || bodyCooldownTicks < 1) return false;
        if (lastObservedTick != Long.MIN_VALUE && gameTick < lastObservedTick) {
            lastAcceptedTick.clear();
            lastBodyAcceptedTick.clear();
        }
        lastObservedTick = gameTick;

        pruneExpired(lastAcceptedTick, gameTick, playerCooldownTicks);
        pruneExpired(lastBodyAcceptedTick, gameTick, bodyCooldownTicks);

        var key = new Key(player, bodyId);
        Long previous = lastAcceptedTick.get(key);
        if (previous != null && gameTick - previous < playerCooldownTicks) return false;
        Long bodyPrevious = lastBodyAcceptedTick.get(bodyId);
        if (bodyPrevious != null && gameTick - bodyPrevious < bodyCooldownTicks) return false;
        if (previous != null) lastAcceptedTick.remove(key);
        if (lastAcceptedTick.size() >= capacity)
            lastAcceptedTick.remove(lastAcceptedTick.keySet().iterator().next());
        lastAcceptedTick.put(key, gameTick);
        boolean newBody = bodyPrevious == null;
        // A refreshed body must move to the tail so age-ordered pruning/eviction remains valid.
        lastBodyAcceptedTick.remove(bodyId);
        if (newBody && lastBodyAcceptedTick.size() >= capacity)
            lastBodyAcceptedTick.remove(lastBodyAcceptedTick.keySet().iterator().next());
        lastBodyAcceptedTick.put(bodyId, gameTick);
        return true;
    }

    public int size() { return lastAcceptedTick.size(); }
    public int bodyCount() { return lastBodyAcceptedTick.size(); }

    public void clear() {
        lastAcceptedTick.clear();
        lastBodyAcceptedTick.clear();
        lastObservedTick = Long.MIN_VALUE;
    }

    private static <K> void pruneExpired(LinkedHashMap<K, Long> accepted, long now, int cooldownTicks) {
        while (!accepted.isEmpty()) {
            var oldest = accepted.entrySet().iterator().next();
            if (now - oldest.getValue() < cooldownTicks) break;
            accepted.remove(oldest.getKey());
        }
    }

    static boolean validBodyId(String id) {
        return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
    }
}
