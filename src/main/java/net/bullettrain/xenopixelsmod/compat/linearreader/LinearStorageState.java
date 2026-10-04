package net.bullettrain.xenopixelsmod.compat.linearreader;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;

/** Exact 1.3.0 merged cache field; resolving after mixin application preserves unsaved linear regions. */
public final class LinearStorageState {
    private static final ClassValue<Field> CACHE = new ClassValue<>() {
        @Override protected Field computeValue(Class<?> storage) {
            try {
                Field field = storage.getDeclaredField("linearCache");
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("LinearReader 1.3.0 linear cache unavailable; refusing unsafe format selection", exception);
            }
        }
    };

    private LinearStorageState() {}

    public static boolean isLinearOpen(Object storage, long key) {
        try {
            Object cache = CACHE.get(storage.getClass()).get(storage);
            return cache != null && ((Long2ObjectMap<?>) cache).containsKey(key);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot inspect LinearReader cache safely", exception);
        }
    }
}
