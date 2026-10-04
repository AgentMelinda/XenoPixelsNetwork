package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Stored and temporary script data with the native bounds. Stored data keeps only strings and
 * finite numbers (64 keys, keys of 1-64 characters, strings up to 1024); temp data keeps any value
 * (64 keys). Refused writes change nothing.
 */
final class XenoBoundedData {
    static final int MAX_KEYS = 64;
    static final int MAX_KEY = 64;
    static final int MAX_VALUE = 1024;

    private XenoBoundedData() {}

    private static boolean validKey(String key) {
        return key != null && !key.isBlank() && key.length() <= MAX_KEY;
    }

    /** {@code read} returns the live tag; {@code write} stores a changed tag back. */
    static XenoDataAdapter.View stored(Supplier<CompoundTag> read, Consumer<CompoundTag> write) {
        return new XenoDataAdapter.View() {
            public void put(String key, Object value) {
                if (!validKey(key)) return;
                CompoundTag tag = read.get();
                if (!tag.contains(key) && tag.size() >= MAX_KEYS) return;
                if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
                    tag.putDouble(key, number.doubleValue());
                } else if (value instanceof String string && string.length() <= MAX_VALUE) {
                    tag.putString(key, string);
                } else {
                    return;
                }
                write.accept(tag);
            }

            public Object get(String key) {
                CompoundTag tag = read.get();
                if (key == null || !tag.contains(key)) return null;
                return tag.get(key) instanceof NumericTag number ? number.getAsDouble() : tag.getString(key);
            }

            public void remove(String key) {
                if (key == null) return;
                CompoundTag tag = read.get();
                if (tag.contains(key)) {
                    tag.remove(key);
                    write.accept(tag);
                }
            }

            public boolean has(String key) { return key != null && read.get().contains(key); }
            public String[] getKeys() { return read.get().getAllKeys().toArray(String[]::new); }

            public void clear() {
                CompoundTag tag = read.get();
                for (String key : List.copyOf(tag.getAllKeys())) tag.remove(key);
                write.accept(tag);
            }
        };
    }

    static XenoDataAdapter.View temp(Map<String, Object> map) {
        return new XenoDataAdapter.View() {
            public void put(String key, Object value) {
                if (key == null) return;
                if (value == null) map.remove(key);
                else if (map.containsKey(key) || map.size() < MAX_KEYS) map.put(key, value);
            }
            public Object get(String key) { return key == null ? null : map.get(key); }
            public void remove(String key) { if (key != null) map.remove(key); }
            public boolean has(String key) { return key != null && map.containsKey(key); }
            public String[] getKeys() { return map.keySet().toArray(String[]::new); }
            public void clear() { map.clear(); }
        };
    }
}
