package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZanzokenLookTest {

    @Test
    void lookingAwayDoesNotForceInwardFacing() {
        // Player at origin looking south (+Z). Clone to the east. Center also south.
        assertFalse(ZanzokenLook.faceCenter(0, 0, 0, 3, 0, 0, 3, 3));
    }

    @Test
    void lookingIntoTheRingTurnsTheRearCopyInward() {
        // Player at (0, 3) on the south slot, looking north into the ring (yaw 180).
        // Rear clone is further south at (0, 6). Center at origin.
        assertTrue(ZanzokenLook.faceCenter(180, 0, 3, 0, 6, 0, 0, 3));
    }

    @Test
    void yawTowardPointsAtTheCenter() {
        float yaw = ZanzokenLook.yawToward(3, 0, 0, 0, 0);
        assertEquals(90.0f, yaw, 0.01f);
    }
}
