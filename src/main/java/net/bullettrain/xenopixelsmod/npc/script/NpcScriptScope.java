package net.bullettrain.xenopixelsmod.npc.script;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The plain values a script is handed.
 *
 * <p>Values include bounded script wrappers and the XenoPixels script facade. Raw Minecraft
 * entities are not bound; bundled Nashorn uses {@code --no-java} and a deny-all class filter.
 */
public final class NpcScriptScope {
    public static final NpcScriptScope EMPTY = new NpcScriptScope(Map.of());

    private final Map<String, Object> values;

    private NpcScriptScope(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Bindings in insertion order; keys given a null value were dropped at build time. */
    public Map<String, Object> asMap() {
        return values;
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public int size() {
        return values.size();
    }

    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Builder put(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                values.put(key, value);
            }
            return this;
        }

        public Builder putString(String key, String value) {
            return put(key, value == null ? "" : value);
        }

        public Builder putInt(String key, int value) {
            return put(key, value);
        }

        public NpcScriptScope build() {
            return new NpcScriptScope(new LinkedHashMap<>(values));
        }
    }
}
