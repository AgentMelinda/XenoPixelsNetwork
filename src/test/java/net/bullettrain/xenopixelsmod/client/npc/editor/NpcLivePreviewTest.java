package net.bullettrain.xenopixelsmod.client.npc.editor;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The visualizer, extracted so more than one screen can have it.
 *
 * <p>It used to live inline in {@code XenoNpcEditorScreen}, which is exactly why the appearance
 * screen had none - that is a separate {@code ScaledScreen} and there was nothing to reuse.
 *
 * <p>The standing rule this guards: the native NPC system does not depend on
 * {@code client/compat/npc/...}. That tree integrates with MyNPCs and CustomNPCs and assumes those
 * mods are installed. {@code NpcPreviewPanel} lives there and does a similar job; reaching for it
 * would have been less code and the wrong dependency.
 *
 * <p>Rendering needs a client, so these are source-shape checks.
 */
class NpcLivePreviewTest {

    @Test
    void previewAuraCanBeShownWhenTheNpcAuraSettingIsOff() throws IOException {
        String preview = source("client/npc/editor/NpcLivePreview.java");
        assertTrue(preview.contains("partialTick, showAura)"));
        assertFalse(preview.contains("showAura && auraOn"));
    }

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void theNativeScreensDoNotReachIntoTheCompatGuiTree() throws IOException {
        // Imports and real references only. These files *talk about* NpcPreviewPanel in comments,
        // explaining why it is not used - a check that fired on any mention would be telling us off
        // for writing down the reason.
        for (String file : new String[]{
                "client/npc/editor/NpcLivePreview.java",
                "client/npc/XenoNpcEditorScreen.java",
                "client/npc/XenoNpcAppearanceScreen.java"}) {
            for (String line : source(file).split("\n")) {
                String code = line.strip();
                if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) {
                    continue;
                }
                assertFalse(code.contains("compat.npc.gui"),
                        file + " must not import the compat GUI package: " + code);
                assertFalse(code.contains("compat.npc.mynpcs"),
                        file + " must not depend on the MyNPCs integration: " + code);
                assertFalse(code.contains("NpcPreviewPanel"),
                        file + " must not use the MyNPCs/CustomNPCs preview panel: " + code);
            }
        }
    }

    @Test
    void theOneCompatImportItDoesKeepIsADragonMineZOne() throws IOException {
        // NpcFullDmzRenderer is under client/compat/npc/ but is a DragonMineZ integration, and
        // DragonMineZ is a hard dependency of this mod - unlike MyNPCs, which is optional. Worth
        // naming so the rule above is not read as "nothing under compat, ever".
        String preview = source("client/npc/editor/NpcLivePreview.java");
        assertTrue(preview.contains("NpcFullDmzRenderer"),
                "the DragonMineZ preview renderer is still the draw call");
        assertTrue(preview.contains("DragonMineZ is a hard dependency"),
                "and the reason that is allowed should be written down");
    }

    @Test
    void bothScreensDrawTheSameVisualizer() throws IOException {
        for (String file : new String[]{"client/npc/XenoNpcEditorScreen.java",
                "client/npc/XenoNpcAppearanceScreen.java"}) {
            String text = source(file);
            assertTrue(text.contains("NpcLivePreview"), file + " should use the shared visualizer");
            assertTrue(text.contains("preview.render("), file + " should draw it");
            assertTrue(text.contains("preview.place("), file + " should place it in init");
        }
    }

    @Test
    void theEditorNoLongerCarriesItsOwnCopy() throws IOException {
        // The extraction is only worth anything if the inline version went away; two copies would
        // drift the moment one is touched.
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        assertFalse(editor.contains("StudioViewport"),
                "the orbit state belongs to the component now");
        assertFalse(editor.contains("private boolean showAura"),
                "so does the aura toggle state");
        assertFalse(editor.contains("private LivingEntity previewNpc()"),
                "and the entity lookup");
    }

    @Test
    void theAppearanceScreenCanDragAndZoomTheModel() throws IOException {
        // It had no mouse handlers at all beyond paging, so without these the panel would be there
        // and inert.
        String screen = source("client/npc/XenoNpcAppearanceScreen.java");
        assertTrue(screen.contains("preview.beginOrbit("), "drag to rotate");
        assertTrue(screen.contains("preview.endDrag()"), "and release");
        assertTrue(screen.contains("preview.drag("), "and the drag itself");
        assertTrue(screen.contains("preview.scroll("), "wheel to zoom");
    }

    @Test
    void everyHitTestConvertsToCanvasUnitsFirst() throws IOException {
        // The panel is not a widget, so ScaledScreen does not convert for it. It also sits outside
        // the frame, which is where an unconverted test looks plausible and is still wrong.
        String screen = source("client/npc/XenoNpcAppearanceScreen.java");
        int at = screen.indexOf("preview.contains(");
        assertTrue(at >= 0, "there should be a bounds test");
        while (at >= 0) {
            String call = screen.substring(at, Math.min(screen.length(), at + 60));
            assertTrue(call.contains("toUiX(") && call.contains("toUiY("),
                    "bounds test at " + at + " must convert: " + call);
            at = screen.indexOf("preview.contains(", at + 1);
        }
    }

    @Test
    void theWheelChecksTheModelBeforeItPagesTheBody() throws IOException {
        // Before the visualizer was there the wheel always paged. If paging stayed first, zooming
        // would page instead, which reads as the zoom being broken.
        String screen = source("client/npc/XenoNpcAppearanceScreen.java");
        int scroll = screen.indexOf("public boolean mouseScrolled(");
        assertTrue(scroll >= 0, "the scroll handler should still exist");
        String body = screen.substring(scroll, Math.min(screen.length(), scroll + 900));
        assertTrue(body.indexOf("preview.contains(") < body.indexOf("turnPage("),
                "the zoom check has to come before the page turn");
    }

    @Test
    void theAppearanceScreenUsesTheNarrowFrameSoThePanelFitsBesideIt() throws IOException {
        String screen = source("client/npc/XenoNpcAppearanceScreen.java");
        assertTrue(screen.contains("xeno_editor_panel_w420"),
                "the 600-wide frame leaves no room for a panel beside it");
        assertTrue(screen.contains("blockW"),
                "frame and panel should be centred as one block, not the frame alone");
    }
}
