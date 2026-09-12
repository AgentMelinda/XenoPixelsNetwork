package net.bullettrain.xenopixelsmod.dmz.form;

import com.dragonminez.common.config.FormConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DmzFormPreviewOverrides {
    private static final Map<String, FormConfig.FormData> NORMAL = new ConcurrentHashMap<>();
    private static final Map<String, FormConfig.FormData> STACK = new ConcurrentHashMap<>();

    private DmzFormPreviewOverrides() {
    }

    public static void set(DmzFormKind kind, String race, String group, String form,
                           FormConfig.FormData data) {
        map(kind).put(key(race, group, form), data);
    }

    public static FormConfig.FormData get(DmzFormKind kind, String race, String group, String form) {
        return map(kind).get(key(race, group, form));
    }

    public static void clear() {
        NORMAL.clear();
        STACK.clear();
    }

    /**
     * Drops only one kind of override, so a stack draft can be previewed on top of a normal form
     * draft that is still being previewed.
     */
    public static void clear(DmzFormKind kind) {
        map(kind).clear();
    }

    private static Map<String, FormConfig.FormData> map(DmzFormKind kind) {
        return kind == DmzFormKind.STACK ? STACK : NORMAL;
    }

    private static String key(String race, String group, String form) {
        return (race == null ? "" : race) + "\u0000" + (group == null ? "" : group)
                + "\u0000" + (form == null ? "" : form);
    }
}
