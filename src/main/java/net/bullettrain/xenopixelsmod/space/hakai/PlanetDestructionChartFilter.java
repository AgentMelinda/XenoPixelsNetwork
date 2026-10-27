package net.bullettrain.xenopixelsmod.space.hakai;

import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Pure chart filtering for erased or cracked bodies and their natural satellites. */
final class PlanetDestructionChartFilter {
    private PlanetDestructionChartFilter() {}

    static Map<String, JsonObject> withoutBodies(Map<String, JsonObject> definitions, Set<String> removedRoots) {
        if (removedRoots.isEmpty()) return definitions;
        var gone = new HashSet<>(removedRoots);
        boolean grew;
        do {
            grew = false;
            for (var entry : definitions.entrySet()) {
                String parent = parentOf(entry.getValue());
                if (gone.contains(entry.getKey()) || !gone.contains(parent) || isStar(definitions.get(parent))) continue;
                gone.add(entry.getKey());
                grew = true;
            }
        } while (grew);

        var visible = new LinkedHashMap<String, JsonObject>();
        definitions.forEach((id, json) -> { if (!gone.contains(id)) visible.put(id, json); });
        return visible;
    }

    private static boolean isStar(JsonObject json) {
        try { return json != null && json.has("kind") && "star".equalsIgnoreCase(json.get("kind").getAsString()); }
        catch (RuntimeException malformed) { return false; }
    }

    private static String parentOf(JsonObject json) {
        try {
            if (json.has("orbit") && json.getAsJsonObject("orbit").has("parent"))
                return json.getAsJsonObject("orbit").get("parent").getAsString();
            if (json.has("parent") && !json.get("parent").isJsonNull()) return json.get("parent").getAsString();
        } catch (RuntimeException malformed) {
            return null;
        }
        return null;
    }
}
