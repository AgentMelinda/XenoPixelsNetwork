package net.bullettrain.xenopixelsmod.space.chart;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Read-only, bounded dimension-sidecar loading. Never creates worlds or edits datapacks. */
public final class DimensionOverrides {
    private DimensionOverrides() {}
    public record Result(Map<String, JsonObject> definitions, List<String> errors, Set<String> dimensions,
                         Map<String, DimensionSpaceSettings> settings) {}
    public static Result load(Path directory, Map<String, JsonObject> base) throws IOException {
        return load(directory, base, new JsonObject());
    }
    public static Result load(Path directory, Map<String, JsonObject> base, JsonObject defaults) throws IOException {
        Map<String, JsonObject> definitions = new TreeMap<>();
        base.forEach((id, row) -> definitions.put(id, ChartDefinition.merge(defaults, row)));
        List<String> errors = new ArrayList<>();
        Set<String> dimensions = new TreeSet<>(), targets = new HashSet<>();
        Map<String, DimensionSpaceSettings> settings = new TreeMap<>();
        if (!Files.exists(directory)) return new Result(definitions, errors, dimensions, settings);
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setAllowRecursiveKeys(false);
        options.setNestingDepthLimit(16);
        options.setCodePointLimit(65536);
        options.setTagInspector(tag -> false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".yml")).sorted().limit(1025).toList();
        }
        if (files.size() > 1024) throw new IOException("more than 1024 dimension sidecars");
        for (Path file : files) {
            try {
                if (Files.isSymbolicLink(file) || Files.size(file) > 65536) throw new IllegalArgumentException("sidecar is a symlink or exceeds 64 KiB");
                Object raw;
                try (var reader = Files.newBufferedReader(file)) { raw = yaml.load(reader); }
                var tree = new Gson().toJsonTree(raw);
                if (!tree.isJsonObject()) throw new IllegalArgumentException("sidecar must be a mapping");
                JsonObject row = tree.getAsJsonObject();
                ChartDefinition.keys(row, "dimension", "charted_as", "implicit_body", "space", "overrides", "flags");
                String dimensionValue = row.has("dimension")
                        ? ChartDefinition.string(row, "dimension", null) : dimensionFromFilename(file);
                String dimension = ChartDefinition.id(dimensionValue);
                String target = ChartDefinition.string(row, "charted_as", null);
                if (target != null && !target.contains(":")) target = "xenopixelsmod:" + target;
                JsonObject definition;
                if (target != null) {
                    ChartDefinition.id(target);
                    if (row.has("implicit_body")) throw new IllegalArgumentException("choose charted_as or implicit_body");
                    definition = definitions.get(target);
                    if (definition == null) throw new IllegalArgumentException("unknown charted_as: " + target);
                    String binding = ChartDefinition.string(definition, "dimension", null);
                    if (binding != null && !binding.equals(dimension)) throw new IllegalArgumentException("dimension conflicts with body binding");
                } else {
                    definition = ChartDefinition.object(row, "implicit_body").deepCopy();
                    String bodyId = ChartDefinition.string(definition, "id", null);
                    target = ChartDefinition.id(bodyId == null
                            ? "xenopixelsmod:dimension/" + dimension.replace(':', '/') : bodyId);
                    definition.remove("id");
                    definition = ChartDefinition.merge(defaults, definition);
                    if (definitions.containsKey(target)) throw new IllegalArgumentException("implicit body already exists: " + target);
                }
                if (dimensions.contains(dimension) || targets.contains(target)) throw new IllegalArgumentException("duplicate sidecar dimension or target");
                DimensionSpaceSettings parsedSettings = DimensionSpaceSettings.parse(
                        ChartDefinition.object(row, "space"), ChartDefinition.object(row, "flags"));
                definition = ChartDefinition.merge(definition, ChartDefinition.object(row, "overrides"));
                JsonObject space = ChartDefinition.object(row, "space");
                if (space.has("exit_altitude")) definition.add("exit_altitude", space.get("exit_altitude").deepCopy());
                if (space.has("warm_up_altitude")) definition.add("warm_up_altitude", space.get("warm_up_altitude").deepCopy());
                if (space.has("orbital_motion")) definition.add("orbital_motion", space.get("orbital_motion").deepCopy());
                if (space.has("gravity_multiplier")) {
                    JsonObject gravity = ChartDefinition.object(definition, "gravity").deepCopy();
                    double surface = gravity.has("surface_ms2") ? gravity.get("surface_ms2").getAsDouble() : 0;
                    gravity.addProperty("surface_ms2", surface * space.get("gravity_multiplier").getAsDouble());
                    definition.add("gravity", gravity);
                }
                String overridden = ChartDefinition.string(definition, "dimension", dimension);
                if (!dimension.equals(overridden)) throw new IllegalArgumentException("overrides cannot change sidecar dimension");
                definition.addProperty("dimension", dimension);
                ChartDefinition.parse(target, definition);
                dimensions.add(dimension); targets.add(target); settings.put(dimension, parsedSettings);
                definitions.put(target, definition);
            } catch (RuntimeException | IOException e) {
                errors.add(file.getFileName() + ": " + e.getMessage());
            }
        }
        return new Result(definitions, List.copyOf(errors), Set.copyOf(dimensions), Map.copyOf(settings));
    }

    private static String dimensionFromFilename(Path file) {
        String name = file.getFileName().toString();
        String stem = name.substring(0, name.length() - ".yml".length());
        int separator = stem.indexOf('.');
        if (separator <= 0 || separator == stem.length() - 1)
            throw new IllegalArgumentException("dimension is required unless filename is <namespace>.<path>.yml");
        return stem.substring(0, separator) + ":" + stem.substring(separator + 1);
    }
}
