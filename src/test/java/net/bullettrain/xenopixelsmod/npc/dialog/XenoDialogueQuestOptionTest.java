package net.bullettrain.xenopixelsmod.npc.dialog;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dialogue quest options, which used to be accepted and thrown away.
 *
 * <p>{@code XenoNpcDialoguePacket} handled {@code COMMAND} and ignored everything else, with a
 * comment saying quests waited on a quest engine. {@code ParallelQuests} exists and now pays real
 * rewards, so the option had somewhere to go and was still being dropped - an NPC offering a quest
 * did nothing, which is exactly the dead-control shape this project keeps out of the editor.
 *
 * <p>Source-shape checks: the handler needs a server and a player, so what is pinned is that the
 * option reaches the quest engine, that it goes through the shared entry point, and that the
 * client's enabled/disabled split matches what the server will actually do.
 */
class XenoDialogueQuestOptionTest {

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void theServerActsOnAQuestOptionInsteadOfDroppingIt() throws IOException {
        String packet = source("network/packet/XenoNpcDialoguePacket.java");
        assertTrue(packet.contains("case QUEST ->"), "the handler must have a QUEST branch");
        assertTrue(packet.contains("offerQuest("), "and it must actually offer the quest");
    }

    @Test
    void filteringCarriesOriginalOptionPositionAndTheServerRechecksAvailability() throws IOException {
        String filter = source("features/progression/QuestDialogueFilter.java");
        assertTrue(filter.contains("for (int index = 0; index < original.size(); index++)"));
        assertTrue(filter.contains("option.quest(), option.command(), sourceIndex, option.palette())"),
                "visible options must retain both their original index and answer color");

        String packet = source("network/packet/XenoNpcDialoguePacket.java");
        assertTrue(packet.contains("node.options().get(optionIndex)"),
                "the server resolves the choice from its current dialogue");
        assertTrue(packet.contains("QuestDialogueFilter.canOffer("),
                "and rechecks current quest availability before starting it");
        assertTrue(packet.contains("ParallelQuests.start(player, questId, giver)"));
    }

    @Test
    void itGoesThroughTheSameEntryPointAsTheCommand() throws IOException {
        // Not a second copy of "are you already on a quest" living in a packet handler. One set of
        // rules, and one place that knows the quest ids.
        String packet = source("network/packet/XenoNpcDialoguePacket.java");
        assertTrue(packet.contains("ParallelQuests.start("),
                "a dialogue offer should take the same path /xenoquest start takes");
    }

    @Test
    void everyRefusalIsReportedRatherThanSwallowed() throws IOException {
        // ParallelQuests.start returns a reason string, or null on success. Ignoring that return
        // is how "I clicked and nothing happened" gets built.
        String packet = source("network/packet/XenoNpcDialoguePacket.java");
        int offer = packet.indexOf("private static void offerQuest(");
        assertTrue(offer >= 0, "offerQuest should exist");
        String body = packet.substring(offer, Math.min(packet.length(), offer + 900));
        assertTrue(body.contains("String refusal"), "the refusal must be captured");
        assertTrue(body.contains("sendSystemMessage"), "and shown to the player");
    }

    @Test
    void theClientEnablesQuestOptionsAndStillDisablesRoleOnes() throws IOException {
        String screen = source("client/npc/XenoNpcDialogueScreen.java");
        int usable = screen.indexOf("private static boolean isUsable(");
        assertTrue(usable >= 0, "the enabled/disabled split should still exist");
        String body = screen.substring(usable, Math.min(screen.length(), usable + 900));

        assertTrue(body.contains("case QUEST ->"), "QUEST needs its own branch now");
        assertTrue(body.contains("case ROLE -> false"),
                "ROLE has no economy-role system and must stay disabled");
    }

    @Test
    void aQuestOptionWithNoQuestIdIsNotClickable() throws IOException {
        // It could only report that it names no quest, which is not worth a click. Better to show
        // it disabled than to let someone press it and get told off.
        String screen = source("client/npc/XenoNpcDialogueScreen.java");
        int usable = screen.indexOf("private static boolean isUsable(");
        String body = screen.substring(usable, Math.min(screen.length(), usable + 900));
        assertTrue(body.contains("option.quest()"), "the id must be checked before enabling");
        assertTrue(body.contains("isBlank()"), "including the blank case");
    }

    @Test
    void theClientSendsTheIndexRatherThanTheQuestId() throws IOException {
        // The server re-reads its own dialogue and trusts nothing but the index - the rule the
        // COMMAND path already followed. Sending a quest id would let a crafted packet start any
        // quest it liked.
        String screen = source("client/npc/XenoNpcDialogueScreen.java");
        int quest = screen.indexOf("case QUEST -> {");
        assertTrue(quest >= 0, "the screen should have a QUEST branch");
        String body = screen.substring(quest, Math.min(screen.length(), quest + 700));
        assertTrue(body.contains("serverOptionIndex(option)"),
                "send the original server index even when earlier offers are hidden");
        assertTrue(screen.contains("int sourceIndex = option.sourceIndex()"),
                "prefer the canonical index preserved by the server filter");
        assertTrue(!body.contains("option.quest()"),
                "the quest id must not travel in the packet");
    }

    @Test
    void theStaleCommentAboutAMissingQuestEngineIsGone() throws IOException {
        // It said quest options waited on an engine that now exists. A comment that is no longer
        // true is worse than none: the next person reads it and stops looking.
        String packet = source("network/packet/XenoNpcDialoguePacket.java");
        assertTrue(!packet.contains("until the quest engine exists"),
                "that comment describes a state this no longer has");
    }
}
