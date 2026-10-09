package net.bullettrain.xenopixelsmod.combat.v2.combo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.bullettrain.xenopixelsmod.combat.v2.HitReaction;
import net.bullettrain.xenopixelsmod.combat.v2.V2Direction;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a {@link ComboGraph} from JSON.
 *
 * <p>Strict on purpose: an unknown intent, reaction, input or direction is an error naming the
 * node, not a silently skipped field. A route that quietly lost its launcher plays as an ordinary
 * punch and nobody can tell why.
 *
 * <pre>
 * {
 *   "starts": { "light": "l1", "heavy": "h1" },
 *   "nodes": {
 *     "h1": { "intent": "MID_KICK_LEFT",
 *             "variants": { "left": "HIGH_ROUNDHOUSE",
 *                           "forward": { "intent": "FLYING_KICK", "reaction": "KICK_UP" } },
 *             "startup": 4, "cancel": 6, "window": 12, "damage": 1.6,
 *             "reaction": "HIT_HEAVY", "charged": "KICK_ARC",
 *             "next": { "light": "h1_light", "heavy": "h2" } }
 *   }
 * }
 * </pre>
 *
 * <p>A variant is either a pose name, which changes only the pose, or an object that may also
 * change what the hit does.
 */
public final class ComboGraphParser {

    private ComboGraphParser() {}

    public static ComboGraph parse(Reader reader) {
        JsonElement rootElement = JsonParser.parseReader(reader);
        if (!rootElement.isJsonObject()) throw new IllegalArgumentException("combo graph: not an object");
        JsonObject root = rootElement.getAsJsonObject();
        if (!root.has("nodes") || !root.get("nodes").isJsonObject()) {
            throw new IllegalArgumentException("combo graph: no \"nodes\" object");
        }
        Map<ComboInput, String> starts = new EnumMap<>(ComboInput.class);
        if (root.has("starts") && root.get("starts").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("starts").entrySet()) {
                starts.put(constant(ComboInput.class, e.getKey(), "starts"), e.getValue().getAsString());
            }
        }
        List<ComboNode> nodes = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("nodes").entrySet()) {
            if (e.getKey().startsWith("_")) continue;
            if (!e.getValue().isJsonObject()) {
                throw new IllegalArgumentException("combo node " + e.getKey() + ": not an object");
            }
            nodes.add(node(e.getKey(), e.getValue().getAsJsonObject()));
        }
        return new ComboGraph(starts, nodes);
    }

    private static ComboNode node(String id, JsonObject o) {
        if (!o.has("intent")) throw new IllegalArgumentException("combo node " + id + ": no intent");
        Bt3AnimationIntent intent = constant(Bt3AnimationIntent.class, o.get("intent").getAsString(), id);
        Map<V2Direction, ComboNode.Variant> variants = new EnumMap<>(V2Direction.class);
        if (o.has("variants") && o.get("variants").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("variants").entrySet()) {
                variants.put(constant(V2Direction.class, e.getKey(), id), variant(id, e.getValue()));
            }
        }
        Map<ComboInput, String> next = new EnumMap<>(ComboInput.class);
        if (o.has("next") && o.get("next").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("next").entrySet()) {
                next.put(constant(ComboInput.class, e.getKey(), id), e.getValue().getAsString());
            }
        }
        HitReaction reaction = o.has("reaction")
                ? constant(HitReaction.class, o.get("reaction").getAsString(), id)
                : HitReaction.HIT_LIGHT;
        HitReaction charged = o.has("charged")
                ? constant(HitReaction.class, o.get("charged").getAsString(), id)
                : null;
        ComboNode.Action action = o.has("action")
                ? constant(ComboNode.Action.class, o.get("action").getAsString(), id)
                : ComboNode.Action.STRIKE;
        return new ComboNode(id, intent, variants,
                integer(o, "startup", 2), integer(o, "cancel", 4), integer(o, "window", 12),
                number(o, "damage", 1.0f), number(o, "ki", 0f), number(o, "stamina", 0f),
                reaction, charged, action, next);
    }

    private static ComboNode.Variant variant(String id, JsonElement element) {
        if (element.isJsonPrimitive()) {
            return new ComboNode.Variant(
                    constant(Bt3AnimationIntent.class, element.getAsString(), id), null);
        }
        if (!element.isJsonObject() || !element.getAsJsonObject().has("intent")) {
            throw new IllegalArgumentException(
                    "combo " + id + ": a variant is a pose name or an object with an \"intent\"");
        }
        JsonObject o = element.getAsJsonObject();
        return new ComboNode.Variant(
                constant(Bt3AnimationIntent.class, o.get("intent").getAsString(), id),
                o.has("reaction") ? constant(HitReaction.class, o.get("reaction").getAsString(), id) : null);
    }

    private static <E extends Enum<E>> E constant(Class<E> type, String raw, String where) {
        String key = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "combo " + where + ": unknown " + type.getSimpleName() + " '" + raw + "'");
        }
    }

    private static int integer(JsonObject o, String key, int fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : fallback;
    }

    private static float number(JsonObject o, String key, float fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsFloat() : fallback;
    }
}
