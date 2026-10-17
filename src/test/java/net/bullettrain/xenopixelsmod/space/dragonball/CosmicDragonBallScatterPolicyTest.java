package net.bullettrain.xenopixelsmod.space.dragonball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CosmicDragonBallScatterPolicyTest {
    @Test
    void adminScatterKeepsAnInventoryBallHeld() {
        assertEquals(DragonBallSavedData.Status.HELD,
                CosmicDragonBalls.statusAfterScatter(DragonBallSavedData.Status.HELD));
    }

    @Test
    void placedAndScatteredBallsBecomeScatteredAtTheNewTarget() {
        assertEquals(DragonBallSavedData.Status.SCATTERED,
                CosmicDragonBalls.statusAfterScatter(DragonBallSavedData.Status.PLACED));
        assertEquals(DragonBallSavedData.Status.SCATTERED,
                CosmicDragonBalls.statusAfterScatter(DragonBallSavedData.Status.SCATTERED));
    }

    @Test
    void explicitAdminRecoveryOnlyReleasesHeldStatus() {
        assertEquals(DragonBallSavedData.Status.SCATTERED,
                CosmicDragonBalls.statusAfterAdminRecovery(DragonBallSavedData.Status.HELD));
        assertEquals(DragonBallSavedData.Status.PLACED,
                CosmicDragonBalls.statusAfterAdminRecovery(DragonBallSavedData.Status.PLACED));
        assertEquals(DragonBallSavedData.Status.SCATTERED,
                CosmicDragonBalls.statusAfterAdminRecovery(DragonBallSavedData.Status.SCATTERED));
    }
}
