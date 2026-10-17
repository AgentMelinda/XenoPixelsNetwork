package net.bullettrain.xenopixelsmod.space.asteroid;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;

/** Bounded-lifecycle queue: one pending fracture operation per body, cancelable when that body is released. */
public final class AsteroidFractureQueue<T> {
    public record Work<T>(UUID bodyId, T payload) {}

    private final ArrayDeque<Work<T>> work = new ArrayDeque<>();

    public boolean enqueue(UUID bodyId, T payload) {
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(payload, "payload");
        if (hasBody(bodyId)) return false;
        work.addLast(new Work<>(bodyId, payload));
        return true;
    }

    public Work<T> first() { return work.getFirst(); }

    public Work<T> removeFirst() { return work.removeFirst(); }

    public boolean hasBody(UUID bodyId) {
        for (Work<T> item : work) if (item.bodyId().equals(bodyId)) return true;
        return false;
    }

    public int cancelBody(UUID bodyId) {
        int removed = 0;
        for (Iterator<Work<T>> it = work.iterator(); it.hasNext();) {
            if (it.next().bodyId().equals(bodyId)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    public boolean isEmpty() { return work.isEmpty(); }

    public int size() { return work.size(); }
}
