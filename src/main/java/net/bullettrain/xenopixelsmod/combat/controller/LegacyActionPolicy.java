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
 * interrupt/cancel services and stay reachable from both controllers. {@link CombatControllerMode#V2}
 * refuses the larger set of moves v2 has rewritten.
 */
public final class LegacyActionPolicy {

    private static final Set<Bt3CombatPacket.Action> LEGACY_OWNED = EnumSet.of(
            Bt3CombatPacket.Action.COMBO_HIT,
            Bt3CombatPacket.Action.CINEMATIC_RUSH);

    /**
     * What v2 replaces outright. Guard, vanish, Zanzoken, Multi-Form, Hakai, ultimates, Sparking
     * and the ki-blast cancel are not rewritten yet, so v2 input still reaches them through the
     * legacy packet.
     */
    private static final Set<Bt3CombatPacket.Action> V2_OWNED = EnumSet.of(
            Bt3CombatPacket.Action.COMBO_HIT,
            Bt3CombatPacket.Action.CINEMATIC_RUSH,
            Bt3CombatPacket.Action.CHARGE_FIST,
            Bt3CombatPacket.Action.CHARGE_KICK,
            Bt3CombatPacket.Action.DRAGON_DASH,
            Bt3CombatPacket.Action.CHASE_DASH,
            Bt3CombatPacket.Action.CHASE_STOP,
            Bt3CombatPacket.Action.Z_BURST,
            Bt3CombatPacket.Action.RUSH_CHAIN,
            Bt3CombatPacket.Action.SONIC_SWAY,
            Bt3CombatPacket.Action.SUPER_COUNTER);

    private LegacyActionPolicy() {}

    private static final Set<Bt3CombatPacket.Action> V3_OWNED = EnumSet.of(
            Bt3CombatPacket.Action.COMBO_HIT, Bt3CombatPacket.Action.CINEMATIC_RUSH,
            Bt3CombatPacket.Action.CHARGE_FIST, Bt3CombatPacket.Action.CHARGE_KICK,
            Bt3CombatPacket.Action.DRAGON_DASH, Bt3CombatPacket.Action.CHASE_DASH,
            Bt3CombatPacket.Action.CHASE_STOP, Bt3CombatPacket.Action.Z_BURST,
            Bt3CombatPacket.Action.RUSH_CHAIN, Bt3CombatPacket.Action.SONIC_SWAY,
            Bt3CombatPacket.Action.SUPER_COUNTER, Bt3CombatPacket.Action.VANISH,
            Bt3CombatPacket.Action.BACKSTEP, Bt3CombatPacket.Action.RUSH_COMBO,
            Bt3CombatPacket.Action.LIFT_COMBO);

    public static boolean allowed(Bt3CombatPacket.Action action, CombatControllerMode mode) {
        CombatControllerMode resolved = mode == null ? CombatControllerMode.DEFAULT : mode;
        if (resolved == CombatControllerMode.LEGACY) return true;
        if (action == null) return true;
        if (resolved == CombatControllerMode.V3) return !V3_OWNED.contains(action);
        if (resolved == CombatControllerMode.V2) return !V2_OWNED.contains(action);
        return !LEGACY_OWNED.contains(action);
    }

    /** Client-side mirror: whether the legacy mash string may be sent under {@code mode}. */
    public static boolean legacyComboAllowed(CombatControllerMode mode) {
        return allowed(Bt3CombatPacket.Action.COMBO_HIT, mode);
    }
}
