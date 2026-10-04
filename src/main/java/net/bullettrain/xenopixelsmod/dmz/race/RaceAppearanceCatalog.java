package net.bullettrain.xenopixelsmod.dmz.race;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.hair.HairMakerDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Per-race authored body types and hair styles stored under
 * {@code config/dragonminez/races/<id>/catalog/}.
 *
 * <p>Body PNGs are also copied into the generated dragonminez-namespace resource pack so
 * {@code TextureCounter} counts them. Hair styles are CustomHair project JSON applied via
 * {@code HairMakerDocument} / {@code UpdateCustomHairC2S}.
 */
public final class RaceAppearanceCatalog {
    public static final String SCHEMA = "xenopixels.race.catalog.v1";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final String raceId;
    private final boolean writable;
    private final List<BodyType> bodies = new ArrayList<>();
    private final List<HairStyle> hairs = new ArrayList<>();

    private RaceAppearanceCatalog(String raceId, boolean writable) {
        this.raceId = raceId;
        this.writable = writable;
    }

    public String raceId() {
        return raceId;
    }

    public boolean isWritable() {
        return writable;
    }

    public List<BodyType> bodies() {
        return List.copyOf(bodies);
    }

    public List<HairStyle> hairs() {
        return List.copyOf(hairs);
    }

    public List<BodyType> bodiesFor(String gender) {
        String gen = normalGender(gender);
        List<BodyType> out = new ArrayList<>();
        for (BodyType body : bodies) {
            if (gen.equals(body.gender())) {
                out.add(body);
            }
        }
        return List.copyOf(out);
    }

    public int maxBodyIndex(String gender) {
        int max = 0;
        for (BodyType body : bodiesFor(gender)) {
            max = Math.max(max, body.index());
        }
        return max;
    }

    public static Path catalogDir(Path dmzConfigRoot, String raceId) {
        return dmzConfigRoot.resolve("races").resolve(RaceCatalogPaths.normal(raceId)).resolve("catalog");
    }

    public static Path catalogJson(Path dmzConfigRoot, String raceId) {
        return catalogDir(dmzConfigRoot, raceId).resolve("catalog.json");
    }

    public static RaceAppearanceCatalog load(Path dmzConfigRoot, String raceId) {
        String key = RaceCatalogPaths.normal(raceId);
        if (key.isEmpty() || !RacePackService.validateRaceId(key).ok() || RacePackService.isDefaultRace(key)) {
            return new RaceAppearanceCatalog(key.isEmpty() ? "unknown" : key, false);
        }
        RaceAppearanceCatalog catalog = new RaceAppearanceCatalog(key, true);
        Path json = catalogJson(dmzConfigRoot, key);
        if (!Files.isRegularFile(json)) {
            return catalog;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) {
                return catalog;
            }
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("bodies") && root.get("bodies").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("bodies")) {
                    if (!el.isJsonObject()) continue;
                    BodyType body = BodyType.fromJson(key, el.getAsJsonObject());
                    if (body != null) {
                        catalog.bodies.add(body);
                    }
                }
            }
            if (root.has("hairs") && root.get("hairs").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("hairs")) {
                    if (!el.isJsonObject()) continue;
                    HairStyle hair = HairStyle.fromJson(el.getAsJsonObject());
                    if (hair != null) {
                        catalog.hairs.add(hair);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return catalog;
    }

    public static RaceAppearanceCatalog loadLive(String raceId) {
        return load(RacePackService.defaultDmzRoot(), raceId);
    }

    public void save(Path dmzConfigRoot) throws IOException {
        if (!writable) {
            return;
        }
        Path dir = catalogDir(dmzConfigRoot, raceId);
        Files.createDirectories(dir.resolve("bodies"));
        Files.createDirectories(dir.resolve("hair"));
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.addProperty("raceId", raceId);
        JsonArray bodyArr = new JsonArray();
        for (BodyType body : bodies) {
            bodyArr.add(body.toJson());
        }
        root.add("bodies", bodyArr);
        JsonArray hairArr = new JsonArray();
        for (HairStyle hair : hairs) {
            hairArr.add(hair.toJson());
        }
        root.add("hairs", hairArr);
        Files.writeString(catalogJson(dmzConfigRoot, raceId), GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        RaceAssetPack.sync(dmzConfigRoot, this);
    }

    public BodyType addGeneratedBody(Path dmzConfigRoot, String gender, String hex) throws IOException {
        if (!writable) {
            throw new IOException("Cannot author bodies for " + raceId);
        }
        Path characterJson = dmzConfigRoot.resolve("races").resolve(raceId).resolve("character.json");
        JsonObject character = JsonParser.parseString(Files.readString(characterJson)).getAsJsonObject();
        if (!character.get("isLayered").getAsBoolean()) {
            throw new IOException("Add Body requires a layered race pack.");
        }
        String model = str(character, "customModel", "");
        if (!model.isBlank() && !model.equals(raceId)) {
            throw new IOException("Add Body requires the race's own model/texture namespace.");
        }
        boolean hasGender = character.get("hasGender").getAsBoolean();
        String gen = hasGender ? normalGender(gender) : "male";
        int index = bodiesFor(gen).isEmpty() && !character.get("useVanillaSkin").getAsBoolean()
                ? 0 : maxBodyIndex(gen) + 1;
        if (index < 0 || index > 100) throw new IOException("DMZ supports body indices from 0 to 100.");
        String id = gen + "_" + index;
        BodyType body = new BodyType(id, gen, index, "bodies/" + id + ".png", "Body " + index,
                RaceCatalogPaths.layeredLayer1(raceId, gen, index, hasGender));
        Path png = bodyPng(dmzConfigRoot, body);
        RaceBodyPng.writePlaceholder(png, RaceBodyPng.parseHex(hex, 0xFFC68642));
        // DMZ otherwise resolves an empty customModel to built-in human textures.
        character.addProperty("customModel", raceId);
        Files.writeString(characterJson, GSON.toJson(character) + "\n", StandardCharsets.UTF_8);
        bodies.add(body);
        return body;
    }

    public HairStyle addHairStyle(Path dmzConfigRoot, String label, HairMakerDocument document) throws IOException {
        if (!writable) {
            throw new IOException("Cannot author hair for " + raceId);
        }
        String id = nextHairId();
        String shown = label == null || label.isBlank() ? "Hair " + (hairs.size() + 1) : label.trim();
        HairStyle style = new HairStyle(id, "hair/" + id + ".json", shown);
        Path json = hairJson(dmzConfigRoot, style);
        Files.createDirectories(json.getParent());
        HairMakerDocument doc = document == null ? new HairMakerDocument() : document;
        if (doc.name() == null || doc.name().isBlank() || "Custom Base".equals(doc.name())
                || "One Strand".equals(doc.name())) {
            doc.name(shown);
        }
        Files.writeString(json, GSON.toJson(doc.toCatalogJson()) + "\n", StandardCharsets.UTF_8);
        hairs.add(style);
        return style;
    }

    public void updateHairStyle(Path dmzConfigRoot, String styleId, HairMakerDocument document) throws IOException {
        HairStyle style = hairById(styleId);
        if (style == null || document == null) {
            return;
        }
        Path json = hairJson(dmzConfigRoot, style);
        Files.createDirectories(json.getParent());
        Files.writeString(json, GSON.toJson(document.toCatalogJson()) + "\n", StandardCharsets.UTF_8);
    }

    public HairStyle hairById(String styleId) {
        if (styleId == null) {
            return null;
        }
        for (HairStyle hair : hairs) {
            if (styleId.equals(hair.id())) {
                return hair;
            }
        }
        return null;
    }

    public BodyType bodyByIndex(String gender, int index) {
        for (BodyType body : bodiesFor(gender)) {
            if (body.index() == index) {
                return body;
            }
        }
        return null;
    }

    public Path bodyPng(Path dmzConfigRoot, BodyType body) {
        return safeCatalogFile(dmzConfigRoot, body.file());
    }

    public Path hairJson(Path dmzConfigRoot, HairStyle style) {
        return safeCatalogFile(dmzConfigRoot, style.file());
    }

    private Path safeCatalogFile(Path dmzConfigRoot, String file) {
        Path dir = catalogDir(dmzConfigRoot, raceId).toAbsolutePath().normalize();
        Path path = dir.resolve(file).normalize();
        if (!path.startsWith(dir)) throw new IllegalArgumentException("Catalog file escapes race directory");
        return path;
    }

    public HairMakerDocument readHair(Path dmzConfigRoot, HairStyle style) throws IOException {
        Path json = hairJson(dmzConfigRoot, style);
        JsonElement parsed = JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8));
        if (parsed == null || !parsed.isJsonObject()) {
            return new HairMakerDocument();
        }
        return HairMakerDocument.fromCatalogJson(parsed.getAsJsonObject());
    }

    private String nextHairId() {
        int n = hairs.size() + 1;
        String id = "style_" + n;
        while (hairById(id) != null) {
            n++;
            id = "style_" + n;
        }
        return id;
    }

    private static String normalGender(String gender) {
        return "female".equalsIgnoreCase(gender) ? "female" : "male";
    }

    public record BodyType(String id, String gender, int index, String file, String label, String texturePath) {
        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("gender", gender);
            o.addProperty("index", index);
            o.addProperty("file", file);
            o.addProperty("label", label);
            o.addProperty("texturePath", texturePath);
            return o;
        }

        static BodyType fromJson(String raceId, JsonObject o) {
            String id = str(o, "id", "");
            String gender = normalGender(str(o, "gender", "male"));
            int index = o.has("index") ? o.get("index").getAsInt() : 0;
            if (id.isBlank() || index < 0) {
                return null;
            }
            String file = str(o, "file", "bodies/" + id + ".png");
            String label = str(o, "label", "Body " + index);
            String texture = str(o, "texturePath", RaceCatalogPaths.layeredLayer1(raceId, gender, index));
            return new BodyType(id, gender, index, file, label, texture);
        }
    }

    public record HairStyle(String id, String file, String label) {
        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("file", file);
            o.addProperty("label", label);
            return o;
        }

        static HairStyle fromJson(JsonObject o) {
            String id = str(o, "id", "");
            if (id.isBlank()) {
                return null;
            }
            return new HairStyle(id, str(o, "file", "hair/" + id + ".json"), str(o, "label", id));
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            String value = o.get(key).getAsString();
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception e) {
            return fallback;
        }
    }
}
