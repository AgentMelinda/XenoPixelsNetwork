package net.bullettrain.xenopixelsmod.client.npc.dialog;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dialogue as bubbles above the NPC rather than a full-screen menu.
 *
 * <p>The conversation state and the palettes are plain data and are checked directly. The renderer
 * and the screen need a client, so those are source-shape checks - what they pin is the security
 * split and the two rules that stop a conversation becoming unanswerable.
 */
class DialogueBubbleTest {

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void thePalettesAreTheFourTheAtlasIsGeneratedIn() {
        assertEquals(java.util.List.of("BLUE", "GOLD", "GREEN", "RED"), NpcCombatProfile.PALETTES);
        assertEquals("BLUE", NpcCombatProfile.DEFAULT_PALETTE);
    }

    @Test
    void anUnknownPaletteFallsBackRatherThanBreakingTheBubble() {
        // These come off a save and out of a packet. A bad one should draw the default, not throw
        // in the middle of a render pass.
        assertEquals("BLUE", NpcCombatProfile.canonicalPalette(null));
        assertEquals("BLUE", NpcCombatProfile.canonicalPalette(""));
        assertEquals("BLUE", NpcCombatProfile.canonicalPalette("chartreuse"));
        assertEquals("GOLD", NpcCombatProfile.canonicalPalette("gold"));
        assertEquals("RED", NpcCombatProfile.canonicalPalette("  Red  "));
    }

    @Test
    void thePalettesRoundTripAndDefaultToTheSameThing() {
        NpcCombatProfile profile = new NpcCombatProfile();
        assertEquals("BLUE", profile.bubblePalette);
        assertEquals("GOLD", profile.optionPalette);

        profile.bubblePalette = "GREEN";
        profile.optionPalette = "GOLD";
        NpcCombatProfile back = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals("GREEN", back.bubblePalette);
        assertEquals("GOLD", back.optionPalette);
    }

    @Test
    void thePalettesReachTheClientThatDrawsThem() {
        // The profile is server-side and the bubbles are not, so these have to ride the visual
        // options tag - the same trap the mark and the battle power both hit.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.bubblePalette = "RED";
        profile.optionPalette = "GREEN";

        NpcCombatProfile client = new NpcCombatProfile();
        client.applyVisualOptions(profile.visualOptionsTag());
        assertEquals("RED", client.bubblePalette);
        assertEquals("GREEN", client.optionPalette);
    }

    @Test
    void bothRenderersActuallyReadThePalette() throws IOException {
        // The whitelist only admits a key once something consumes it. Both of these do.
        assertTrue(source("client/npc/speech/SpeechBubbleRenderer.java").contains("bubblePalette"),
                "the speech bubble should honour it");
        String dialogue = source("client/npc/dialog/DialogueBubbleRenderer.java");
        assertTrue(dialogue.contains("bubblePalette"), "and so should the dialogue line");
        assertTrue(dialogue.contains("optionPalette"), "and the options");
    }

    @Test
    void navigationIsLocalButConsequencesGoToTheServer() throws IOException {
        // TEXT only changes which node is shown, so resolving it client-side is fine. QUEST and
        // COMMAND have consequences, so the server re-reads its own dialogue and is sent nothing
        // but the index - which is what stops a crafted packet starting any quest it likes.
        // The deciding half moved out of the screen when a second input route appeared - the
        // crosshair - so that both answer options through one implementation instead of two that
        // drift. The invariant is unchanged; only its address is.
        String screen = source("client/npc/dialog/DialogueBubbleChoice.java");
        int text = screen.indexOf("case TEXT ->");
        int quest = screen.indexOf("case QUEST, COMMAND ->");
        assertTrue(text >= 0 && quest >= 0, "both branches should exist");

        String textBody = screen.substring(text, quest);
        assertFalse(textBody.contains("sendToServer"), "navigation needs no packet");

        String questBody = screen.substring(quest, Math.min(screen.length(), quest + 500));
        assertTrue(questBody.contains("sendToServer"), "a quest must go to the server");
        assertTrue(questBody.contains("index"), "and travel as an index");
        assertFalse(questBody.contains("option.quest()"), "never as a quest id");
    }

    @Test
    void aFilteredQuestOptionRetainsItsServerIndex() {
        XenoDialogue dialogue = new XenoDialogue("root", java.util.Map.of("root",
                new XenoDialogue.Node("Choose", java.util.List.of(
                        new XenoDialogue.Option("Available quest", XenoDialogue.OptionType.QUEST,
                                "", "example:quest", "", 2)))));
        DialogueBubbleSession session = DialogueBubbleSession.begin(7, dialogue, "Quest giver");

        assertEquals(2, DialogueBubbleChoice.serverOptionIndex(session, 0));
        assertEquals(-1, DialogueBubbleChoice.serverOptionIndex(session, 1));
    }

    @Test
    void questAndCommandBubblesSendTheStableServerIndex() throws IOException {
        String choices = source("client/npc/dialog/DialogueBubbleChoice.java");
        int quest = choices.indexOf("case QUEST, COMMAND ->");
        assertTrue(quest >= 0);
        String body = choices.substring(quest, Math.min(choices.length(), quest + 400));
        assertTrue(body.contains("serverOptionIndex(session, index)"),
                "consequential bubble choices must use the original unfiltered option index");
    }

    @Test
    void theLegacyDialogueScreenAlsoSendsTheOriginalOptionIndex() throws IOException {
        String screen = source("client/npc/XenoNpcDialogueScreen.java");
        int quest = screen.indexOf("case QUEST -> {");
        assertTrue(quest >= 0);
        String body = screen.substring(quest, Math.min(screen.length(), quest + 900));
        assertTrue(body.contains("serverOptionIndex(option)"),
                "filtered option identity must work in the retained dialogue screen too");
        assertTrue(screen.contains("int sourceIndex = option.sourceIndex()"),
                "the legacy screen helper must prefer the original server index");
    }

    @Test
    void aConversationCanAlwaysBeAnswered() throws IOException {
        // A bubble can end up behind the player or off the edge of the screen. Without a keyboard
        // route the conversation would be stuck open with no way to reply.
        String screen = source("client/npc/dialog/DialogueBubbleScreen.java");
        assertTrue(screen.contains("keyPressed("), "number keys should pick options too");
        assertTrue(screen.contains("key - 49"), "1-9 mapped to the option indices");
    }

    @Test
    void theOldScreenIsStillReachable() throws IOException {
        // Both implementations stay, so the two can be compared in play rather than one being
        // deleted on the strength of a preview.
        String handlers = source("client/ClientPacketHandlers.java");
        assertTrue(handlers.contains("dialogueBubbles"), "the switch should exist");
        assertTrue(handlers.contains("XenoNpcDialogueScreen.open("),
                "and the screen should still be reachable through it");

        Path old = RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/client/npc/XenoNpcDialogueScreen.java");
        assertTrue(Files.exists(old), "the proven screen must not have been deleted");
    }

    @Test
    void theBubblesDoNotDimTheWorldOrPauseTheGame() throws IOException {
        // The point of leaving the menu behind was keeping the world visible while an NPC talks.
        String screen = source("client/npc/dialog/DialogueBubbleScreen.java");
        assertTrue(screen.contains("isPauseScreen"), "a conversation should not pause the game");
        assertTrue(screen.contains("Deliberately not super.render"),
                "and should not paint the dimming layer");
    }

    @Test
    void anOptionBubbleThatWasNotDrawnCannotBeClicked() throws IOException {
        // hits() is republished every frame. Off screen, too far, or no conversation all clear it,
        // which is exactly when a click should select nothing.
        String renderer = source("client/npc/dialog/DialogueBubbleRenderer.java");
        assertTrue(renderer.contains("hits = List.of();"),
                "the rectangles should be cleared when nothing is drawn");
        assertTrue(renderer.contains("behindCamera()"),
                "and an option behind the camera should not get a usable rectangle");
    }

    @Test
    void speakerAndOptionsUseSeparateVerticalAnchors() throws IOException {
        String renderer = source("client/npc/dialog/DialogueBubbleRenderer.java");
        assertTrue(renderer.contains("OPTION_HEIGHT_FRACTION"),
                "the answer group should use a head-level anchor");
        assertTrue(renderer.contains("npc.getBbHeight() * OPTION_HEIGHT_FRACTION"),
                "the option anchor should be relative to the NPC body");
        assertTrue(renderer.contains("speakerAnchorHeight"),
                "the speaker bubble should be moved above the answer group");
        assertTrue(renderer.contains("DialogueBubbleLayout.groupHeight(optionHeights, rowChoices)"),
                "the speaker position should account for the entire answer group");
        assertTrue(renderer.contains("forward.scale(0.16)"),
                "dialogue bubbles should sit in front of the NPC");
    }
}
