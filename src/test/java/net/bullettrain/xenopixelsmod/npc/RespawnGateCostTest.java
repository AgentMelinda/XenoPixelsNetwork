package net.bullettrain.xenopixelsmod.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the earliest-due gate actually costs, measured rather than assumed.
 *
 * <p>The plan for this change recorded the per-tick scan as <em>a code reading, not a profile</em>,
 * and asked for a number before any speed-up was claimed. This is that number. It is a
 * microbenchmark of the two code paths on the real {@link XenoNpcRespawnData}, not a server tick
 * profile: it says what the respawn handler's own work costs per tick with a full entry set and
 * nothing due, which is the situation the gate exists for. It says nothing about MSPT, because
 * nothing here has been run on a server.
 *
 * <p>Measured 2026-09-23, 4096 entries, none due: <b>39.3 us/tick before, 0.0455 us/tick
 * after</b> - roughly 860x, but the honest reading is the absolute number, not the ratio. 39.3 us
 * is 0.08% of a 50 ms tick budget, so this was never a visible TPS problem and fixing it is not a
 * visible TPS win. What it removes is a cost that scaled with a set nothing was waiting on, paid
 * twenty times a second forever. Most of the 39.3 us is the {@code List.copyOf} in
 * {@link XenoNpcRespawnData#entries()}, not the comparison - the walk allocated a 4096-element
 * copy every tick.
 *
 * <p>Nothing about timing is asserted - a wall-clock assertion on a CI box is a flaky test. What
 * <em>is</em> asserted is the part that would be a bug: that the cheap path reaches the same
 * decision as the full walk, on every tick of a long run.
 */
class RespawnGateCostTest {

    private static final int ENTRIES = XenoNpcRespawnData.MAX_ENTRIES;
    private static final long NOW = 1_000_000L;

    /** A full set, none of it due for a long while - the common case, by a wide margin. */
    private static XenoNpcRespawnData full() {
        XenoNpcRespawnData data = new XenoNpcRespawnData();
        for (int i = 0; i < ENTRIES; i++) {
            data.schedule(new XenoNpcRespawnData.Entry(UUID.randomUUID(),
                    ResourceLocation.parse("minecraft:overworld"), i, 64, i, 0f, 0f,
                    NOW + 20_000L + i, XenoNpcRole.GUARD, NOW, new CompoundTag()));
        }
        return data;
    }

    /** What the handler did every tick before the gate: prune, copy the list, walk it. */
    private static int oldPath(XenoNpcRespawnData data, long now) {
        data.prune(now);
        int due = 0;
        for (XenoNpcRespawnData.Entry entry : data.entries()) {
            if (now < entry.respawnTick()) continue;
            due++;
        }
        return due;
    }

    /** What it does now. */
    private static boolean newPath(XenoNpcRespawnData data, long now) {
        return now >= data.earliestDue();
    }

    @Test
    void theGateReachesTheSameDecisionAsTheFullWalk() {
        XenoNpcRespawnData data = full();
        // A full in-game day of ticks, across the point where the earliest entry comes due.
        for (long now = NOW; now < NOW + 24_000L; now += 7L) {
            assertEquals(oldPath(data, now) > 0, newPath(data, now),
                    "the gate disagreed with the walk at tick " + now);
        }
    }

    @Test
    void measureAndReportBothPaths() {
        XenoNpcRespawnData data = full();
        long now = NOW;

        // Warm up, or the first path measured pays for JIT and looks slower for no reason.
        for (int i = 0; i < 2_000; i++) {
            oldPath(data, now);
            newPath(data, now);
        }

        int ticks = 20_000;
        long oldStart = System.nanoTime();
        long sink = 0;
        for (int i = 0; i < ticks; i++) {
            sink += oldPath(data, now);
        }
        long oldNanos = System.nanoTime() - oldStart;

        long newStart = System.nanoTime();
        for (int i = 0; i < ticks; i++) {
            sink += newPath(data, now) ? 1 : 0;
        }
        long newNanos = System.nanoTime() - newStart;

        System.out.printf(
                "respawn per-tick cost, %d entries, none due: before %.1f us/tick, "
                        + "after %.4f us/tick (%d ticks each, sink=%d)%n",
                ENTRIES, oldNanos / 1000.0 / ticks, newNanos / 1000.0 / ticks, ticks, sink);

        assertTrue(oldNanos > 0 && newNanos > 0, "both paths ran");
    }
}
