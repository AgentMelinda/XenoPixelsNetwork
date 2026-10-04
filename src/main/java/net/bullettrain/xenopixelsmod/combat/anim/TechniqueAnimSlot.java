package net.bullettrain.xenopixelsmod.combat.anim;

import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;

/**
 * Named animation slots that are not combo intents. Studio BIND and scripts retarget these
 * without putting every pose on the mash packet enum.
 */
public enum TechniqueAnimSlot {
    HAKAI_HOLD(DmzAnimHelper.HAKAI_HOLD, true),
    HAKAI_FIRE(DmzAnimHelper.HAKAI_FIRE, false),
    TRANSFORM("", true),
    PUNCH("", false),
    CHARGE_PUNCH(DmzAnimHelper.CHARGE_LIGHT, true),
    CHARGE_PUNCH_FIRE(DmzAnimHelper.CHARGE_LIGHT_FIRE, false),
    CHARGE_KICK(DmzAnimHelper.CHARGE_HEAVY, true),
    CHARGE_KICK_FIRE(DmzAnimHelper.KICK_GUT_R, false),
    CHARGE_KI(DmzAnimHelper.KI_CHARGE, true);

    private final String defaultAnim;
    private final boolean hold;

    TechniqueAnimSlot(String defaultAnim, boolean hold) {
        this.defaultAnim = defaultAnim;
        this.hold = hold;
    }

    public String defaultAnim() {
        return defaultAnim;
    }

    /** True when the slot is a looping / last-frame hold rather than a one-shot fire. */
    public boolean hold() {
        return hold;
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
