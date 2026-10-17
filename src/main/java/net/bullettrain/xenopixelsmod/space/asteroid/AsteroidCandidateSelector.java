package net.bullettrain.xenopixelsmod.space.asteroid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.Predicate;

/** Bounded deterministic nearest-candidate selection for per-player and global activation queues. */
final class AsteroidCandidateSelector {
    record Ranked(AsteroidField.Candidate candidate, double distanceSquared) {}

    private static final java.util.Comparator<Ranked> ORDER = java.util.Comparator
            .comparingDouble(Ranked::distanceSquared)
            .thenComparing(ranked -> ranked.candidate().id())
            .thenComparing(ranked -> ranked.candidate().icy());

    private final int capacity;
    private final NavigableSet<Ranked> ordered = new TreeSet<>(ORDER);
    private final Map<AsteroidField.ObjectId, Ranked> byId = new HashMap<>();

    AsteroidCandidateSelector(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("capacity cannot be negative");
        this.capacity = capacity;
    }

    static double distanceSquared(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    void offer(AsteroidField.Candidate candidate, double distanceSquared) {
        if (capacity == 0 || candidate == null || !Double.isFinite(distanceSquared) || distanceSquared < 0) return;
        offer(candidate, distanceSquared, null);
    }

    void offer(Ranked ranked) {
        if (ranked == null) return;
        offer(ranked.candidate(), ranked.distanceSquared(), ranked);
    }

    private void offer(AsteroidField.Candidate candidate, double distanceSquared, Ranked reusable) {
        if (capacity == 0 || candidate == null || !Double.isFinite(distanceSquared) || distanceSquared < 0) return;
        AsteroidField.ObjectId id = candidate.id();
        Ranked existing = byId.get(id);
        if (existing != null) {
            if (compare(distanceSquared, id, candidate.icy(), existing) >= 0) return;
            ordered.remove(existing);
            byId.remove(id);
        }
        if (ordered.size() >= capacity) {
            Ranked farthest = ordered.last();
            if (compare(distanceSquared, id, candidate.icy(), farthest) >= 0) return;
            ordered.remove(farthest);
            byId.remove(farthest.candidate().id());
        }
        Ranked ranked = reusable == null ? new Ranked(candidate, distanceSquared) : reusable;
        ordered.add(ranked);
        byId.put(id, ranked);
    }

    private static int compare(double distanceSquared, AsteroidField.ObjectId id, boolean icy, Ranked other) {
        int order = Double.compare(distanceSquared, other.distanceSquared());
        if (order == 0) order = id.compareTo(other.candidate().id());
        if (order == 0) order = Boolean.compare(icy, other.candidate().icy());
        return order;
    }

    int size() { return ordered.size(); }
    List<Ranked> ranked() { return List.copyOf(ordered); }

    void offerAllTo(AsteroidCandidateSelector destination) {
        offerAllTo(destination, ignored -> true);
    }

    void offerAllTo(AsteroidCandidateSelector destination, Predicate<Ranked> filter) {
        if (destination == null || destination == this) return;
        for (Ranked ranked : ordered) if (filter.test(ranked)) destination.offer(ranked);
    }
}
