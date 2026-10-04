package net.bullettrain.xenopixelsmod.dmz.race;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Race-pack IO for Unified Maker Studio (PR-D6a / KD15 r3).
 *
 * <p>DMZ discovers custom races by scanning {@code config/dragonminez/races/} in
 * {@code ConfigManager#loadAllRaces} (see evidence
 * {@code docs/superpowers/evidence/2026-10-03-race-registration-path.md}). This service
 * writes the skeleton only — it does <b>not</b> invent a registrar API. Callers reload via
 * {@code ConfigManager.reload()} so the scan picks up the new folder.
 */
public final class RacePackService {
    /** Same version string as {@code RaceCharacterConfig.CURRENT_VERSION}. */
    public static final String CONFIG_VERSION = "2.1.3";

    /**
     * DMZ {@code ConfigManager.DEFAULT_RACES} — never created via this service.
     */
    private static final Set<String> DEFAULT_RACES = Set.of(
            "human", "saiyan", "namekian", "frostdemon", "bioandroid", "majin"
    );

    /** Xeno form-price race folders under {@code data/xenopixelsmod/dmz/races/}. */
    private static final Set<String> XENO_PACK_RACES = Set.of(
            "saiyan", "human", "namekian", "frostdemon", "majin", "bioandroid"
    );

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private RacePackService() {
    }

    /** Installed defaults + xeno packs + custom folders under the live DMZ config root. */
    public static Set<String> knownRaceIds() {
        return knownRaceIds(defaultDmzRoot());
    }

    /**
     * Known race ids for a fixture or live root: defaults ∪ xeno packs ∪
     * directories under {@code races/} that contain {@code character.json}.
     */
    public static Set<String> knownRaceIds(Path dmzConfigRoot) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(DEFAULT_RACES);
        ids.addAll(XENO_PACK_RACES);
        if (dmzConfigRoot != null) {
            Path races = dmzConfigRoot.resolve("races");
            if (Files.isDirectory(races)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(races)) {
                    for (Path entry : stream) {
                        if (!Files.isDirectory(entry)) continue;
                        String name = normal(entry.getFileName().toString());
                        if (name.isEmpty() || !isId(name)) continue;
                        if (Files.isRegularFile(entry.resolve("character.json"))) {
                            ids.add(name);
                        }
                    }
                } catch (IOException ignored) {
                    // Keep builtins; scan is best-effort for listing.
                }
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    /** Syntax check for a race id (does not require the race to exist). */
    public static ValidationResult validateRaceId(String id) {
        String key = normal(id);
        if (key.isEmpty() || !isId(key)) {
            return ValidationResult.fail("Race id must start a-z0-9, then a-z0-9_.-");
        }
        return ValidationResult.pass();
    }

    /**
     * Creates {@code races/<id>/character.json} + empty {@code forms/} under the live
     * DMZ config root. Does not call {@code ConfigManager.reload()}.
     */
    public static CreateResult createRacePack(String raceId, RacePackTemplate template) {
        return createRacePack(defaultDmzRoot(), raceId, template, RacePartDefaults.defaults());
    }

    public static CreateResult createRacePack(String raceId, RacePackTemplate template, RacePartDefaults parts) {
        return createRacePack(defaultDmzRoot(), raceId, template, parts);
    }

    /**
     * Fixture-friendly create under {@code dmzConfigRoot/races/<id>/}.
     *
     * <p>Cited path: {@code ConfigManager#loadAllRaces} loads non-default directories here
     * after {@code ConfigManager.reload()}.
     */
    public static CreateResult createRacePack(Path dmzConfigRoot, String raceId, RacePackTemplate template) {
        return createRacePack(dmzConfigRoot, raceId, template, RacePartDefaults.defaults());
    }

    public static CreateResult createRacePack(Path dmzConfigRoot, String raceId, RacePackTemplate template,
                                              RacePartDefaults parts) {
        return createRacePack(dmzConfigRoot, raceId, template, parts, null);
    }

    public static CreateResult createRacePack(String raceId, RacePackTemplate template, RacePartDefaults parts,
                                              RaceLabels labels) {
        return createRacePack(defaultDmzRoot(), raceId, template, parts, labels);
    }

    public static CreateResult createRacePack(Path dmzConfigRoot, String raceId, RacePackTemplate template,
                                              RacePartDefaults parts, RaceLabels labels) {
        Objects.requireNonNull(dmzConfigRoot, "dmzConfigRoot");
        RacePackTemplate pack = template == null ? RacePackTemplate.defaults() : template;
        RacePartDefaults appearance = parts == null ? RacePartDefaults.defaults() : parts;
        ValidationResult idCheck = validateRaceId(raceId);
        if (!idCheck.ok()) {
            return CreateResult.fail(idCheck.message());
        }
        String key = normal(raceId);
        if (DEFAULT_RACES.contains(key)) {
            return CreateResult.fail("Cannot create a pack for DMZ default race: " + key);
        }
        Path raceDir = dmzConfigRoot.resolve("races").resolve(key).normalize();
        if (!raceDir.startsWith(dmzConfigRoot.resolve("races").normalize())) {
            return CreateResult.fail("Race path escapes races root");
        }
        if (Files.exists(raceDir.resolve("character.json"))) {
            return CreateResult.fail("Race pack already exists: " + key);
        }
        try {
            Path formsDir = raceDir.resolve("forms");
            Files.createDirectories(formsDir);
            Path character = raceDir.resolve("character.json");
            Files.writeString(character, GSON.toJson(skeletonCharacter(key, pack, appearance)),
                    StandardCharsets.UTF_8);
            writeLabelsIfPresent(dmzConfigRoot, key, labels);
            return CreateResult.ok(key, character, formsDir);
        } catch (IOException e) {
            return CreateResult.fail("Failed to write race pack: " + e.getMessage());
        }
    }

    /** True for the six DragonMineZ built-in races that this service must never overwrite. */
    public static boolean isDefaultRace(String raceId) {
        return DEFAULT_RACES.contains(normal(raceId));
    }

    /** True when {@code races/<id>/character.json} exists and is not a DMZ default race. */
    public static boolean isCustomPack(String raceId) {
        return isCustomPack(defaultDmzRoot(), raceId);
    }

    public static boolean isCustomPack(Path dmzConfigRoot, String raceId) {
        String key = normal(raceId);
        if (key.isEmpty() || DEFAULT_RACES.contains(key) || dmzConfigRoot == null) {
            return false;
        }
        Path character = dmzConfigRoot.resolve("races").resolve(key).resolve("character.json");
        return Files.isRegularFile(character);
    }

    /**
     * Overwrites {@code races/<id>/character.json} for an existing custom pack.
     * Default DMZ races are refused.
     */
    public static CreateResult updateRacePack(String raceId, RacePackTemplate template) {
        return updateRacePack(defaultDmzRoot(), raceId, template, RacePartDefaults.defaults());
    }

    public static CreateResult updateRacePack(String raceId, RacePackTemplate template, RacePartDefaults parts) {
        return updateRacePack(defaultDmzRoot(), raceId, template, parts);
    }

    public static CreateResult updateRacePack(Path dmzConfigRoot, String raceId, RacePackTemplate template) {
        return updateRacePack(dmzConfigRoot, raceId, template, RacePartDefaults.defaults());
    }

    public static CreateResult updateRacePack(Path dmzConfigRoot, String raceId, RacePackTemplate template,
                                              RacePartDefaults parts) {
        return updateRacePack(dmzConfigRoot, raceId, template, parts, null);
    }

    public static CreateResult updateRacePack(String raceId, RacePackTemplate template, RacePartDefaults parts,
                                              RaceLabels labels) {
        return updateRacePack(defaultDmzRoot(), raceId, template, parts, labels);
    }

    public static CreateResult updateRacePack(Path dmzConfigRoot, String raceId, RacePackTemplate template,
                                              RacePartDefaults parts, RaceLabels labels) {
        Objects.requireNonNull(dmzConfigRoot, "dmzConfigRoot");
        RacePackTemplate pack = template == null ? RacePackTemplate.defaults() : template;
        RacePartDefaults appearance = parts == null ? RacePartDefaults.defaults() : parts;
        ValidationResult idCheck = validateRaceId(raceId);
        if (!idCheck.ok()) {
            return CreateResult.fail(idCheck.message());
        }
        String key = normal(raceId);
        if (DEFAULT_RACES.contains(key)) {
            return CreateResult.fail("Cannot overwrite DMZ default race: " + key);
        }
        Path raceDir = dmzConfigRoot.resolve("races").resolve(key).normalize();
        if (!raceDir.startsWith(dmzConfigRoot.resolve("races").normalize())) {
            return CreateResult.fail("Race path escapes races root");
        }
        Path character = raceDir.resolve("character.json");
        if (!Files.isRegularFile(character)) {
            return CreateResult.fail("No custom pack to edit: " + key);
        }
        try {
            Path formsDir = raceDir.resolve("forms");
            Files.createDirectories(formsDir);
            JsonObject root = readJsonObject(character).orElseGet(() -> skeletonCharacter(key, pack, appearance));
            mergeAppearance(root, key, pack, appearance);
            Files.writeString(character, GSON.toJson(root), StandardCharsets.UTF_8);
            writeLabelsIfPresent(dmzConfigRoot, key, labels);
            return CreateResult.ok(key, character, formsDir);
        } catch (IOException e) {
            return CreateResult.fail("Failed to update race pack: " + e.getMessage());
        }
    }

    private static void writeLabelsIfPresent(Path dmzConfigRoot, String raceId, RaceLabels labels) {
        if (labels == null) {
            return;
        }
        RaceLabelRegistry.writeSidecar(dmzConfigRoot, raceId, labels.displayName(), labels.description());
    }

    static void mergeAppearance(JsonObject root, String raceId, RacePackTemplate template,
                                RacePartDefaults parts) {
        RacePackTemplate pack = template == null ? RacePackTemplate.defaults() : template;
        RacePartDefaults appearance = parts == null ? RacePartDefaults.defaults() : parts;
        root.addProperty("raceName", raceId);
        root.addProperty("hasGender", pack.hasGender());
        root.addProperty("useVanillaSkin", pack.useVanillaSkin());
        root.addProperty("isLayered", pack.isLayered());
        root.addProperty("racialSkill", nullTo(pack.racialSkill(), "human"));
        root.addProperty("auraType", nullTo(pack.auraType(), "kakarot"));
        root.addProperty("defaultBodyType", appearance.bodyType());
        root.addProperty("defaultHairType", appearance.hairType());
        root.addProperty("defaultEyesType", appearance.eyesType());
        root.addProperty("defaultNoseType", appearance.noseType());
        root.addProperty("defaultMouthType", appearance.mouthType());
        root.addProperty("defaultTattooType", appearance.tattooType());
        root.addProperty("defaultBodyColor", nullTo(pack.defaultBodyColor(), "#FFD3C9"));
        root.addProperty("defaultBodyColor2", nullTo(pack.defaultBodyColor2(), "#FFD3C9"));
        root.addProperty("defaultBodyColor3", nullTo(pack.defaultBodyColor3(), "#FFD3C9"));
        root.addProperty("defaultHairColor", nullTo(pack.defaultHairColor(), "#222629"));
        root.addProperty("defaultEye1Color", nullTo(pack.defaultEye1Color(), "#222629"));
        root.addProperty("defaultEye2Color", nullTo(pack.defaultEye2Color(), "#222629"));
        root.addProperty("defaultAuraColor", nullTo(pack.defaultAuraColor(), "#7FFFFF"));
    }

    static Optional<JsonObject> readJsonObject(Path character) {
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(character, StandardCharsets.UTF_8));
            if (parsed != null && parsed.isJsonObject()) {
                return Optional.of(parsed.getAsJsonObject());
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    /**
     * Reads {@code races/<id>/character.json} for a custom pack so the maker can edit it.
     * Empty when the folder is missing or JSON cannot be parsed.
     */
    public static Optional<LoadedCharacter> readPack(String raceId) {
        return readPack(defaultDmzRoot(), raceId);
    }

    public static Optional<LoadedCharacter> readPack(Path dmzConfigRoot, String raceId) {
        String key = normal(raceId);
        if (key.isEmpty() || dmzConfigRoot == null || DEFAULT_RACES.contains(key)) {
            return Optional.empty();
        }
        Path character = dmzConfigRoot.resolve("races").resolve(key).resolve("character.json");
        if (!Files.isRegularFile(character)) {
            return Optional.empty();
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(character, StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject root = parsed.getAsJsonObject();
            RacePackTemplate template = new RacePackTemplate(
                    bool(root, "hasGender", true),
                    bool(root, "useVanillaSkin", true),
                    bool(root, "isLayered", true),
                    str(root, "racialSkill", "human"),
                    str(root, "auraType", "kakarot"),
                    str(root, "defaultBodyColor", "#FFD3C9"),
                    str(root, "defaultBodyColor2", "#FFD3C9"),
                    str(root, "defaultBodyColor3", "#FFD3C9"),
                    str(root, "defaultHairColor", "#222629"),
                    str(root, "defaultEye1Color", "#222629"),
                    str(root, "defaultEye2Color", "#222629"),
                    str(root, "defaultAuraColor", "#7FFFFF"));
            RacePartDefaults parts = new RacePartDefaults(
                    integer(root, "defaultBodyType", 0),
                    integer(root, "defaultHairType", 1),
                    integer(root, "defaultEyesType", 0),
                    integer(root, "defaultNoseType", 0),
                    integer(root, "defaultMouthType", 0),
                    integer(root, "defaultTattooType", 0));
            return Optional.of(new LoadedCharacter(key, template, parts,
                    RaceLabelRegistry.readSidecar(dmzConfigRoot, key)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Relative pack path shown in maker status ({@code races/<id>/character.json}). */
    public static String characterRelativePath(String raceId) {
        String key = normal(raceId);
        return key.isEmpty() ? "races/?/character.json" : "races/" + key + "/character.json";
    }

    public static Path dmzRoot() {
        return defaultDmzRoot();
    }

    static Path defaultDmzRoot() {
        return FMLPaths.CONFIGDIR.get().resolve("dragonminez");
    }

    /**
     * JSON shape for {@code RaceCharacterConfig} matching {@code setupDefaultCharacter}
     * plus empty {@code formSkillsCosts} entries with Adapter object shape.
     */
    static JsonObject skeletonCharacter(String raceId, RacePackTemplate template) {
        return skeletonCharacter(raceId, template, RacePartDefaults.defaults());
    }

    static JsonObject skeletonCharacter(String raceId, RacePackTemplate template, RacePartDefaults parts) {
        RacePartDefaults appearance = parts == null ? RacePartDefaults.defaults() : parts;
        JsonObject root = new JsonObject();
        root.addProperty("configVersion", CONFIG_VERSION);
        root.addProperty("raceName", raceId);
        root.addProperty("hasGender", template.hasGender());
        root.addProperty("useVanillaSkin", template.useVanillaSkin());
        root.addProperty("customModel", "");
        root.addProperty("isLayered", template.isLayered());
        root.add("headBones", new JsonArray());
        root.addProperty("racialSkill", nullTo(template.racialSkill(), "human"));
        root.addProperty("hasSaiyanTail", false);
        root.addProperty("auraType", nullTo(template.auraType(), "kakarot"));
        JsonArray scaling = new JsonArray();
        scaling.add(0.9375F);
        scaling.add(0.9375F);
        scaling.add(0.9375F);
        root.add("defaultModelScaling", scaling);
        root.addProperty("defaultBodyType", appearance.bodyType());
        root.addProperty("defaultHairType", appearance.hairType());
        root.addProperty("defaultEyesType", appearance.eyesType());
        root.addProperty("defaultNoseType", appearance.noseType());
        root.addProperty("defaultMouthType", appearance.mouthType());
        root.addProperty("defaultTattooType", appearance.tattooType());
        root.addProperty("defaultBodyColor", nullTo(template.defaultBodyColor(), "#FFD3C9"));
        root.addProperty("defaultBodyColor2", nullTo(template.defaultBodyColor2(), "#FFD3C9"));
        root.addProperty("defaultBodyColor3", nullTo(template.defaultBodyColor3(), "#FFD3C9"));
        root.addProperty("defaultHairColor", nullTo(template.defaultHairColor(), "#222629"));
        root.addProperty("defaultEye1Color", nullTo(template.defaultEye1Color(), "#222629"));
        root.addProperty("defaultEye2Color", nullTo(template.defaultEye2Color(), "#222629"));
        root.addProperty("defaultAuraColor", nullTo(template.defaultAuraColor(), "#7FFFFF"));
        JsonObject costs = new JsonObject();
        costs.add("superforms", emptyCost());
        costs.add("godforms", emptyCost());
        costs.add("legendaryforms", emptyCost());
        root.add("formSkillsCosts", costs);
        return root;
    }

    private static JsonObject emptyCost() {
        JsonObject entry = new JsonObject();
        entry.addProperty("buyFromMaster", false);
        entry.add("prices", new JsonArray());
        return entry;
    }

    private static String nullTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String str(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            String value = root.get(key).getAsString();
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return root.get(key).getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return root.get(key).getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String normal(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isId(String value) {
        return value.matches("[a-z0-9][a-z0-9_.-]*") && !value.contains("..");
    }

    /** Outcome of {@link #validateRaceId(String)}. */
    public record ValidationResult(boolean success, String message) {
        public boolean ok() {
            return success;
        }

        public static ValidationResult pass() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message == null ? "Invalid" : message);
        }
    }

    /** Body / hair / face indices stored on {@code character.json}. */
    public record RacePartDefaults(int bodyType, int hairType, int eyesType, int noseType, int mouthType,
                                   int tattooType) {
        public static RacePartDefaults defaults() {
            return new RacePartDefaults(0, 1, 0, 0, 0, 0);
        }
    }

    /** Parsed custom pack used by Race Character Maker to edit a created race. */
    public record LoadedCharacter(String raceId, RacePackTemplate template, RacePartDefaults parts,
                                  RaceLabels labels) {
        public LoadedCharacter(String raceId, RacePackTemplate template, RacePartDefaults parts) {
            this(raceId, template, parts, RaceLabels.blank(raceId));
        }

        public RaceLabels labels() {
            return labels == null ? RaceLabels.blank(raceId) : labels;
        }
    }

    /** Outcome of {@link #createRacePack(String, RacePackTemplate)}. */
    public record CreateResult(boolean success, String raceId, Path characterJson, Path formsDir, String message) {
        public boolean ok() {
            return success;
        }

        public static CreateResult ok(String raceId, Path characterJson, Path formsDir) {
            return new CreateResult(true, raceId, characterJson, formsDir, "");
        }

        public static CreateResult fail(String message) {
            return new CreateResult(false, "", null, null, message == null ? "Failed" : message);
        }
    }
}
