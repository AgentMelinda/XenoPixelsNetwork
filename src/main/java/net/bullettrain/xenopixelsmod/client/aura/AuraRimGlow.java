package net.bullettrain.xenopixelsmod.client.aura;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entities showing the aura-off rim glow (a form is on, the aura is off, the HD aura is chosen)
 * and their aura colour: the outline glow mixins read this. Kept free of client classes.
 */
public final class AuraRimGlow {
    private static final Map<Integer, Integer> COLOURS = new ConcurrentHashMap<>();

    private AuraRimGlow() {
    }

    /** The rim colour (0xRRGGBB) for this entity id, or null when it has no rim glow. */
    public static Integer colour(int entityId) {
        return COLOURS.get(entityId);
    }

    static void set(int entityId, int rgb) {
        COLOURS.put(entityId, rgb & 0xFFFFFF);
    }

    static void clear(int entityId) {
        COLOURS.remove(entityId);
    }

    static void clearAll() {
        COLOURS.clear();
    }

    static java.util.Set<Integer> ids() {
        return COLOURS.keySet();
    }
}
