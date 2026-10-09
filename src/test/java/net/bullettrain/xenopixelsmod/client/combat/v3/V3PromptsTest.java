package net.bullettrain.xenopixelsmod.client.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import net.bullettrain.xenopixelsmod.client.combat.v2.PromptPlate;
import net.bullettrain.xenopixelsmod.client.combat.v3.V3Prompts.Slot;
import net.bullettrain.xenopixelsmod.combat.v3.V3State;
import net.bullettrain.xenopixelsmod.combat.v3.V3Window;
import org.junit.jupiter.api.Test;

class V3PromptsTest {
    private static V3Prompts.Row idle(boolean locked, V3Window window, int left, int total) {
        return V3Prompts.row(locked, V3State.IDLE, 0, left, total, window);
    }

    @Test void nothingIsShownWithoutAnApprovedLock() {
        assertTrue(idle(false, V3Window.NONE, 0, 0).prompts().isEmpty());
        assertTrue(V3Prompts.row(false, V3State.TRAVEL, 0, 10, 24, V3Window.DASH_CROSS).prompts().isEmpty());
    }

    @Test void lockedAndIdleShowsAttackHeavyDashAndGrab() {
        var row = idle(true, V3Window.NONE, 0, 0);
        assertEquals(4, row.prompts().size());
        assertEquals(Slot.LIGHT, row.prompts().get(0).slot());
        assertEquals(PromptPlate.PUNCH, row.prompts().get(0).plate());
        assertEquals(Slot.HEAVY, row.prompts().get(1).slot());
        assertEquals(PromptPlate.FINISH, row.prompts().get(1).plate());
        assertEquals(Slot.DASH, row.prompts().get(2).slot());
        assertEquals(PromptPlate.TRAVEL, row.prompts().get(2).plate());
        assertEquals(Slot.GRAB, row.prompts().get(3).slot());
        assertEquals(PromptPlate.GRAB, row.prompts().get(3).plate());
        assertEquals(0f, row.fraction());
    }

    @Test void chargingShowsOnlyTheReleaseOnTheButtonThatStartedIt() {
        var punch = V3Prompts.row(true, V3State.CHARGING_PUNCH, 10, 0, 0, V3Window.NONE);
        assertEquals(1, punch.prompts().size());
        assertEquals(Slot.LIGHT, punch.prompts().get(0).slot());
        assertEquals(0.5f, punch.fraction(), 1e-6);
        var kick = V3Prompts.row(true, V3State.CHARGING_KICK, 20, 0, 0, V3Window.NONE);
        assertEquals(Slot.HEAVY, kick.prompts().get(0).slot());
        assertEquals(1f, kick.fraction(), 1e-6);
        // A charge that has only just started still shows a sliver, so the bar is visible.
        assertTrue(V3Prompts.row(true, V3State.CHARGING_PUNCH, 0, 0, 0, V3Window.NONE).fraction() > 0f);
    }

    @Test void flyingAndTheContinuationWindowOfferTheCross() {
        var flying = V3Prompts.row(true, V3State.TRAVEL, 0, 0, 0, V3Window.NONE);
        assertEquals(1, flying.prompts().size());
        assertEquals(Slot.DASH, flying.prompts().get(0).slot());
        assertEquals(0f, flying.fraction());

        var window = idle(true, V3Window.DASH_CROSS, 12, 24);
        assertEquals(Slot.DASH, window.prompts().get(0).slot());
        // Follow presses reposition behind the target; the manual punches and heavy stay beside it.
        assertEquals("Behind", window.prompts().get(0).label());
        assertEquals(0.5f, window.fraction(), 1e-6);
        // The ordinary strikes stay available beside the reposition.
        assertEquals(3, window.prompts().size());
    }

    @Test void chaseAndCounterWindowsAreSingleUrgentPrompts() {
        var chase = idle(true, V3Window.CHASE, 20, 40);
        assertEquals(1, chase.prompts().size());
        assertEquals(Slot.CHASE, chase.prompts().get(0).slot());
        assertEquals(PromptPlate.TRAVEL, chase.prompts().get(0).plate());
        assertEquals(0.5f, chase.fraction(), 1e-6);

        var counter = idle(true, V3Window.COUNTER, 6, 12);
        assertEquals(1, counter.prompts().size());
        assertEquals(Slot.VANISH, counter.prompts().get(0).slot());
        assertEquals(PromptPlate.ALERT, counter.prompts().get(0).plate());
    }

    @Test void grabPromptsDoNotNeedALock() {
        var held = V3Prompts.row(false, V3State.GRABBED, 0, 5, 10, V3Window.GRAB);
        assertEquals(1, held.prompts().size());
        assertEquals(PromptPlate.ALERT, held.prompts().get(0).plate());
        assertEquals(Slot.LIGHT, held.prompts().get(0).slot());
        assertEquals(0.5f, held.fraction(), 1e-6);
        // Past the break-out window the victim is told, without a key that would do nothing.
        var late = V3Prompts.row(false, V3State.GRABBED, 0, 0, 0, V3Window.NONE);
        assertEquals(Slot.NONE, late.prompts().get(0).slot());

        var holding = V3Prompts.row(false, V3State.GRAB_HOLD, 0, 8, 16, V3Window.GRAB);
        assertEquals(PromptPlate.GRAB, holding.prompts().get(0).plate());
        assertEquals("Throw", holding.prompts().get(0).label());
    }

    @Test void statesWithNoPlayerChoiceShowNothing() {
        assertTrue(V3Prompts.row(true, V3State.CINEMATIC, 0, 0, 0, V3Window.NONE).prompts().isEmpty());
        assertTrue(V3Prompts.row(true, V3State.STRIKE, 0, 0, 0, V3Window.NONE).prompts().isEmpty());
        assertTrue(V3Prompts.row(true, null, 0, 0, 0, V3Window.NONE).prompts().isEmpty());
    }
}
