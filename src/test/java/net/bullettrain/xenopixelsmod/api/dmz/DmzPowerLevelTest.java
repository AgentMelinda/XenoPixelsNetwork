package net.bullettrain.xenopixelsmod.api.dmz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 2026-09-29 owner: "a dmz power level for npcs even tho i can see in the ki sense they do get".
 * Every living entity carries DMZ's IBattlePower value (the one ki sense shows); an NPC is compared
 * by that, not by its max health.
 */
class DmzPowerLevelTest {
    private static final DmzAccess.PowerSource NONE = () -> 0.0;
    private static final DmzAccess.PowerSource BROKEN = () -> {
        throw new IllegalStateException("not loaded");
    };

    @Test
    void aPlayerUsesItsStats() {
        assertEquals(500.0, DmzAccess.pick(true, () -> 500.0, () -> 9.0, () -> 8.0, () -> 7.0), 1e-9);
    }

    @Test
    void anNpcUsesWhatKiSenseShowsFirst() {
        assertEquals(9.0, DmzAccess.pick(false, () -> 500.0, () -> 9.0, () -> 8.0, () -> 7.0), 1e-9,
                "a non-player never reads the player capability");
    }

    @Test
    void aZeroFallsThroughToNpcStatsThenDmzsMobFormula() {
        assertEquals(8.0, DmzAccess.pick(false, NONE, NONE, () -> 8.0, () -> 7.0), 1e-9);
        assertEquals(7.0, DmzAccess.pick(false, NONE, NONE, NONE, () -> 7.0), 1e-9);
        assertEquals(0.0, DmzAccess.pick(false, NONE, NONE, NONE, NONE), 1e-9);
    }

    @Test
    void aSourceThatThrowsIsSkipped() {
        assertEquals(7.0, DmzAccess.pick(false, BROKEN, BROKEN, BROKEN, () -> 7.0), 1e-9);
        assertEquals(9.0, DmzAccess.pick(true, BROKEN, () -> 9.0, NONE, NONE), 1e-9,
                "a player whose stats are not loaded yet still has the cached figure");
    }
}
