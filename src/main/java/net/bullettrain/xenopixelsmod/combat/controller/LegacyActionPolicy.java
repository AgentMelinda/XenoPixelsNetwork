package net.bullettrain.xenopixelsmod.combat.controller;

import net.bullettrain.xenopixelsmod.network.Bt3CombatPacket;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which {@link Bt3CombatPacket.Action}s the legacy controller owns outright.
 *
 * <p>In {@link CombatControllerMode#LEGACY} everything is accepted, unchanged. In
 * {@link CombatControllerMode#BT3_MANUAL} the auto mash string and its cinematic-rush branch are
 * replaced by the manual controller, so the legacy packets that drive them are refused; the
 * remaining actions (guard, vanish, chase, Zanzoken, Multi-Form, Hakai, ultimates...) are shared
 * interrupt/cancel services and stay reachable from both controllers.
 */
public final class LegacyActionPolicy {

    private static final Set<Bt3CombatPacket.Action> LEGACY_OWNED = EnumSet.of(
            Bt3CombatPacket.Action.COMBO_HIT,
            Bt3CombatPacket.Action.CINEMATIC_RUSH);

    private LegacyActionPolicy() {}

    public static boolean allowed(Bt3CombatPacket.Action action, CombatControllerMode mode) {
        CombatControllerMode resolved = mode == null ? CombatControllerMode.DEFAULT : mode;
        if (resolved == CombatControllerMode.LEGACY) return true;
        return action == null || !LEGACY_OWNED.contains(action);
    }

    /** Client-side mirror: whether the legacy mash string may be sent under {@code mode}. */
    public static boolean legacyComboAllowed(CombatControllerMode mode) {
        return allowed(Bt3CombatPacket.Action.COMBO_HIT, mode);
    }
}
