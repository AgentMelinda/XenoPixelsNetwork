package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HakaiHoldPlaybackTest {

    @Test
    void holdIsNeverRetriggeredAfterStart() {
        assertFalse(DmzAnimHelper.shouldRetriggerHold(0));
        assertFalse(DmzAnimHelper.shouldRetriggerHold(1));
        assertFalse(DmzAnimHelper.shouldRetriggerHold(20));
        assertFalse(DmzAnimHelper.shouldRetriggerHold(40));
    }

    @Test
    void anyHoldUsesPlayAndHoldKi() {
        assertTrue(DmzAnimHelper.isShippedHakaiHold(DmzAnimHelper.HAKAI_HOLD));
        assertFalse(DmzAnimHelper.isShippedHakaiHold("combat.xeno_my_studio_hold"));
        assertTrue(DmzAnimHelper.useLoopingHakaiHold(DmzAnimHelper.HAKAI_HOLD, null));
        assertTrue(DmzAnimHelper.useLoopingHakaiHold(DmzAnimHelper.HAKAI_HOLD, "hakai_hold"));
        assertTrue(DmzAnimHelper.useLoopingHakaiHold("combat.xeno_my_studio_hold", "my_studio_hold"));
        assertFalse(DmzAnimHelper.useLoopingHakaiHold("", null));
        assertEquals(1, DmzAnimHelper.HAKAI_HOLD_KI_VARIANT);
    }
}
