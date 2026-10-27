package net.bullettrain.xenopixelsmod.space.pod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.math.BigDecimal;

/** Validated, immutable data-pack description for one P16 pod hull. */
public record PodHullDefinition(ResourceLocation id, int schemaVersion, ResourceLocation model,
                                ResourceLocation colliderProfile, Map<String, Double> statModifiers,
                                Map<Slot, List<ResourceLocation>> allowedSlots) {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_STAT_MODIFIERS = 32;
    public static final int MAX_SLOT_OPTIONS = 64;
    private static final Pattern STAT_KEY = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    /** Slot groups a hull may constrain. These IDs match {@link SpacePodLoadout}'s stable path groups. */
    public enum Slot {
        COCKPIT("cockpit", "pod/cockpit/"),
        THRUSTER("thruster", "pod/thruster/"),
        TRIM("trim", "pod/trim/");

        private final String jsonKey;
        private final String idPrefix;

        Slot(String jsonKey, String idPrefix) {
            this.jsonKey = jsonKey;
            this.idPrefix = idPrefix;
        }

        public String jsonKey() { return jsonKey; }

        private boolean accepts(ResourceLocation id) {
            return id.getPath().startsWith(idPrefix) && id.getPath().length() > idPrefix.length();
        }
    }

    public PodHullDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(colliderProfile, "colliderProfile");
        if (!id.getPath().startsWith("pod/hull/") || id.getPath().length() == "pod/hull/".length())
            throw new IllegalArgumentException("hull id must use the pod/hull/ path");
        if (schemaVersion != SCHEMA_VERSION)
            throw new IllegalArgumentException("unsupported pod hull schema_version " + schemaVersion);
        if (statModifiers == null || statModifiers.size() > MAX_STAT_MODIFIERS)
            throw new IllegalArgumentException("stat_modifiers must contain at most " + MAX_STAT_MODIFIERS + " entries");
        Map<String, Double> stats = new LinkedHashMap<>();
        for (var entry : statModifiers.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "stat modifier key");
            Double value = Objects.requireNonNull(entry.getValue(), "stat modifier value");
            if (!STAT_KEY.matcher(key).matches() || !Double.isFinite(value))
                throw new IllegalArgumentException("invalid stat modifier " + key + "=" + value);
            stats.put(key, value);
        }
        statModifiers = Collections.unmodifiableMap(stats);

        if (allowedSlots == null || allowedSlots.size() != Slot.values().length)
            throw new IllegalArgumentException("slot_rules must define cockpit, thruster, and trim");
        EnumMap<Slot, List<ResourceLocation>> slots = new EnumMap<>(Slot.class);
        for (Slot slot : Slot.values()) {
            List<ResourceLocation> options = allowedSlots.get(slot);
            if (options == null || options.isEmpty() || options.size() > MAX_SLOT_OPTIONS)
                throw new IllegalArgumentException("slot_rules." + slot.jsonKey() + " must contain 1.." + MAX_SLOT_OPTIONS + " options");
            var unique = new java.util.LinkedHashSet<ResourceLocation>();
            for (ResourceLocation option : options) {
                if (option == null || !slot.accepts(option))
                    throw new IllegalArgumentException("slot_rules." + slot.jsonKey() + " contains an id outside " + slot.idPrefix);
                if (!unique.add(option)) throw new IllegalArgumentException("duplicate slot option " + option);
            }
            slots.put(slot, List.copyOf(unique));
        }
        allowedSlots = Collections.unmodifiableMap(slots);
    }

    /** Parse one JSON object after the loader has derived its stable ID from the resource path. */
    public static PodHullDefinition parse(ResourceLocation id, JsonElement element) {
        if (element == null || !element.isJsonObject())
            throw new IllegalArgumentException("pod hull must be a JSON object");
        JsonObject json = element.getAsJsonObject();
        for (String key : json.keySet()) {
            if (!List.of("schema_version", "model", "collider_profile", "stat_modifiers", "slot_rules").contains(key))
                throw new IllegalArgumentException("unknown pod hull field " + key);
        }
        int version = integer(json, "schema_version");
        ResourceLocation model = resourceId(json, "model");
        ResourceLocation collider = resourceId(json, "collider_profile");
        Map<String, Double> stats = parseStats(json);
        Map<Slot, List<ResourceLocation>> slots = parseSlots(json);
        return new PodHullDefinition(id, version, model, collider, stats, slots);
    }

    private static Map<String, Double> parseStats(JsonObject json) {
        JsonElement element = json.get("stat_modifiers");
        if (element == null) return Map.of();
        if (!element.isJsonObject()) throw new IllegalArgumentException("stat_modifiers must be an object");
        JsonObject object = element.getAsJsonObject();
        if (object.size() > MAX_STAT_MODIFIERS)
            throw new IllegalArgumentException("stat_modifiers exceeds " + MAX_STAT_MODIFIERS + " entries");
        Map<String, Double> result = new LinkedHashMap<>();
        for (var entry : object.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("stat modifier " + entry.getKey() + " must be numeric");
            double value = entry.getValue().getAsDouble();
            if (!Double.isFinite(value)) throw new IllegalArgumentException("stat modifier " + entry.getKey() + " must be finite");
            result.put(entry.getKey(), value);
        }
        return result;
    }

    private static Map<Slot, List<ResourceLocation>> parseSlots(JsonObject json) {
        JsonElement element = json.get("slot_rules");
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException("slot_rules must be an object");
        JsonObject rules = element.getAsJsonObject();
        for (String key : rules.keySet()) {
            boolean known = false;
            for (Slot slot : Slot.values()) if (slot.jsonKey().equals(key)) known = true;
            if (!known) throw new IllegalArgumentException("unknown slot_rules group " + key);
        }
        EnumMap<Slot, List<ResourceLocation>> result = new EnumMap<>(Slot.class);
        for (Slot slot : Slot.values()) {
            JsonElement optionsElement = rules.get(slot.jsonKey());
            if (optionsElement == null || !optionsElement.isJsonArray())
                throw new IllegalArgumentException("slot_rules." + slot.jsonKey() + " must be an array");
            var options = new ArrayList<ResourceLocation>();
            if (optionsElement.getAsJsonArray().size() > MAX_SLOT_OPTIONS)
                throw new IllegalArgumentException("slot_rules." + slot.jsonKey() + " exceeds " + MAX_SLOT_OPTIONS + " options");
            for (JsonElement option : optionsElement.getAsJsonArray()) {
                if (!option.isJsonPrimitive() || !option.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("slot_rules." + slot.jsonKey() + " ids must be strings");
                ResourceLocation parsed = ResourceLocation.tryParse(option.getAsString());
                if (parsed == null) throw new IllegalArgumentException("invalid slot id " + option.getAsString());
                options.add(parsed);
            }
            result.put(slot, options);
        }
        return result;
    }

    private static int integer(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("missing or invalid " + key);
        try {
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException malformed) {
            throw new IllegalArgumentException("invalid " + key, malformed);
        }
    }

    private static ResourceLocation resourceId(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("missing or invalid " + key);
        ResourceLocation parsed = ResourceLocation.tryParse(value.getAsString());
        if (parsed == null) throw new IllegalArgumentException("invalid " + key + " id " + value.getAsString());
        return parsed;
    }
}
