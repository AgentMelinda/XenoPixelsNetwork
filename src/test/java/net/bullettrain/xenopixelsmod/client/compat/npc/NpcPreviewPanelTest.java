package net.bullettrain.xenopixelsmod.client.compat.npc;

import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcPreviewPanel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the visualizer's placement contract: it parks to the right of the 420px
 * {@code GuiNPCInterface2} body, and only the model window — not the controls row below it — is
 * draggable or zoomable. A null NPC is fine here because the geometry never dereferences it.
 */
class NpcPreviewPanelTest {
    private final NpcPreviewPanel panel = new NpcPreviewPanel(null, null);

    @Test
    void parksBesideTheFourTwentyBodyWithAGap() {
        assertEquals(420 + NpcPreviewPanel.GAP, panel.left(0));
        assertEquals(428, panel.left(0));
        // left() offsets from the host's guiLeft, it does not clamp to it.
        assertEquals(120 + 428, panel.left(120));
        assertEquals(7, panel.top(7));
    }

    @Test
    void modelWindowIsHitButTheControlsRowIsNot() {
        assertTrue(panel.inside(0, 0, 430, 20));
        // The controls row sits below the scissor box, so it must not start a drag.
        assertFalse(panel.inside(0, 0, 430, 160));
        // One pixel past the right edge.
        assertFalse(panel.inside(0, 0, 428 + NpcPreviewPanel.WIDTH, 20));
        // Left of the panel, still inside the host body.
        assertFalse(panel.inside(0, 0, 419, 20));
    }

    @Test
    void zoomOnlyAppliesInsideTheModelWindow() {
        assertFalse(panel.mouseScrolled(0, 0, 419, 20, 1.0));
        assertTrue(panel.mouseScrolled(0, 0, 430, 20, 1.0));
        assertFalse(panel.mouseScrolled(0, 0, 430, 160, 1.0));
    }

    @Test
    void panelNeverOverlapsTheHostBody() {
        assertTrue(panel.left(0) >= 420, "panel must clear the host body");
        assertTrue(NpcPreviewPanel.WIDTH > 0 && NpcPreviewPanel.HEIGHT > 0);
    }
}