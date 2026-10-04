package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the Curios block sits relative to the NPC mod's own inventory widgets.
 *
 * <p>The block is laid out at fixed coordinates on a screen another mod owns, and it landed exactly
 * on top of that mod's Min Exp and Max Exp fields and its Normal/Auto button. Nothing in this
 * repository described those widgets, so there was no way to notice the clash except by opening the
 * screen and looking at it.
 *
 * <p>The rectangles below were read out of {@code espi.mynpcs.client.gui.mainmenu.GuiNPCInv.init} in
 * the shipped jar, so they are facts rather than estimates. Pinning them means a future move of
 * either layout fails here instead of silently overlapping again.
 */
class NpcCuriosLayoutTest {

    /** A widget rectangle in screen-local coordinates. */
    private record Rect(String name, int x, int y, int width, int height) {
        boolean intersects(Rect other) {
            return x < other.x + other.width && other.x < x + width
                    && y < other.y + other.height && other.y < y + height;
        }
    }

    /** My NPCs' own widgets on the Inventory tab, from GuiNPCInv.init. */
    private static final Rect[] HOST_WIDGETS = {
            new Rect("Min Exp label", 118, 18, 60, 10),
            new Rect("Min Exp field", 108, 29, 60, 20),
            new Rect("Max Exp label", 118, 52, 60, 10),
            new Rect("Max Exp field", 108, 63, 60, 20),
            new Rect("Normal/Auto button", 88, 88, 80, 20),
    };

    private static Rect curiosBlock() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < NpcCuriosInventory.extraSlotCount(); i++) {
            int x = NpcCuriosInventory.slotX(i);
            int y = NpcCuriosInventory.slotY(i);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + NpcCuriosInventory.SLOT_SIZE);
            maxY = Math.max(maxY, y + NpcCuriosInventory.SLOT_SIZE);
        }
        return new Rect("curios block", minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * The block really does cover the host's widgets.
     *
     * <p>Kept as an assertion rather than a comment because it is the reason the block is hidden by
     * default. If a later change moves the block somewhere free, this test should be revisited
     * deliberately — not left passing by accident while the toggle stays.
     */
    @Test
    void theBlockOverlapsTheHostWidgetsWhichIsWhyItIsHidden() {
        Rect block = curiosBlock();
        for (Rect widget : HOST_WIDGETS) {
            assertTrue(block.intersects(widget),
                    widget.name() + " no longer sits under the Curios block; if the block was moved "
                            + "to free space the default-hidden toggle may no longer be needed");
        }
    }

    /**
     * Hidden by default is the whole fix, since there is nowhere free to move to.
     *
     * <p>Static state, so this also guards against something flipping the default on at class load.
     */
    @Test
    void theBlockStartsHidden() {
        assertFalse(NpcCuriosInventory.slotsVisible(),
                "the Curios slots would cover the Exp fields the moment the screen opened");
    }

    @Test
    void theToggleShowsAndHides() {
        try {
            NpcCuriosInventory.setSlotsVisible(true);
            assertTrue(NpcCuriosInventory.slotsVisible());
            NpcCuriosInventory.setSlotsVisible(false);
            assertFalse(NpcCuriosInventory.slotsVisible());
        } finally {
            NpcCuriosInventory.setSlotsVisible(false);
        }
    }

    /**
     * The slot count stays fixed however the block is laid out.
     *
     * <p>Server and client agree on menu indices only because this number never varies — including
     * when the Curios capability is missing on one side. Hiding the block had to be done through
     * slot visibility for exactly this reason, and a future "just don't add the empty ones" would
     * desync the menu.
     */
    @Test
    void everySlotIsAlwaysPresentEvenWhenHidden() {
        assertTrue(NpcCuriosInventory.extraSlotCount() == NpcCuriosInventory.SLOT_IDS.length * 2,
                "slot count must stay a fixed function of the id list, not of what an NPC has");
    }

    /** Slots stay on their grid, so the painted wells line up with them. */
    @Test
    void slotsSitOnARegularGrid() {
        for (int i = 0; i < NpcCuriosInventory.extraSlotCount(); i++) {
            int dx = NpcCuriosInventory.slotX(i) - NpcCuriosInventory.START_X;
            int dy = NpcCuriosInventory.slotY(i) - NpcCuriosInventory.START_Y;
            assertTrue(dx >= 0 && dx % (NpcCuriosInventory.SLOT_SIZE + NpcCuriosInventory.COL_GAP) == 0,
                    "slot " + i + " is off the column grid");
            assertTrue(dy >= 0 && dy % NpcCuriosInventory.SLOT_SIZE == 0,
                    "slot " + i + " is off the row grid");
        }
    }
}
