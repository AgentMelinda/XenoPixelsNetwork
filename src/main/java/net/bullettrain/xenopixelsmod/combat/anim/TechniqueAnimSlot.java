package net.bullettrain.xenopixelsmod.combat.anim;

import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;

/**
 * Technique clips that are not combo intents. Hakai hold/fire are named here so studio BIND
 * can retarget them without putting Hakai on the mash packet enum.
 */
public enum TechniqueAnimSlot {
    HAKAI_HOLD(DmzAnimHelper.HAKAI_HOLD),
    HAKAI_FIRE(DmzAnimHelper.HAKAI_FIRE);

    private final String defaultAnim;

    TechniqueAnimSlot(String defaultAnim) {
        this.defaultAnim = defaultAnim;
    }

    public String defaultAnim() {
        return defaultAnim;
    }

    public static TechniqueAnimSlot of(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        for (TechniqueAnimSlot slot : values()) {
            if (slot.name().equalsIgnoreCase(trimmed)) return slot;
        }
        return null;
    }
}
