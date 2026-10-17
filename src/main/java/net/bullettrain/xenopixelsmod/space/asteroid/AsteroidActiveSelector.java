package net.bullettrain.xenopixelsmod.space.asteroid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic retention policy that enforces live asteroid caps after players move or config changes. */
final class AsteroidActiveSelector {
    private record Ranked(AsteroidField.Candidate candidate, double nearestDistanceSquared) {}
    record Selection(Set<AsteroidField.ObjectId> retained, boolean keepBuilding) {}

    private AsteroidActiveSelector() {}

    static Set<AsteroidField.ObjectId> retain(Collection<AsteroidField.Candidate> active,
                                               List<double[]> viewers,
                                               double releaseRange,
                                               int maxPerViewer,
                                               int maxGlobal,
                                               AsteroidField.Candidate building,
                                               Set<AsteroidField.ObjectId> pinned) {
        return select(active, viewers, releaseRange, maxPerViewer, maxGlobal, building, pinned).retained();
    }

    static Selection select(Collection<AsteroidField.Candidate> active,
                            List<double[]> viewers,
                            double releaseRange,
                            int maxPerViewer,
                            int maxGlobal,
                            AsteroidField.Candidate building,
                            Set<AsteroidField.ObjectId> pinned) {
        return select(active, viewers, releaseRange, maxPerViewer, maxGlobal, building, pinned, List.of());
    }

    /**
     * Selects managed roots after reserving capacity occupied by other already-live asteroid-family bodies,
     * such as detached Sable bodies and split fragments.
     */
    static Selection select(Collection<AsteroidField.Candidate> active,
                            List<double[]> viewers,
                            double releaseRange,
                            int maxPerViewer,
                            int maxGlobal,
                            AsteroidField.Candidate building,
                            Set<AsteroidField.ObjectId> pinned,
                            Collection<double[]> alreadyLiveBodies) {
        return select(active, viewers, releaseRange, maxPerViewer, maxGlobal, building, pinned,
                alreadyLiveBodies, 0);
    }

    static Selection select(Collection<AsteroidField.Candidate> active,
                            List<double[]> viewers,
                            double releaseRange,
                            int maxPerViewer,
                            int maxGlobal,
                            AsteroidField.Candidate building,
                            Set<AsteroidField.ObjectId> pinned,
                            Collection<double[]> alreadyLiveBodies,
                            int unlocatedReservations) {
        if (!Double.isFinite(releaseRange) || releaseRange < 0 || maxPerViewer < 0 || maxGlobal < 0)
            throw new IllegalArgumentException("invalid asteroid retention limits");
        if (unlocatedReservations < 0) throw new IllegalArgumentException("invalid unlocated body reservation");
        var keep = new HashSet<AsteroidField.ObjectId>();
        int[] counts = new int[viewers.size()];
        double rangeSquared = releaseRange * releaseRange;

        int externalCount = unlocatedReservations;
        java.util.Arrays.fill(counts, unlocatedReservations);
        if (alreadyLiveBodies != null) {
            for (double[] body : alreadyLiveBodies) {
                if (body == null || body.length < 3
                        || !Double.isFinite(body[0]) || !Double.isFinite(body[1]) || !Double.isFinite(body[2])) continue;
                externalCount++;
                for (int i = 0; i < viewers.size(); i++) {
                    double[] viewer = viewers.get(i);
                    if (AsteroidCandidateSelector.distanceSquared(viewer[0], viewer[1], viewer[2],
                            body[0], body[1], body[2]) <= rangeSquared) counts[i]++;
                }
            }
        }

        var pinnedRanked = new ArrayList<Ranked>();
        var ranked = new ArrayList<Ranked>();
        for (var candidate : active) {
            if (candidate == null) continue;
            if (pinned != null && pinned.contains(candidate.id())) {
                pinnedRanked.add(new Ranked(candidate, nearestDistanceSquared(candidate, viewers)));
                continue;
            }
            double nearest = nearestDistanceSquared(candidate, viewers);
            if (nearest <= rangeSquared) ranked.add(new Ranked(candidate, nearest));
        }

        Comparator<Ranked> nearestFirst = Comparator.comparingDouble(Ranked::nearestDistanceSquared)
                .thenComparing(r -> r.candidate().id());
        pinnedRanked.sort(nearestFirst);
        ranked.sort(nearestFirst);

        // Fracture work has priority, but is not exempt from hard caps. When caps are lowered below
        // current occupancy, the caller cancels excluded partial cuts before releasing those roots.
        int globalUsed = externalCount;
        if (maxPerViewer == 0 || globalUsed >= maxGlobal) return new Selection(Set.of(), false);
        for (Ranked fracture : pinnedRanked) {
            if (globalUsed >= maxGlobal || !fitsPerViewer(fracture.candidate(), viewers, counts, rangeSquared, maxPerViewer)) continue;
            keep.add(fracture.candidate().id());
            countForViewers(fracture.candidate(), viewers, counts, rangeSquared);
            globalUsed++;
        }

        // A pending build may reserve a slot after fracture pins, but cannot displace them.
        boolean keepBuilding = building != null && globalUsed < maxGlobal
                && fitsPerViewer(building, viewers, counts, rangeSquared, maxPerViewer);
        if (keepBuilding) {
            countForViewers(building, viewers, counts, rangeSquared);
            globalUsed++;
        }

        if (globalUsed >= maxGlobal) return new Selection(Set.copyOf(keep), keepBuilding);
        for (Ranked candidate : ranked) {
            if (keep.contains(candidate.candidate().id())) continue;
            if (globalUsed >= maxGlobal || !fitsPerViewer(candidate.candidate(), viewers, counts, rangeSquared, maxPerViewer))
                continue;
            keep.add(candidate.candidate().id());
            countForViewers(candidate.candidate(), viewers, counts, rangeSquared);
            globalUsed++;
        }
        return new Selection(Set.copyOf(keep), keepBuilding);
    }

    private static boolean fitsPerViewer(AsteroidField.Candidate candidate, List<double[]> viewers,
                                         int[] counts, double rangeSquared, int limit) {
        for (int i = 0; i < viewers.size(); i++) {
            double[] viewer = viewers.get(i);
            if (AsteroidCandidateSelector.distanceSquared(viewer[0], viewer[1], viewer[2],
                    candidate.x(), candidate.y(), candidate.z()) <= rangeSquared && counts[i] >= limit) return false;
        }
        return true;
    }

    private static void countForViewers(AsteroidField.Candidate candidate, List<double[]> viewers,
                                        int[] counts, double rangeSquared) {
        for (int i = 0; i < viewers.size(); i++) {
            double[] viewer = viewers.get(i);
            if (AsteroidCandidateSelector.distanceSquared(viewer[0], viewer[1], viewer[2],
                    candidate.x(), candidate.y(), candidate.z()) <= rangeSquared) counts[i]++;
        }
    }

    private static double nearestDistanceSquared(AsteroidField.Candidate candidate, List<double[]> viewers) {
        double nearest = Double.POSITIVE_INFINITY;
        for (double[] viewer : viewers)
            nearest = Math.min(nearest, AsteroidCandidateSelector.distanceSquared(viewer[0], viewer[1], viewer[2],
                    candidate.x(), candidate.y(), candidate.z()));
        return nearest;
    }
}
