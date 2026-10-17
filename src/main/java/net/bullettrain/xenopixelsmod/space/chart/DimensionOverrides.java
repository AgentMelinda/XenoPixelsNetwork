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
    public record Result(Map<String, JsonObject> definitions, List<String> errors, Set<String> dimensions) {}
    public static Result load(Path directory, Map<String, JsonObject> base) throws IOException {
        return load(directory, base, new JsonObject());
    }
    public static Result load(Path directory, Map<String, JsonObject> base, JsonObject defaults) throws IOException {
        Map<String, JsonObject> definitions = new TreeMap<>();
        base.forEach((id, row) -> definitions.put(id, ChartDefinition.merge(defaults, row)));
        List<String> errors = new ArrayList<>();
        Set<String> dimensions = new TreeSet<>(), targets = new HashSet<>();
        if (!Files.exists(directory)) return new Result(definitions, errors, dimensions);
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
                ChartDefinition.keys(row, "dimension", "charted_as", "implicit_body", "overrides");
                String dimension = ChartDefinition.id(ChartDefinition.string(row, "dimension", null));
                String target = ChartDefinition.string(row, "charted_as", null);
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
                    target = ChartDefinition.id(ChartDefinition.string(definition, "id", null));
                    definition.remove("id");
                    definition = ChartDefinition.merge(defaults, definition);
                    if (definitions.containsKey(target)) throw new IllegalArgumentException("implicit body already exists: " + target);
                }
                if (dimensions.contains(dimension) || targets.contains(target)) throw new IllegalArgumentException("duplicate sidecar dimension or target");
                definition = ChartDefinition.merge(definition, ChartDefinition.object(row, "overrides"));
                String overridden = ChartDefinition.string(definition, "dimension", dimension);
                if (!dimension.equals(overridden)) throw new IllegalArgumentException("overrides cannot change sidecar dimension");
                definition.addProperty("dimension", dimension);
                ChartDefinition.parse(target, definition);
                dimensions.add(dimension); targets.add(target);
                definitions.put(target, definition);
            } catch (RuntimeException | IOException e) {
                errors.add(file.getFileName() + ": " + e.getMessage());
            }
        }
        return new Result(definitions, List.copyOf(errors), Set.copyOf(dimensions));
    }
}
