package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When the toast fires, and when it must not. */
class QuestToastOverlayTest {

    private static ClientQuests.Entry quest(String id) {
        return new ClientQuests.Entry(id, id, "", "", 0, 1, false, "");
    }

    @Test
    void aNewQuestFiresTheToast() {
        ClientQuests.clear();
        ClientQuests.accept(List.of());
        ClientQuests.accept(List.of(quest("wolf_trouble")));
        assertEquals("wolf_trouble", QuestToastOverlay.lastShown());
    }

    @Test
    void aProgressUpdateDoesNotFireItAgain() {
        // Every kill pushes a fresh packet. If the toast fired on each one it would sit on screen
        // permanently, which is exactly what its four-second life is meant to prevent.
        ClientQuests.clear();
        ClientQuests.accept(List.of());
        ClientQuests.accept(List.of(quest("wolf_trouble")));
        QuestToastOverlay.reset();

        ClientQuests.accept(List.of(
                new ClientQuests.Entry("wolf_trouble", "wolf_trouble", "", "", 3, 5, false, "")));
        assertEquals("", QuestToastOverlay.lastShown(), "same quest, only more progress");
    }

    @Test
    void theFirstSyncAfterLoginDoesNotToastEveryExistingQuest() {
        // Otherwise a player relogging mid-playthrough is hit with one toast per active quest,
        // for quests they took hours ago.
        ClientQuests.clear();
        ClientQuests.accept(List.of(quest("a"), quest("b"), quest("c")));
        assertEquals("", QuestToastOverlay.lastShown(),
                "the first sync establishes a baseline, it does not announce it");
    }

    @Test
    void aSecondNewQuestAfterThatDoesToast() {
        ClientQuests.clear();
        ClientQuests.accept(List.of(quest("a")));
        ClientQuests.accept(List.of(quest("a"), quest("b")));
        assertEquals("b", QuestToastOverlay.lastShown());
    }

    @Test
    void aDisconnectSilencesIt() {
        // Otherwise a stale toast from the last world greets the player in the next one.
        ClientQuests.clear();
        ClientQuests.accept(List.of());
        ClientQuests.accept(List.of(quest("a")));
        assertEquals("a", QuestToastOverlay.lastShown());

        ClientQuests.clear();
        assertEquals("", QuestToastOverlay.lastShown());
    }

    @Test
    void itLastsFourSeconds() throws IOException {
        // Reusing the editor notice's reasoning: long enough to read, short enough not to become
        // furniture.
        String source = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest",
                        "QuestToastOverlay.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("4000L"), "four seconds, matching the editor notice");
    }

    @Test
    void itIsRegisteredAsAHudLayer() throws IOException {
        // An overlay nobody registered draws nothing, and compiles perfectly while doing so.
        String registration = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client",
                        "XenoHudRegistration.java"), StandardCharsets.UTF_8);
        assertTrue(registration.contains("QuestToastOverlay::render"),
                "matching the static ::render form the neighbouring layers use");
        assertTrue(registration.contains("xeno_quest_toast"), "with its own layer id");
    }
}
