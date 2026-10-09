package net.bullettrain.xenopixelsmod.combat.anim;

import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.combat.v2.V2ChargeRules;

/**
 * Named animation slots that are not combo intents. Studio BIND and scripts retarget these
 * without putting every pose on the mash packet enum.
 */
public enum TechniqueAnimSlot {
    HAKAI_HOLD(DmzAnimHelper.HAKAI_HOLD, true),
    HAKAI_FIRE(DmzAnimHelper.HAKAI_FIRE, false),
    TRANSFORM("", true),
    PUNCH("", false),
    CHARGE_PUNCH(V2ChargeRules.PUNCH_HOLD, true),
    CHARGE_PUNCH_FIRE(V2ChargeRules.PUNCH_FIRE, false),
    CHARGE_KICK(V2ChargeRules.KICK_HOLD, true),
    CHARGE_KICK_FIRE(V2ChargeRules.KICK_FIRE, false),
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
