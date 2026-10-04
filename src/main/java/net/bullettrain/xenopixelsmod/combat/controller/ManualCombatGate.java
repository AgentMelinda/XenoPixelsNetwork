package net.bullettrain.xenopixelsmod.combat.controller;

/**
 * The server-side admission rule for manual BT3 input. Every manual packet handler must ask this
 * before touching state so a client that keeps sending manual input after the owner switched
 * back to {@code legacy} (or that never had permission) is refused for a named reason.
 */
public final class ManualCombatGate {

    public enum Decision {
        ACCEPT,
        REJECT_LEGACY_MODE,
        REJECT_COMBAT_DISABLED,
        REJECT_NO_PERMISSION;

        public boolean accepted() {
            return this == ACCEPT;
        }
    }

    private ManualCombatGate() {}

    public static Decision decide(CombatControllerMode mode, boolean bt3CombatEnabled,
                                  boolean hasManualUsePermission) {
        CombatControllerMode resolved = mode == null ? CombatControllerMode.DEFAULT : mode;
        if (resolved != CombatControllerMode.BT3_MANUAL) return Decision.REJECT_LEGACY_MODE;
        if (!bt3CombatEnabled) return Decision.REJECT_COMBAT_DISABLED;
        if (!hasManualUsePermission) return Decision.REJECT_NO_PERMISSION;
        return Decision.ACCEPT;
    }
}
