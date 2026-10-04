package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every place quest state changes must tell the client, or the log silently goes stale.
 *
 * <p>Source-level assertions: sending a packet needs a server and a connection, and a unit test
 * has neither. What is checkable here is that no mutation site was left without a push - which is
 * the actual failure mode, and one that produces a screen that looks correct and is wrong.
 */
class QuestSyncTest {

    private static String code(String dir, String file) throws IOException {
        String raw = Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8);
        // Comments are stripped before searching: a test asserting "this call exists" must not be
        // satisfied by a comment that merely mentions it.
        return raw.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static String progression(String file) throws IOException {
        return code("src/main/java/net/bullettrain/xenopixelsmod/features/progression", file);
    }

    @Test
    void theSyncFiresOnJoinAndAfterReload() throws IOException {
        // OnDatapackSyncEvent is the right hook rather than a login listener: it fires on join AND
        // after every /reload, so an operator who edits a quest file sees it without rejoining.
        String sync = progression("QuestSync.java");
        assertTrue(sync.contains("OnDatapackSyncEvent"), "join and /reload come through one event");
        assertTrue(sync.contains("public static void push(ServerPlayer player)"),
                "and a direct push for state changes between those moments");
    }

    @Test
    void aReloadWithNoJoiningPlayerReachesEverybody() throws IOException {
        // getPlayer() is null for a /reload. Returning early on null would mean a reload updated
        // nobody - which is exactly the case an operator is exercising when they run it.
        assertTrue(progression("QuestSync.java").contains("getPlayerList()"),
                "a null joining player must fan out to the online players");
    }

    @Test
    void completingAQuestPushesTheNewLog() throws IOException {
        // Without this the finished quest stays on the player's screen until they relog.
        String events = progression("ProgressionEvents.java");
        int complete = events.indexOf("public static void completeQuest(");
        assertTrue(complete >= 0);
        assertTrue(events.indexOf("QuestSync.push(", complete) >= 0,
                "completeQuest should tell the client");
    }

    @Test
    void startingAQuestPushesTheNewLog() throws IOException {
        // Otherwise the New Quest toast has nothing to fire on and the log stays empty.
        assertTrue(progression("ParallelQuests.java").contains("QuestSync.push("),
                "start should tell the client");
    }

    @Test
    void markingReadyPushesTheNewLog() throws IOException {
        // "Ready - complete with Stephanie" is the whole point of NPC mode; if the client is not
        // told, the log shows a full bar that has not paid out, which reads as a bug.
        String events = progression("ProgressionEvents.java");
        int finish = events.indexOf("private static void finish(");
        assertTrue(finish >= 0);
        int nextMethod = events.indexOf("public static boolean tryHandIn(", finish);
        assertTrue(nextMethod > finish);
        int push = events.indexOf("QuestSync.push(", finish);
        assertTrue(push >= 0 && push < nextMethod,
                "finish() should push before the next method begins");
    }

    @Test
    void abandoningAQuestPushesTheNewLog() throws IOException {
        // Otherwise an aborted quest stays visible and looks abortable again.
        String commands = code("src/main/java/net/bullettrain/xenopixelsmod/command",
                "ProgressionCommands.java");
        int abort = commands.indexOf("questAbort(CommandSourceStack src, String id)");
        assertTrue(abort >= 0);
        assertTrue(commands.indexOf("QuestSync.push(", abort) >= 0,
                "abort should tell the client");
    }

    @Test
    void theClientForgetsOnDisconnect() throws IOException {
        // ClientQuests is static and outlives a world.
        String reset = code("src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest",
                "ClientQuestReset.java");
        assertTrue(reset.contains("ClientPlayerNetworkEvent.LoggingOut"));
        assertTrue(reset.contains("ClientQuests.clear()"));
        assertTrue(reset.contains("Dist.CLIENT"),
                "a client-only listener must never load on a dedicated server");
    }
}
