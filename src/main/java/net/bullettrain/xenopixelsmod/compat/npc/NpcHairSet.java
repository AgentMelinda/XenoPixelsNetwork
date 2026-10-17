package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one answer to "which four hair slots does this look wear", resolved from DragonMineZ's own
 * codec so server and client cannot disagree about it.
 *
 * <p>{@code styleId} 0 means the NPC's custom hair code; anything else is one of DMZ's
 * character-creation presets, resolved exactly as DMZ resolves it for a real player. An id past the
 * end of the preset list falls back to the code rather than throwing, so a profile written against a
 * larger preset set still works.
 *
 * <p>Lives here rather than in {@code client.compat.npc.NpcHairVis} because writing an NPC's look
 * onto a player happens on the server thread, and client classes must stay off that path. The
 * renderer calls through {@code NpcHairVis}, which now only delegates.
 */
public final class NpcHairSet {
    /** Remembered failure, so a garbage code is not re-parsed every frame. */
    private static final CustomHair[] INVALID = new CustomHair[0];
    private static final Map<String, CustomHair[]> CACHE = new ConcurrentHashMap<>();

    private NpcHairSet() {}

    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * @return four slots (base, SSJ, SSJ2, SSJ3), or null when there is nothing to resolve
     */
    public static CustomHair[] resolve(String code, String color, int styleId) {
        if (styleId > 0 && styleId <= presetCount()) {
            String tint = color == null ? "" : color;
            CustomHair base = HairManager.getPresetHair(styleId, tint);
            if (base != null) {
                return new CustomHair[]{
                        base,
                        orElse(HairManager.getPresetHairSSJ(styleId, tint), base),
                        orElse(HairManager.getPresetHairSSJ2(styleId, tint), base),
                        orElse(HairManager.getPresetHairSSJ3(styleId, tint), base)
                };
            }
        }
        return parse(code);
    }

    /** The same four slots from a stored code alone, without a preset id. */
    public static CustomHair[] parse(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String key = code.trim();
        CustomHair[] cached = CACHE.get(key);
        if (cached != null) {
            return cached.length == 0 ? null : cached;
        }
        CustomHair[] parsed = null;
        try {
            if (HairManager.isFullSetCode(key)) {
                if (key.startsWith("DMZF4:") || key.length() >= 64) {
                    parsed = HairManager.fromFullSetCode(key);
                }
            } else {
                CustomHair base = HairManager.fromCode(key);
                parsed = base == null ? null : new CustomHair[]{base, base, base, base};
            }
            if (parsed != null && (parsed.length == 0 || parsed[0] == null)) {
                parsed = null;
            }
        } catch (Exception ignored) {
            parsed = null;
        }
        CACHE.put(key, parsed == null ? INVALID : parsed);
        return parsed;
    }

    /**
     * Slot {@code index} of a resolved set, deep-copied so the caller owns it. An unresolvable set
     * becomes DMZ's empty hair rather than null, which is what clears a slot on a target.
     */
    public static CustomHair slot(CustomHair[] hair, int index) {
        if (hair == null || hair.length == 0 || hair[0] == null) return new CustomHair();
        CustomHair selected = index < hair.length && hair[index] != null ? hair[index] : hair[0];
        return selected == null ? new CustomHair() : selected.copy();
    }

    /** How many presets DMZ registered; 0 when its hair system is unavailable. */
    public static int presetCount() {
        try {
            return Math.max(0, HairManager.getPresetCount());
        } catch (Throwable unavailable) {
            return 0;
        }
    }

    /**
     * Whether {@code code} is a hair code DMZ can actually read. An empty code is valid and means
     * "no custom hair", which is what {@code NpcHairBridge.setCode} stores as a clear.
     */
    public static boolean validCode(String code) {
        String value = code == null ? "" : code.trim();
        if (value.isEmpty()) return true;
        try {
            return value.isEmpty() ? false
                    : HairManager.isFullSetCode(value) ? HairManager.fromFullSetCode(value) != null
                    : HairManager.fromCode(value) != null;
        } catch (RuntimeException garbage) {
            return false;
        }
    }

    private static CustomHair orElse(CustomHair value, CustomHair fallback) {
        return value == null ? fallback : value;
    }
}
