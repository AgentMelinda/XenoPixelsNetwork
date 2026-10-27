package net.bullettrain.xenopixelsmod.space.hakai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Fair, bounded-per-tick dispatch order for full integrity snapshots and their later deltas. */
final class PlanetIntegritySyncQueue {
    static final int MAX_PACKETS_PER_SERVER_TICK = 4;
    static final int MAX_PENDING_UPDATE_BODIES = PlanetIntegritySnapshot.MAX_ENTRIES;

    enum DeltaAdmission { DIRECT, QUEUED, SNAPSHOT_REFRESH_REQUIRED }

    record Delivery(UUID playerId, Object packet) {}

    private static final class Pending {
        List<PlanetIntegritySnapshot> pages;
        int nextPage;
        final TreeMap<Long, PlanetIntegrityUpdate> updatesByRevision = new TreeMap<>();
        final Map<String, Long> revisionByBody = new HashMap<>();

        Pending(List<PlanetIntegritySnapshot> pages) {
            this.pages = List.copyOf(pages);
        }

        void replaceSnapshot(List<PlanetIntegritySnapshot> replacement) {
            long snapshotRevision = replacement.getFirst().revision();
            var newerUpdates = new ArrayList<>(updatesByRevision.tailMap(snapshotRevision, false).values());
            pages = List.copyOf(replacement);
            nextPage = 0;
            updatesByRevision.clear();
            revisionByBody.clear();
            for (var update : newerUpdates) addUpdate(update);
        }

        void addUpdate(PlanetIntegrityUpdate update) {
            Long previousRevision = revisionByBody.put(update.bodyId(), update.revision());
            if (previousRevision != null) updatesByRevision.remove(previousRevision);
            updatesByRevision.put(update.revision(), update);
        }

        Object poll() {
            if (nextPage < pages.size()) return pages.get(nextPage++);
            var oldest = updatesByRevision.pollFirstEntry();
            if (oldest == null) return null;
            revisionByBody.remove(oldest.getValue().bodyId(), oldest.getKey());
            return oldest.getValue();
        }

        boolean isEmpty() {
            return nextPage >= pages.size() && updatesByRevision.isEmpty();
        }
    }

    private final Map<UUID, Pending> pendingByPlayer = new HashMap<>();
    private final ArrayDeque<UUID> roundRobin = new ArrayDeque<>();
    private final int maxPendingUpdateBodies;

    PlanetIntegritySyncQueue() {
        this(MAX_PENDING_UPDATE_BODIES);
    }

    PlanetIntegritySyncQueue(int maxPendingUpdateBodies) {
        if (maxPendingUpdateBodies < 1 || maxPendingUpdateBodies > MAX_PENDING_UPDATE_BODIES)
            throw new IllegalArgumentException("invalid deferred-update bound");
        this.maxPendingUpdateBodies = maxPendingUpdateBodies;
    }

    void enqueueSnapshot(UUID playerId, List<PlanetIntegritySnapshot> pages) {
        if (pages == null || pages.isEmpty()) throw new IllegalArgumentException("snapshot pages are required");
        Pending pending = pendingByPlayer.get(playerId);
        if (pending == null) {
            pendingByPlayer.put(playerId, new Pending(pages));
            roundRobin.addLast(playerId);
        } else {
            pending.replaceSnapshot(pages);
        }
    }

    /** Returns true when a pending snapshot requires this delta to wait behind its pages. */
    DeltaAdmission enqueueUpdateIfSnapshotPending(UUID playerId, PlanetIntegrityUpdate update) {
        Pending pending = pendingByPlayer.get(playerId);
        if (pending == null) return DeltaAdmission.DIRECT;
        if (!pending.revisionByBody.containsKey(update.bodyId())
                && pending.revisionByBody.size() >= maxPendingUpdateBodies)
            return DeltaAdmission.SNAPSHOT_REFRESH_REQUIRED;
        pending.addUpdate(update);
        return DeltaAdmission.QUEUED;
    }

    List<Delivery> drainServerTick() {
        var deliveries = new ArrayList<Delivery>(MAX_PACKETS_PER_SERVER_TICK);
        while (deliveries.size() < MAX_PACKETS_PER_SERVER_TICK && !roundRobin.isEmpty()) {
            UUID playerId = roundRobin.removeFirst();
            Pending pending = pendingByPlayer.get(playerId);
            if (pending == null) continue;
            Object packet = pending.poll();
            if (packet != null) deliveries.add(new Delivery(playerId, packet));
            if (pending.isEmpty()) pendingByPlayer.remove(playerId);
            else roundRobin.addLast(playerId);
        }
        return List.copyOf(deliveries);
    }

    void remove(UUID playerId) {
        pendingByPlayer.remove(playerId);
        roundRobin.remove(playerId);
    }

    boolean isEmpty() { return pendingByPlayer.isEmpty(); }
    int pendingPlayers() { return pendingByPlayer.size(); }
}
