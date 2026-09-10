package net.bullettrain.xenopixelsmod.compat.npc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves NPC-mod classes by name across CustomNPCs and its My NPCs fork.
 *
 * <p>My NPCs is CustomNPCs with its root package renamed: {@code noppes.npcs.*} became
 * {@code espi.mynpcs.*}, and of the 44 types this mod touches, 41 kept their names exactly. So a
 * touchpoint does not need to know which of the two is installed — it needs the class under whichever
 * root is present.
 *
 * <p>Several bridges here already carried both names in a {@code String[]} and looped over them; this
 * is the same idea in one place, so a new touchpoint cannot quietly support only one of the two the
 * way {@code NpcKiAim} and {@code NpcHairBridge} did.
 *
 * <p>Everything stays reflective because these classes must be absent without breaking us: this mod
 * compiles and loads with neither NPC mod installed.
 */
public final class NpcTypes {

    /**
     * Package roots, fork first.
     *
     * <p>Order matters only if both are installed, which is a misconfiguration — they are the same
     * mod twice. My NPCs wins because it is the one the integration is maintained against.
     */
    private static final String[] ROOTS = {"espi.mynpcs.", "noppes.npcs."};

    /** Resolved classes, and misses, so a lookup on a hot path costs a map read. */
    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();
    private static final Object MISSING = new Object();

    /** The NPC base class, by far the most-asked-for type. */
    public static final String NPC_INTERFACE = "entity.EntityNPCInterface";

    private NpcTypes() {
    }

    /**
     * Finds a class by its path below the NPC mod's root package.
     *
     * @param relative the name under the root, such as {@code "entity.EntityNPCInterface"}
     * @return the class, or null when no NPC mod provides it
     */
    public static Class<?> find(String relative) {
        if (relative == null || relative.isBlank()) return null;
        Object cached = CACHE.get(relative);
        if (cached != null) {
            return cached == MISSING ? null : (Class<?>) cached;
        }
        for (String root : ROOTS) {
            try {
                Class<?> found = Class.forName(root + relative);
                CACHE.put(relative, found);
                return found;
            } catch (Throwable ignored) {
                // Try the next root; absent is the normal case with neither mod installed.
            }
        }
        CACHE.put(relative, MISSING);
        return null;
    }

    /** The NPC base class, or null when no NPC mod is installed. */
    public static Class<?> npcInterface() {
        return find(NPC_INTERFACE);
    }

    /** Whether this entity is an NPC from either mod. */
    public static boolean isNpc(Object entity) {
        Class<?> type = npcInterface();
        return type != null && entity != null && type.isInstance(entity);
    }

    /** Both fully-qualified spellings of one relative name, for callers that need the strings. */
    public static String[] names(String relative) {
        return new String[]{ROOTS[0] + relative, ROOTS[1] + relative};
    }
}
