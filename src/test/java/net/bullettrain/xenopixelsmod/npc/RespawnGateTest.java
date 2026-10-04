package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The respawn handler stops scanning every tick.
 *
 * <p>It used to prune and then walk up to {@code MAX_ENTRIES} entries twenty times a second, to
 * act on the few that were actually due. A cached earliest-due tick turns the common case into one
 * comparison.
 *
 * <p>Source-level, because {@code XenoNpcRespawnData} extends {@code SavedData} and needs a server
 * to construct. What is checkable here is the shape of the decision, which is where the bug would
 * be - and in particular the unloaded-chunk case, which naively defeats the whole gate.
 */
class RespawnGateTest {

    private static String code(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    void theHandlerReturnsBeforeDoingAnyWork() throws IOException {
        String handler = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcRespawnHandler.java");
        int gate = handler.indexOf("now < data.earliestDue()");
        int prune = handler.indexOf("data.prune(now)");
        assertTrue(gate >= 0, "the gate must exist");
        assertTrue(prune > gate, "and prune must sit behind it, not in front");
    }

    @Test
    void aDueEntryInAnUnloadedChunkDoesNotHoldTheGateOpenForever() throws IOException {
        // The trap. An entry only respawns when its chunk is loaded, so one in an empty corner of
        // the world is due indefinitely - and without deferring, the gate would pass every tick
        // and the scan would be exactly as expensive as before.
        String handler = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcRespawnHandler.java");
        assertTrue(handler.contains("blocked = true"), "an unconsumable entry is noticed");
        assertTrue(handler.contains("data.deferUntil("), "and the gate is held shut for a while");
    }

    @Test
    void deferringNeverDelaysAnEntryThatIsGenuinelySooner() throws IOException {
        // A fresh kill must not wait behind an unreachable one.
        String data = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcRespawnData.java");
        assertTrue(data.contains("if (tick > earliestDue)"),
                "deferUntil moves the gate later only");
    }

    @Test
    void schedulingMovesTheGateEarlierWithoutAScan() throws IOException {
        // An addition can only ever bring the soonest entry forward, which is one comparison.
        String data = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcRespawnData.java");
        assertTrue(data.contains("earliestDue = Math.min(earliestDue, entry.respawnTick())"));
    }

    @Test
    void everyRemovalPathRecomputesTheGate() throws IOException {
        // A removal may have taken the earliest entry with it. Missing one of these paths leaves
        // the gate in the past, which is silently just the old behaviour again.
        String data = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcRespawnData.java");
        int recomputes = data.split("recomputeEarliestDue\\(\\)", -1).length - 1;
        assertTrue(recomputes >= 5,
                "cancel, remove, prune and load all recompute, plus the method itself: "
                        + recomputes);
    }

    @Test
    void theSaveFormatIsUntouched() throws IOException {
        // The gate is derived and rebuilt on load. Sorting the list or persisting the gate would
        // have changed the format for no gain.
        String data = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcRespawnData.java");
        assertTrue(data.contains("result.recomputeEarliestDue()"), "rebuilt on load");
        assertTrue(!data.contains("putLong(\"EarliestDue\""), "and never written");
    }
}
