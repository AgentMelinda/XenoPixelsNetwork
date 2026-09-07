package net.bullettrain.xenopixelsmod.combat.clone;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/** Session-only lock leases; retains entity identity, never a reusable numeric entity id. */
public final class CloneTargetTracker<T> {
    public static final int HEARTBEAT_TICKS = 20;
    public static final int LEASE_TICKS = 60;
    private final Map<UUID, Entry<T>> entries = new HashMap<>();

    private static final class Entry<T> {
        T target;
        long receivedAt = Long.MIN_VALUE;
        long checkedAt = Long.MIN_VALUE;
    }

    public void accept(UUID owner, int targetId, long now, IntFunction<T> resolve, Predicate<T> valid) {
        Entry<T> entry = entries.computeIfAbsent(owner, ignored -> new Entry<>());
        // Clear always wins, including when a client releases in the same tick it acquired.
        if (targetId < 0) {
            entry.target = null;
            return;
        }
        if (entry.checkedAt == now) return;
        entry.checkedAt = now;
        T target = resolve.apply(targetId);
        entry.target = target != null && valid.test(target) ? target : null;
        entry.receivedAt = now;
    }

    public T target(UUID owner, long now, Predicate<T> valid) {
        Entry<T> entry = entries.get(owner);
        if (entry == null || entry.target == null) return null;
        if (now < entry.receivedAt || now - entry.receivedAt >= LEASE_TICKS || !valid.test(entry.target)) {
            entry.target = null;
        }
        return entry.target;
    }

    public void forget(UUID owner) {
        entries.remove(owner);
    }

    public void clear() {
        entries.clear();
    }
}
