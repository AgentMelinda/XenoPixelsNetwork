package net.bullettrain.xenopixelsmod.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.regex.Pattern;

public record XenoNpcRoleDefinition(
        int schema,
        XenoNpcRole role,
        String bodyFamily,
        int combatDecisionTicks,
        int activeDecisionTicks,
        int idleDecisionTicks,
        DefinitionRefs refs
) {
    public static final int SCHEMA_VERSION = 1;
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    /**
     * Datapack ids the role points at.
     *
     * <p>{@code lines} was added alongside the speech system; the rest predate it. A blank string
     * means the role does not use that reference, which is the normal case for most of them.
     */
    public record DefinitionRefs(String dialogue, String quest, String trades, String job,
                                 String lines) {}

    public static XenoNpcRoleDefinition fromJson(JsonElement element, XenoNpcRole expectedRole) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("NPC role definition must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        int schema = requiredInt(root, "schema");
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported NPC role schema " + schema);
        }
        XenoNpcRole role = XenoNpcRole.byId(requiredString(root, "role"));
        if (role != expectedRole) {
            throw new IllegalArgumentException("NPC role definition expected " + expectedRole.id()
                    + " but declared " + role.id());
        }
        String brain = requiredString(root, "brain").toLowerCase(Locale.ROOT);
        if (!brain.equals("v5")) {
            throw new IllegalArgumentException("Native NPC role definitions require brain v5");
        }
        String bodyFamily = requiredString(root, "body_family").toLowerCase(Locale.ROOT);
        if (!bodyFamily.equals("humanoid") && !bodyFamily.equals("creature")) {
            throw new IllegalArgumentException("body_family must be humanoid or creature");
        }
        JsonObject decisions = requiredObject(root, "decision_ticks");
        int combat = boundedTicks(decisions, "combat");
        int active = boundedTicks(decisions, "active");
        int idle = boundedTicks(decisions, "idle");
        JsonObject references = root.has("refs") && root.get("refs").isJsonObject()
                ? root.getAsJsonObject("refs")
                : new JsonObject();
        return new XenoNpcRoleDefinition(schema, role, bodyFamily, combat, active, idle,
                new DefinitionRefs(
                        optionalResourceId(references, "dialogue"),
                        optionalResourceId(references, "quest"),
                        optionalResourceId(references, "trades"),
                        optionalResourceId(references, "job"),
                        optionalResourceId(references, "lines")));
    }

    public static XenoNpcRoleDefinition defaults(XenoNpcRole role) {
        int combat = role == XenoNpcRole.GUARD ? 8 : 10;
        int active = role == XenoNpcRole.COMPANION ? 10 : 20;
        return new XenoNpcRoleDefinition(SCHEMA_VERSION, role,
                role.creature() ? "creature" : "humanoid", combat, active, 40,
                new DefinitionRefs("", "", "", "", ""));
    }

    private static int boundedTicks(JsonObject object, String key) {
        int value = requiredInt(object, key);
        if (value < 1 || value > 1200) {
            throw new IllegalArgumentException(key + " decision interval must be between 1 and 1200 ticks");
        }
        return value;
    }

    private static String optionalResourceId(JsonObject object, String key) {
        if (!object.has(key)) return "";
        String value = object.get(key).getAsString().trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return "";
        if (!RESOURCE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(key + " must be a namespaced resource id");
        }
        return value;
    }

    private static JsonObject requiredObject(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonObject()) {
            throw new IllegalArgumentException("Missing JSON object " + key);
        }
        return object.getAsJsonObject(key);
    }

    private static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing string " + key);
        }
        String value = object.get(key).getAsString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(key + " cannot be blank");
        return value;
    }

    private static int requiredInt(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing integer " + key);
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }
}
