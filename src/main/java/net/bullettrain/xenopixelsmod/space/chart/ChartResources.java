package net.bullettrain.xenopixelsmod.space.chart;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One listener per resource reload; preparation never mutates the live chart. */
public final class ChartResources extends SimplePreparableReloadListener<ChartResources.Input> {
    /** Immutable candidate data from a resource reload; consumers publish it only after validation. */
    public record Input(Map<String, JsonObject> definitions, List<String> errors,
                        Map<String, net.bullettrain.xenopixelsmod.space.asteroid.BeltDefinition> belts,
                        Map<Integer, net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetDefinition> dragonBallPlanets) {
        public Input(Map<String, JsonObject> definitions, List<String> errors) { this(definitions, errors, Map.of(), Map.of()); }
        public Input(Map<String, JsonObject> definitions, List<String> errors,
                     Map<String, net.bullettrain.xenopixelsmod.space.asteroid.BeltDefinition> belts) {
            this(definitions, errors, belts, Map.of());
        }
    }
    private volatile Input input;
    public Input input() { return input; }
    @Override protected Input prepare(ResourceManager manager, ProfilerFiller profiler) { return scan(manager); }
    @Override protected void apply(Input input, ResourceManager manager, ProfilerFiller profiler) { this.input = input; }
    public static Input scan(ResourceManager manager) {
        Map<String, JsonObject> definitions = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        Set<String> collisions = new HashSet<>();
        int count = 0;
        for (String folder : List.of("xenospace/planet", "xenospace/chart_entry")) {
            var resources = manager.listResources(folder, id -> id.getPath().endsWith(".json"));
            for (var entry : new TreeMap<>(resources).entrySet()) {
                if (++count > 1024) throw new IllegalArgumentException("chart exceeds 1024 resource files");
                var location = entry.getKey();
                String path = location.getPath();
                String id = location.getNamespace() + ":" + path.substring(folder.length() + 1, path.length() - 5);
                try {
                    if (definitions.containsKey(id) || collisions.contains(id)) {
                        definitions.remove(id); collisions.add(id);
                        throw new IllegalArgumentException("body id occurs in both planet and chart_entry folders: " + id);
                    }
                    byte[] bytes;
                    try (var stream = entry.getValue().open()) { bytes = stream.readNBytes(65537); }
                    if (bytes.length > 65536) throw new IllegalArgumentException("definition exceeds 64 KiB");
                    var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                    if (!json.isJsonObject()) throw new IllegalArgumentException("definition must be an object");
                    JsonObject definition = json.getAsJsonObject();
                    CelestialBody body = ChartDefinition.parse(id, definition);
                    if ("xenopixelsmod:planet_surfaces".equals(body.dimensionId())
                            && !net.bullettrain.xenopixelsmod.space.ChartSurfaceBodyResolver.anchorMatchesCell(body))
                        throw new IllegalArgumentException("planet_surfaces spatial_anchor must land inside this body's reserved identity cell");
                    if (folder.endsWith("chart_entry")) {
                        if (body.dimensionId() == null)
                            throw new IllegalArgumentException("chart_entry requires dimension");
                        if (body.orbit() == null)
                            throw new IllegalArgumentException("chart_entry requires orbit");
                    }
                    definitions.put(id, definition);
                } catch (Exception e) {
                    errors.add(location + ": " + e.getMessage());
                }
            }
        }
        Map<String, net.bullettrain.xenopixelsmod.space.asteroid.BeltDefinition> belts = new TreeMap<>();
        String beltFolder = "xenospace/belt";
        var beltResources = manager.listResources(beltFolder, id -> id.getPath().endsWith(".json"));
        if (beltResources.size() > 64) errors.add(beltFolder + ": more than 64 belt files; belts ignored");
        else for (var entry : new TreeMap<>(beltResources).entrySet()) {
            var location = entry.getKey();
            String path = location.getPath();
            String id = location.getNamespace() + ":" + path.substring(beltFolder.length() + 1, path.length() - 5);
            try {
                byte[] bytes;
                try (var stream = entry.getValue().open()) { bytes = stream.readNBytes(16385); }
                if (bytes.length > 16384) throw new IllegalArgumentException("belt definition exceeds 16 KiB");
                var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (!json.isJsonObject()) throw new IllegalArgumentException("belt definition must be an object");
                belts.put(id, net.bullettrain.xenopixelsmod.space.asteroid.BeltDefinition.parse(id, json.getAsJsonObject()));
            } catch (Exception e) {
                errors.add(location + ": " + e.getMessage());
            }
        }
        Map<Integer, net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetDefinition> dragonBalls = new TreeMap<>();
        Set<Integer> duplicateBallSlots = new HashSet<>();
        String dragonBallFolder = "xenospace/superdragonball";
        var dragonBallResources = manager.listResources(dragonBallFolder,
                id -> id.getPath().toLowerCase(Locale.ROOT).endsWith(".json"));
        var slotResources = new ArrayList<>(dragonBallResources.entrySet());
        slotResources.removeIf(entry -> {
            String path = entry.getKey().getPath();
            String relative = path.substring(Math.min(path.length(), dragonBallFolder.length() + 1));
            boolean valid = relative.matches("[1-7]\\.[jJ][sS][oO][nN]");
            if (!valid) errors.add(entry.getKey() + ": expected a top-level slot file " + dragonBallFolder + "/<1-7>.json");
            return !valid;
        });
        // Give bundled defaults deterministic priority, and bound parsing without letting unrelated JSON
        // files from another pack blank the seven valid assignments.
        slotResources.sort(Comparator.<Map.Entry<net.minecraft.resources.ResourceLocation, net.minecraft.server.packs.resources.Resource>, Integer>
                comparing(entry -> entry.getKey().getNamespace().equals("xenopixelsmod") ? 0 : 1)
                .thenComparing(Map.Entry::getKey));
        if (slotResources.size() > 64) errors.add(dragonBallFolder + ": more than 64 candidate slot files; parsing the first 64 in deterministic order");
        int parsedCandidates = 0;
        for (var entry : slotResources) {
            var location = entry.getKey();
            if (++parsedCandidates > 64) continue;
            String path = location.getPath();
            String resourceId = location.getNamespace() + ":" + path.substring(dragonBallFolder.length() + 1, path.length() - 5);
            try {
                byte[] bytes;
                try (var stream = entry.getValue().open()) { bytes = stream.readNBytes(16385); }
                if (bytes.length > 16384) throw new IllegalArgumentException("assignment exceeds 16 KiB");
                var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (!json.isJsonObject()) throw new IllegalArgumentException("assignment must be an object");
                var definition = net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetDefinition
                        .parse(resourceId, json.getAsJsonObject());
                if (duplicateBallSlots.contains(definition.slot())) {
                    errors.add(location + ": assignment ignored because slot " + definition.slot() + " already has conflicting pack assignments");
                    continue;
                }
                if (dragonBalls.putIfAbsent(definition.slot(), definition) != null) {
                    dragonBalls.remove(definition.slot());
                    duplicateBallSlots.add(definition.slot());
                    throw new IllegalArgumentException("multiple data packs assign Super Dragon Ball slot " + definition.slot());
                }
            } catch (Exception e) {
                errors.add(location + ": " + e.getMessage());
            }
        }
        for (int slot = 1; slot <= net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls.COUNT; slot++)
            if (!dragonBalls.containsKey(slot) && !duplicateBallSlots.contains(slot))
                errors.add(dragonBallFolder + ": missing valid assignment for slot " + slot);
        return new Input(Map.copyOf(definitions), List.copyOf(errors), Map.copyOf(belts), Map.copyOf(dragonBalls));
    }
}
