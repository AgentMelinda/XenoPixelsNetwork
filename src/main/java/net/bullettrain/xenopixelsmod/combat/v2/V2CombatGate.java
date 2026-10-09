package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.combat.controller.CombatControllerMode;

/**
 * The server-side admission rule for v2 input. Every v2 packet handler asks this before touching
 * state, so a client that keeps sending v2 input after the owner switched back to {@code legacy}
 * (or that never had permission) is refused for a named reason.
 *
 * <p>Two rules, because one part of v2 is not v2's alone. Under the v2 controller every input is
 * considered ({@link #decide}). Under the legacy and manual controllers exactly one is: the grab,
 * with its throw and its break-out, which those controllers run as well ({@link #decideGrabOnly}).
 */
public final class V2CombatGate {

    public enum Decision {
        ACCEPT,
        REJECT_NOT_V2,
        REJECT_COMBAT_DISABLED,
        REJECT_NO_PERMISSION,
        REJECT_NOT_ABLE;

        public boolean accepted() {
            return this == ACCEPT;
        }
    }

    private V2CombatGate() {}

    /**
     * @param able alive, not a spectator and not seated in a pilot seat
     */
    public static Decision decide(CombatControllerMode mode, boolean bt3CombatEnabled,
                                  boolean hasPermission, boolean able) {
        CombatControllerMode resolved = mode == null ? CombatControllerMode.DEFAULT : mode;
        if (resolved != CombatControllerMode.V2) return Decision.REJECT_NOT_V2;
        if (!bt3CombatEnabled) return Decision.REJECT_COMBAT_DISABLED;
        if (!hasPermission) return Decision.REJECT_NO_PERMISSION;
        if (!able) return Decision.REJECT_NOT_ABLE;
        return Decision.ACCEPT;
    }

    /**
     * Admission under a controller other than v2, where the grab is the only v2 input there is.
     * Anything else is refused as "not v2", exactly as it was before the grab was shared.
     *
     * @param grabOutsideV2 the server setting that lets the other controllers grab at all
     * @param hasPermission the grab's own permission; v2's does not apply outside v2
     * @param able          alive, not a spectator and not seated in a pilot seat
     */
    public static Decision decideGrabOnly(V2Input input, boolean bt3CombatEnabled, boolean grabOutsideV2,
                                          boolean hasPermission, boolean able) {
        if (input != V2Input.GRAB || !grabOutsideV2) return Decision.REJECT_NOT_V2;
        if (!bt3CombatEnabled) return Decision.REJECT_COMBAT_DISABLED;
        if (!hasPermission) return Decision.REJECT_NO_PERMISSION;
        if (!able) return Decision.REJECT_NOT_ABLE;
        return Decision.ACCEPT;
    }
}
