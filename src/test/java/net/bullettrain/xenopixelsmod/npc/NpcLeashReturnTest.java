package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-09-30 owner: "when he is returining to respawn location by foot he is flickering".
 *
 * <p>Measured (runtime probe, 1.21.1): after chasing a player past its leash, the leash cleared the
 * target and turned the NPC for home every 20 ticks, and the target goal re-acquired the player on
 * the very next tick. The NPC shuttled between x=15.5 and x=17.7 with its yaw snapping ~90 degrees
 * each second, never got home, and was teleported back when walking was judged stuck. As CustomNPCs'
 * Return To Start does, an NPC the leash is walking home is disengaged until it has arrived.
 */
class NpcLeashReturnTest {

    @Test
    void anNpcWalkingHomeTakesNoNewTarget() {
        assertTrue(XenoNpcBehaviour.refusesNewTarget(true, NpcMovementOwner.Claim.LEASH, true));
        assertFalse(XenoNpcBehaviour.refusesNewTarget(true, NpcMovementOwner.Claim.LEASH, false),
                "clearing the target is always allowed");
        assertFalse(XenoNpcBehaviour.refusesNewTarget(true, null, true), "not returning: fights as usual");
        assertFalse(XenoNpcBehaviour.refusesNewTarget(false, NpcMovementOwner.Claim.LEASH, true),
                "arbitration off keeps the previous behaviour for comparison");
    }

    @Test
    void theLeashHoldsUntilTheNpcIsActuallyHome() {
        double radius = 10.0;
        assertFalse(XenoNpcBehaviour.leashReleases(true, true, 9.0 * 9.0, radius),
                "back inside the radius is not home: it would turn straight round to the player");
        assertTrue(XenoNpcBehaviour.leashReleases(true, true, 1.0, radius));
        assertTrue(XenoNpcBehaviour.leashReleases(false, true, 9.0 * 9.0, radius),
                "arbitration off: the old release at the radius");
    }

    /**
     * Measured: with the target already refused, the combat brain still turned the NPC every tick
     * (yaw 0/180 flipping, ~0.002 blocks/tick) while the leash's path pointed home; with the brain
     * off for the return, the walk was a steady 0.146 blocks/tick at yaw 90 straight home.
     */
    @Test
    void theCombatBrainYieldsWhileTheLeashWalksTheNpcHome() {
        assertTrue(XenoNpcBehaviour.brainYieldsToLeash(true, NpcMovementOwner.Claim.LEASH));
        assertFalse(XenoNpcBehaviour.brainYieldsToLeash(true, null));
        assertFalse(XenoNpcBehaviour.brainYieldsToLeash(false, NpcMovementOwner.Claim.LEASH));
    }

    @Test
    void aStrayIsOnlyGrabbedPastTheRadiusPlusItsDeadBand() {
        double radius = 10.0;
        assertFalse(XenoNpcBehaviour.leashGrabs(11.0 * 11.0, radius));
        assertTrue(XenoNpcBehaviour.leashGrabs(12.0 * 12.0, radius));
    }
}
