package net.bullettrain.xenopixelsmod.space.chart;
import com.google.gson.JsonObject;
import java.util.*;

/** Pure candidate construction shared by reload and dry-run validation. */
public final class ChartAssembly {
    private ChartAssembly() {}
    public record Result(ChartRegistry chart, List<String> errors) {}
    public static Result build(Map<String, JsonObject> definitions, JsonObject defaults, Set<String> dimensions) {
        List<String> errors = new ArrayList<>(); List<CelestialBody> bodies = new ArrayList<>();
        for (var entry : new TreeMap<>(definitions).entrySet()) {
            try {
                JsonObject row = ChartDefinition.merge(defaults, entry.getValue());
                String dimension = ChartDefinition.string(row, "dimension", null);
                if (dimension != null && !dimensions.contains(dimension)) {
                    errors.add(entry.getKey() + ": unavailable dimension " + dimension + " (showcase)");
                    row.addProperty("kind", "showcase");
                }
                bodies.add(ChartDefinition.parse(entry.getKey(), row));
            } catch (RuntimeException e) { errors.add(entry.getKey() + ": " + e.getMessage()); }
        }
        // Authored chains are bounded before the existing recursive topological ordering.
        // A malformed deep hierarchy must be a diagnostic, not a server-thread stack overflow.
        Map<String, CelestialBody> byId = new HashMap<>();
        for (CelestialBody body : bodies) byId.put(body.id(), body);
        Set<String> tooDeep = new HashSet<>();
        for (CelestialBody body : bodies) {
            String parent = body.parentId(); int depth = 0;
            Set<String> visited = new HashSet<>();
            while (parent != null && byId.containsKey(parent) && visited.add(parent)) {
                if (++depth > 64) {
                    tooDeep.add(body.id()); errors.add(body.id() + ": parent hierarchy exceeds depth 64"); break;
                }
                parent = byId.get(parent).parentId();
            }
        }
        bodies.removeIf(body -> tooDeep.contains(body.id()));
        ChartRegistry chart = new ChartRegistry(); errors.addAll(chart.rebuild(bodies));
        for (CelestialBody body : bodies) if (body.parentId() != null && chart.byId(body.parentId()) == null)
            errors.add(body.id() + ": unavailable parent " + body.parentId() + " (root fallback)");
        return new Result(chart, List.copyOf(errors));
    }
}
