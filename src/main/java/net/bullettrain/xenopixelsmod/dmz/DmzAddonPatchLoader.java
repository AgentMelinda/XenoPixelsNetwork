package net.bullettrain.xenopixelsmod.dmz;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads additive third-party patches from {@code data/<namespace>/xenopixels/dmz_patch.json}. */
final class DmzAddonPatchLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String PATCH_PATH = "xenopixels/dmz_patch.json";
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "formSkillsAdd", "skillOfferings", "exclusiveFormSkills", "skillCosts",
            "nonFormSkillOfferings");
    private static final List<String> REMAPPED_FORM_TERMS = List.of(
            "super", "god", "legendary", "android");

    private DmzAddonPatchLoader() {}

    static List<PatchDocument> discover(ResourceManager resources) {
        List<PatchDocument> patches = new ArrayList<>();
        Map<ResourceLocation, List<Resource>> stacks = resources.listResourceStacks(
                "xenopixels", location -> PATCH_PATH.equals(location.getPath()));
        stacks.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entry.getValue().stream()
                        .sorted(Comparator.comparing(Resource::sourcePackId))
                        .forEach(resource -> patches.add(read(entry.getKey(), resource))));
        validateDocuments(patches);
        return List.copyOf(patches);
    }

    static boolean apply(Path skillsJson, List<PatchDocument> patches) throws IOException {
        if (patches.isEmpty()) return false;
        if (!Files.isRegularFile(skillsJson)) {
            throw new IOException("DragonMineZ skills.json is missing: " + skillsJson);
        }

        JsonObject original;
        try (Reader reader = Files.newBufferedReader(skillsJson, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("skills.json root must be an object");
            original = parsed.getAsJsonObject();
        }

        JsonObject merged = merge(original, patches);
        if (merged.equals(original)) return false;
        writeAtomically(skillsJson, merged);
        XenoPixelsMod.LOGGER.info("Applied {} additive addon DMZ patch resource(s)", patches.size());
        return true;
    }

    static JsonObject merge(JsonObject original, List<PatchDocument> patches) {
        validateDocuments(patches);
        JsonObject merged = JsonParser.parseString(GSON.toJson(original)).getAsJsonObject();
        JsonArray formSkills = objectArray(merged, "formSkills");
        JsonObject offerings = objectObject(merged, "skillOfferings");
        JsonObject skills = objectObject(merged, "skills");
        Map<String, String> claimedFormSkills = new HashMap<>();
        Map<String, String> claimedCosts = new HashMap<>();

        for (PatchDocument document : patches) {
            JsonObject patch = document.patch();
            String source = document.source();
            Set<String> introducedForms = strings(patch, "formSkillsAdd", source);

            for (String skill : introducedForms) {
                claim(claimedFormSkills, skill, source, "form skill");
                validateFormSkillId(skill, source);
                if (!contains(formSkills, skill)) formSkills.add(skill);
            }

            JsonObject costs = optionalObject(patch, "skillCosts", source);
            for (Map.Entry<String, JsonElement> entry : costs.entrySet()) {
                String skill = requireId(entry.getKey(), source + ".skillCosts");
                claim(claimedCosts, skill, source, "skill cost");
                JsonObject definition = requireObject(entry.getValue(), source + ".skillCosts." + skill);
                validateCosts(definition, source, skill);
                if (!skills.has(skill)) skills.add(skill, definition.deepCopy());
            }

            for (String formSkill : introducedForms) {
                if (!costs.has(formSkill)) {
                    throw invalid(source, "form skill '" + formSkill + "' requires skillCosts." + formSkill);
                }
            }

            appendOfferings(offerings, optionalObject(patch, "skillOfferings", source), source);
            appendOfferings(offerings, optionalObject(patch, "nonFormSkillOfferings", source), source);

            JsonObject exclusive = optionalObject(patch, "exclusiveFormSkills", source);
            for (Map.Entry<String, JsonElement> entry : exclusive.entrySet()) {
                String skill = requireId(entry.getKey(), source + ".exclusiveFormSkills");
                if (!introducedForms.contains(skill)) {
                    throw invalid(source, "exclusiveFormSkills may only name a form skill introduced by the same patch: " + skill);
                }
                JsonArray masters = requireArray(entry.getValue(), source + ".exclusiveFormSkills." + skill);
                if (masters.isEmpty()) throw invalid(source, "exclusive master list is empty for " + skill);
                for (String master : stringValues(masters, source + ".exclusiveFormSkills." + skill)) {
                    appendOffering(offerings, master, skill);
                }
            }
        }

        merged.add("formSkills", formSkills);
        merged.add("skillOfferings", offerings);
        merged.add("skills", skills);
        return merged;
    }

    private static PatchDocument read(ResourceLocation id, Resource resource) {
        String source = id + " from " + resource.sourcePackId();
        try (Reader reader = resource.openAsReader()) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw invalid(source, "root must be a JSON object");
            return new PatchDocument(source, parsed.getAsJsonObject());
        } catch (IOException | RuntimeException error) {
            throw new IllegalArgumentException("Failed reading addon DMZ patch " + source, error);
        }
    }

    private static void validateDocuments(List<PatchDocument> patches) {
        for (PatchDocument document : patches) {
            for (String field : document.patch().keySet()) {
                if ("formSkillsRemove".equals(field)) {
                    throw invalid(document.source(), "formSkillsRemove is forbidden for third-party patches");
                }
                if (!ALLOWED_FIELDS.contains(field)) {
                    throw invalid(document.source(), "unsupported field '" + field + "'");
                }
            }
            strings(document.patch(), "formSkillsAdd", document.source());
            optionalObject(document.patch(), "skillOfferings", document.source());
            optionalObject(document.patch(), "exclusiveFormSkills", document.source());
            optionalObject(document.patch(), "skillCosts", document.source());
            optionalObject(document.patch(), "nonFormSkillOfferings", document.source());
        }
    }

    private static void appendOfferings(JsonObject target, JsonObject additions, String source) {
        for (Map.Entry<String, JsonElement> entry : additions.entrySet()) {
            String master = requireId(entry.getKey(), source + ".offerings");
            JsonArray values = requireArray(entry.getValue(), source + ".offerings." + master);
            for (String skill : stringValues(values, source + ".offerings." + master)) {
                appendOffering(target, master, skill);
            }
        }
    }

    private static void appendOffering(JsonObject offerings, String master, String skill) {
        JsonArray existing = offerings.has(master) && offerings.get(master).isJsonArray()
                ? offerings.getAsJsonArray(master)
                : new JsonArray();
        if (!contains(existing, skill)) existing.add(skill);
        offerings.add(master, existing);
    }

    private static void validateFormSkillId(String id, String source) {
        String lower = id.toLowerCase(java.util.Locale.ROOT);
        for (String term : REMAPPED_FORM_TERMS) {
            if (lower.contains(term)) {
                throw invalid(source, "form skill '" + id + "' contains '" + term
                        + "' and DragonMineZ remaps that formType");
            }
        }
    }

    private static void validateCosts(JsonObject definition, String source, String skill) {
        if (!definition.has("costs") || !definition.get("costs").isJsonArray()) {
            throw invalid(source, "skillCosts." + skill + ".costs must be an array");
        }
        JsonArray costs = definition.getAsJsonArray("costs");
        if (costs.isEmpty()) throw invalid(source, "skillCosts." + skill + ".costs must not be empty");
        for (JsonElement cost : costs) {
            if (!cost.isJsonPrimitive() || !cost.getAsJsonPrimitive().isNumber()) {
                throw invalid(source, "skillCosts." + skill + ".costs must contain only numbers");
            }
        }
        if (definition.has("allowedRaces") && !definition.get("allowedRaces").isJsonArray()) {
            throw invalid(source, "skillCosts." + skill + ".allowedRaces must be an array");
        }
    }

    private static void claim(Map<String, String> claims, String id, String source, String kind) {
        String previous = claims.putIfAbsent(id, source);
        if (previous != null && !previous.equals(source)) {
            throw invalid(source, kind + " '" + id + "' is also claimed by " + previous);
        }
    }

    private static Set<String> strings(JsonObject object, String field, String source) {
        if (!object.has(field)) return Set.of();
        JsonArray array = requireArray(object.get(field), source + "." + field);
        return new HashSet<>(stringValues(array, source + "." + field));
    }

    private static List<String> stringValues(JsonArray array, String path) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(path + " must contain only strings");
            }
            values.add(requireId(element.getAsString(), path));
        }
        return values;
    }

    private static String requireId(String value, String path) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " contains a blank id");
        return value;
    }

    private static JsonObject optionalObject(JsonObject root, String field, String source) {
        if (!root.has(field)) return new JsonObject();
        return requireObject(root.get(field), source + "." + field);
    }

    private static JsonObject requireObject(JsonElement element, String path) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException(path + " must be an object");
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonElement element, String path) {
        if (element == null || !element.isJsonArray()) throw new IllegalArgumentException(path + " must be an array");
        return element.getAsJsonArray();
    }

    private static JsonArray objectArray(JsonObject object, String field) {
        if (!object.has(field)) return new JsonArray();
        return requireArray(object.get(field), "skills.json." + field).deepCopy();
    }

    private static JsonObject objectObject(JsonObject object, String field) {
        if (!object.has(field)) return new JsonObject();
        return requireObject(object.get(field), "skills.json." + field).deepCopy();
    }

    private static boolean contains(JsonArray array, String value) {
        for (JsonElement element : array) {
            if (element.isJsonPrimitive() && value.equals(element.getAsString())) return true;
        }
        return false;
    }

    private static IllegalArgumentException invalid(String source, String message) {
        return new IllegalArgumentException("Invalid addon DMZ patch " + source + ": " + message);
    }

    private static void writeAtomically(Path target, JsonObject content) throws IOException {
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(content, writer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    record PatchDocument(String source, JsonObject patch) {}
}
