package net.bullettrain.xenopixelsmod.combat.controller;

import net.bullettrain.xenopixelsmod.network.Bt3CombatPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which {@link Bt3CombatPacket.Action}s the pre-manual controller owns. In {@code legacy} mode
 * every existing action keeps working byte-for-byte. In {@code bt3_manual} mode the mash combo
 * string and its cinematic-rush auto-branch belong to the manual controller, so the legacy
 * packets that drive them are refused; interrupt/cancel branches (guard, vanish, chase, Hakai,
 * Zanzoken, Multi-Form...) are shared services and stay reachable.
 */
class LegacyActionPolicyTest {

    @Test
    void legacyModeAcceptsEveryExistingAction() {
        for (Bt3CombatPacket.Action action : Bt3CombatPacket.Action.values()) {
            assertTrue(LegacyActionPolicy.allowed(action, CombatControllerMode.LEGACY),
                    action + " must keep working in legacy mode");
        }
    }

    @Test
    void manualModeRefusesTheLegacyComboString() {
        assertFalse(LegacyActionPolicy.allowed(Bt3CombatPacket.Action.COMBO_HIT,
                CombatControllerMode.BT3_MANUAL));
        assertFalse(LegacyActionPolicy.allowed(Bt3CombatPacket.Action.CINEMATIC_RUSH,
                CombatControllerMode.BT3_MANUAL));
    }

    @Test
    void manualModeKeepsSharedInterruptBranches() {
        for (Bt3CombatPacket.Action action : Bt3CombatPacket.Action.values()) {
            if (action == Bt3CombatPacket.Action.COMBO_HIT
                    || action == Bt3CombatPacket.Action.CINEMATIC_RUSH) {
                continue;
            }
            assertTrue(LegacyActionPolicy.allowed(action, CombatControllerMode.BT3_MANUAL),
                    action + " is a shared branch and must stay reachable in manual mode");
        }
    }

    @Test
    void nullModeIsLegacy() {
        assertTrue(LegacyActionPolicy.allowed(Bt3CombatPacket.Action.COMBO_HIT, null));
    }
}
