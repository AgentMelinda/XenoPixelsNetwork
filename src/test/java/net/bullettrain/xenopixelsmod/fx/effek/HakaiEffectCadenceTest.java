package net.bullettrain.xenopixelsmod.fx.effek;

import net.bullettrain.xenopixelsmod.combat.fx.HakaiFx;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-28 owner: the Hakai swirl only showed once the target had already faded. HakaiFx.tick is
 * called every 2nd channel tick, and the old pulse rule ((gameTime + id) % 10 == 0) never matched
 * when the game time's parity on those calls was wrong for that target - so the swirl never played
 * during the channel, only the erase at the end.
 */
class HakaiEffectCadenceTest {
    private static int pulses(HakaiFx.PulseClock clock, int target, long start, int calls) {
        int n = 0;
        for (int i = 0; i < calls; i++) if (clock.due(target, start + 2L * i)) n++;
        return n;
    }

    @Test
    void theSwirlStartsOnTheFirstChannelTickWhateverTheTiming() {
        for (long start = 0; start < 10; start++) {
            for (int target = 0; target < 4; target++) {
                HakaiFx.PulseClock clock = new HakaiFx.PulseClock();
                assertTrue(clock.due(target, start), "start " + start + " target " + target);
            }
        }
    }

    @Test
    void itPulsesEveryTenTicksWithCallsEverySecondTick() {
        for (long start : new long[] {0, 1, 7}) {
            HakaiFx.PulseClock clock = new HakaiFx.PulseClock();
            assertEquals(10, pulses(clock, 5, start, 50), "100 ticks of channel from " + start);
        }
    }

    @Test
    void targetsKeepTheirOwnClock() {
        HakaiFx.PulseClock clock = new HakaiFx.PulseClock();
        assertTrue(clock.due(1, 100));
        assertTrue(clock.due(2, 102), "a second Hakai starts its own swirl at once");
        assertFalse(clock.due(1, 104));
        clock.forget(1);
        assertTrue(clock.due(1, 106), "a new channel on the same target starts again");
    }
}
