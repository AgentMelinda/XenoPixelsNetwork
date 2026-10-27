package net.bullettrain.xenopixelsmod.space.pod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Validated data-pack description for a pod cockpit component. */
public record PodCockpitDefinition(ResourceLocation id, int schemaVersion,
                                  ResourceLocation model, boolean pressurized) {
    public static final int SCHEMA_VERSION = 1;
    private static final String ID_PREFIX = "pod/cockpit/";

    public PodCockpitDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(model, "model");
        if (!id.getPath().startsWith(ID_PREFIX) || id.getPath().length() == ID_PREFIX.length())
            throw new IllegalArgumentException("cockpit id must use the " + ID_PREFIX + " path");
        if (schemaVersion != SCHEMA_VERSION)
            throw new IllegalArgumentException("unsupported pod cockpit schema_version " + schemaVersion);
    }

    public static PodCockpitDefinition parse(ResourceLocation id, JsonElement element) {
        if (element == null || !element.isJsonObject())
            throw new IllegalArgumentException("pod cockpit must be a JSON object");
        JsonObject json = element.getAsJsonObject();
        for (String key : json.keySet()) {
            if (!List.of("schema_version", "model", "pressurized").contains(key))
                throw new IllegalArgumentException("unknown pod cockpit field " + key);
        }
        int version = integer(json, "schema_version");
        ResourceLocation model = resourceId(json, "model");
        JsonElement pressure = json.get("pressurized");
        if (pressure == null || !pressure.isJsonPrimitive() || !pressure.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("missing or invalid pressurized");
        return new PodCockpitDefinition(id, version, model, pressure.getAsBoolean());
    }

    private static int integer(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("missing or invalid " + key);
        try {
            return new java.math.BigDecimal(value.getAsString()).intValueExact();
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
