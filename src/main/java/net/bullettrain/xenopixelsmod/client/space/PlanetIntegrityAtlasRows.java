package net.bullettrain.xenopixelsmod.client.space;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Bounded lowest-integrity records for the finite Atlas sidebar; does not imply physical map coordinates. */
public final class PlanetIntegrityAtlasRows {
    public record Row(String bodyId, double integrity) {}
    public record View(List<Row> rows, int total) {
        public View { rows = List.copyOf(rows); }
        public int omitted() { return Math.max(0, total - rows.size()); }
    }

    private static final Comparator<Row> ORDER = Comparator.comparingDouble(Row::integrity)
            .thenComparing(Row::bodyId);

    private PlanetIntegrityAtlasRows() {}

    /** Selects the N lowest saved integrity values in O(records × N), without allocating one row per record. */
    public static View lowest(Map<String, Double> values, int limit) {
        int maximum = Math.max(0, Math.min(limit, 16));
        if (values == null || values.isEmpty() || maximum == 0) return new View(List.of(), 0);
        var rows = new ArrayList<Row>(maximum);
        int total = 0;
        for (var entry : values.entrySet()) {
            Double value = entry.getValue();
            if (value == null || !Double.isFinite(value) || value < 0.0 || value >= 100.0) continue;
            total++;
            Row candidate = new Row(entry.getKey(), value);
            int insertAt = 0;
            while (insertAt < rows.size() && ORDER.compare(rows.get(insertAt), candidate) <= 0) insertAt++;
            if (insertAt >= maximum) continue;
            rows.add(insertAt, candidate);
            if (rows.size() > maximum) rows.remove(maximum);
        }
        return new View(rows, total);
    }
}
