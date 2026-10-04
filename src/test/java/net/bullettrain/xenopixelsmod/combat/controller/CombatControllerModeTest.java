package net.bullettrain.xenopixelsmod.combat.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The controller mode is the one switch that decides whether a server runs the pre-manual Xeno
 * combat or the new manual BT3 controller. Anything the server cannot read as an explicit opt-in
 * must fall back to legacy, because a typo in the config file must never silently turn on a
 * combat system the owner did not ask for.
 */
class CombatControllerModeTest {

    @Test
    void missingOrGarbageFallsBackToLegacy() {
        assertEquals(CombatControllerMode.LEGACY, CombatControllerMode.fromId(null));
        assertEquals(CombatControllerMode.LEGACY, CombatControllerMode.fromId(""));
        assertEquals(CombatControllerMode.LEGACY, CombatControllerMode.fromId("   "));
        assertEquals(CombatControllerMode.LEGACY, CombatControllerMode.fromId("manual"));
        assertEquals(CombatControllerMode.LEGACY, CombatControllerMode.fromId("bt3"));
        assertEquals(CombatControllerMode.LEGACY, CombatControllerMode.fromId("legacy"));
    }

    @Test
    void manualIsCaseAndWhitespaceInsensitive() {
        assertEquals(CombatControllerMode.BT3_MANUAL, CombatControllerMode.fromId("bt3_manual"));
        assertEquals(CombatControllerMode.BT3_MANUAL, CombatControllerMode.fromId(" BT3_Manual "));
        assertEquals(CombatControllerMode.BT3_MANUAL, CombatControllerMode.fromId("bt3-manual"));
    }

    @Test
    void idsRoundTripThroughFromId() {
        for (CombatControllerMode mode : CombatControllerMode.values()) {
            assertEquals(mode, CombatControllerMode.fromId(mode.id()));
        }
        assertEquals("legacy", CombatControllerMode.LEGACY.id());
        assertEquals("bt3_manual", CombatControllerMode.BT3_MANUAL.id());
        assertEquals(CombatControllerMode.LEGACY, CombatControllerMode.DEFAULT);
    }

    @Test
    void strictParseRejectsUnknownInsteadOfDefaulting() {
        // The command path must tell the operator "unknown mode", not quietly select legacy.
        assertEquals(CombatControllerMode.BT3_MANUAL, CombatControllerMode.parseStrict("bt3_manual"));
        assertEquals(CombatControllerMode.LEGACY, CombatControllerMode.parseStrict("Legacy"));
        assertNull(CombatControllerMode.parseStrict("manual"));
        assertNull(CombatControllerMode.parseStrict(null));
        assertNull(CombatControllerMode.parseStrict(""));
    }

    @Test
    void onlyARealChangeRequiresCombatStateCleanup() {
        assertTrue(CombatControllerMode.requiresCleanup(CombatControllerMode.LEGACY,
                CombatControllerMode.BT3_MANUAL));
        assertTrue(CombatControllerMode.requiresCleanup(CombatControllerMode.BT3_MANUAL,
                CombatControllerMode.LEGACY));
        assertFalse(CombatControllerMode.requiresCleanup(CombatControllerMode.LEGACY,
                CombatControllerMode.LEGACY));
        // Unknown previous state (first tick after boot) is treated as a change so stale state
        // from a save file written under the other mode is still swept.
        assertTrue(CombatControllerMode.requiresCleanup(null, CombatControllerMode.LEGACY));
        // A null "next" is a missing config, which is legacy.
        assertTrue(CombatControllerMode.requiresCleanup(CombatControllerMode.BT3_MANUAL, null));
        assertFalse(CombatControllerMode.requiresCleanup(CombatControllerMode.LEGACY, null));
    }

    @Test
    void switchDecisionTableCoversDriftAndNoOps() {
        CombatControllerMode L = CombatControllerMode.LEGACY;
        CombatControllerMode M = CombatControllerMode.BT3_MANUAL;
        // Nothing to do: config already says the mode and it has already been swept for.
        assertEquals(CombatControllerMode.SwitchOutcome.UNCHANGED, CombatControllerMode.decide(L, L, L));
        assertEquals(CombatControllerMode.SwitchOutcome.UNCHANGED, CombatControllerMode.decide(M, M, M));
        // A real switch.
        assertEquals(CombatControllerMode.SwitchOutcome.SWITCHED, CombatControllerMode.decide(L, L, M));
        assertEquals(CombatControllerMode.SwitchOutcome.SWITCHED, CombatControllerMode.decide(M, M, L));
        // Config drifted through another write path (/xenoset, reload) and has not been swept
        // yet: same mode on paper, but the sweep must still run and be reported as work done.
        assertEquals(CombatControllerMode.SwitchOutcome.SWEPT_SAME_MODE, CombatControllerMode.decide(M, L, M));
        assertEquals(CombatControllerMode.SwitchOutcome.SWEPT_SAME_MODE, CombatControllerMode.decide(L, null, L));
        // Null requested is legacy; null previous is the first observation after boot.
        assertEquals(CombatControllerMode.SwitchOutcome.SWITCHED, CombatControllerMode.decide(M, M, null));
        assertEquals(CombatControllerMode.SwitchOutcome.UNCHANGED, CombatControllerMode.decide(L, L, null));
        assertTrue(CombatControllerMode.SwitchOutcome.SWITCHED.workDone());
        assertTrue(CombatControllerMode.SwitchOutcome.SWEPT_SAME_MODE.workDone());
        assertFalse(CombatControllerMode.SwitchOutcome.UNCHANGED.workDone());
    }

    @Test
    void manualInputIsOnlyAcceptedInManualModeWithPermissionAndCombatOn() {
        assertEquals(ManualCombatGate.Decision.ACCEPT,
                ManualCombatGate.decide(CombatControllerMode.BT3_MANUAL, true, true));
        assertEquals(ManualCombatGate.Decision.REJECT_LEGACY_MODE,
                ManualCombatGate.decide(CombatControllerMode.LEGACY, true, true));
        assertEquals(ManualCombatGate.Decision.REJECT_COMBAT_DISABLED,
                ManualCombatGate.decide(CombatControllerMode.BT3_MANUAL, false, true));
        assertEquals(ManualCombatGate.Decision.REJECT_NO_PERMISSION,
                ManualCombatGate.decide(CombatControllerMode.BT3_MANUAL, true, false));
        // A null mode is a missing config, which is legacy.
        assertEquals(ManualCombatGate.Decision.REJECT_LEGACY_MODE,
                ManualCombatGate.decide(null, true, true));
        assertTrue(ManualCombatGate.Decision.ACCEPT.accepted());
        assertFalse(ManualCombatGate.Decision.REJECT_LEGACY_MODE.accepted());
    }
}
