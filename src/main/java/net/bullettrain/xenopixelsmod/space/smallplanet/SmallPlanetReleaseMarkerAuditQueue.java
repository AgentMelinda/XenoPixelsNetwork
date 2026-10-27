package net.bullettrain.xenopixelsmod.space.smallplanet;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.UUID;

/** Prioritized, duplicate-safe queues for bounded release-marker storage audits. */
final class SmallPlanetReleaseMarkerAuditQueue {
    record Candidate(UUID bodyId, boolean prioritized) {}

    private final LinkedHashSet<UUID> fallback = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> priority = new LinkedHashSet<>();

    void enqueue(UUID bodyId) {
        if (!priority.contains(bodyId)) fallback.add(bodyId);
    }

    /** O(1) membership transfer: no linear scan and no stale fallback node. */
    void prioritize(UUID bodyId) {
        fallback.remove(bodyId);
        priority.add(bodyId);
    }

    Candidate poll() {
        UUID bodyId = removeFirst(priority);
        if (bodyId != null) return new Candidate(bodyId, true);
        bodyId = removeFirst(fallback);
        return bodyId == null ? null : new Candidate(bodyId, false);
    }

    void retry(Candidate candidate) {
        // Failed/pending candidates re-enter at the tail and cannot monopolize future saves.
        enqueue(candidate.bodyId());
    }

    void remove(UUID bodyId) {
        priority.remove(bodyId);
        fallback.remove(bodyId);
    }

    Batch beginBatch(int maxVisits) {
        return new Batch(this, Math.max(0, maxVisits));
    }

    boolean isEmpty() { return priority.isEmpty() && fallback.isEmpty(); }
    boolean contains(UUID bodyId) { return priority.contains(bodyId) || fallback.contains(bodyId); }
    boolean isFallback(UUID bodyId) { return fallback.contains(bodyId); }
    boolean isPrioritized(UUID bodyId) { return priority.contains(bodyId); }
    int size() { return priority.size() + fallback.size(); }
    int fallbackSize() { return fallback.size(); }
    int prioritySize() { return priority.size(); }

    private static UUID removeFirst(LinkedHashSet<UUID> entries) {
        Iterator<UUID> iterator = entries.iterator();
        if (!iterator.hasNext()) return null;
        UUID first = iterator.next();
        iterator.remove();
        return first;
    }

    static final class Batch {
        private final SmallPlanetReleaseMarkerAuditQueue queue;
        private final java.util.Set<UUID> attempted = new java.util.HashSet<>();
        private int visitsRemaining;

        private Batch(SmallPlanetReleaseMarkerAuditQueue queue, int visitsRemaining) {
            this.queue = queue;
            this.visitsRemaining = visitsRemaining;
        }

        Candidate pollNext() {
            while (visitsRemaining > 0) {
                Candidate candidate = queue.poll();
                if (candidate == null) return null;
                visitsRemaining--;
                if (attempted.add(candidate.bodyId())) return candidate;
                queue.retry(candidate);
            }
            return null;
        }

        void retry(Candidate candidate) { queue.retry(candidate); }
    }
}
