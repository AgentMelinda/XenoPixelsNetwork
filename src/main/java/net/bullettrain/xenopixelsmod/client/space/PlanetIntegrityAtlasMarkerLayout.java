package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Groups Atlas integrity icons whose screen-space footprints overlap, without moving their source coordinates. */
public final class PlanetIntegrityAtlasMarkerLayout {
    private static final int MAX_MARKERS = PlanetIntegrityAtlasSnapshot.MAX_ENTRIES;
    private static final int ICON_RADIUS = 4;

    public record Point(int x, int y) {}
    public record Cluster(Point point, List<PlanetIntegrityAtlasSnapshot.Entry> markers) {
        public Cluster { markers = List.copyOf(markers); }
    }

    private PlanetIntegrityAtlasMarkerLayout() {}

    /** The bounded marker cap makes the pairwise overlap union deterministic and at most 128² comparisons. */
    public static List<Cluster> layout(List<PlanetIntegrityAtlasSnapshot.Entry> entries,
                                       Function<PlanetIntegrityAtlasSnapshot.Entry, Point> project) {
        if (entries == null || entries.isEmpty()) return List.of();
        int size = Math.min(entries.size(), MAX_MARKERS);
        var points = new ArrayList<Point>(size);
        for (int i = 0; i < size; i++) points.add(project.apply(entries.get(i)));
        int[] parents = new int[size];
        for (int i = 0; i < size; i++) parents[i] = i;
        int overlapDistance = ICON_RADIUS * 2;
        for (int i = 0; i < size; i++) {
            Point a = points.get(i);
            for (int j = i + 1; j < size; j++) {
                Point b = points.get(j);
                long dx = (long) a.x - b.x;
                long dy = (long) a.y - b.y;
                if (Math.abs(dx) < overlapDistance && Math.abs(dy) < overlapDistance) union(parents, i, j);
            }
        }

        var members = new java.util.HashMap<Integer, ArrayList<PlanetIntegrityAtlasSnapshot.Entry>>();
        var indexes = new java.util.HashMap<Integer, ArrayList<Integer>>();
        for (int i = 0; i < size; i++) {
            int root = find(parents, i);
            members.computeIfAbsent(root, ignored -> new ArrayList<>()).add(entries.get(i));
            indexes.computeIfAbsent(root, ignored -> new ArrayList<>()).add(i);
        }

        var result = new ArrayList<Cluster>(members.size());
        for (var entry : members.entrySet()) {
            var markers = entry.getValue();
            markers.sort(Comparator.comparing(PlanetIntegrityAtlasSnapshot.Entry::bodyId));
            var point = medoid(indexes.get(entry.getKey()), points, entries);
            result.add(new Cluster(point, markers));
        }
        result.sort(Comparator.comparingInt((Cluster c) -> c.point().y()).thenComparingInt(c -> c.point().x())
                .thenComparing(c -> c.markers().getFirst().bodyId()));
        return List.copyOf(result);
    }

    private static Point medoid(List<Integer> members, List<Point> points,
                                List<PlanetIntegrityAtlasSnapshot.Entry> entries) {
        int best = members.getFirst();
        long bestCost = Long.MAX_VALUE;
        for (int candidate : members) {
            long cost = 0;
            Point point = points.get(candidate);
            for (int other : members) {
                long dx = (long) point.x - points.get(other).x;
                long dy = (long) point.y - points.get(other).y;
                cost += dx * dx + dy * dy;
            }
            if (cost < bestCost || (cost == bestCost
                    && entries.get(candidate).bodyId().compareTo(entries.get(best).bodyId()) < 0)) {
                best = candidate;
                bestCost = cost;
            }
        }
        return points.get(best);
    }

    /** Picks the nearest visible badge; equal-distance ties use the stable layout order. */
    public static int pick(List<Cluster> clusters, double mouseX, double mouseY, int radius) {
        if (clusters == null || radius < 0 || !Double.isFinite(mouseX) || !Double.isFinite(mouseY)) return -1;
        int best = -1;
        double bestSquared = (double) radius * radius;
        for (int i = 0; i < clusters.size(); i++) {
            Point point = clusters.get(i).point();
            double dx = mouseX - point.x;
            double dy = mouseY - point.y;
            if (Math.abs(dx) > radius || Math.abs(dy) > radius) continue;
            double distance = dx * dx + dy * dy;
            if (distance < bestSquared) { best = i; bestSquared = distance; }
        }
        return best;
    }

    private static int find(int[] parents, int index) {
        int root = index;
        while (parents[root] != root) root = parents[root];
        while (parents[index] != index) {
            int next = parents[index];
            parents[index] = root;
            index = next;
        }
        return root;
    }

    private static void union(int[] parents, int a, int b) {
        int rootA = find(parents, a);
        int rootB = find(parents, b);
        if (rootA != rootB) parents[Math.max(rootA, rootB)] = Math.min(rootA, rootB);
    }
}
