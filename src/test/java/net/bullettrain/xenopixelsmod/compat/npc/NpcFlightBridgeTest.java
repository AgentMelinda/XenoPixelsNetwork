package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The periodic sync must not re-push the flying navigator onto an NPC whose combat
 * target stands on the ground. This is the "player pressed F" emulation that stops
 * the land/take-off flip-flop between NpcCounterpartSync and the combat brains.
 */
class NpcFlightBridgeTest {

    @Test
    void skillOffNeverFlies() {
        assertFalse(NpcFlightBridge.wantsFlight(false, false, false));
        assertFalse(NpcFlightBridge.wantsFlight(false, true, false));
        assertFalse(NpcFlightBridge.wantsFlight(false, true, true));
    }

    @Test
    void idleNpcWalksEvenWithTheSkill() {
        // No live target: the fly skill only permits flight; an idle NPC walks (todolist #1).
        assertFalse(NpcFlightBridge.wantsFlight(true, false, false));
    }

    @Test
    void engagedAgainstAirborneTargetFlies() {
        assertTrue(NpcFlightBridge.wantsFlight(true, true, false));
    }

    @Test
    void engagedAgainstGroundedTargetStaysDown() {
        // The reported bug: target lands (or switches to a walking mob) and the NPC
        // must ground instead of bobbing back onto the flying navigator.
        assertFalse(NpcFlightBridge.wantsFlight(true, true, true));
    }

    @Test
    void noTargetMeansUnengaged() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.flySkillOn = true;
        assertFalse(NpcFlightBridge.wantsFlightFor(profile, null),
                "no target means no reason to take off");
        profile.flySkillOn = false;
        assertFalse(NpcFlightBridge.wantsFlightFor(profile, null));
    }

    @Test
    void canUseFlightOffKeepsTheGroundNavigator() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.flySkillOn = true;
        profile.canUseFlight = false;
        assertFalse(NpcFlightBridge.wantsFlightFor(profile, null),
                "Can Use Flight off must win over the fly skill");
    }

    @Test
    void aHoppingTargetDoesNotFlipTheNavigatorEveryCall() {
        int[] pending = new int[] {1, 0};
        // First decision ever applies at once.
        assertTrue(NpcFlightBridge.debounce(null, true, pending, 3));
        // Target touches the ground for one call, then is airborne again: no change.
        assertTrue(NpcFlightBridge.debounce(true, false, pending, 3));
        assertTrue(NpcFlightBridge.debounce(true, true, pending, 3));
        // Grounded for three calls in a row: now it lands.
        assertTrue(NpcFlightBridge.debounce(true, false, pending, 3));
        assertTrue(NpcFlightBridge.debounce(true, false, pending, 3));
        assertFalse(NpcFlightBridge.debounce(true, false, pending, 3));
    }
}
