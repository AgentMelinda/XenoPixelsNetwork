package net.bullettrain.xenopixelsmod.client.npc.editor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcEditorScreenManifestTest {
    @Test
    void tabOrderMatchesTheReference() {
        assertEquals(List.of("Display", "Stats", "AI", "Inventory", "Advanced", "Global",
                        "DMZ", "Brain", "Delete", "X"),
                NpcEditorScreenManifest.tabs());
    }

    @Test
    void everyReferenceScreenshotIsRepresented() {
        for (int screenshot : IntStream.rangeClosed(15, 48).toArray()) {
            assertTrue(NpcEditorScreenManifest.pages().stream()
                            .anyMatch(page -> page.screenshots().contains(screenshot)),
                    "missing screenshot " + screenshot);
        }
    }

    @Test
    void advancedHubUsesTheReferenceButtonOrder() {
        assertEquals(List.of("Lines", "Factions", "Dialogs", "Sounds", "Night", "Linked",
                        "Scenes", "Marks"),
                NpcEditorScreenManifest.page(NpcEditorScreenManifest.ScreenId.ADVANCED)
                        .entries());
    }

    @Test
    void globalHubUsesTheReferenceButtonOrder() {
        assertEquals(List.of("Banks", "Factions", "Dialogs", "Quests", "Transport",
                        "PlayerData", "Recipes (no bench)", "Natural Spawns", "Linked"),
                NpcEditorScreenManifest.page(NpcEditorScreenManifest.ScreenId.GLOBAL)
                        .entries());
    }

    @Test
    void dmzSkillAndTechniqueEditorsAreReachableFromTheXenoNpcDmzPage() {
        assertTrue(NpcEditorScreenManifest.pages().stream()
                .anyMatch(page -> page.title().equals("Skills")
                        && page.parent() == NpcEditorScreenManifest.ScreenId.DMZ));
        assertTrue(NpcEditorScreenManifest.pages().stream()
                .anyMatch(page -> page.title().equals("Techniques")
                        && page.parent() == NpcEditorScreenManifest.ScreenId.DMZ));
        assertTrue(NpcEditorScreenManifest.page(NpcEditorScreenManifest.ScreenId.DMZ)
                .entries().containsAll(List.of("Skills", "Techniques", "Forms", "Attacks")));
    }
}
