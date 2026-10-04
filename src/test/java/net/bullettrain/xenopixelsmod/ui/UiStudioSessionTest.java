package net.bullettrain.xenopixelsmod.ui;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiStudioSessionTest {

    @Test
    void statsTemplateIsDmzMenuStats() throws Exception {
        UiStudioSession session = new UiStudioSession(loadStats());
        assertEquals("dmz_menu", session.document().kind);
        assertEquals("stats", session.document().page);
        assertTrue(session.canSave(), session.validate()::toString);
    }

    @Test
    void grantActionIsRejected() {
        UiStudioSession session = new UiStudioSession(loadStats());
        session.select(session.document().root.id);
        session.addNode("BUTTON");
        UiNode button = UiStudioSession.find(session.document().root, session.selectedId());
        session.applyInspector(new UiStudioSession.InspectorPatch(
                Integer.toString(button.x), Integer.toString(button.y),
                Integer.toString(button.w), Integer.toString(button.h),
                "0", "0", "0", "0",
                "TOP_LEFT", "true", "", "",
                "Grant", "", "/xenoskill grant"));
        List<String> errors = session.validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown action")), errors::toString);
        assertFalse(session.canSave());
    }

    @Test
    void uniqueIdsIncrement() {
        UiStudioSession session = new UiStudioSession(new UiDocument());
        session.addNode("PANEL");
        String first = session.selectedId();
        session.select(session.document().root.id);
        session.addNode("PANEL");
        String second = session.selectedId();
        assertNotEquals(first, second);
        assertTrue(first.startsWith("panel"));
        assertTrue(second.startsWith("panel"));
    }

    @Test
    void undoRestoresJson() {
        UiStudioSession session = new UiStudioSession(new UiDocument());
        session.document().kind = "dmz_menu";
        session.document().page = "stats";
        session.document().id = "demo";
        String before = session.toJson();
        session.addNode("NAV_TAB");
        assertNotEquals(before, session.toJson());
        session.undo();
        assertEquals(before, session.toJson());
    }

    @Test
    void newMenuIsDmzPlateNotTinyHud() {
        UiStudioSession session = new UiStudioSession(new UiDocument());
        session.newMenu(java.util.Set.of());
        assertEquals("dmz_menu", session.document().kind);
        assertEquals("custom", session.document().page);
        assertEquals(365, session.document().canvasW);
        assertEquals(293, session.document().canvasH);
        assertEquals(365, session.document().root.w);
        assertEquals(293, session.document().root.h);
        assertTrue(session.document().id.startsWith("dmz_menu_"));
        UiNode plate = session.flatten().stream()
                .filter(n -> "IMAGE".equals(n.type) && n.texture != null
                        && n.texture.contains("menunpc.png"))
                .findFirst()
                .orElse(null);
        assertTrue(plate != null, "blank menu must use the DMZ menunpc plate");
        assertTrue(session.canSave(), session.validate()::toString);
    }

    @Test
    void newHudIsNotDemoHud() {
        UiStudioSession session = new UiStudioSession(new UiDocument());
        session.newHud(java.util.Set.of("demo_hud"));
        assertEquals("hud", session.document().kind);
        assertNotEquals("demo_hud", session.document().id);
        assertTrue(session.document().id.startsWith("dmz_hud_"));
        assertTrue(session.flatten().stream().anyMatch(n -> "IMAGE".equals(n.type)));
        assertTrue(session.canSave(), session.validate()::toString);
    }

    @Test
    void newMenuSkipsTakenIds() {
        UiStudioSession session = new UiStudioSession(new UiDocument());
        session.newMenu(java.util.Set.of("dmz_menu_1"));
        assertEquals("dmz_menu_2", session.document().id);
    }

    @Test
    void saveAsChangesId() {
        UiStudioSession session = new UiStudioSession(new UiDocument());
        session.newMenu(java.util.Set.of());
        assertTrue(session.saveAs("my_stats"));
        assertEquals("my_stats", session.document().id);
        assertTrue(session.canSave());
    }

    @Test
    void menuPreviewIsCenteredHudPreviewIsOrigin() {
        int[] menu = StudioPreview.origin("dmz_menu", 720, 420, 365, 293);
        assertEquals((720 - 365) / 2, menu[0]);
        assertEquals((420 - 293) / 2, menu[1]);
        int[] hud = StudioPreview.origin("hud", 720, 420, 1920, 1080);
        assertEquals(0, hud[0]);
        assertEquals(0, hud[1]);
    }

    @Test
    void cyclePageStaysOnDmzMenu() {
        UiStudioSession session = new UiStudioSession(loadStats());
        session.cyclePage(1);
        assertEquals("dmz_menu", session.document().kind);
        assertEquals("skills", session.document().page);
    }

    @Test
    void additiveSelectionMovesEveryUnlockedNode() {
        UiStudioSession session = new UiStudioSession(new UiDocument());
        session.addNode("PANEL");
        String first = session.selectedId();
        session.select(session.document().root.id);
        session.addNode("TEXT");
        String second = session.selectedId();
        session.select(first);
        session.select(second, true);

        session.moveSelectionBy(8, 16);

        assertEquals(java.util.Set.of(first, second), session.selectedIds());
        assertEquals(16, UiStudioSession.find(session.document().root, first).x);
        assertEquals(24, UiStudioSession.find(session.document().root, second).y);
    }

    @Test
    void lockedAndEditorHiddenNodesAreNotCanvasSelectable() {
        UiStudioSession session = new UiStudioSession(new UiDocument());
        session.addNode("PANEL");
        String id = session.selectedId();
        session.setEditorLocked(id, true);
        assertFalse(session.isCanvasSelectable(id));
        session.setEditorLocked(id, false);
        session.setEditorHidden(id, true);
        assertFalse(session.isCanvasSelectable(id));
    }

    @Test
    void deletingMultiSelectionRemovesAllSelectedChildren() {
        UiStudioSession session = new UiStudioSession(new UiDocument());
        session.addNode("PANEL");
        String first = session.selectedId();
        session.select(session.document().root.id);
        session.addNode("TEXT");
        String second = session.selectedId();
        session.select(first);
        session.select(second, true);
        session.deleteSelected();
        assertEquals(null, UiStudioSession.find(session.document().root, first));
        assertEquals(null, UiStudioSession.find(session.document().root, second));
        assertEquals("root", session.selectedId());
    }

    private static UiDocument loadStats() {
        try (var in = UiStudioSessionTest.class.getResourceAsStream(
                "/assets/xenopixelsmod/ui/xeno_stats.json")) {
            assertTrue(in != null, "missing bundled xeno_stats.json");
            var loaded = UiDocumentIO.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(loaded.ok(), loaded.errors()::toString);
            return loaded.document();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
