package net.bullettrain.xenopixelsmod.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a save reports about itself.
 *
 * <p>The server rolls back despite {@code /xenosave} reporting success, and the reflection into
 * LinearReader cannot be exercised here — that mod is not in this project's environment at all. So
 * the thing worth pinning is the reporting: a flush that wrote nothing and a flush that wrote the
 * world used to print the same sentence, which is precisely why nobody could say where the bytes
 * stopped.
 */
class WorldSaveFlushResultTest {

    /**
     * A LinearReader that does not match is a failure, not a quiet success.
     *
     * <p>It previously returned a null error, so {@code ok()} stayed true and the operator was told
     * the world had been saved while the region flush had not run at all.
     */
    @Test
    void anErrorMakesTheResultNotOk() {
        assertFalse(new WorldSaveFlush.Result(true, 0, "flush API did not match").ok());
        assertTrue(new WorldSaveFlush.Result(true, 0, null).ok());
        assertTrue(new WorldSaveFlush.Result(true, 0, "   ").ok(),
                "a blank error is the same as none; only a real message means failure");
    }

    /** No files found says so plainly rather than reporting a reassuring zero. */
    @Test
    void anEmptyDiskSurveySaysNothingWasFound() {
        assertEquals("no region files found on disk",
                new WorldSaveFlush.Result(true, 3, null, 0, 0L, 0).diskSummary());
    }

    /**
     * The summary carries the number to check against the disk after a rollback.
     *
     * <p>This is the whole point of the change: if those files still carry this timestamp after the
     * world reverts, the save landed and something replaced it — which is outside this mod.
     */
    @Test
    void theSummaryNamesTheFileCountAndTheNewestWrite() {
        String summary = new WorldSaveFlush.Result(true, 12, null, 418,
                java.time.Instant.parse("2026-09-16T14:02:11Z").toEpochMilli(), 3).diskSummary();
        assertTrue(summary.startsWith("418 region file(s)"), summary);
        assertTrue(summary.contains("newest written"), summary);
        assertTrue(summary.contains("3 dir(s) fsynced"), summary);
    }

    /** With fsync off the summary simply does not mention it, rather than claiming zero synced. */
    @Test
    void fsyncIsOnlyMentionedWhenItHappened() {
        String summary = new WorldSaveFlush.Result(true, 1, null, 10, 1_000L, 0).diskSummary();
        assertFalse(summary.contains("fsync"), summary);
        assertTrue(summary.contains("10 region file(s)"), summary);
    }

    /** An unreadable timestamp is reported as unknown, never as the epoch. */
    @Test
    void aMissingTimestampReadsAsUnknown() {
        String summary = new WorldSaveFlush.Result(true, 1, null, 5, 0L, 0).diskSummary();
        assertTrue(summary.contains("unknown"), summary);
    }

    /** The short constructor still works for the paths that have no disk survey. */
    @Test
    void theShortFormDefaultsToNoDiskEvidence() {
        WorldSaveFlush.Result result = new WorldSaveFlush.Result(false, 0, "no server");
        assertEquals(0, result.regionFiles());
        assertEquals(0L, result.newestMillis());
        assertEquals(0, result.synced());
        assertFalse(result.ok());
    }
}
