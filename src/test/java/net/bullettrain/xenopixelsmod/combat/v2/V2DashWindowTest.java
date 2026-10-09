package net.bullettrain.xenopixelsmod.combat.v2;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class V2DashWindowTest {
    @Test void onlyDragonDashTravelOffersTheActiveTarget() {
        var fighter = new V2Fighter(UUID.randomUUID());
        fighter.travelTargetId = 42;
        fighter.travelStartTick = 100;
        fighter.travelTimeoutTicks = 40;
        fighter.travelKind = V2Fighter.TravelKind.CHASE;
        assertEquals(-1, fighter.dashWindowTarget(110));
        fighter.travelKind = V2Fighter.TravelKind.DRAGON_DASH;
        fighter.dashReadyTick = 160;
        assertEquals(42, fighter.dashWindowTarget(110));
        assertEquals(140, fighter.dashWindowEnd(110));
        assertEquals(-1, fighter.dashWindowTarget(140));
    }

    @Test void postHitWindowExpiresAndCancellationClearsItDespiteLaunchCooldown() {
        var fighter = new V2Fighter(UUID.randomUUID());
        fighter.dashFollowTargetId = 42;
        fighter.dashFollowUntilTick = 124;
        fighter.dashReadyTick = 160;
        assertEquals(42, fighter.dashWindowTarget(123));
        assertFalse(fighter.idle(123));
        assertEquals(-1, fighter.dashWindowTarget(124));
        fighter.clearDashFollow();
        assertEquals(-1, fighter.dashWindowTarget(110));
        assertTrue(fighter.idle(110));
    }

    @Test void followupConfigurationCannotCreateAnUnboundedWindow() {
        var cfg = new V2Config.Values();
        cfg.dragonDashFollowupTicks = Integer.MAX_VALUE;
        assertEquals(100, cfg.clamped().dragonDashFollowupTicks);
        cfg.dragonDashFollowupTicks = -1;
        assertEquals(0, cfg.clamped().dragonDashFollowupTicks);
    }
}
