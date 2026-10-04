package net.bullettrain.xenopixelsmod.client.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZanzokenFadeTest {

    @Test
    void inLifetimeCopyStillStands() {
        assertTrue(ZanzokenFade.imageStillStanding(0, 40, true, false));
        assertTrue(ZanzokenFade.imageStillStanding(39, 40, true, false));
    }

    @Test
    void expiredOrDeadCopyDoesNotStand() {
        assertFalse(ZanzokenFade.imageStillStanding(40, 40, true, false));
        assertFalse(ZanzokenFade.imageStillStanding(41, 40, true, false));
        assertFalse(ZanzokenFade.imageStillStanding(10, 40, false, false));
        assertFalse(ZanzokenFade.imageStillStanding(10, 40, true, true));
    }

    @Test
    void restoreReachesSolidAfterConfiguredTicks() {
        assertEquals(0.2f, ZanzokenFade.restoreAlpha(0.2f, ZanzokenFade.RESTORE_TICKS), 0.001f);
        assertEquals(0.6f, ZanzokenFade.restoreAlpha(0.2f, 5), 0.001f);
        assertEquals(1.0f, ZanzokenFade.restoreAlpha(0.2f, 0), 0.001f);
    }
}
