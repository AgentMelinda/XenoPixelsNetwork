package net.bullettrain.xenopixelsmod.dmz.race;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadataRegistry;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Operator-authored race picker literals. Not shipped in {@code en_us.json}.
 *
 * <p>Sidecar {@code config/dragonminez/races/<id>/xeno_labels.json} survives DMZ Gson
 * rewrites of {@code character.json}. The snapshot is sent to clients so dedicated-server
 * names appear without a resource pack.
 */
public final class RaceLabelRegistry {
    public static final int MAX_NAME = 64;
    public static final int MAX_DESC = 256;
    public static final int MAX_JSON_CHARS = 65_536;
    public static final String SIDECAR = "xeno_labels.json";
    public static final String TRANSLATION_PREFIX = DmzFormMetadataRegistry.TRANSLATION_PREFIX;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, RaceLabels> ENTRIES = new LinkedHashMap<>();

    private RaceLabelRegistry() {
    }

    public static String nameKey(String raceId) {
        return TRANSLATION_PREFIX + normal(raceId);
    }

    public static String descKey(String raceId) {
        return nameKey(raceId) + ".desc";
    }

    public static synchronized void put(String raceId, String displayName, String description) {
        String key = normal(raceId);
        if (key.isEmpty() || !RacePackService.validateRaceId(key).ok()
                || RacePackService.isDefaultRace(key)) {
            return;
        }
        ENTRIES.put(key, new RaceLabels(key, clamp(displayName, MAX_NAME), clamp(description, MAX_DESC)));
    }

    public static synchronized String displayName(String raceId) {
        RaceLabels labels = ENTRIES.get(normal(raceId));
        return labels == null || labels.displayName() == null ? "" : labels.displayName();
    }

    public static synchronized String description(String raceId) {
        RaceLabels labels = ENTRIES.get(normal(raceId));
        return labels == null || labels.description() == null ? "" : labels.description();
    }

    public static synchronized RaceLabels get(String raceId) {
        RaceLabels labels = ENTRIES.get(normal(raceId));
        return labels == null ? RaceLabels.blank(raceId) : labels;
    }

    /**
     * Serves {@code race.dragonminez.<id>} and {@code .desc} only when this registry owns that
     * exact id. Form and group keys ({@code .form.} / {@code .group.}) fall through.
     */
    public static synchronized String translate(String key, String locale) {
        if (key == null || !key.startsWith(TRANSLATION_PREFIX)) {
            return null;
        }
        String rest = key.substring(TRANSLATION_PREFIX.length());
        if (rest.isEmpty() || rest.contains(".form.") || rest.contains(".group.")) {
            return null;
        }
        boolean desc = rest.endsWith(".desc");
        String id = desc ? rest.substring(0, rest.length() - ".desc".length()) : rest;
        if (id.isEmpty() || id.contains(".form") || id.contains(".group")) {
            return null;
        }
        RaceLabels labels = ENTRIES.get(normal(id));
        if (labels == null) {
            return null;
        }
        String value = desc ? labels.description() : labels.displayName();
        return value == null || value.isBlank() ? null : value;
    }

    public static synchronized String snapshotJson() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, RaceLabels> entry : ENTRIES.entrySet()) {
            RaceLabels labels = entry.getValue();
            if (labels == null) {
                continue;
            }
            JsonObject node = new JsonObject();
            node.addProperty("displayName", labels.displayName() == null ? "" : labels.displayName());
            node.addProperty("description", labels.description() == null ? "" : labels.description());
            root.add(entry.getKey(), node);
        }
        return GSON.toJson(root);
    }

    public static synchronized void applySnapshot(String json) {
        ENTRIES.clear();
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            var parsed = JsonParser.parseString(json);
            if (parsed == null || !parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            for (var entry : root.entrySet()) {
                if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject node = entry.getValue().getAsJsonObject();
                put(entry.getKey(), str(node, "displayName"), str(node, "description"));
            }
        } catch (Exception exception) {
            XenoPixelsMod.LOGGER.error("Failed applying race-label snapshot", exception);
        }
    }

    public static void loadFromDisk() {
        loadFromRoot(defaultDmzRoot());
    }

    public static synchronized void loadFromRoot(Path dmzConfigRoot) {
        ENTRIES.clear();
        if (dmzConfigRoot == null) {
            return;
        }
        Path races = dmzConfigRoot.resolve("races");
        if (!Files.isDirectory(races)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(races)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                String id = normal(entry.getFileName().toString());
                RaceLabels labels = readSidecar(dmzConfigRoot, id);
                if (labels.hasLiteral()) {
                    put(id, labels.displayName(), labels.description());
                }
            }
        } catch (IOException exception) {
            XenoPixelsMod.LOGGER.error("Failed scanning race label sidecars", exception);
        }
    }

    public static RacePackService.ValidationResult writeSidecar(String raceId, String displayName,
                                                                String description) {
        return writeSidecar(defaultDmzRoot(), raceId, displayName, description);
    }

    public static RacePackService.ValidationResult writeSidecar(Path dmzConfigRoot, String raceId,
                                                                String displayName, String description) {
        if (dmzConfigRoot == null) {
            return RacePackService.ValidationResult.fail("Missing DMZ config root");
        }
        RacePackService.ValidationResult idCheck = RacePackService.validateRaceId(raceId);
        if (!idCheck.ok()) {
            return idCheck;
        }
        String key = normal(raceId);
        if (RacePackService.isDefaultRace(key)) {
            return RacePackService.ValidationResult.fail("Cannot label a DMZ default race: " + key);
        }
        Path races = dmzConfigRoot.resolve("races").normalize();
        Path raceDir = races.resolve(key).normalize();
        if (!raceDir.startsWith(races)) {
            return RacePackService.ValidationResult.fail("Race path escapes races root");
        }
        try {
            Files.createDirectories(raceDir);
            JsonObject node = new JsonObject();
            node.addProperty("displayName", clamp(displayName, MAX_NAME));
            node.addProperty("description", clamp(description, MAX_DESC));
            Files.writeString(raceDir.resolve(SIDECAR), GSON.toJson(node), StandardCharsets.UTF_8);
            put(key, displayName, description);
            return RacePackService.ValidationResult.pass();
        } catch (IOException e) {
            return RacePackService.ValidationResult.fail("Failed to write race labels: " + e.getMessage());
        }
    }

    public static RaceLabels readSidecar(Path dmzConfigRoot, String raceId) {
        String key = normal(raceId);
        if (key.isEmpty() || dmzConfigRoot == null) {
            return RaceLabels.blank(raceId);
        }
        Path sidecar = dmzConfigRoot.resolve("races").resolve(key).resolve(SIDECAR);
        if (!Files.isRegularFile(sidecar)) {
            return RaceLabels.blank(key);
        }
        try {
            var parsed = JsonParser.parseString(Files.readString(sidecar, StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) {
                return RaceLabels.blank(key);
            }
            JsonObject node = parsed.getAsJsonObject();
            return new RaceLabels(key,
                    clamp(str(node, "displayName"), MAX_NAME),
                    clamp(str(node, "description"), MAX_DESC));
        } catch (Exception e) {
            return RaceLabels.blank(key);
        }
    }

    static Path defaultDmzRoot() {
        return FMLPaths.CONFIGDIR.get().resolve("dragonminez");
    }

    private static String str(JsonObject node, String field) {
        if (node == null || !node.has(field) || node.get(field).isJsonNull()) {
            return "";
        }
        try {
            String value = node.get(field).getAsString();
            return value == null ? "" : value;
        } catch (Exception e) {
            return "";
        }
    }

    private static String clamp(String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private static String normal(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

}
